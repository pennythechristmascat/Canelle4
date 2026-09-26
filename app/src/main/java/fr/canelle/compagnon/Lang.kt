package fr.canelle.compagnon

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Langues de Canelle AI. Les textes sont écrits en français ; pour les autres langues, ils sont traduits
 * sur le téléphone par ML Kit de Google (mêmes modèles que le mode hors ligne de Google Traduction).
 * Un pack d'environ 30 Mo est téléchargé une fois par langue ; ensuite, rien ne quitte le téléphone.
 * Les traductions sont gardées en mémoire (et sur le téléphone) pour être instantanées la fois suivante.
 */
object Lang {
    val CODES = listOf("fr", "en", "es", "pt", "ru", "zh", "ja", "ar")

    /** Nom de la langue, pour la consigne donnée au cerveau. */
    private val PROMPT_NAME = mapOf(
        "fr" to "français", "en" to "anglais", "es" to "espagnol", "pt" to "portugais",
        "ru" to "russe", "zh" to "chinois simplifié", "ja" to "japonais", "ar" to "arabe"
    )
    /** Langue de la voix et de la reconnaissance vocale. */
    private val BCP = mapOf(
        "fr" to "fr-FR", "en" to "en-US", "es" to "es-ES", "pt" to "pt-BR",
        "ru" to "ru-RU", "zh" to "zh-CN", "ja" to "ja-JP", "ar" to "ar"
    )

    fun current(): String = Store.lang.takeIf { it in CODES } ?: "fr"
    fun promptName(): String = PROMPT_NAME[current()] ?: "français"
    fun bcp(): String = BCP[current()] ?: "fr-FR"
    fun locale(): Locale = Locale.forLanguageTag(bcp())

    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = HashMap<String, Translator>()
    private val cache = object : LinkedHashMap<String, String>(2048, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 8000
    }
    private var appCtx: Context? = null
    private var loadedFor = ""

    // ---------------------------------------------------------------- patchs de langue (assets/i18n/<langue>.js)
    // Chaque langue patchée a tous les textes de l'appli traduits à l'avance : aucune traduction automatique pour eux.

    @Volatile private var dict: Map<String, String> = emptyMap()
    @Volatile private var rev: Map<String, String> = emptyMap()
    @Volatile private var frag: Regex? = null
    private var dictLang = ""

    /** Mots courts traduits aussi à l'intérieur des phrases composées. */
    private val SHORT = setOf("À", "à", "de")

    private fun norm(t: String) = t.lowercase(Locale.ROOT).trim().trimEnd('!', '?', '.', '…', ' ')

    @Synchronized
    private fun ensureDict() {
        val lang = current()
        if (dictLang == lang) return
        dictLang = lang
        dict = emptyMap(); rev = emptyMap(); frag = null
        if (lang == "fr") return
        val ctx = appCtx ?: return
        val txt = runCatching { ctx.assets.open("i18n/$lang.js").bufferedReader().use { it.readText() } }.getOrNull() ?: return
        val start = txt.indexOf('{')
        val end = txt.lastIndexOf('}')
        if (start < 0 || end <= start) return
        val o = runCatching { JSONObject(txt.substring(start, end + 1)) }.getOrNull() ?: return
        val m = HashMap<String, String>()
        o.keys().forEach { k -> m[k] = o.optString(k) }
        dict = m
        rev = m.entries.associate { norm(it.value) to it.key }
        val keys = m.keys.filter { it.length >= 3 || it in SHORT }.sortedByDescending { it.length }
        frag = if (keys.isEmpty()) null else Regex("(?<!\\p{L})(?:" + keys.joinToString("|") { Regex.escape(it) } + ")(?!\\p{L})")
    }

    /** La langue actuelle a-t-elle son patch complet ? */
    fun hasPatch(): Boolean {
        ensureDict()
        return dict.isNotEmpty()
    }

    /** Traduction par le patch : phrase exacte, sinon chacun des morceaux connus d'une phrase composée. */
    fun staticTr(text: String): String? {
        ensureDict()
        if (dict.isEmpty()) return null
        val core = text.trim()
        dict[core]?.let { return text.replace(core, it) }
        // même texte avec une majuscule ou une minuscule au début
        if (core.isNotEmpty()) {
            val up = core[0].isUpperCase()
            val alt = if (up) core[0].lowercaseChar() + core.substring(1) else core[0].uppercaseChar() + core.substring(1)
            dict[alt]?.let { v ->
                val fixed = if (v.isEmpty()) v else if (up) v[0].uppercaseChar() + v.substring(1) else v[0].lowercaseChar() + v.substring(1)
                return text.replace(core, fixed)
            }
        }
        val re = frag ?: return null
        var hit = false
        val out = re.replace(text) { m -> hit = true; dict[m.value] ?: m.value }
        return if (hit) out else null
    }

