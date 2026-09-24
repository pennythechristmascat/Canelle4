package fr.canelle.compagnon

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Tant que l'application est à l'écran, actualise la position toutes les 2 minutes,
 * pour que la météo et les lieux ne soient jamais calculés sur une vieille position.
 * Le nom de la ville est mis à jour (si on a bougé de plus d'un kilomètre) seulement quand on demande la météo ou un lieu.
 * S'arrête dès que l'application passe en arrière-plan.
 */
object LocationKeeper {
    private const val EVERY_MS = 120_000L

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var appCtx: Context? = null
    @Volatile private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            refresh()
            handler.postDelayed(this, EVERY_MS)
        }
    }

    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        if (running) return
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        job?.cancel()
    }

    private fun refresh() {
        val ctx = appCtx ?: return
        if (!Device.hasLocationPermission(ctx)) return
        if (job?.isActive == true) return
        job = scope.launch {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { p -> runCatching { lm.allProviders.contains(p) && lm.isProviderEnabled(p) }.getOrDefault(false) }
            var fix: Location? = null
            for (p in providers) {
                fix = runCatching { withTimeoutOrNull(20_000L) { Device.currentLocation(ctx, lm, p) } }.getOrNull()
                if (fix != null) break
            }
            if (fix == null) return@launch
            // La position reste sur le téléphone : le nom de la ville n'est demandé à internet
            // que lorsque l'utilisateur demande la météo ou un lieu (voir Tools).
            Device.remember(fix)
        }
    }

    /** Met à jour le nom de la ville si on s'est déplacé de plus d'un kilomètre. */
    suspend fun updateCity(lat: Double, lon: Double): String? {
        val d = FloatArray(1)
        Location.distanceBetween(lat, lon, Store.placeLat, Store.placeLon, d)
        if (Store.placeName.isNotBlank() && d[0] < 1000f) return Store.placeName
        val name = cityName(lat, lon) ?: return null
        Store.placeName = name
        Store.placeLat = lat
        Store.placeLon = lon
        return name
    }

    /** Nom de la commune (OpenStreetMap). */
    suspend fun cityName(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&accept-language=fr&lat=$lat&lon=$lon"
            val a = JSONObject(Net.get(url)).optJSONObject("address")
            listOf("city", "town", "village", "municipality").map { a?.optString(it).orEmpty() }.firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }
}
