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
        val temp = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0

        var minutes: Int? = null
        var estimated = false
        if (charging && !full) {
            if (Build.VERSION.SDK_INT >= 28) {
                val ms = bm.computeChargeTimeRemaining()
                if (ms > 0) minutes = (ms / 60_000L).toInt().coerceAtLeast(1)
            }
            if (minutes == null) {
                // Estimation maison : capacité restante / courant de charge.
                val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // µAh
                val now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)       // µA
                if (counter > 0 && now != 0 && now != Int.MIN_VALUE && level in 1..99) {
                    var current = abs(now.toDouble())
                    if (current < 20_000) current *= 1000.0 // certains téléphones donnent des mA
                    val capacity = counter / (level / 100.0)
                    val hours = (capacity - counter) / current
                    if (hours > 0.01 && hours < 15) {
                        minutes = (hours * 60).roundToInt().coerceAtLeast(1)
                        estimated = true
                    }
                }
            }
        }

        val advice = when {
            full -> "batterie pleine, tu peux débrancher"
            charging -> "en train de charger"
            level <= 15 -> "à recharger tout de suite"
            level <= 30 -> "à recharger bientôt"
            else -> "pas besoin de recharger pour l'instant"
        }
        return JSONObject()
            .put("pourcentage", level)
            .put("en_charge", charging)
            .put("pleine", full)
            .put("source", source ?: JSONObject.NULL)
            .put("temperature_c", temp)
            .put("minutes_avant_pleine_charge", minutes ?: JSONObject.NULL)
            .put("estimation_approximative", estimated)
            .put("conseil", advice)
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
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 10 * 60_000L) return remember(last)

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

    private fun remember(l: Location): Pair<Double, Double> {
        Store.lastLat = l.latitude
        Store.lastLon = l.longitude
        Store.lastLocTime = System.currentTimeMillis()
        return l.latitude to l.longitude
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(ctx: Context, lm: LocationManager, provider: String): Location? =
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
