package fr.canelle.compagnon

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.audiofx.Visualizer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Service vide : il sert seulement à obtenir l'autorisation « accès aux notifications »,
 * qu'Android exige pour connaître le titre de la musique en cours. Il ne lit ni ne garde aucune notification.
 */
class MusicListener : NotificationListenerService()

/** La musique du téléphone : ce qui joue, les commandes, Spotify et Deezer, et le rythme pour faire danser Canelle. */
object MusicWatcher {
    const val SPOTIFY = "com.spotify.music"
    const val DEEZER = "deezer.android.app"
    private val APPS = mapOf(
        SPOTIFY to "Spotify", DEEZER to "Deezer", "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.google.android.youtube" to "YouTube", "com.amazon.mp3" to "Amazon Music", "com.apple.android.music" to "Apple Music",
        "com.soundcloud.android" to "SoundCloud", "fr.radiofrance.app" to "Radio France"
    )

    fun installed(ctx: Context, pkg: String): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    }.getOrDefault(false)

    fun apps(ctx: Context): JSONArray = JSONArray().apply {
        if (installed(ctx, SPOTIFY)) put("spotify")
        if (installed(ctx, DEEZER)) put("deezer")
    }

    // ---------------------------------------------------------------- ce qui joue

    fun titleAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun openTitleAccess(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private val MUSIC_APPS = setOf(SPOTIFY, DEEZER)

    /** Lecteur Spotify ou Deezer (celui qui joue en priorité). Les autres applications et sons sont ignorés. */
    private fun playingController(ctx: Context): MediaController? {
        if (!titleAccess(ctx)) return null
        return runCatching {
            val msm = ctx.getSystemService(MediaSessionManager::class.java)
            val list = msm.getActiveSessions(ComponentName(ctx, MusicListener::class.java)).filter { it.packageName in MUSIC_APPS }
            list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        }.getOrNull()
    }

    /**
     * { playing, title, artist, app, access }
     * playing = Spotify ou Deezer est en train de lire de la musique. Les notifications, vidéos, jeux
     * et les sons de Canelle lui-même ne comptent pas.
     */
    fun state(ctx: Context): JSONObject {
        val access = titleAccess(ctx)
        val o = JSONObject().put("access", access).put("playing", false)
        val c = playingController(ctx)
        if (c != null) {
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            val md = c.metadata
            o.put("playing", playing)
                .put("title", md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")
                .put("artist", md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "")
                .put("app", APPS[c.packageName] ?: "")
        }
        if (!o.optBoolean("playing")) release()
        return o
    }

    // ---------------------------------------------------------------- commandes

    /** "play", "pause", "toggle", "next", "previous". */
    fun control(ctx: Context, action: String): Boolean {
        val c = playingController(ctx)
        if (c != null) {
            val t = c.transportControls
            when (action) {
                "play" -> t.play()
                "pause" -> t.pause()
                "next" -> t.skipToNext()
                "previous" -> t.skipToPrevious()
                else -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            }
            return true
        }
        // sans l'autorisation : touches multimédia, reçues par l'application qui joue
        val code = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(AudioManager::class.java)
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        return true
    }

    /**
     * Lance une recherche et la lecture dans Spotify ou Deezer ("spotify", "deezer" ou "" = celle qui est installée).
     * Renvoie "ok:<appli>", "absent:<appli>" (proposée sur le Play Store) ou "aucune".
     */
    fun play(ctx: Context, app: String, query: String): String {
        val wanted = when (app) {
            "spotify" -> SPOTIFY
            "deezer" -> DEEZER
            else -> when {
                Store.musicApp == "deezer" && installed(ctx, DEEZER) -> DEEZER
                Store.musicApp == "spotify" && installed(ctx, SPOTIFY) -> SPOTIFY
                installed(ctx, SPOTIFY) -> SPOTIFY
                installed(ctx, DEEZER) -> DEEZER
                else -> null
            }
        } ?: return "aucune"
        val label = APPS[wanted] ?: wanted
        if (!installed(ctx, wanted)) {
            start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$wanted")))
                || start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$wanted")))
            return "absent:$label"
        }
        val q = query.trim()
        if (q.isEmpty()) {
            // pas de titre demandé : on ouvre l'appli et on relance la lecture
            val launch = ctx.packageManager.getLaunchIntentForPackage(wanted)
            if (launch != null) start(ctx, launch)
            control(ctx, "play")
            return "ok:$label"
        }
        // « Joue … » : recherche et lecture directe
        val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(wanted)
            .putExtra(SearchManager.QUERY, q)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        if (start(ctx, playIntent)) return "ok:$label"
        // sinon, on ouvre la recherche dans l'appli
        val search = if (wanted == SPOTIFY) "spotify:search:${Uri.encode(q)}" else "deezer://www.deezer.com/search/${Uri.encode(q)}"
        return if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(search)).setPackage(wanted))) "ok:$label" else "aucune"
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    // ---------------------------------------------------------------- rythme (pour que Canelle danse en musique)

    private var viz: Visualizer? = null
    @Volatile private var level = -1f
    @Volatile private var peak = 0.08f
    private var vizFailed = false

    /**
     * Intensité de la musique entre 0 et 1, mise à jour ~20 fois par seconde,
     * ou -1 si le téléphone ne permet pas de la mesurer (Canelle suit alors un tempo régulier).
     */
    fun level(ctx: Context): Float {
        if (viz == null && !vizFailed) startViz(ctx)
        return if (viz == null) -1f else level
    }

    private fun startViz(ctx: Context) {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            vizFailed = true
            return
        }
        try {
            val v = Visualizer(0) // mélange de sortie du téléphone : on ne mesure que le volume, rien n'est enregistré
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128))
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    val w = waveform ?: return
                    var sum = 0.0
                    for (b in w) {
                        val x = ((b.toInt() and 0xFF) - 128) / 128.0
                        sum += x * x
                    }
                    val rms = sqrt(sum / w.size).toFloat()
                    peak = maxOf(rms, peak * 0.995f, 0.04f) // gain automatique
                    level = (rms / peak).coerceIn(0f, 1f)
                }
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
            }, (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(10_000), true, false)
            v.setEnabled(true)
            viz = v
        } catch (e: Throwable) {
            vizFailed = true
            viz = null
        }
    }

    fun release() {
        runCatching { viz?.setEnabled(false); viz?.release() }
        viz = null
        level = -1f
        vizFailed = false
    }
}
