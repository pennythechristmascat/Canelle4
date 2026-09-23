package fr.canelle.compagnon

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Le cerveau local de Canelle : le modèle Gemma 4 E2B de Google, qui tourne
 * entièrement sur le téléphone grâce à LiteRT-LM. Aucune donnée ne part sur internet.
 */
object LocalModel {
    const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    private const val PART_NAME = "gemma-4-E2B-it.litertlm.part"
    private const val URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
    const val APPROX_BYTES = 2_590_000_000L
    private const val MIN_BYTES = 2_000_000_000L
    private const val NEEDED_FREE = 3_300_000_000L
    private const val MAX_TOKENS = 4096

    /** idle, loading, loaded ou error. */
    @Volatile var state = "idle"
        private set
    @Volatile var backendUsed: String? = null
        private set
    @Volatile var lastError: String? = null
        private set

    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationKey: String? = null
    private var conversationTurns = 0
    @Volatile private var dirty = false

    // ---------------------------------------------------------------- fichier du modèle

    private fun dir(ctx: Context): File = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    fun file(ctx: Context): File = File(dir(ctx), FILE_NAME)
    private fun partFile(ctx: Context): File = File(dir(ctx), PART_NAME)

    fun isDownloaded(ctx: Context): Boolean {
        val f = file(ctx)
        return f.exists() && f.length() >= MIN_BYTES
    }

    fun ramGb(ctx: Context): Double = runCatching {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem / 1_000_000_000.0
    }.getOrDefault(0.0)

    // ---------------------------------------------------------------- téléchargement

    private class Dl(val status: Int, val reason: Int, val done: Long, val total: Long)

