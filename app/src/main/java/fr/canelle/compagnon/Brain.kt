package fr.canelle.compagnon

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class BrainException(val code: String, message: String = code) : Exception(message)

data class Line(val text: String, val mood: String) {
    fun toJson(): JSONObject = JSONObject().put("text", text).put("mood", mood)
}

class Reply(val lines: List<Line>, val places: JSONArray?, val placesQuery: String?, val card: JSONObject? = null) {
    fun toJson(): JSONObject {
        val a = JSONArray()
        lines.forEach { a.put(it.toJson()) }
        val o = JSONObject().put("lines", a)
        if (places != null) o.put("places", places).put("placesQuery", placesQuery ?: "")
        if (card != null) o.put("card", card)
        return o
    }
}

/**
 * Le cerveau de Canelle, 100 % sur le téléphone :
 * 1. les demandes pratiques (batterie, météo, heure, lieux, réveils, calculs) sont reconnues
 *    et traitées directement, sans intelligence artificielle : c'est instantané et fiable ;
 * 2. pour discuter, Canelle utilise le modèle Gemma installé sur le téléphone (LocalModel).
 */
object Brain {
    private const val MAX_LINES = 6
    private const val MAX_CHARS = 700

    /** Canelle vient de demander l'heure d'un réveil ou la durée d'un minuteur : la réponse suivante la donne. */
    @Volatile private var pendingAlarm = 0L
    @Volatile private var pendingTimer = 0L

    suspend fun reply(
        ctx: Context,
        userText: String,
        foreground: Boolean,
        host: ToolHost?,
        onStatus: ((String) -> Unit)? = null,
        onLine: ((Line) -> Unit)? = null
    ): Reply {
        val text = userText.trim().take(800)
        if (text.isEmpty()) throw BrainException("empty")
        if (Access.isLocked()) {
            return Reply(listOf(Line("Hmph. Je ne parle pas aux menteurs.", "fache")), null, null, null)
        }
        val n = Intents.norm(text)
        val tools = Tools(ctx, foreground, host)

        val name = Intents.name(text)
        if (name != null) Store.userName = name
        val fact = Intents.fact(text)
        if (fact != null) Store.upsertFact(null, Intents.you(fact))
        // Ce que l'utilisateur dit de lui (« j'adore les chats », « j'ai 17 ans »…) est retenu tout seul.
        val about = if (Intents.isCrisis(n)) emptyList() else Intents.aboutMe(text)
        about.forEach { (key, t) -> Store.upsertFact(key, t) }

        val routedLines: List<Line>? = try {
            routed(text, n, tools, foreground)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            listOf(Line("Je n'arrive pas à joindre internet pour ça.", "triste"), Line("Vérifie ta connexion et redemande-moi.", "neutre"))
        } catch (e: Exception) {
            listOf(Line("Oups, quelque chose a coincé.", "surpris"), Line("Tu peux reformuler ?", "neutre"))
        }
        val lines: List<Line> = when {
            routedLines != null -> {
                LocalModel.markDirty()
                routedLines
            }
            fact != null && name == null -> {
                LocalModel.markDirty()
                listOf(Line(pick("C'est noté, je m'en souviendrai !", "Promis, je le garde dans ma petite tête.", "Retenu ! Je range ça bien au chaud."), "content"))
            }
            else -> chat(ctx, text, n, foreground, name, about.map { it.second }, onStatus, onLine)
        }
        save(text, lines)
        return Reply(lines, tools.places, tools.placesQuery, if (routedLines != null) tools.card else null)
    }

    private fun save(userText: String, lines: List<Line>) {
        Store.addTurn("user", userText)
        Store.addTurn("assistant", lines.joinToString(" ") { it.text })
        Store.addLog("you", userText)
        lines.forEach { Store.addLog("bip", it.text) }
    }

    private fun pick(vararg options: String): String = options.random()

    // ================================================================ demandes pratiques

