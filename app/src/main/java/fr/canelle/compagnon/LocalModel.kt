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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    // Niveaux de chargement, du plus rapide au plus léger. Après un plantage, on passe au suivant.
    const val TIER_GPU = 0
    const val TIER_CPU = 1
    const val TIER_ECO = 2
    const val TIER_BLOCKED = 3
    /** Taille de la mémoire de conversation par niveau (moins de jetons = moins de mémoire). */
    private val TOKENS = intArrayOf(2048, 2048, 1024)

    fun tierName(t: Int): String = when (t) {
        TIER_GPU -> "chargement sur la puce graphique"
        TIER_CPU -> "chargement sur le processeur"
        TIER_ECO -> "chargement en mode économe"
        else -> "niveau $t"
    }

    /** idle, loading, loaded ou error. */
    @Volatile var state = "idle"
        private set
    @Volatile var backendUsed: String? = null
        private set
    @Volatile var lastError: String? = null
        private set

    private val mutex = Mutex()
    private var engine: Engine? = null
    @Volatile private var engineTier = -1
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var releaseJob: Job? = null
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
            val s = when {
                state == "loading" -> "loading"
                state == "loaded" -> "loaded"
                state == "error" -> "error"
                engine == null && Store.brainTier >= TIER_BLOCKED -> "blocked"
                else -> "ready"
            }
            return o.put("state", s)
                .put("backend", backendUsed ?: JSONObject.NULL)
                .put("error", lastError ?: JSONObject.NULL)
                .put("tier", if (engine != null) engineTier else Store.brainTier)
                .put("crashNotice", Store.brainCrashNotice)
                .put("memoryCrash", Store.lastExitMemory)
                .put("report", Store.lastExitReport)
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
                val complete = part.exists() && part.length() >= MIN_BYTES && (d.total <= 0 || part.length() == d.total)
                if (complete && part.renameTo(file(ctx))) {
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

    /**
     * Charge le cerveau. [manual] : l'utilisateur l'a demandé (bouton « Réessayer ») ;
     * sinon on ne charge pas tout seul un cerveau qui a déjà fait fermer l'appli.
     */
    suspend fun ensureLoaded(ctx: Context, manual: Boolean = false): Boolean = mutex.withLock { loadLocked(ctx, manual) }

    /** Le dernier chargement ou la dernière réponse a-t-il fait fermer l'appli ? Si oui, on passe au niveau plus léger. */
    private fun checkPreviousCrash() {
        var crashed = Store.loadAttempt
        if (Store.gpuPending) {
            Store.gpuPending = false
            crashed = maxOf(crashed, TIER_GPU)
        }
        if (crashed < 0) return
        Store.loadAttempt = -1
        // L'utilisateur a juste fermé l'appli, ou Android l'a mise à jour : ce n'est pas la faute du cerveau.
        val r = Store.lastExitReason
        val harmless = r == android.app.ApplicationExitInfo.REASON_USER_REQUESTED || r == android.app.ApplicationExitInfo.REASON_EXIT_SELF ||
            r == android.app.ApplicationExitInfo.REASON_USER_STOPPED || r == 15 || r == 16
        if (harmless) return
        if (crashed == TIER_GPU) Store.gpuBroken = true
        Store.brainTier = maxOf(Store.brainTier, crashed + 1)
        Store.brainCrashNotice = true
    }

    private fun lowMemoryNow(ctx: Context): Boolean = runCatching {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.lowMemory || mi.availMem < 1_500_000_000L
    }.getOrDefault(false)

    private suspend fun loadLocked(ctx: Context, manual: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        releaseJob?.cancel()
        if (engine != null) return@withContext true
        if (!isDownloaded(ctx)) return@withContext false
        checkPreviousCrash()
        var tier = maxOf(Store.brainTier, if (Store.useGpu && !Store.gpuBroken) TIER_GPU else TIER_CPU)
        if (tier >= TIER_BLOCKED) {
            if (!manual) {
                lastError = "Le cerveau a fait fermer l'appli plusieurs fois : il ne se lance plus tout seul."
                return@withContext false
            }
            tier = TIER_ECO
        }
        // Peu de mémoire libre en ce moment : on évite la puce graphique, plus gourmande.
        if (tier == TIER_GPU && lowMemoryNow(ctx)) tier = TIER_CPU
        state = "loading"
        lastError = null
        val path = file(ctx).absolutePath
        val cache = ctx.cacheDir.absolutePath
        while (tier <= TIER_ECO) {
            Store.loadAttempt = tier
            val e = tryEngine(path, cache, if (tier == TIER_GPU) Backend.GPU() else Backend.CPU(), TOKENS[tier])
            Store.loadAttempt = -1
            if (e != null) {
                engine = e
                engineTier = tier
                backendUsed = if (tier == TIER_GPU) "gpu" else "cpu"
                state = "loaded"
                Store.brainOkOnce = true
                // un chargement manuel réussi débloque le cerveau (en restant au niveau qui marche)
                if (Store.brainTier > tier) Store.brainTier = tier
                return@withContext true
            }
            if (tier == TIER_GPU) Store.gpuBroken = true
            tier++
        }
        state = "error"
        false
    }

    private fun tryEngine(path: String, cache: String, backend: Backend, maxTokens: Int): Engine? {
        var e: Engine? = null
        return try {
            e = Engine(EngineConfig(modelPath = path, backend = backend, maxNumTokens = maxTokens, cacheDir = cache))
            e.initialize()
            e
        } catch (t: Throwable) {
            lastError = (t.message ?: t.javaClass.simpleName).take(240)
            runCatching { e?.close() }
            null
        }
    }

    /** Libère la mémoire du cerveau un peu après que l'appli est passée en arrière-plan. */
    fun releaseSoon(delayMs: Long = 60_000L) {
        releaseJob?.cancel()
        releaseJob = bg.launch {
            delay(delayMs)
            release()
        }
    }

    fun cancelRelease() {
        releaseJob?.cancel()
    }

    fun ackCrashNotice() {
        Store.brainCrashNotice = false
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
            engineTier = -1
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
        if (!loadLocked(ctx)) {
            if (Store.brainTier >= TIER_BLOCKED) throw BrainException("model_blocked")
            throw BrainException("model_error", lastError ?: "chargement impossible")
        }
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
            Store.loadAttempt = engineTier
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
            } catch (e: Throwable) {
                dirty = true
                throw BrainException("model_error", (e.message ?: "erreur du modèle").take(240))
            } finally {
                Store.loadAttempt = -1
            }
            conversationTurns++
            if (stoppedEarly) dirty = true
            sb.toString()
        }
    }
}
