package fr.canelle.compagnon

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic des arrêts de l'application. Android garde la raison des derniers arrêts
 * (plantage, manque de mémoire, limite de mémoire d'Android 17…) : on la lit au lancement
 * pour adapter le cerveau et pouvoir montrer un rapport. Rien ne quitte le téléphone.
 */
object Diagnostics {

    /** Arrêts qui ne sont pas des problèmes (l'utilisateur a fermé l'appli, mise à jour…). */
    private val NORMAL = setOf(
        ApplicationExitInfo.REASON_EXIT_SELF,
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE,
        15, // REASON_PACKAGE_STATE_CHANGE (Android 13+)
        16  // REASON_PACKAGE_UPDATED (Android 13+)
    )

    private fun reasonName(r: Int, desc: String): String = when {
        desc.contains("MemoryLimiter") -> "limite de mémoire d'Android dépassée"
        r == ApplicationExitInfo.REASON_LOW_MEMORY -> "manque de mémoire sur le téléphone"
        r == ApplicationExitInfo.REASON_CRASH -> "plantage (Java)"
        r == ApplicationExitInfo.REASON_CRASH_NATIVE -> "plantage (code natif)"
        r == ApplicationExitInfo.REASON_ANR -> "l'application ne répondait plus"
        r == ApplicationExitInfo.REASON_SIGNALED -> "arrêtée par le système"
        r == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "trop de ressources utilisées"
        r == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "échec au démarrage"
        r == ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "un composant nécessaire s'est arrêté"
        r == ApplicationExitInfo.REASON_OTHER -> "autre raison"
        r == 14 -> "gelée par le système" // REASON_FREEZER (Android 13+)
        else -> "raison inconnue ($r)"
    }

    /** À appeler au lancement de l'application. */
    fun collect(ctx: Context) {
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return
        val list = runCatching { am.getHistoricalProcessExitReasons(ctx.packageName, 0, 5) }.getOrNull() ?: return
        val last = list.firstOrNull { it.processName == ctx.packageName } ?: return
        if (last.timestamp <= Store.lastExitSeen) return
        Store.lastExitSeen = last.timestamp
        val desc = last.description.orEmpty()
        val memory = desc.contains("MemoryLimiter") || last.reason == ApplicationExitInfo.REASON_LOW_MEMORY
        Store.lastExitReason = last.reason
        Store.lastExitMemory = memory
        if (last.reason in NORMAL) return
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date(last.timestamp))
        val report = buildString {
            val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
            append("Canelle ").append(version).append(" — rapport d'arrêt\n")
            append("Date : ").append(date).append('\n')
            append("Raison : ").append(reasonName(last.reason, desc)).append(" (code ").append(last.reason).append(")\n")
            if (desc.isNotBlank()) append("Détail Android : ").append(desc.take(300)).append('\n')
            append("Mémoire de l'appli : ").append(last.pss / 1024).append(" Mo (PSS), ").append(last.rss / 1024).append(" Mo (RSS)\n")
            append("Téléphone : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Mémoire totale : ").append(String.format(Locale.FRANCE, "%.1f", LocalModel.ramGb(ctx))).append(" Go\n")
            val attempt = Store.loadAttempt
            if (attempt >= 0) append("Pendant : ").append(LocalModel.tierName(attempt)).append('\n')
            val trace = Store.javaCrash
            if (trace.isNotBlank()) append("\nTrace :\n").append(trace.take(3000))
        }
        Store.lastExitReport = report
        Store.javaCrash = ""
    }

    /** Garde la trace des plantages Java (écrite tout de suite, avant que l'appli ne se ferme). */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { Store.javaCrash = Log.getStackTraceString(error).take(4000) }
            previous?.uncaughtException(thread, error)
        }
    }
}
