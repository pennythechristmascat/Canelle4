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
    /** Un cerveau téléchargeable (fichier .litertlm de Google, licence Apache 2.0). */
    class Spec(
        val id: String, val label: String, val fileName: String, val url: String,
        val approxBytes: Long, val minBytes: Long, val neededFree: Long
    )

    val E2B = Spec(
        "e2b", "Gemma 4 E2B", "gemma-4-E2B-it.litertlm",
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
        2_590_000_000L, 2_000_000_000L, 3_300_000_000L
    )
    val E4B = Spec(
        "e4b", "Gemma 4 E4B", "gemma-4-E4B-it.litertlm",
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
        3_660_000_000L, 3_000_000_000L, 4_500_000_000L
    )
    fun spec(id: String): Spec = if (id == E4B.id) E4B else E2B

    // Mémoire totale telle qu'Android l'annonce (un téléphone « 8 Go » annonce environ 7,4 Go).
    /** À partir d'ici (téléphones de 12 Go), le cerveau E4B est choisi d'office. */
    const val RAM_E4B_AUTO = 10.5
    /** À partir d'ici (téléphones de 8 Go), le cerveau E4B peut être choisi à la main. */
    const val RAM_E4B_POSSIBLE = 7.2
    /** En dessous (moins de 6 Go), le cerveau ne tient pas : Canelle fonctionne sans lui. */
    const val RAM_E2B_MIN = 5.2

    /** Cerveau conseillé pour ce téléphone : "e4b", "e2b" ou "none". */
    fun recommended(ctx: Context): String {
        val ram = ramGb(ctx)
        return when {
            ram <= 0.0 -> E2B.id
            ram >= RAM_E4B_AUTO -> E4B.id
            ram >= RAM_E2B_MIN -> E2B.id
            else -> "none"
        }
    }

    /** Cerveau installé (ou à installer) sur ce téléphone. */
    private fun current(ctx: Context): Spec {
        if (Store.modelId.isBlank()) {
            // installation antérieure : le cerveau E2B est déjà là
            if (File(dir(ctx), E2B.fileName).let { it.exists() && it.length() >= E2B.minBytes }) Store.modelId = E2B.id
        }
        return spec(Store.modelId.ifBlank { recommended(ctx).let { if (it == "none") E2B.id else it } })
    }

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
    fun file(ctx: Context): File = File(dir(ctx), current(ctx).fileName)
    private fun partFile(ctx: Context, sp: Spec): File = File(dir(ctx), sp.fileName + ".part")

    fun isDownloaded(ctx: Context): Boolean {
        val sp = current(ctx) // (reconnaît aussi un cerveau E2B installé par une ancienne version)
        if (Store.modelId.isBlank()) return false
        val f = File(dir(ctx), sp.fileName)
        return f.exists() && f.length() >= sp.minBytes
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

    /**
     * Lance le téléchargement d'un cerveau ([modelId] vide = celui conseillé pour ce téléphone).
     * Renvoie un code d'erreur ("place", "faible", "indisponible"), ou null si tout va bien.
     * Si un autre cerveau est déjà installé, il reste utilisable jusqu'à la fin du téléchargement.
     */
    @Synchronized
    fun startDownload(ctx: Context, allowMobile: Boolean, modelId: String = "", force: Boolean = false): String? {
        val rec = recommended(ctx)
        val target = modelId.ifBlank { if (Store.modelId.isNotBlank() && !isDownloaded(ctx)) Store.modelId else rec }
        if (target == "none" && !force) return "faible"
        val sp = spec(if (target == "none") E2B.id else target)
        if (sp.id == Store.modelId && isDownloaded(ctx)) return null
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: return "indisponible"
        val current = Store.downloadId
        if (current >= 0) {
            val d = query(dm, current)
            if (d != null && d.status != DownloadManager.STATUS_FAILED && d.status != DownloadManager.STATUS_SUCCESSFUL && Store.pendingModel == sp.id) {
                return null // déjà en cours
            }
            runCatching { dm.remove(current) }
            Store.downloadId = -1L
        }
        val free = runCatching { StatFs(dir(ctx).path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < sp.neededFree) return "place"
        partFile(ctx, sp).delete()
        return try {
            val req = DownloadManager.Request(Uri.parse(sp.url))
                .setTitle("Cerveau de ${Store.companionName}")
                .setDescription("${sp.label} (environ ${String.format(java.util.Locale.FRANCE, "%.1f", sp.approxBytes / 1e9)} Go)")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(ctx, null, sp.fileName + ".part")
                .setAllowedOverMetered(allowMobile)
                .setAllowedOverRoaming(false)
            Store.pendingModel = sp.id
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
        if (Store.pendingModel.isNotBlank()) partFile(ctx, spec(Store.pendingModel)).delete()
        Store.pendingModel = ""
    }

    /** Un nouveau cerveau vient d'arriver : il remplace l'ancien, avec un garde-fou tout neuf. */
    private fun install(ctx: Context, sp: Spec, part: File): Boolean {
        val target = File(dir(ctx), sp.fileName)
        target.delete()
        if (!part.renameTo(target)) return false
        val old = if (Store.modelId.isNotBlank() && Store.modelId != sp.id) File(dir(ctx), spec(Store.modelId).fileName) else null
        Store.modelId = sp.id
        Store.pendingModel = ""
        Store.brainTier = TIER_GPU
        Store.gpuBroken = false
        Store.brainOkOnce = false
        Store.loadAttempt = -1
        bg.launch {
            release()
            old?.delete()
        }
        return true
    }

    /** Progression du téléchargement en cours (et installation quand il se termine), ou null. */
    private fun download(ctx: Context): JSONObject? {
        val id = Store.downloadId
        if (id < 0) return null
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: return null
        val d = query(dm, id)
        val sp = spec(Store.pendingModel.ifBlank { current(ctx).id })
        if (d == null) {
            Store.downloadId = -1L
            Store.pendingModel = ""
            return null
        }
        val o = JSONObject().put("model", sp.id).put("modelLabel", sp.label)
        when (d.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val part = partFile(ctx, sp)
                Store.downloadId = -1L
                val complete = part.exists() && part.length() >= sp.minBytes && (d.total <= 0 || part.length() == d.total)
                if (complete && install(ctx, sp, part)) return o.put("state", "ready").put("justFinished", true)
                part.delete()
                Store.pendingModel = ""
                return o.put("state", "failed").put("reason", "incomplet")
            }
            DownloadManager.STATUS_FAILED -> {
                Store.downloadId = -1L
                Store.pendingModel = ""
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
        return o.put("done", d.done).put("total", if (d.total > 0) d.total else sp.approxBytes)
    }

    /** État du cerveau, pour l'écran « Mon cerveau ». */
    @Synchronized
    fun status(ctx: Context): JSONObject {
        val ram = ramGb(ctx)
        val rec = recommended(ctx)
        val dl = download(ctx)
        val installed = isDownloaded(ctx)
        val cur = current(ctx)
        val o = JSONObject()
            .put("ramGb", ram)
            .put("useGpu", Store.useGpu)
            .put("recommended", rec)
            .put("model", if (installed) cur.id else "")
            .put("modelLabel", if (installed) cur.label else "")
            .put("approxBytes", spec(if (rec == "none") E2B.id else rec).approxBytes)
            .put("e2bBytes", E2B.approxBytes)
            .put("e4bBytes", E4B.approxBytes)
            // passer au cerveau E4B : possible à partir de 8 Go, si le E2B est installé
            .put("canUpgrade", installed && cur.id == E2B.id && ram >= RAM_E4B_POSSIBLE)
            .put("canDowngrade", installed && cur.id == E4B.id)
            .put("report", Store.lastExitReport)
            .put("memoryCrash", Store.lastExitMemory)
            .put("crashNotice", Store.brainCrashNotice)
        if (installed) {
            val s = when {
                state == "loading" -> "loading"
                state == "loaded" -> "loaded"
                state == "error" -> "error"
                engine == null && Store.brainTier >= TIER_BLOCKED -> "blocked"
                else -> "ready"
            }
            o.put("state", s)
                .put("backend", backendUsed ?: JSONObject.NULL)
                .put("error", lastError ?: JSONObject.NULL)
                .put("tier", if (engine != null) engineTier else Store.brainTier)
            // téléchargement d'un autre cerveau pendant que celui-ci reste utilisable
            if (dl != null && dl.optString("state") != "ready") o.put("switch", dl)
            if (dl != null && dl.optBoolean("justFinished")) o.put("justFinished", true).put("switched", true)
            return o
        }
        if (dl != null) {
            dl.keys().forEach { k -> o.put(k, dl.get(k)) }
            return o
        }
        return o.put("state", if (rec == "none") "weak" else "absent")
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
        File(dir(ctx), E2B.fileName).delete()
        File(dir(ctx), E4B.fileName).delete()
        Store.modelId = ""
        Store.brainTier = TIER_GPU
        Store.brainOkOnce = false
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