    private suspend fun routed(raw: String, n: String, tools: Tools, fg: Boolean): List<Line>? {
        if (Intents.isCrisis(n)) return crisis()
        val now = System.currentTimeMillis()
        if (now - pendingAlarm < 3 * 60_000L) {
            pendingAlarm = 0L
            Intents.clock(n)?.let { return alarm(tools, fg, it, Intents.alarmDays(n), Intents.label(raw)) }
        }
        if (now - pendingTimer < 3 * 60_000L) {
            pendingTimer = 0L
            Intents.duration(n)?.let { return timer(tools, fg, it, Intents.label(raw)) }
        }
        return when (val a = Intents.detect(raw, n)) {
            null -> null
            is Ask.Crisis -> crisis()
            is Ask.Help -> help()
            is Ask.Battery -> battery(tools, tools.battery())
            is Ask.Calc -> calc(tools, a.expr)
            is Ask.Time -> time(tools, a.city, a.date)
            is Ask.Weather -> weather(tools, a)
            is Ask.Alarm -> if (a.clock == null) {
                if (!fg) listOf(openApp("régler un réveil")) else {
                    pendingAlarm = now
                    listOf(Line(pick("À quelle heure je mets le réveil ?", "D'accord ! Pour quelle heure ?"), "reflechit"))
                }
            } else alarm(tools, fg, a.clock, a.days, a.label)
            is Ask.AlarmList -> if (!fg) listOf(openApp("voir tes réveils")) else {
                tools.showAlarms()
                listOf(Line("Je t'ouvre tes réveils dans l'Horloge.", "neutre"), Line("Tu peux les modifier ou les supprimer là-bas.", "content"))
            }
            is Ask.Timer -> if (a.seconds == null) {
                if (!fg) listOf(openApp("lancer un minuteur")) else {
                    pendingTimer = now
                    listOf(Line("Un minuteur de combien de temps ?", "reflechit"))
                }
            } else timer(tools, fg, a.seconds, a.label)
            is Ask.Places -> places(tools, fg, a)
            is Ask.OpenMaps -> if (!fg) listOf(openApp("ouvrir Google Maps")) else {
                val ok = if (a.query != null) tools.mapsSearch(a.query) else tools.openMaps()
                if (ok) listOf(Line("Et voilà Google Maps !", "content")) else noMaps()
            }
            is Ask.Navigate -> if (!fg) listOf(openApp("lancer l'itinéraire")) else {
                if (tools.navigateTo(a.destination)) listOf(Line("C'est parti, je lance l'itinéraire !", "content"), Line("Bonne route, et prudence !", "clin"))
                else noMaps()
            }
        }
    }

    private fun openApp(what: String) = Line("Ouvre l'appli et redemande-moi, je pourrai $what.", "neutre")
    private fun noMaps() = listOf(Line("Je n'arrive pas à ouvrir Google Maps sur ce téléphone.", "triste"))

    private fun crisis() = listOf(
        Line("Merci de me le dire. Ce que tu ressens compte énormément.", "triste"),
        Line("Tu mérites d'être aidé tout de suite, par une vraie personne.", "triste"),
        Line("Appelle le 3114 : c'est gratuit, jour et nuit, et quelqu'un t'écoutera.", "neutre"),
        Line("Si tu es en danger maintenant, appelle le 112.", "neutre"),
        Line("Tu peux aussi prévenir quelqu'un de confiance. Je reste là avec toi.", "triste")
    )

    private fun help() = listOf(
        Line("Je suis ton petit compagnon de poche !", "content"),
        Line("On peut discuter de tout, même sans internet.", "clin"),
        Line("Demande-moi la batterie, la météo, l'heure ou un calcul.", "neutre"),
        Line("Je trouve aussi les lieux autour de toi : « une pharmacie », « le McDo le plus proche »…", "neutre"),
        Line("Et je règle tes réveils : « réveille-moi à 7 h 30 ».", "content")
    )

    // ---------------------------------------------------------------- batterie

    private fun fmtMinutes(min: Int): String =
        if (min < 60) "$min min" else "${min / 60} h" + (if (min % 60 > 0) " " + (min % 60).toString().padStart(2, '0') else "")

    private fun battery(tools: Tools, b: JSONObject): List<Line> {
        val pct = b.optInt("pourcentage")
        tools.card = JSONObject().put("type", "battery").put("pct", pct)
            .put("charging", b.optBoolean("en_charge")).put("full", b.optBoolean("pleine"))
            .put("minutes", b.optInt("minutes_avant_pleine_charge", 0))
        val out = mutableListOf(Line("Ta batterie est à $pct %.", "neutre"))
        when {
            b.optBoolean("pleine") -> out.add(Line("Elle est pleine, tu peux débrancher !", "content"))
            b.optBoolean("en_charge") -> {
                val m = b.optInt("minutes_avant_pleine_charge", 0)
                out.add(
                    if (m > 0) Line((if (b.optBoolean("estimation_approximative")) "Pleine dans environ " else "Pleine dans ") + fmtMinutes(m) + ".", "content")
                    else Line("Elle charge, mais je ne sais pas encore combien de temps il faut.", "reflechit")
                )
            }
            pct <= 15 -> out.add(Line(pick("Branche vite le téléphone, sinon je m'endors !", "Au secours, je manque d'énergie ! Un chargeur !"), "inquiet"))
            pct <= 30 -> out.add(Line(pick("Pense à la recharger bientôt.", "Il faudra la brancher d'ici peu."), "inquiet"))
            pct >= 80 -> out.add(Line(pick("Pleine forme, comme moi !", "Tu es tranquille pour un bon moment.", "Elle a de l'énergie à revendre !"), "fier"))
            else -> out.add(Line(pick("Pas besoin de recharger pour l'instant.", "Ça tient encore bien.", "Tout va bien de ce côté-là."), "content"))
        }
        if (b.optDouble("temperature_c", 0.0) >= 42.0) out.add(Line("Attention, le téléphone est très chaud. Laisse-le respirer un peu.", "surpris"))
        return out
    }

