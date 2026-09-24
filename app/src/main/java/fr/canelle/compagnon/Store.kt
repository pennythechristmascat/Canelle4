package fr.canelle.compagnon

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tout ce que Canelle garde en mémoire, sur le téléphone uniquement :
 * réglages, souvenirs, historique de conversation et fil de la notification.
 */
object Store {
    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        if (!::p.isInitialized) {
            p = ctx.applicationContext.getSharedPreferences("canelle", Context.MODE_PRIVATE)
        }
    }

    private fun str(k: String, d: String = ""): String = p.getString(k, d) ?: d
    private fun putStr(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun putInt(k: String, v: Int) = p.edit().putInt(k, v).apply()
    private fun putLong(k: String, v: Long) = p.edit().putLong(k, v).apply()
    private fun putBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()

    // --- cerveau local
    /** Numéro du téléchargement en cours dans le gestionnaire de téléchargements d'Android (-1 = aucun). */
    var downloadId: Long
        get() = p.getLong("downloadId", -1L)
        set(v) = putLong("downloadId", v)
    /** Utiliser la puce graphique quand c'est possible (plus rapide). */
    var useGpu: Boolean
        get() = p.getBoolean("useGpu", true)
        set(v) = putBool("useGpu", v)
    /** Vrai pendant un démarrage sur la puce graphique : s'il est encore vrai au lancement suivant, c'est qu'elle a planté. */
    var gpuPending: Boolean
        get() = p.getBoolean("gpuPending", false)
        set(v) { p.edit().putBoolean("gpuPending", v).commit() }
    var gpuBroken: Boolean
        get() = p.getBoolean("gpuBroken", false)
        set(v) = putBool("gpuBroken", v)
    var brainIntroShown: Boolean
        get() = p.getBoolean("brainIntroShown", false)
        set(v) = putBool("brainIntroShown", v)

    // --- rang donné par un code d'accès ("dev" ou "owner") et avertissement de confidentialité
    var rank: String
        get() = str("rank")
        set(v) = putStr("rank", v)
    var ownerName: String
        get() = str("ownerName")
        set(v) = putStr("ownerName", v)
    var hidePrivacy: Boolean
        get() = p.getBoolean("hidePrivacy", false)
        set(v) = putBool("hidePrivacy", v)

    // --- réglages
    var companionName: String
        get() = str("companionName", "Canelle")
        set(v) = putStr("companionName", v)
    var userName: String
        get() = str("userName")
        set(v) = putStr("userName", v)
    var sound: String
        get() = str("sound", "bips")
        set(v) = putStr("sound", v)
    var visits: Int
        get() = p.getInt("visits", 0)
        set(v) = putInt("visits", v)
    var lastSeen: Long
        get() = p.getLong("lastSeen", 0L)
        set(v) = putLong("lastSeen", v)

    // --- prises de nouvelles
    var checkins: Boolean
        get() = p.getBoolean("checkins", true)
        set(v) = putBool("checkins", v)
    var intervalHours: Int
        get() = p.getInt("intervalHours", 4)
        set(v) = putInt("intervalHours", v)
    var quietStart: Int
        get() = p.getInt("quietStart", 22)
        set(v) = putInt("quietStart", v)
    var quietEnd: Int
        get() = p.getInt("quietEnd", 9)
        set(v) = putInt("quietEnd", v)
    var lastCheckin: Long
        get() = p.getLong("lastCheckin", 0L)
        set(v) = putLong("lastCheckin", v)
    var lastBatteryWarn: Long
        get() = p.getLong("lastBatteryWarn", 0L)
        set(v) = putLong("lastBatteryWarn", v)

    // --- dernière position connue (utile quand l'appli est en arrière-plan)
    var lastLat: Double
        get() = str("lastLat", "0").toDoubleOrNull() ?: 0.0
        set(v) = putStr("lastLat", v.toString())
    var lastLon: Double
        get() = str("lastLon", "0").toDoubleOrNull() ?: 0.0
        set(v) = putStr("lastLon", v.toString())
    var lastLocTime: Long
        get() = p.getLong("lastLocTime", 0L)
        set(v) = putLong("lastLocTime", v)

    fun lastPosition(): Pair<Double, Double>? =
        if (lastLocTime > 0L) lastLat to lastLon else null

    // --- nom de la ville trouvée pour la dernière position (évite de le redemander à chaque fois)
    var placeName: String
        get() = str("placeName")
        set(v) = putStr("placeName", v)
    var placeLat: Double
        get() = str("placeLat", "0").toDoubleOrNull() ?: 0.0
        set(v) = putStr("placeLat", v.toString())
    var placeLon: Double
        get() = str("placeLon", "0").toDoubleOrNull() ?: 0.0
        set(v) = putStr("placeLon", v.toString())

    // --- souvenirs
    @Synchronized
    fun facts(): MutableList<String> {
        val a = JSONArray(str("facts", "[]"))
        return MutableList(a.length()) { a.optString(it) }
    }

    @Synchronized
    fun addFacts(newFacts: List<String>) {
        val list = facts()
        for (f in newFacts) {
            val t = f.trim().take(140)
            if (t.isNotEmpty() && list.none { it.equals(t, ignoreCase = true) }) list.add(t)
        }
        putStr("facts", JSONArray(list.takeLast(40)).toString())
    }

    /** Ajoute un souvenir. [key] : début de phrase qui remplace l'ancien souvenir du même sujet (« Tu as », « Tu habites »…). */
    @Synchronized
    fun upsertFact(key: String?, text: String) {
        val list = facts()
        val t = text.trim().take(140)
        if (t.isEmpty()) return
        if (key != null) list.removeAll { it.startsWith(key, ignoreCase = true) }
        list.removeAll { it.equals(t, ignoreCase = true) }
        list.add(t)
        putStr("facts", JSONArray(list.takeLast(40)).toString())
    }

    @Synchronized
    fun removeFact(text: String) {
        val list = facts()
        list.removeAll { it == text }
        putStr("facts", JSONArray(list).toString())
    }

    // --- historique de la discussion (donné au cerveau local)
    @Synchronized
    fun turns(): JSONArray = JSONArray(str("turns", "[]"))

    @Synchronized
    fun addTurn(role: String, content: String) {
        val a = turns()
        a.put(JSONObject().put("role", role).put("content", content))
        putStr("turns", tail(a, 40).toString())
    }

    // --- journal affiché dans l'application ("you" = l'utilisateur, "bip" = Canelle)
    @Synchronized
    fun log(): JSONArray = JSONArray(str("log", "[]"))

    @Synchronized
    fun addLog(who: String, text: String) {
        val a = log()
        a.put(JSONObject().put("w", who).put("t", text))
        putStr("log", tail(a, 200).toString())
    }

    // --- fil de discussion de la notification
    @Synchronized
    fun thread(): JSONArray = JSONArray(str("thread", "[]"))

    @Synchronized
    fun threadReset(first: String) {
        val a = JSONArray().put(JSONObject().put("w", "bip").put("t", first).put("at", System.currentTimeMillis()))
        putStr("thread", a.toString())
    }

    @Synchronized
    fun threadAdd(who: String, text: String) {
        val a = thread()
        a.put(JSONObject().put("w", who).put("t", text).put("at", System.currentTimeMillis()))
        putStr("thread", tail(a, 10).toString())
    }

    @Synchronized
    fun forget() {
        p.edit().remove("facts").remove("turns").remove("log").remove("thread").remove("userName").apply()
    }

    fun uiState(hasModel: Boolean): JSONObject = JSONObject()
        .put("companionName", companionName)
        .put("userName", userName)
        .put("facts", JSONArray(facts()))
        .put("log", log())
        .put("sound", sound)
        .put("visits", visits)
        .put("lastSeen", lastSeen)
        .put("hasModel", hasModel)
        .put("rank", rank)
        .put("ownerName", ownerName)
        .put("hidePrivacy", hidePrivacy)

    private fun tail(a: JSONArray, n: Int): JSONArray {
        if (a.length() <= n) return a
        val out = JSONArray()
        for (i in a.length() - n until a.length()) out.put(a.get(i))
        return out
    }
}