    private fun query(dm: DownloadManager, id: Long): Dl? = runCatching {
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (c == null || !c.moveToFirst()) {
                null
            } else {
                Dl(
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                )
            }
        }
    }.getOrNull()

    /** Lance le téléchargement. Renvoie un code d'erreur, ou null si tout va bien. */
    @Synchronized
    fun startDownload(ctx: Context, allowMobile: Boolean): String? {
        if (isDownloaded(ctx)) return null
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: return "indisponible"
        val current = Store.downloadId
        if (current >= 0) {
            val d = query(dm, current)
            if (d != null && d.status != DownloadManager.STATUS_FAILED && d.status != DownloadManager.STATUS_SUCCESSFUL) {
                return null // déjà en cours
            }
            runCatching { dm.remove(current) }
            Store.downloadId = -1L
        }
        val free = runCatching { StatFs(dir(ctx).path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < NEEDED_FREE) return "place"
        partFile(ctx).delete()
        return try {
            val req = DownloadManager.Request(Uri.parse(URL))
                .setTitle("Cerveau de ${Store.companionName}")
                .setDescription("Gemma 4 E2B (environ 2,6 Go)")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(ctx, null, PART_NAME)
                .setAllowedOverMetered(allowMobile)
                .setAllowedOverRoaming(false)
            Store.downloadId = dm.enqueue(req)
            null
        } catch (e: Exception) {
            "indisponible"
        }
    }

    @Synchronized
    fun cancelDownload(ctx: Context) {
        val id = Store.downloadId
        if (id >= 0) runCatching { ctx.getSystemService(DownloadManager::class.java)?.remove(id) }
        Store.downloadId = -1L
        partFile(ctx).delete()
    }

    /** État du cerveau, pour l'écran « Mon cerveau ». */
    @Synchronized
    fun status(ctx: Context): JSONObject {
        val o = JSONObject()
            .put("approxBytes", APPROX_BYTES)
            .put("ramGb", ramGb(ctx))
            .put("useGpu", Store.useGpu)
        if (isDownloaded(ctx)) {
            val s = when (state) {
                "loading" -> "loading"
                "loaded" -> "loaded"
                "error" -> "error"
                else -> "ready"
            }
            return o.put("state", s)
                .put("backend", backendUsed ?: JSONObject.NULL)
                .put("error", lastError ?: JSONObject.NULL)
        }
        val id = Store.downloadId
        if (id < 0) return o.put("state", "absent")
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: return o.put("state", "absent")
        val d = query(dm, id)
        if (d == null) {
            Store.downloadId = -1L
            return o.put("state", "absent")
        }
        when (d.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val part = partFile(ctx)
                Store.downloadId = -1L
                if (part.exists() && part.length() >= MIN_BYTES && part.renameTo(file(ctx))) {
                    return o.put("state", "ready").put("justFinished", true)
                }
                part.delete()
                return o.put("state", "failed").put("reason", "incomplet")
            }
            DownloadManager.STATUS_FAILED -> {
                Store.downloadId = -1L
                runCatching { dm.remove(id) }
                val reason = when (d.reason) {
                    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "place"
                    DownloadManager.ERROR_DEVICE_NOT_FOUND, DownloadManager.ERROR_FILE_ERROR -> "stockage"
                    else -> "reseau"
                }
                return o.put("state", "failed").put("reason", reason)
            }
            DownloadManager.STATUS_PAUSED -> {
                val reason = when (d.reason) {
                    DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "wifi"
                    DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "reseau"
                    else -> "attente"
                }
                o.put("state", "paused").put("reason", reason)
            }
            else -> o.put("state", "downloading")
        }
        return o.put("done", d.done).put("total", if (d.total > 0) d.total else APPROX_BYTES)
    }

    // ---------------------------------------------------------------- moteur

    suspend fun ensureLoaded(ctx: Context): Boolean = mutex.withLock { loadLocked(ctx) }

    private suspend fun loadLocked(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext true
        if (!isDownloaded(ctx)) return@withContext false
        state = "loading"
        lastError = null
        // Si le téléphone a planté pendant le dernier essai sur la puce graphique, on ne recommence pas.
        if (Store.gpuPending) {
            Store.gpuPending = false
            Store.gpuBroken = true
        }
        val path = file(ctx).absolutePath
        val cache = ctx.cacheDir.absolutePath
        var e: Engine? = null
        if (Store.useGpu && !Store.gpuBroken) {
            Store.gpuPending = true
            e = tryEngine(path, cache, Backend.GPU())
            Store.gpuPending = false
            if (e == null) Store.gpuBroken = true else backendUsed = "gpu"
        }
        if (e == null) {
            e = tryEngine(path, cache, Backend.CPU())
            if (e != null) backendUsed = "cpu"
        }
        if (e == null) {
            state = "error"
            return@withContext false
        }
        engine = e
        state = "loaded"
        true
    }

    private fun tryEngine(path: String, cache: String, backend: Backend): Engine? {
        var e: Engine? = null
        return try {
            e = Engine(EngineConfig(modelPath = path, backend = backend, maxNumTokens = MAX_TOKENS, cacheDir = cache))
            e.initialize()
            e
        } catch (t: Throwable) {
            lastError = (t.message ?: t.javaClass.simpleName).take(240)
            runCatching { e?.close() }
            null
        }
    }

    /** Oublie la conversation en cours : elle sera reconstruite avec tout l'historique au prochain message. */
    fun markDirty() {
        dirty = true
    }

    suspend fun release() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
            conversation = null
            conversationKey = null
            engine = null
            backendUsed = null
            state = "idle"
        }
    }

    suspend fun delete(ctx: Context) {
        release()
        cancelDownload(ctx)
        file(ctx).delete()
    }

    /**
     * Envoie un message au modèle. [history] : échanges précédents (vrai = utilisateur).
     * [onText] reçoit le texte complet au fur et à mesure ; il renvoie faux pour arrêter la génération.
     */
    suspend fun chat(
        ctx: Context,
        system: String,
        history: List<Pair<Boolean, String>>,
        text: String,
        onText: (String) -> Boolean
    ): String = mutex.withLock {
        if (!loadLocked(ctx)) throw BrainException("model_error", lastError ?: "chargement impossible")
        withContext(Dispatchers.IO) {
            val eng = engine ?: throw BrainException("model_error", "moteur absent")
            val reusable = conversation?.takeIf { !dirty && conversationKey == system && conversationTurns < 8 }
            val conv: Conversation = reusable ?: run {
                val old = conversation
                runCatching { old?.close() }
                conversation = null
                val fresh = eng.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(system),
                        initialMessages = history.map { (isUser, t) -> if (isUser) Message.user(t) else Message.model(t) },
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
                        thinkingConfig = ThinkingConfig(enableThinking = false)
                    )
                )
                conversation = fresh
                conversationKey = system
                conversationTurns = 0
                dirty = false
                fresh
            }
            val sb = StringBuilder()
            var stoppedEarly = false
            try {
                conv.sendMessageAsync(text)
                    .takeWhile { chunk ->
                        sb.append(chunk.toString())
                        val goOn = onText(sb.toString())
                        if (!goOn) stoppedEarly = true
                        goOn
                    }
                    .collect { }
            } catch (e: CancellationException) {
                dirty = true
                throw e
            } catch (e: Exception) {
                dirty = true
                throw BrainException("model_error", (e.message ?: "erreur du modèle").take(240))
            }
            conversationTurns++
            if (stoppedEarly) dirty = true
            sb.toString()
        }
    }
}
