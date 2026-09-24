package fr.canelle.compagnon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Surveille la batterie :
 * - note l'heure exacte de chaque changement de pourcentage, pour mesurer la vraie vitesse
 *   de charge (ou de décharge) de CE téléphone avec CE chargeur ;
 * - prévient à 20 %, 15 % et 5 % (dans l'application si elle est ouverte, sinon par notification).
 */
object BatteryWatch {

    /** Appelé quand l'application est à l'écran : Canelle annonce l'alerte lui-même. */
    @Volatile var uiAlert: ((threshold: Int, level: Int) -> Unit)? = null

    // (heure en ms, pourcentage) à chaque changement, pour la session en cours (charge ou décharge)
    private val steps = ArrayList<Pair<Long, Int>>()
    private var stepsCharging: Boolean? = null
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val o = org.json.JSONObject(Store.battSteps)
            stepsCharging = if (o.has("charging")) o.getBoolean("charging") else null
            val a = o.getJSONArray("steps")
            for (i in 0 until a.length()) {
                val s = a.getJSONArray(i)
                steps.add(s.getLong(0) to s.getInt(1))
            }
        }
    }

    private fun save() {
        val a = JSONArray()
        steps.forEach { a.put(JSONArray().put(it.first).put(it.second)) }
        Store.battSteps = org.json.JSONObject().put("charging", stepsCharging ?: false).put("steps", a).toString()
    }

    /** À appeler à chaque lecture de la batterie. */
    @Synchronized
    fun sample(level: Int, charging: Boolean, now: Long = System.currentTimeMillis()) {
        load()
        if (stepsCharging != charging) {
            // on vient de brancher ou de débrancher : nouvelle session
            steps.clear()
            stepsCharging = charging
            steps.add(now to level)
            save()
            return
        }
        val last = steps.lastOrNull()
        if (last == null || last.second != level) {
            steps.add(now to level)
            while (steps.size > 60) steps.removeAt(0)
            save()
        }
    }

    /**
     * Vitesse mesurée en % par minute (toujours positive), ou null s'il n'y a pas encore assez de mesures.
     * On prend les changements récents et on tient compte du temps écoulé depuis le dernier,
     * pour ne pas surestimer quand la charge ralentit.
     */
    @Synchronized
    fun rate(charging: Boolean, now: Long = System.currentTimeMillis()): Double? {
        load()
        if (stepsCharging != charging || steps.size < 3) return null
        val window = if (charging) 45 * 60_000L else 3 * 3_600_000L
        val recent = steps.filter { now - it.first <= window }
        if (recent.size < 3) return null
        // le premier point est l'arrivée sur un nouveau pourcentage : on part du 2e pour avoir des paliers complets
        val first = recent[1]
        val last = recent.last()
        val delta = kotlin.math.abs(last.second - first.second)
        if (delta < 1) return null
        val span = (last.first - first.first).toDouble()
        val step = span / delta
        val late = (now - last.first - step).coerceAtLeast(0.0) // on attend le palier suivant depuis plus longtemps que prévu
        val minutes = (span + late) / 60_000.0
        if (minutes < 1.0) return null
        return delta / minutes
    }

    /** Minutes avant 100 % d'après la vitesse mesurée (la charge ralentit après 80 %). */
    fun minutesToFull(level: Int, ratePerMin: Double): Int {
        val t = if (level < 80) (80 - level) / ratePerMin + 20 / (ratePerMin * 0.45)
        else (100 - level) / ratePerMin * 1.15
        return t.roundToInt().coerceAtLeast(1)
    }

    // ---------------------------------------------------------------- alertes 20 / 15 / 5 %

    @Synchronized
    fun check(ctx: Context, level: Int, charging: Boolean) {
        if (charging || level > 22) {
            if (Store.battAlert != 0) Store.battAlert = 0
            return
        }
        val threshold = when {
            level <= 5 -> 5
            level <= 15 -> 15
            level <= 20 -> 20
            else -> 0
        }
        val last = Store.battAlert
        if (threshold == 0 || (last != 0 && threshold >= last)) return
        Store.battAlert = threshold
        val ui = uiAlert
        if (ui != null) ui(threshold, level) else Notifs.batteryAlert(ctx, threshold, level)
    }

    private fun read(intent: Intent): Pair<Int, Boolean> {
        val l = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val level = if (l >= 0 && s > 0) l * 100 / s else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return level to charging
    }

    /** Écoute la batterie tant que l'application tourne (même en arrière-plan). */
    fun register(ctx: Context) {
        val receiver = object : BroadcastReceiver() {
            private var lastLevel = -1
            private var lastCharging: Boolean? = null
            override fun onReceive(c: Context, intent: Intent) {
                val (level, charging) = read(intent)
                if (level < 0 || (level == lastLevel && charging == lastCharging)) return
                lastLevel = level
                lastCharging = charging
                sample(level, charging)
                check(c.applicationContext, level, charging)
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}

/** Filet de sécurité quand l'application a été fermée par Android : vérifie la batterie toutes les 15 minutes. */
class BatteryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Store.init(ctx)
        val b = Device.battery(ctx)
        BatteryWatch.check(ctx, b.optInt("pourcentage", 100), b.optBoolean("en_charge"))
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("canelle-batterie", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