    /** Traduction immédiate d'un texte de l'appli (notifications, fenêtres…). */
    fun t(text: String): String = if (current() == "fr") text else staticTr(text) ?: text

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }

    private fun client(from: String, to: String): Translator = synchronized(clients) {
        clients.getOrPut("$from>$to") {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
        }
    }

    // ---------------------------------------------------------------- cache sur le téléphone

    private fun cacheFile(ctx: Context, lang: String) = File(ctx.filesDir, "traductions-$lang.json")

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        loadCache(ctx, current())
    }

    @Synchronized
    private fun loadCache(ctx: Context, lang: String) {
        if (loadedFor == lang) return
        loadedFor = lang
        if (lang == "fr") return
        runCatching {
            val o = JSONObject(cacheFile(ctx, lang).readText())
            o.keys().forEach { k -> cache["fr>$lang|$k"] = o.getString(k) }
        }
    }

    /** Sauvegarde les traductions françaises → langue actuelle (appelé quand l'appli passe en arrière-plan). */
    @Synchronized
    fun save() {
        val ctx = appCtx ?: return
        val lang = current()
        if (lang == "fr") return
        val prefix = "fr>$lang|"
        val o = JSONObject()
        synchronized(cache) { cache.entries.filter { it.key.startsWith(prefix) }.takeLast(6000).forEach { o.put(it.key.removePrefix(prefix), it.value) } }
        runCatching { cacheFile(ctx, lang).writeText(o.toString()) }
    }

    // ---------------------------------------------------------------- traduction

    /** Télécharge les packs de langue (français ↔ langue choisie). */
    suspend fun prepare(lang: String): Boolean {
        if (lang == "fr") return true
        appCtx?.let { loadCache(it, lang) }
        return runCatching {
            // langue patchée : le traducteur ne sert qu'aux commandes tapées librement, on attend donc le wifi
            val cond = if (hasPatch()) DownloadConditions.Builder().requireWifi().build() else DownloadConditions.Builder().build()
            client("fr", lang).downloadModelIfNeeded(cond).await()
            client(lang, "fr").downloadModelIfNeeded(cond).await()
            true
        }.getOrDefault(false)
    }

    /** Traduit un texte, ou null si la traduction a échoué (jamais mise en cache dans ce cas). */
    suspend fun tryTr(text: String, from: String, to: String): String? {
        if (from == to || text.isBlank() || !text.any { it.isLetter() }) return text
        val key = "$from>$to|$text"
        synchronized(cache) { cache[key] }?.let { return it }
        val out = withTimeoutOrNull(8_000L) { runCatching { client(from, to).translate(text).await() }.getOrNull() } ?: return null
        synchronized(cache) { cache[key] = out }
        return out
    }

    /** Traduit un texte (renvoie le texte d'origine si la traduction échoue). */
    suspend fun tr(text: String, from: String, to: String): String = tryTr(text, from, to) ?: text

    /**
     * Traduit d'avance tous les textes de l'appli dans la langue choisie (avec la progression),
     * pour que rien ne reste en français ensuite. Renvoie le nombre de textes non traduits.
     */
    suspend fun pretranslate(texts: List<String>, lang: String, progress: (Int, Int) -> Unit): Int {
        if (lang == "fr") return 0
        var failed = 0
        texts.forEachIndexed { i, t ->
            if (lang != current()) return failed // la langue a changé entre-temps
            if (tryTr(t, "fr", lang) == null) failed++
            if (i % 25 == 0 || i == texts.size - 1) progress(i + 1, texts.size)
        }
        save()
        return failed
    }

    /** Toutes les traductions connues (français → langue actuelle), pour la page. */
    fun dump(): String {
        val lang = current()
        val o = JSONObject()
        if (lang == "fr") return o.toString()
        val prefix = "fr>$lang|"
        synchronized(cache) { cache.entries.filter { it.key.startsWith(prefix) }.forEach { o.put(it.key.removePrefix(prefix), it.value) } }
        return o.toString()
    }

    suspend fun toUser(text: String): String {
        if (current() == "fr") return text
        staticTr(text)?.let { return it }
        return if (hasPatch()) text else tr(text, "fr", current())
    }

    /** Ce que l'utilisateur écrit → français, pour comprendre les commandes (le patch d'abord, sinon le traducteur). */
    suspend fun fromUser(text: String): String {
        if (current() == "fr") return text
        ensureDict()
        rev[norm(text)]?.let { return it }
        return tr(text, current(), "fr")
    }

    /** Traduit des répliques écrites en français vers la langue de l'utilisateur. */
    suspend fun lines(lines: List<Line>): List<Line> =
        if (current() == "fr") lines else lines.map { Line(toUser(it.text), it.mood) }

    /** Traduit en arrière-plan puis rend la main (pour les notifications). */
    fun async(texts: List<String>, done: (List<String>) -> Unit) {
        if (current() == "fr") {
            done(texts)
            return
        }
        bg.launch { done(texts.map { toUser(it) }) }
    }
}
