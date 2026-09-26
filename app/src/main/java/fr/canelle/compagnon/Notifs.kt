package fr.canelle.compagnon

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat

/** Les notifications de Canelle : prises de nouvelles (avec réponse directe), batterie faible. */
object Notifs {
    const val CH_CHECKIN = "nouvelles"
    const val CH_BATTERY = "batterie"
    const val CH_BATTERY_CRIT = "batterie-critique"
    const val CH_WORK = "reflexion"
    const val ID_THREAD = 1001
    const val ID_BATTERY = 1002
    const val ID_WORK = 1003
    const val KEY_REPLY = "reponse"

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_CHECKIN, Lang.t("Prendre de tes nouvelles"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = Lang.t("Canelle te demande régulièrement si tu vas bien. Tu peux répondre directement depuis la notification.")
        })
        nm.createNotificationChannel(NotificationChannel(CH_BATTERY, Lang.t("Batterie"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = Lang.t("Rappels quand la batterie est presque vide.")
        })
        nm.createNotificationChannel(NotificationChannel(CH_BATTERY_CRIT, Lang.t("Batterie presque vide"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = Lang.t("Alerte quand il reste 5 % : le téléphone va bientôt s'éteindre.")
        })
        nm.createNotificationChannel(NotificationChannel(CH_WORK, Lang.t("Réflexion en cours"), NotificationManager.IMPORTANCE_LOW))
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun face(ctx: Context): IconCompat? = runCatching {
        IconCompat.createWithBitmap(BitmapFactory.decodeResource(ctx.resources, R.drawable.canelle_face))
    }.getOrNull()

    /** Affiche (ou met à jour) le fil de discussion dans la notification, avec le bouton « Répondre ». */
    @SuppressLint("MissingPermission")
    fun showThread(ctx: Context, thinking: Boolean) {
        if (!canPost(ctx)) return
        val name = Store.companionName
        val me = Person.Builder().setName("Toi").build()
        val canelle = Person.Builder().setName(name).setIcon(face(ctx)).setBot(true).build()
        val style = NotificationCompat.MessagingStyle(me)
        val thread = Store.thread()
        for (i in 0 until thread.length()) {
            val m = thread.getJSONObject(i)
            style.addMessage(m.optString("t"), m.optLong("at", System.currentTimeMillis()),
                if (m.optString("w") == "you") me else canelle)
        }
        if (thinking) style.addMessage("…", System.currentTimeMillis(), canelle)

        val remote = RemoteInput.Builder(KEY_REPLY).setLabel(Lang.t("Répondre à") + " $name").build()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val replyIntent = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, ReplyReceiver::class.java), flags)
        val action = NotificationCompat.Action.Builder(R.drawable.ic_notif, Lang.t("Répondre"), replyIntent)
            .addRemoteInput(remote)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()

        val n = NotificationCompat.Builder(ctx, CH_CHECKIN)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFFE8743B.toInt())
            .setStyle(style)
            .addAction(action)
            .setContentIntent(openApp(ctx))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(thread.length() > 1 || thinking)
            .setAutoCancel(false)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_THREAD, n)
        } catch (e: SecurityException) {
            // autorisation retirée entre-temps : rien à faire
        }
    }

    @SuppressLint("MissingPermission")
    fun battery(ctx: Context, level: Int) {
        if (!canPost(ctx)) return
        val name = Store.companionName
        val n = NotificationCompat.Builder(ctx, CH_BATTERY)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFFE8743B.toInt())
            .setContentTitle(Lang.t("Batterie à $level %"))
            .setContentText(Lang.t("$name a besoin d'énergie : pense à brancher ton téléphone !"))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_BATTERY, n)
        } catch (e: SecurityException) {
        }
    }

    /** Alertes à 20 %, 15 % et 5 %. */
    @SuppressLint("MissingPermission")
    fun batteryAlert(ctx: Context, threshold: Int, level: Int) {
        if (!canPost(ctx)) return
        val name = Store.companionName
        val (title, text) = when (threshold) {
            20 -> "Batterie à $level %" to "$name : pense à prévoir de charger ton téléphone bientôt."
            15 -> "Batterie à $level %" to "$name : il est temps de brancher ton téléphone !"
            else -> "Batterie à $level % !" to "$name : ton téléphone va s'éteindre si tu ne le branches pas maintenant !"
        }
        // traduites dans la langue de l'utilisateur (sur le téléphone) avant d'être affichées
        Lang.async(listOf(title, text)) { out -> postBattery(ctx, threshold, out[0], out[1]) }
    }

    @SuppressLint("MissingPermission")
    private fun postBattery(ctx: Context, threshold: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(ctx, if (threshold <= 5) CH_BATTERY_CRIT else CH_BATTERY)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFFE8743B.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (threshold <= 5) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_BATTERY, n)
        } catch (e: SecurityException) {
        }
    }

    fun working(ctx: Context): Notification = NotificationCompat.Builder(ctx, CH_WORK)
        .setSmallIcon(R.drawable.ic_notif)
        .setContentTitle(Lang.t("${Store.companionName} réfléchit…"))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}
