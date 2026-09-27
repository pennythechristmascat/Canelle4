package fr.canelle.compagnon

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.provider.AlarmClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ce que l'écran principal peut faire pour les outils (demander une autorisation, par exemple). */
interface ToolHost {
    suspend fun ensureLocationPermission(): Boolean
}

/** Accès au matériel et aux applications du téléphone. */
object Device {

    // ---------------------------------------------------------------- batterie

    fun battery(ctx: Context): JSONObject {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        var level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level !in 0..100 && sticky != null) {
            val l = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            level = if (l >= 0 && s > 0) l * 100 / s else 0
        }
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val full = status == BatteryManager.BATTERY_STATUS_FULL || (charging && level >= 100)
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "secteur"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "sans fil"
            else -> null
        }
        // valeurs impossibles écartées (null = « — » dans l'appli plutôt qu'un chiffre faux)
        val temp: Double? = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE && it != 0 }?.let { it / 10.0 }?.takeIf { it in -20.0..90.0 }
        val voltsRaw = (sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0).let { if (it > 100) it / 1000.0 else it.toDouble() }
        // une batterie de téléphone est entre 2,5 et 4,8 V (5 V, c'est la tension du chargeur, pas de la batterie)
        val volts = voltsRaw.takeIf { it in 2.5..4.8 } ?: 0.0
        val health = when (sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "bonne"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "surchauffe"
            BatteryManager.BATTERY_HEALTH_DEAD -> "usée"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "surtension"
            BatteryManager.BATTERY_HEALTH_COLD -> "trop froide"
            else -> null
        }
        val cycles = sticky?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1

        // Courant instantané : les fabricants ne sont pas d'accord sur l'unité (µA ou mA) ni sur le signe.
        val rawNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        var currentMa: Double? = null
        if (rawNow != 0 && rawNow != Int.MIN_VALUE && rawNow != Int.MAX_VALUE) {
            val a0 = abs(rawNow.toDouble())
            currentMa = (if (a0 > 20_000) a0 / 1000.0 else a0).takeIf { it in 5.0..10_000.0 }
        }
        val watts = if (currentMa != null && volts > 0) volts * currentMa / 1000.0 else null
        // Capacité : d'abord celle d'origine déclarée par le fabricant (quand Android la donne),
        // sinon une estimation (charge restante ÷ niveau), seulement si elle est plausible.
        val design = designCapacity(ctx)
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // µAh
        val estimated = if (counter > 0 && level in 10..100) ((counter / 1000.0) / (level / 100.0)).takeIf { it in 1000.0..20_000.0 } else null
        val capacityMah = design ?: estimated
        val capacitySource = if (design != null) "origine" else if (estimated != null) "estimation" else null

        BatteryWatch.sample(level, charging)
        val measured = BatteryWatch.rate(charging)

        var minutes: Int? = null
        var method: String? = null
        // temps plausible : au moins 1/3 de minute par % restant (3 %/min au maximum), au plus 24 h
        fun plausible(m: Int) = m >= (100 - level) / 3 && m <= 24 * 60
        if (charging && !full) {
            // 1) l'estimation d'Android, celle que le téléphone affiche dans ses réglages
            val ms = bm.computeChargeTimeRemaining()
            if (ms > 0) {
                val m = (ms / 60_000L).toInt().coerceAtLeast(1)
                if (plausible(m)) { minutes = m; method = "android" }
            }
            // 2) la vitesse réellement mesurée sur ce téléphone, avec ce chargeur
            if (minutes == null && measured != null) {
                val m = BatteryWatch.minutesToFull(level, measured)
                if (plausible(m)) { minutes = m; method = "mesure" }
            }
            // 3) capacité restante / courant de charge, avec le ralentissement après 80 %
            if (minutes == null && currentMa != null && currentMa > 50 && capacityMah != null && level in 1..99) {
                val perMin = currentMa / capacityMah * 100.0 / 60.0 // % par minute au courant actuel
                if (perMin > 0.01) {
                    val m = BatteryWatch.minutesToFull(level, perMin)
                    if (plausible(m)) { minutes = m; method = "courant" }
                }
            }
        }
        // autonomie restante quand le téléphone est débranché
        val autonomy = if (!charging && measured != null && measured > 0.0) (level / measured).roundToInt() else null

        val advice = when {
            full -> "batterie pleine, tu peux débrancher"
            charging -> "en train de charger"
            level <= 5 -> "le téléphone va s'éteindre s'il n'est pas branché"
            level <= 15 -> "à recharger maintenant"
            level <= 20 -> "prévoir de recharger bientôt"
            else -> "pas besoin de recharger pour l'instant"
        }
        fun r1(v: Double) = (v * 10).roundToInt() / 10.0
        return JSONObject()
            .put("pourcentage", level)
            .put("en_charge", charging)
            .put("pleine", full)
            .put("source", source ?: JSONObject.NULL)
            .put("temperature_c", temp ?: JSONObject.NULL)
            .put("tension_v", if (volts > 0) r1(volts) else JSONObject.NULL)
            .put("courant_ma", currentMa?.roundToInt() ?: JSONObject.NULL)
            .put("puissance_w", watts?.let { r1(it) } ?: JSONObject.NULL)
            .put("sante", health ?: JSONObject.NULL)
            .put("cycles", if (cycles >= 0) cycles else JSONObject.NULL)
            .put("capacite_mah", capacityMah?.roundToInt() ?: JSONObject.NULL)
            .put("capacite_source", capacitySource ?: JSONObject.NULL)
            .put("vitesse_pct_heure", measured?.let { r1(it * 60) } ?: JSONObject.NULL)
            .put("minutes_avant_pleine_charge", minutes ?: JSONObject.NULL)
            .put("methode_estimation", method ?: JSONObject.NULL)
            .put("estimation_approximative", method != "android" && method != "mesure")
            .put("autonomie_minutes", autonomy ?: JSONObject.NULL)
            .put("conseil", advice)
    }

    /**
     * Capacité d'origine de la batterie (mAh), déclarée par le fabricant dans le profil d'alimentation d'Android.
     * Ce n'est pas une API publique : si le téléphone la refuse, on renvoie null.
     */
    private var designCache: Double? = null
    private var designTried = false
    private fun designCapacity(ctx: Context): Double? {
        if (designTried) return designCache
        designTried = true
        designCache = runCatching {
            val cls = Class.forName("com.android.internal.os.PowerProfile")
            val profile = cls.getConstructor(Context::class.java).newInstance(ctx)
            (cls.getMethod("getBatteryCapacity").invoke(profile) as Double).takeIf { it in 1000.0..20_000.0 }
        }.getOrNull()
        return designCache
    }

    // ---------------------------------------------------------------- position

    fun hasLocationPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun location(ctx: Context, foreground: Boolean, host: ToolHost?): Pair<Double, Double>? {
        if (!hasLocationPermission(ctx)) {
            val granted = foreground && host != null && host.ensureLocationPermission()
            if (!granted) return Store.lastPosition()
        }
        // position actualisée toutes les 2 minutes quand l'application est ouverte (LocationKeeper)
        if (Store.lastLocTime > 0L && System.currentTimeMillis() - Store.lastLocTime < 150_000L) return Store.lastPosition()
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 150_000L) return remember(last)

        if (foreground) {
            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider != null) {
                val fresh = withTimeoutOrNull(12_000L) { currentLocation(ctx, lm, provider) }
                if (fresh != null) return remember(fresh)
            }
        }
        return last?.let { remember(it) } ?: Store.lastPosition()
    }

    fun remember(l: Location): Pair<Double, Double> {
        Store.lastLat = l.latitude
        Store.lastLon = l.longitude
        Store.lastLocTime = System.currentTimeMillis()
        return l.latitude to l.longitude
    }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(ctx: Context, lm: LocationManager, provider: String): Location? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    cont.invokeOnCancellation { signal.cancel() }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                }
            }
        }

    // ---------------------------------------------------------------- Google Maps

    private const val MAPS_PACKAGE = "com.google.android.apps.maps"

    private fun tryStart(ctx: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun openMapsUri(ctx: Context, uri: String, fallbackUrl: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        return tryStart(ctx, Intent(intent).setPackage(MAPS_PACKAGE)) ||
            tryStart(ctx, intent) ||
            tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
    }

    fun mapsSearch(ctx: Context, query: String, near: Pair<Double, Double>?): Boolean {
        val q = Uri.encode(query)
        val geo = if (near != null) "geo:${near.first},${near.second}?q=$q" else "geo:0,0?q=$q"
        return openMapsUri(ctx, geo, "https://www.google.com/maps/search/?api=1&query=$q")
    }

    fun mapsPlace(ctx: Context, lat: Double, lon: Double, name: String): Boolean =
        openMapsUri(ctx, "geo:$lat,$lon?q=$lat,$lon(${Uri.encode(name)})", "https://www.google.com/maps/search/?api=1&query=$lat,$lon")

    fun openMaps(ctx: Context, near: Pair<Double, Double>?): Boolean {
        val geo = if (near != null) "geo:${near.first},${near.second}" else "geo:0,0"
        return openMapsUri(ctx, geo, "https://www.google.com/maps")
    }

    fun mapsNavigateQuery(ctx: Context, destination: String): Boolean {
        val q = Uri.encode(destination)
        return openMapsUri(ctx, "google.navigation:q=$q", "https://www.google.com/maps/dir/?api=1&destination=$q")
    }

    fun showAlarms(ctx: Context): Boolean = tryStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun mapsNavigate(ctx: Context, lat: Double, lon: Double): Boolean =
        openMapsUri(ctx, "google.navigation:q=$lat,$lon", "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon")

    // ---------------------------------------------------------------- réveils

    fun setAlarm(ctx: Context, hour: Int, minute: Int, label: String, days: List<Int>?): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        if (!days.isNullOrEmpty()) intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
        return tryStart(ctx, intent)
    }

    fun setTimer(ctx: Context, seconds: Int, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return tryStart(ctx, intent)
    }
}
