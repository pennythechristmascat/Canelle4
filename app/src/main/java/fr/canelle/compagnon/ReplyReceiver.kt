package fr.canelle.compagnon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

/** Reçoit la réponse tapée directement dans la notification, sans ouvrir l'application. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(Notifs.KEY_REPLY)?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        Store.init(ctx)
        Store.threadAdd("you", text)
        Notifs.showThread(ctx, thinking = true)
        val work = OneTimeWorkRequestBuilder<ReplyWorker>()
            .setInputData(workDataOf("text" to text))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(ctx).enqueue(work)
    }
}
