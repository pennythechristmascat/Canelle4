package fr.canelle.compagnon

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

/** Prépare la réponse de Canelle à un message envoyé depuis la notification. */
class ReplyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Store.init(ctx)
        val text = inputData.getString("text") ?: return Result.success()
        val answer = try {
            Brain.reply(ctx, text, foreground = false, host = null).lines.joinToString(" ") { it.text }
        } catch (e: Throwable) {
            "Oups, je n'ai pas réussi à répondre. Ouvre l'application pour qu'on en parle ?"
        }
        Store.threadAdd("bip", answer)
        Notifs.showThread(ctx, thinking = false)
        // Réponse donnée depuis une notification : on ne garde pas le cerveau en mémoire en arrière-plan.
        if (!CanelleApp.visible) LocalModel.releaseSoon(20_000L)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(Notifs.ID_WORK, Notifs.working(applicationContext))
}
