package fr.canelle.compagnon

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Compteur de pas via Health Connect, le service d'Android qui rassemble les pas de Google Fit,
 * Samsung Health, Fitbit… Depuis Android 14, Health Connect compte aussi les pas du téléphone tout seul.
 * On lit seulement le nombre de pas, rien n'est envoyé sur internet.
 */
object Steps {
    const val PROVIDER = "com.google.android.apps.healthdata"
    val PERMISSIONS = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    /** Derniers pas lus aujourd'hui (pour les pièces). */
    @Volatile var lastToday = 0L
        private set

    /** "ok", "update" (Health Connect à mettre à jour) ou "absent". */
    fun availability(ctx: Context): String = when (runCatching { HealthConnectClient.getSdkStatus(ctx, PROVIDER) }.getOrDefault(-1)) {
        HealthConnectClient.SDK_AVAILABLE -> "ok"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update"
        else -> "absent"
    }

    suspend fun granted(ctx: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }.getOrDefault(false)

    /** { availability, granted, today, days: [{date, steps}] (7 derniers jours) } */
    suspend fun read(ctx: Context): JSONObject {
        val o = JSONObject()
        val av = availability(ctx)
        o.put("availability", av)
        if (av != "ok") return o
        val ok = granted(ctx)
        o.put("granted", ok)
        if (!ok) return o
        val client = HealthConnectClient.getOrCreate(ctx)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = JSONArray()
        for (i in 6 downTo 0) {
            val d = today.minusDays(i.toLong())
            val start = d.atStartOfDay(zone).toInstant()
            val end = if (i == 0) Instant.now() else d.plusDays(1).atStartOfDay(zone).toInstant()
            val n = runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )[StepsRecord.COUNT_TOTAL] ?: 0L
            }.getOrDefault(0L)
            days.put(JSONObject().put("date", d.toString()).put("steps", n))
        }
        lastToday = days.getJSONObject(6).optLong("steps", 0L)
        return o.put("days", days).put("today", lastToday)
    }

    /** Installer (ou mettre à jour) Health Connect depuis le Play Store (Android 12 et 13). */
    fun openStore(ctx: Context) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
            .setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** Réglages de Health Connect (pour gérer les applis qui y envoient des pas). */
    fun openSettings(ctx: Context) {
        for (action in listOf("android.health.connect.action.HEALTH_HOME_SETTINGS", "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")) {
            try {
                ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
            }
        }
    }
}