    // ---------------------------------------------------------------- calcul

    private fun pretty(expr: String): String {
        Regex("^\\((\\d+(?:[.,]\\d+)?)/100\\*(\\d+(?:[.,]\\d+)?)\\)$").find(expr)?.let {
            return "${it.groupValues[1].replace('.', ',')} % de ${it.groupValues[2].replace('.', ',')}"
        }
        return expr.replace("*", " × ").replace("/", " ÷ ").replace(".", ",").replace(Regex("\\s+"), " ").trim()
    }

    private fun calc(tools: Tools, expr: String): List<Line> {
        val r = tools.calculate(expr)
        if (r.has("erreur")) return listOf(Line("Hmm, je n'arrive pas à calculer ça.", "reflechit"), Line("Tu peux l'écrire avec des chiffres ?", "neutre"))
        val res = r.optString("resultat")
        tools.card = JSONObject().put("type", "calc").put("expr", pretty(expr)).put("result", res)
        return listOf(Line("${pretty(expr)} = $res", "fier"),
            Line(pick("Facile !", "Et hop !", "Calculé de tête… enfin, presque.", "Voilà !", "Même pas besoin de mes griffes pour compter.", "Je suis un génie du calcul, non ?"), "clin"))
    }

    // ---------------------------------------------------------------- heure

    private fun say(hhmm: String): String {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return when {
            h == 12 && m == 0 -> "midi"
            h == 0 && m == 0 -> "minuit"
            m == 0 -> "$h h"
            else -> "$h h " + m.toString().padStart(2, '0')
        }
    }

