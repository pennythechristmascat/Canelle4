package fr.canelle.compagnon

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Tourne environ toutes les heures en arrière-plan (la batterie est surveillée par BatteryWatch) :
 * - envoie une prise de nouvelles quand l'intervalle choisi est écoulé (sauf pendant les heures calmes).
 */
class CheckinWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Store.init(ctx)
        val now = System.currentTimeMillis()

        if (!Store.checkins || isQuiet(LocalTime.now().hour) || Access.isLocked()) return Result.success()
        if (now - Store.lastCheckin < Store.intervalHours * 3_600_000L - 10 * 60_000L) return Result.success()
        postCheckin(ctx)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "canelle-nouvelles"

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<CheckinWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun postCheckin(ctx: Context) {
            val msg = pickMessage()
            Store.lastCheckin = System.currentTimeMillis()
            Store.threadReset(msg)
            Store.addTurn("assistant", msg)
            LocalModel.markDirty()
            Store.addLog("bip", msg)
            Notifs.showThread(ctx, thinking = false)
        }

        private fun isQuiet(h: Int): Boolean {
            val s = Store.quietStart
            val e = Store.quietEnd
            return when {
                s == e -> false
                s > e -> h >= s || h < e
                else -> h in s until e
            }
        }

        private fun pickMessage(): String {
            val who = Store.userName.let { if (it.isBlank()) "" else " $it" }
            val h = LocalTime.now().hour
            val pool = when (h) {
                in 5..10 -> listOf(
                    "Coucou$who ! Bien dormi ? Comment tu te sens ce matin ?",
                    "Bonjour$who ! Prêt pour la journée ? Dis-moi comment ça va.",
                    "Hé$who, petit check du matin : ça va aujourd'hui ?")
                in 11..13 -> listOf(
                    "Coucou$who ! Tu as pensé à manger ? Et le moral, ça va ?",
                    "Petite pause de midi$who ? Raconte-moi comment se passe ta journée.")
                in 14..18 -> listOf(
                    "Coucou$who ! Je passe voir si tout va bien.",
                    "Hé$who, tu as bu un verre d'eau récemment ? Et toi, ça va ?",
                    "Rawr ! …Je voulais juste savoir comment tu vas$who.")
                else -> listOf(
                    "Bonsoir$who ! Comment s'est passée ta journée ?",
                    "Coucou$who, la journée se termine : tu te sens comment ?",
                    "Hé$who, je me roule en boule pour la nuit bientôt. Tout va bien de ton côté ?")
            }
            return pool.random()
        }
    }
}