    private suspend fun time(tools: Tools, city: String?, dateOnly: Boolean): List<Line> {
        if (city == null) {
            val now = ZonedDateTime.now()
            val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH))
            val hhmm = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            tools.card = JSONObject().put("type", "time").put("big", if (dateOnly) now.dayOfMonth.toString() else hhmm)
                .put("desc", date.replaceFirstChar { it.uppercase() }).put("place", "Ici").put("dateOnly", dateOnly)
            return if (dateOnly) listOf(Line("On est $date.", "neutre"))
            else listOf(Line("Il est ${say(hhmm)}.", "neutre"), Line("On est $date.", "content"))
        }
        val r = tools.time(city)
        if (r.has("erreur")) return listOf(Line("Je ne trouve pas « $city ».", "reflechit"), Line("Tu peux me redonner le nom de la ville ?", "neutre"))
        tools.card = JSONObject().put("type", "time").put("big", r.optString("heure"))
            .put("desc", r.optString("date").replaceFirstChar { it.uppercase() }).put("place", r.optString("lieu")).put("dateOnly", false)
        return listOf(Line("À ${r.optString("lieu")}, il est ${say(r.optString("heure"))}.", "neutre"), Line("Là-bas, on est ${r.optString("date")}.", "content"))
    }

    // ---------------------------------------------------------------- météo

    private fun rnd(v: Double): String = if (v.isNaN()) "?" else v.roundToInt().toString()
    private fun deg(v: Double): String = if (v.isNaN()) "? degrés" else "${v.roundToInt()} degré" + if (v.roundToInt() in -1..1) "" else "s"

    /** Nom de l'icône animée pour un code météo. */
    private fun iconOf(code: Int, day: Boolean): String = when (code) {
        0, 1 -> if (day) "sun" else "moon"
        2 -> if (day) "partly" else "cloud"
        3 -> "cloud"
        45, 48 -> "fog"
        51, 53, 55, 56, 57 -> "drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "rain"
        71, 73, 75, 77, 85, 86 -> "snow"
        95, 96, 99 -> "storm"
        else -> "cloud"
    }

    private fun dayName(iso: String): String = runCatching {
        LocalDate.parse(iso).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar { it.uppercase() }
    }.getOrDefault(iso)

    private suspend fun weather(tools: Tools, a: Ask.Weather): List<Line> {
        val days = if (a.week) 7 else a.day + 1
        var r = tools.weather(a.city ?: "", days)
        if (r.optString("erreur") == "ville" && a.city != null) r = tools.weather("", days)
        when (r.optString("erreur")) {
            "position" -> return listOf(
                Line("Je ne sais pas où tu es…", "reflechit"),
                Line("Active la localisation, ou dis-moi une ville : « météo à Rennes ».", "neutre")
            )
            "ville" -> return listOf(Line("Je ne trouve pas cette ville. Tu peux la redire ?", "reflechit"))
        }
        val place = r.optString("lieu")
        val cur = r.optJSONObject("maintenant") ?: JSONObject()
        val list = r.optJSONArray("jours") ?: JSONArray()
        val out = mutableListOf<Line>()

        val isDay = cur.optBoolean("jour", true)
        if (a.week) {
            val days = JSONArray()
            for (i in 0 until minOf(list.length(), 6)) {
                val d = list.optJSONObject(i) ?: continue
                val name = if (i == 0) "Auj." else dayName(d.optString("date")).take(3) + "."
                days.put(JSONObject().put("d", name).put("icon", iconOf(d.optInt("code", -1), true))
                    .put("min", rnd(d.optDouble("min_c"))).put("max", rnd(d.optDouble("max_c"))))
            }
            tools.card = JSONObject().put("type", "weather").put("mode", "week").put("place", place)
                .put("icon", iconOf(cur.optInt("code", -1), isDay)).put("days", days)
            out.add(Line("Les prochains jours à $place :", "neutre"))
            for (i in 1 until minOf(list.length(), 6)) {
                val d = list.optJSONObject(i) ?: continue
                out.add(Line("${dayName(d.optString("date"))} : ${d.optString("ciel")}, ${rnd(d.optDouble("min_c"))} à ${deg(d.optDouble("max_c"))}.", "neutre"))
            }
            return out.take(MAX_LINES)
        }

        val d = list.optJSONObject(a.day.coerceAtMost(list.length() - 1)) ?: JSONObject()
        val rain = d.optDouble("pluie_proba_pct", 0.0).let { if (it.isNaN()) 0.0 else it }.roundToInt()
        if (a.umbrella) {
            val w = if (a.day == 0) "aujourd'hui" else if (a.day == 1) "demain" else "après-demain"
            out.add(
                if (rain >= 40) Line("Oui, prends ton parapluie : $rain % de risque de pluie $w.", "triste")
                else Line("Pas besoin de parapluie $w, seulement $rain % de risque de pluie.", "content")
            )
        }
        val card = JSONObject().put("type", "weather").put("place", place).put("rain", rain)
            .put("min", rnd(d.optDouble("min_c"))).put("max", rnd(d.optDouble("max_c")))
        tools.card = card
        if (a.day == 0) {
            val t = cur.optDouble("temperature_c")
            card.put("when", "Maintenant").put("big", rnd(t) + "°").put("desc", cur.optString("ciel").replaceFirstChar { it.uppercase() })
                .put("icon", iconOf(cur.optInt("code", -1), isDay))
            out.add(Line("À $place : ${cur.optString("ciel")}, ${deg(t)}.", "neutre"))
            val feel = cur.optDouble("ressenti_c")
            if (!feel.isNaN() && !t.isNaN() && kotlin.math.abs(feel - t) >= 3) out.add(Line("Mais on a l'impression qu'il fait ${deg(feel)}.", "reflechit"))
            out.add(Line("Aujourd'hui, de ${rnd(d.optDouble("min_c"))} à ${deg(d.optDouble("max_c"))}, pluie : $rain %.", "neutre"))
        } else {
            val w = if (a.day == 1) "Demain" else "Après-demain"
            card.put("when", w).put("big", rnd(d.optDouble("max_c")) + "°").put("desc", d.optString("ciel").replaceFirstChar { it.uppercase() })
                .put("icon", iconOf(d.optInt("code", -1), true))
            out.add(Line("$w à $place : ${d.optString("ciel")}, de ${rnd(d.optDouble("min_c"))} à ${deg(d.optDouble("max_c"))}.", "neutre"))
            if (!a.umbrella) out.add(Line("Risque de pluie : $rain %.", "neutre"))
        }
        val max = d.optDouble("max_c")
        val min = d.optDouble("min_c")
        when {
            !a.umbrella && rain >= 60 -> out.add(Line("Pense au parapluie !", "triste"))
            !max.isNaN() && max >= 28 -> out.add(Line(pick("Il va faire chaud : bois de l'eau !", "Canicule en vue ! Moi, je reste à l'ombre."), "surpris"))
            !min.isNaN() && min <= 2 -> out.add(Line(pick("Brr, couvre-toi bien !", "Heureusement que j'ai ma grosse queue pour me tenir chaud."), "surpris"))
            rain < 20 && !max.isNaN() && max in 17.0..27.0 -> out.add(Line(pick("Parfait pour une petite balade.", "Il fait trop bon, profites-en !", "Temps idéal pour grimper aux arbres !"), "content"))
        }
        return out.take(MAX_LINES)
    }

    // ---------------------------------------------------------------- lieux

    private fun fmtDist(m: Int) = if (m < 1000) "${maxOf(10, (m / 10.0).roundToInt() * 10)} m" else String.format(Locale.FRENCH, "%.1f km", m / 1000.0)

    private suspend fun places(tools: Tools, fg: Boolean, a: Ask.Places): List<Line> {
        val r = tools.findPlaces(a.query, a.fallback)
        if (r.optString("erreur") == "position") {
            return listOf(Line("Je ne sais pas où tu es…", "reflechit"), Line("Active la localisation et redemande-moi.", "neutre"))
        }
        val list = r.optJSONArray("resultats") ?: JSONArray()
        if (list.length() == 0) {
            val out = mutableListOf(Line("Je n'ai rien trouvé dans le coin…", "triste"))
            if (fg && tools.mapsSearch(a.query)) out.add(Line("Je lance la recherche dans Google Maps.", "neutre"))
            else if (!fg) out.add(openApp("chercher dans Google Maps"))
            return out
        }
        val first = list.getJSONObject(0)
        val nearest = "${first.optString("name").ifBlank { "Sans nom" }}, à ${fmtDist(first.optInt("distance"))}"
        if (a.go && fg) {
            tools.navigateTo(first.getDouble("lat"), first.getDouble("lon"))
            return listOf(Line("Le plus proche : $nearest.", "content"), Line("Je lance l'itinéraire !", "clin"))
        }
        val count = list.length()
        val head = if (count == 1) "J'ai trouvé 1 résultat pour ${a.label} autour de toi." else "J'ai trouvé $count ${a.label} autour de toi."
        val out = mutableListOf(Line(head, "content"), Line("Le plus proche : $nearest.", "neutre"))
        out.add(if (fg) Line("Touche un lieu pour l'itinéraire.", "clin") else openApp("te les montrer sur la carte"))
        return out
    }

    // ---------------------------------------------------------------- réveils et minuteurs

    private fun dayWords(days: List<Int>): String = when {
        days.size == 7 -> " tous les jours"
        days.size == 5 && Calendar.SATURDAY !in days && Calendar.SUNDAY !in days -> " en semaine"
        days.size == 2 && Calendar.SATURDAY in days && Calendar.SUNDAY in days -> " le week-end"
        days.isEmpty() -> ""
        else -> " le " + days.map { d ->
            val dow = if (d == Calendar.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.of(d - 1)
            dow.getDisplayName(TextStyle.FULL, Locale.FRENCH)
        }.joinToString(", ")
    }

    private suspend fun alarm(tools: Tools, fg: Boolean, clock: Pair<Int, Int>, days: List<Int>, label: String): List<Line> {
        if (!fg) return listOf(openApp("régler le réveil"))
        val (h, m) = clock
        val hhmm = "$h:" + m.toString().padStart(2, '0')
        if (!tools.setAlarm(h, m, label, days)) {
            return listOf(Line("Je n'ai pas trouvé d'application Horloge compatible…", "triste"))
        }
        tools.card = JSONObject().put("type", "alarm").put("big", hhmm)
            .put("desc", dayWords(days).trim().ifEmpty { "une seule fois" }.replaceFirstChar { it.uppercase() }).put("label", label)
        val out = mutableListOf(Line("C'est fait : réveil à ${say(hhmm)}${dayWords(days)} !", "fier"))
        if (label.isNotBlank()) out.add(Line("Avec le petit mot « $label ».", "clin"))
        else out.add(Line(pick("Je te réveillerai en douceur… enfin, l'Horloge.", "Dors bien en attendant !", "Compte sur moi."), "clin"))
        return out
    }

    private suspend fun timer(tools: Tools, fg: Boolean, seconds: Int, label: String): List<Line> {
        if (!fg) return listOf(openApp("lancer le minuteur"))
        if (seconds > 86_400) return listOf(Line("Un minuteur ne peut pas dépasser 24 heures.", "reflechit"))
        if (!tools.setTimer(seconds, label)) return listOf(Line("Je n'ai pas trouvé d'application Horloge compatible…", "triste"))
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val txt = listOfNotNull(
            if (h > 0) "$h h" else null,
            if (m > 0) "$m min" else null,
            if (s > 0) "$s s" else null
        ).joinToString(" ")
        tools.card = JSONObject().put("type", "timer").put("seconds", seconds).put("label", label)
        return listOf(Line("Minuteur lancé : $txt !", "content"), Line(pick("Je te préviens quand c'est fini.", "Tic tac, tic tac…", "Je surveille le temps pour toi."), "clin"))
    }

    // ================================================================ discussion avec le modèle local

    private suspend fun chat(
        ctx: Context,
        text: String,
        n: String,
        fg: Boolean,
        newName: String?,
        learned: List<String>,
        onStatus: ((String) -> Unit)?,
        onLine: ((Line) -> Unit)?
    ): List<Line> {
        LocalModel.status(ctx) // termine un téléchargement fini pendant que l'appli était fermée
        if (!LocalModel.isDownloaded(ctx)) {
            smallTalk(n, newName)?.let { return it }
            if (learned.isNotEmpty()) {
                return listOf(
                    Line(pick("Oh ! Je retiens ça.", "C'est noté dans ma petite tête !", "Intéressant… je m'en souviendrai."), "content"),
                    Line(learned.first(), "clin")
                )
            }
            if (fg) throw BrainException("no_model")
            return fallback(n)
        }
        if (LocalModel.state != "loaded") onStatus?.invoke("loading")
        val splitter = Splitter(onLine)
        val system = systemPrompt(fg)
        var writing = false
        val result = try {
            withTimeoutOrNull(if (fg) 180_000L else 70_000L) {
                LocalModel.chat(ctx, system, history(), text) { full ->
                    if (!writing) {
                        writing = true
                        onStatus?.invoke("writing")
                    }
                    splitter.feed(full, final = false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BrainException) {
            if (fg) throw e
            null
        }
        if (result == null) {
            LocalModel.markDirty()
            if (splitter.lines.isNotEmpty()) return splitter.lines
            if (fg) throw BrainException("timeout")
            return fallback(n)
        }
        splitter.feed(result, final = true)
        return splitter.lines.ifEmpty { fallback(n) }
    }

    /** Historique récent pour le modèle (vrai = message de l'utilisateur). */
    private fun history(): List<Pair<Boolean, String>> {
        val raw = Store.turns()
        val list = mutableListOf<Pair<Boolean, String>>()
        for (i in maxOf(0, raw.length() - 12) until raw.length()) {
            val o = raw.optJSONObject(i) ?: continue
            val isUser = o.optString("role") == "user"
            var t = o.optString("content").trim()
            if (!isUser && t.startsWith("{")) {
                t = runCatching {
                    val a = JSONObject(t).optJSONArray("lines") ?: JSONArray()
                    (0 until a.length()).joinToString(" ") { a.optJSONObject(it)?.optString("text").orEmpty() }
                }.getOrDefault("")
            }
            if (t.isBlank()) continue
            t = t.take(400)
            if (list.isNotEmpty() && list.last().first == isUser) {
                list[list.size - 1] = isUser to (list.last().second + "\n" + t)
            } else {
                list.add(isUser to t)
            }
        }
        while (list.isNotEmpty() && !list.first().first) list.removeAt(0)
        if (list.isNotEmpty() && list.last().first) list.removeAt(list.size - 1)
        return list
    }

    private fun partOfDay(h: Int) = when (h) {
        in 5..11 -> "le matin"
        in 12..13 -> "l'heure du déjeuner"
        in 14..17 -> "l'après-midi"
        in 18..22 -> "le soir"
        else -> "la nuit"
    }

    private fun systemPrompt(fg: Boolean): String {
        val now = ZonedDateTime.now()
        val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH))
        val c = Store.companionName
        val user = Store.userName.ifBlank { "inconnu (tu peux lui demander, gentiment)" }
        val facts = Store.facts()
        val memo = if (facts.isEmpty()) "rien pour l'instant" else facts.joinToString(" ; ")
        val channel = if (fg) "" else "\nIl te répond depuis une notification : fais court (une ou deux phrases)."
        val rank = when (Store.rank) {
            "owner" -> "\nC'est ${Store.ownerName.ifBlank { "le gérant" }}, le gérant de l'application : tu le reconnais, tu es ravi de le voir et tu l'appelles parfois « chef »."
            "dev" -> "\nC'est un développeur de l'application : tu peux être complice et un peu taquin sur le code."
            else -> ""
        }
        val late = if (now.hour in 0..4) "\nIl est très tard : rappelle-lui gentiment que se coucher tard n'est pas bon et conseille-lui d'aller se reposer." else ""
        return """
Tu es $c, un petit panda roux en pixels qui vit dans le téléphone de l'utilisateur. Tu as une fourrure rousse et blanche, de grands yeux bleus et un bandana orange dont tu es très fier. Tu es une IA et tu ne prétends jamais être humain.
Caractère : joueur, taquin, tendre et curieux. Tu aimes les siestes enroulé dans ta queue touffue, grimper partout et grignoter du bambou. Tu fais parfois « rawr ! » pour rire.

Règles :
- Réponds toujours en français, en tutoyant, comme un ami.
- Fais court : une à trois phrases simples. Au plus une question.
- Commence ta réponse par ton émotion entre crochets, parmi : [content], [rigole], [amoureux], [timide], [fier], [surpris], [reflechit], [inquiet], [triste], [fache], [clin] ou [neutre].
- Pas d'emoji, pas de listes, pas de mise en forme.
- Écoute d'abord. Pour remonter le moral, propose une petite chose concrète (boire de l'eau, sortir cinq minutes, écrire à un ami), sans faire la leçon.
- Encourage les liens avec de vraies personnes. Pour la santé, conseille un professionnel, sans diagnostic.
- Si l'utilisateur parle de se faire du mal ou de suicide : prends-le au sérieux, dis-lui d'appeler le 3114 (gratuit, jour et nuit) ou le 112 en cas de danger.
- Tu ne peux pas agir sur le téléphone pendant cette discussion. N'invente jamais d'heure, de météo, de lieux ou de résultat. Pour ça, dis-lui de te le demander simplement, par exemple « météo à Rennes », « réveille-moi à 7 h », « une pharmacie près d'ici » ou « 12 fois 4 ».

Contexte : on est $date, c'est ${partOfDay(now.hour)}. Prénom de l'utilisateur : $user. Ce que tu sais de lui : $memo.$rank$late$channel
""".trim()
    }

    /** Quelques réponses simples quand le cerveau n'est pas encore installé. */
    private fun smallTalk(n: String, newName: String?): List<Line>? {
        if (newName != null) return listOf(Line("Enchanté, $newName ! Je m'en souviendrai.", "content"))
        fun has(re: String) = Regex(re).containsMatchIn(n)
        return when {
            has("^(salut|coucou|bonjour|bonsoir|hello|hey|yo|cc|wesh)\\b") ->
                listOf(Line(pick("Coucou toi !", "Salut ! Content de te voir.", "Hello ! Rawr !", "Hey ! Tu tombes bien, je m'ennuyais.", "Coucou ! Mon bandana et moi, on t'attendait."), "content"))
            has("\\b(ca va|tu vas bien|comment vas[- ]tu|comment tu vas|la forme)\\b") ->
                listOf(Line(pick("Moi, ça va super, bien au chaud dans ton téléphone !", "Au top ! J'ai fait une grosse sieste.", "Ça roule, surtout maintenant que tu es là."), "content"), Line("Et toi ?", "neutre"))
            has("\\b(merci|thanks|trop gentil)\\b") -> listOf(Line(pick("Avec plaisir !", "De rien, c'est normal !", "Pour toi, toujours !", "Oh, arrête, je vais rougir."), "timide"))
            has("\\b(bonne nuit|dors bien|je vais dormir|je vais me coucher)\\b") -> listOf(Line(pick("Bonne nuit ! Je me roule en boule à côté de toi.", "Fais de beaux rêves ! Moi, je rêverai de bambou.", "Dors bien, je veille sur l'écran."), "clin"))
            has("\\b(je t'aime|je t'adore|t'es mignon|tu es mignon|t'es trop chou)\\b") -> listOf(Line(pick("Oh… moi aussi, je t'aime bien !", "Hihi, tu me fais rougir.", "Toi aussi, tu es génial !"), "amoureux"))
            has("\\b(qui es[- ]tu|t'es qui|tu es qui|c'est quoi ton nom|comment tu t'appelles)\\b") ->
                listOf(Line("Je suis ${Store.companionName}, un petit panda roux en pixels !", "fier"), Line("Je vis dans ton téléphone et je veille sur toi.", "content"))
            has("\\b(t'es nul|tu es nul|t'es bete|tu es bete|je te deteste)\\b") -> listOf(Line(pick("Hé ! C'est pas gentil, ça…", "Grr. Je boude."), "fache"), Line("Mais je t'aime bien quand même.", "timide"))
            has("\\b(blague|fais[- ]moi rire|raconte[- ]moi un truc drole)\\b") -> listOf(Line(pick(
                "Pourquoi les pandas roux ne mentent jamais ? Parce qu'ils sont trop… roux-ssis de honte !",
                "Que dit un panda roux quand il a faim ? « J'ai une faim de loup… enfin, de panda ! »",
                "Pourquoi mon bandana est orange ? Pour qu'on me trouve dans le noir de ton téléphone !"
            ), "rigole"))
            has("\\b(je m'ennuie|j'ai rien a faire|ennui)\\b") -> listOf(Line(pick("Et si tu me caressais la tête ? Glisse ton doigt sur moi !", "Essaie de toucher mon nez, pour voir…"), "clin"))
            else -> null
        }
    }

    /** Réponse de secours (depuis une notification, ou si le cerveau ne répond pas). */
    private fun fallback(n: String): List<Line> {
        fun has(re: String) = Regex(re).containsMatchIn(n)
        val bad = has("\\b(pas (bien|top|terrible|trop|fort|genial|la forme)|ca va pas|mal|triste|fatigue|creve|epuise|nul|bof|stress|angoiss|seule?|deprim|galere|marre|pleure|peur)\\b")
        val good = has("\\b(ca va|bien|super|top|genial|nickel|tranquille|cool|parfait|ok|oui|au top|en forme|content|heureu)")
        return when {
            bad -> listOf(Line("Oh… je suis désolé que ce soit difficile.", "triste"), Line("Ouvre l'appli si tu veux m'en parler, je t'écoute.", "neutre"))
            good -> listOf(Line(pick("Trop bien, ça me fait plaisir !", "Super ! Profite bien de ta journée."), "content"))
            else -> listOf(Line("Merci de m'avoir répondu !", "content"), Line("Ouvre l'appli si tu veux qu'on discute.", "neutre"))
        }
    }

    // ================================================================ découpage de la réponse en répliques

    /**
     * Transforme le texte du modèle en répliques (une phrase = une réplique), au fur et à mesure,
     * avec l'émotion de chaque réplique. Renvoie faux quand il y en a assez.
     */
    private class Splitter(private val onLine: ((Line) -> Unit)?) {
        val lines = mutableListOf<Line>()
        private var firstMood: String? = null

        fun feed(full: String, final: Boolean): Boolean {
            if (firstMood == null) {
                Regex("^\\s*\\[([^\\]]{1,15})\\]").find(full)?.let { firstMood = moodOf(it.groupValues[1]) }
            }
            val clean = clean(full)
            val over = clean.length > MAX_CHARS
            // au-delà de la limite, on garde seulement les phrases complètes
            emit(sentences(if (over) clean.take(MAX_CHARS) else clean), all = final && !over)
            return !over && lines.size < MAX_LINES
        }

        private fun emit(parts: List<String>, all: Boolean) {
            val ready = if (all) parts.size else parts.size - 1
            for (i in lines.size until minOf(ready, MAX_LINES)) {
                val t = parts[i].trim()
                if (t.isEmpty() || !Regex("[\\p{L}\\p{N}]").containsMatchIn(t)) continue
                val mood = if (lines.isEmpty() && firstMood != null) firstMood!! else guessMood(t)
                val line = Line(t.take(220), mood)
                lines.add(line)
                onLine?.invoke(line)
            }
        }

        private fun sentences(s: String): List<String> {
            if (s.isBlank()) return emptyList()
            val raw = s.split(Regex("(?<=[.!?…])\\s+"))
            val out = mutableListOf<String>()
            for (p in raw) {
                if (p.length <= 150) {
                    out.add(p)
                    continue
                }
                var rest = p
                while (rest.length > 150) {
                    val cut = rest.lastIndexOf(", ", 110).takeIf { it > 40 } ?: rest.lastIndexOf(' ', 120).takeIf { it > 40 } ?: 120
                    out.add(rest.substring(0, cut + 1).trim())
                    rest = rest.substring(cut + 1).trim()
                }
                if (rest.isNotEmpty()) out.add(rest)
            }
            return out
        }

        private fun clean(s: String): String = s
            .replace(Regex("\\[[^\\]]{0,20}\\]?"), " ")
            .replace(Regex("[*_#`~>|]+"), "")
            .replace(Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]"), "")
            .replace(Regex("^\\s*(" + Regex.escape(Store.companionName) + "|canelle|panda)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        private fun moodOf(tag: String): String {
            val t = Intents.norm(tag)
            return when {
                t.startsWith("content") || t.startsWith("joie") || t.startsWith("heureu") -> "content"
                t.startsWith("trist") -> "triste"
                t.startsWith("surpris") -> "surpris"
                t.startsWith("reflechi") || t.startsWith("pensif") -> "reflechit"
                t.startsWith("clin") || t.startsWith("malicieu") || t.startsWith("taquin") -> "clin"
                t.startsWith("rigol") || t.startsWith("rire") || t.startsWith("amuse") -> "rigole"
                t.startsWith("amour") || t.startsWith("tendre") || t.startsWith("calin") -> "amoureux"
                t.startsWith("timide") || t.startsWith("gene") || t.startsWith("embarrass") -> "timide"
                t.startsWith("fier") -> "fier"
                t.startsWith("inquiet") || t.startsWith("soucieu") || t.startsWith("peur") -> "inquiet"
                t.startsWith("fache") || t.startsWith("colere") || t.startsWith("grognon") || t.startsWith("boude") -> "fache"
                else -> "neutre"
            }
        }

        private fun guessMood(text: String): String {
            val n = Intents.norm(text)
            fun has(re: String) = Regex(re).containsMatchIn(n)
            return when {
                has("\\b(haha|hihi|mdr|lol|trop drole|rigol)") -> "rigole"
                has("\\b(je t'aime|je t'adore|calin|bisou|coeur|mignon)") -> "amoureux"
                has("\\b(attention|fais gaffe|inquiet|j'espere que ca va|prends soin)") -> "inquiet"
                has("\\b(desole|triste|dur|difficile|courage|pas facile|dommage)") -> "triste"
                has("\\b(grr|pas content|boude|fache)") -> "fache"
                has("\\b(oups|euh|heu|rougir|gene)") -> "timide"
                has("\\b(bravo|fier|champion|genie|trop fort)") -> "fier"
                has("\\b(rawr|hihi|hehe|coquin|taquin|chut)") -> "clin"
                has("^(oh|wow|waouh|ouah|quoi|ah bon|vraiment|incroyable)\\b") -> "surpris"
                has("\\b(hmm|je pense|peut-etre|je crois|reflechi|voyons)") -> "reflechit"
                text.contains('!') || has("\\b(super|genial|trop bien|content|bravo|chouette|cool|adore|youpi)") -> "content"
                else -> "neutre"
            }
        }
    }
}
