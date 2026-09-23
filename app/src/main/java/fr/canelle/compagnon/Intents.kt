package fr.canelle.compagnon

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/** Ce que l'utilisateur demande, quand c'est une action que le téléphone sait faire tout seul. */
sealed class Ask {
    object Help : Ask()
    object Crisis : Ask()
    object Battery : Ask()
    object AlarmList : Ask()
    data class Time(val city: String?, val date: Boolean) : Ask()
    data class Weather(val city: String?, val day: Int, val week: Boolean, val umbrella: Boolean) : Ask()
    data class Calc(val expr: String) : Ask()
    data class Alarm(val clock: Pair<Int, Int>?, val days: List<Int>, val label: String) : Ask()
    data class Timer(val seconds: Int?, val label: String) : Ask()
    data class Places(val query: String, val fallback: String?, val label: String, val go: Boolean) : Ask()
    data class OpenMaps(val query: String?) : Ask()
    data class Navigate(val destination: String) : Ask()
}

/**
 * Reconnaît les demandes courantes en français, sans intelligence artificielle :
 * c'est instantané, fiable, et ça marche même quand le cerveau n'est pas téléchargé.
 */
object Intents {

    /** Minuscules, sans accents, apostrophes et espaces normalisés. */
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('’', '\'').replace('‘', '\'').replace('œ', 'o')
        .replace(Regex("[\\s\\u00A0\\u202F]+"), " ")
        .trim()

    private fun has(n: String, re: String) = Regex(re).containsMatchIn(n)

    // ---------------------------------------------------------------- détection principale

    fun detect(raw: String, n: String): Ask? {
        if (isCrisis(n)) return Ask.Crisis
        if (has(n, HELP)) return Ask.Help

        if (has(n, "\\b(reveils?|alarmes?)\\b") && has(n, "\\b(annule|annuler|supprime|supprimer|enleve|enlever|desactive|desactiver|arrete|arreter|quels?|liste|mes)\\b")) {
            return Ask.AlarmList
        }
        if (has(n, ALARM)) return Ask.Alarm(clock(n), alarmDays(n), label(raw))
        if (has(n, TIMER)) return Ask.Timer(duration(n), label(raw))

        calc(n)?.let { return Ask.Calc(it) }

        if (has(n, BATTERY) || (has(n, "\\bpourcent") && n.none { it.isDigit() })) return Ask.Battery

        val date = has(n, DATE_Q)
        if (has(n, TIME_Q) || date) return Ask.Time(city(n), date && !has(n, TIME_Q))

        if (has(n, WEATHER)) {
            val day = when {
                has(n, "\\bapres[- ]demain\\b") -> 2
                has(n, "\\bdemain\\b") -> 1
                else -> 0
            }
            val week = has(n, "\\b(cette semaine|la semaine|semaine prochaine|7 jours|sept jours|prochains jours|week-?end)\\b")
            val umbrella = has(n, "\\bparapluie\\b|\\bpleuvoir\\b|\\bpleut[- ]il\\b|\\bpleuvra[- ]t[- ]il\\b")
            return Ask.Weather(city(n), day, week, umbrella)
        }

        if (has(n, OPEN_MAPS)) {
            val q = Regex("\\b(?:maps|map|carte|gps)\\b\\s+(?:sur|pour|vers|a|au|avec)\\s+(.{2,60})$").find(n)?.groupValues?.get(1)?.trim()
            return Ask.OpenMaps(q?.takeIf { it.isNotBlank() })
        }

        val nav = Regex(NAVIGATE).find(n)
        val cat = category(n)
        if (nav != null) {
            val dest = nav.groupValues[nav.groupValues.size - 1].trim().trimEnd('?', '!', '.', ' ')
            if (cat != null) return Ask.Places(cat.query, cat.en, cat.label, go = true)
            if (dest.length >= 2) return Ask.Navigate(dest)
        }

        if (cat != null) {
            val words = n.split(' ').count { it.isNotBlank() }
            if (words <= 3 || has(n, PLACE_INTENT) || n.endsWith("?")) {
                return Ask.Places(cat.query, cat.en, cat.label, go = false)
            }
        }
        Regex(GENERIC_PLACE).find(n)?.let { m ->
            val q = m.groupValues[4].trim()
            if (q.length in 2..40 && q.split(' ').size <= 5) return Ask.Places(q, null, "« $q »", go = false)
        }
        return null
    }

    // ---------------------------------------------------------------- expressions

    private const val HELP = "\\b(que sais[- ]tu faire|qu'est[- ]ce que tu (sais|peux) faire|tu sais faire quoi|tu peux faire quoi|" +
        "tu sers a quoi|a quoi tu sers|tes fonctions|comment tu marches|comment ca marche|comment t'utiliser)\\b|^aide\\W*$"

    private const val ALARM = "\\b(reveils?|reveille|reveiller|alarmes?|sonnerie)\\b|\\bme lever a\\b|\\bdebout a\\b"

    private const val TIMER = "\\b(minuteur|minuterie|chrono|chronometre|compte a rebours|timer)\\b|" +
        "\\b(previens|rappelle|previens)[- ]moi dans\\b|\\bsonne dans\\b"

    private const val BATTERY = "\\bbatterie|\\bautonomie\\b|\\btemps de (re)?charge\\b|\\bpleine charge\\b|\\ben charge\\b|" +
        "\\b(re)?charger (mon|le|ton) (tel|telephone|portable|mobile)\\b|\\b(dois|faut)[- ]?(il|je|t-il)?\\s*(le |la )?(re)?charger\\b"

    private const val TIME_Q = "\\bquelle heure\\b|\\bheure est[- ]il\\b|\\bl'heure (qu'il est|il est|est[- ]il)\\b|" +
        "\\bdonne[- ]moi l'heure\\b|^(l')?heure\\W*$"

    private const val DATE_Q = "\\bquel jour\\b|\\bquelle date\\b|\\bon est le combien\\b|\\bla date\\b|\\bnous sommes (quel|le)\\b|\\bon est quel\\b"

    private const val WEATHER = "\\bmeteo\\b|\\bquel temps\\b|\\btemps (qu'il )?(fait|fera|va faire)\\b|" +
        "\\bil (fait|fera|va faire) (beau|chaud|froid|bon|combien)\\b|\\bfait[- ]il (beau|chaud|froid|bon)\\b|" +
        "\\bpleu(t|voir|vra|vrait)\\b|\\bparapluie\\b|\\b(va|vas) neiger\\b|\\bneige[- ]t[- ]il\\b|" +
        "\\b(quelle|la) temperature (dehors|exterieure|il fait|fait[- ]il|a|en|au)\\b|\\bcombien de degres\\b|\\bprevisions?\\b"

    private const val OPEN_MAPS = "\\b(ouvre|lance|affiche|montre)[- ](moi )?(google )?(maps|map|la carte|le gps)\\b"

    private const val NAVIGATE = "\\b(itineraire|emmene[- ]moi|amene[- ]moi|guide[- ]moi|conduis[- ]moi|" +
        "comment (aller|je vais|se rendre|me rendre)|la route (pour|vers|jusqu'a))\\s+" +
        "(?:vers |a |au |aux |jusqu'a |pour |chez |a la |a l')?(.+)$"

    private const val PLACE_INTENT = "\\b(ou|trouve|trouver|cherche|chercher|proches?|autour|coin|pres|a cote|il y a|y a[- ]t[- ]il|" +
        "connais|conseille|recommande|montre|liste|donne|quels?|quelles?|le plus|la plus|ouverte?s?|pas loin)\\b"

    private const val GENERIC_PLACE = "\\b(ou (est|sont|se trouve|se trouvent|trouver|je peux trouver)|trouve[- ]moi|cherche[- ]moi|" +
        "y a[- ]t[- ]il|il y a)\\s+(un |une |des |le |la |les |l'|du |de la |de l')?([a-z0-9' -]{2,40}?)\\s+" +
        "(le plus proche|la plus proche|les plus proches|pres d'ici|pres de moi|autour de moi|autour|dans le coin|a cote|par ici|proche|pas loin)\\b"

    // ---------------------------------------------------------------- détresse

    fun isCrisis(n: String): Boolean = has(
        n,
        "\\bsuicid|\\bme (tuer|suicider|foutre en l'air|pendre)\\b|\\b(veux|vais|voudrais|envie d') en finir\\b|" +
            "\\ben finir avec (la vie|tout|moi)\\b|\\b(plus|pas) envie de vivre\\b|\\benvie de mourir\\b|" +
            "\\bje (veux|voudrais) mourir\\b|\\bme faire du mal\\b|\\bscarifi|\\bautomutil|\\bme mutiler\\b|" +
            "\\bje (veux|voudrais) disparaitre\\b|\\bmettre fin a mes jours\\b"
    )

    // ---------------------------------------------------------------- nombres, heures, durées

    private val NUM_WORDS = listOf(
        "quarante-cinq" to "45", "quarante cinq" to "45", "vingt-cinq" to "25", "vingt cinq" to "25",
        "dix-sept" to "17", "dix sept" to "17", "dix-huit" to "18", "dix huit" to "18", "dix-neuf" to "19", "dix neuf" to "19",
        "soixante" to "60", "cinquante" to "50", "quarante" to "40", "trente" to "30", "vingt" to "20",
        "seize" to "16", "quinze" to "15", "quatorze" to "14", "treize" to "13", "douze" to "12", "onze" to "11", "dix" to "10",
        "neuf" to "9", "huit" to "8", "sept" to "7", "six" to "6", "cinq" to "5", "quatre" to "4", "trois" to "3",
        "deux" to "2", "une" to "1", "un" to "1"
    )

    private fun numify(n: String): String {
        var s = " $n "
        s = s.replace(Regex("\\b(une |1 )?demi[- ]heure\\b"), "30 minutes")
        s = s.replace(Regex("\\btrois quarts? d'heure\\b"), "45 minutes")
        s = s.replace(Regex("\\b(un )?quart d'heure\\b"), "15 minutes")
        for ((w, d) in NUM_WORDS) s = s.replace(Regex("\\b$w\\b"), d)
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /** Heure demandée : « 7h30 », « 7 heures et demie », « midi », « 19:45 », « dans 20 minutes »… */
    fun clock(n0: String): Pair<Int, Int>? {
        val n = numify(n0)
        if (has(n, "\\bminuit\\b")) return 0 to (if (has(n, "minuit et demie?")) 30 else 0)
        if (has(n, "\\bmidi\\b")) return 12 to (if (has(n, "midi et demie?")) 30 else if (has(n, "midi et quart")) 15 else 0)

        Regex("\\bdans (\\d+) ?(minutes?|mins?|mn|heures?|h)\\b").find(n)?.let { m ->
            val v = m.groupValues[1].toInt()
            val c = Calendar.getInstance()
            if (m.groupValues[2].startsWith("h")) c.add(Calendar.HOUR_OF_DAY, v) else c.add(Calendar.MINUTE, v)
            return c.get(Calendar.HOUR_OF_DAY) to c.get(Calendar.MINUTE)
        }

        val m = Regex("\\b(\\d{1,2}) ?(?:heures?|h(?![a-z])|:) ?(\\d{1,2})?(?!\\d)").find(n) ?: return null
        var h = m.groupValues[1].toInt()
        var min = m.groupValues[2].toIntOrNull() ?: 0
        val after = " " + n.substring(m.range.last + 1).trimStart()
        when {
            after.startsWith(" et demi") -> min = 30
            after.startsWith(" et quart") -> min = 15
            after.startsWith(" moins le quart") || after.startsWith(" moins quart") -> {
                h -= 1
                min = 45
            }
            else -> Regex("^ moins (\\d{1,2})\\b").find(after)?.let {
                val less = it.groupValues[1].toInt()
                if (less in 1..59) {
                    h -= 1
                    min = 60 - less
                }
            }
        }
        if (h < 0) h += 24
        if (h in 1..11 && has(n, "\\b(du soir|de l'apres[- ]midi|de l'aprem|ce soir|cet apres[- ]midi|cette aprem)\\b")) h += 12
        if (h !in 0..23 || min !in 0..59) return null
        return h to min
    }

    /** Durée en secondes : « 10 minutes », « 1h30 », « une demi-heure », « 45 secondes »… */
    fun duration(n0: String): Int? {
        var s = " " + numify(n0) + " "
        var total = 0
        var found = false
        Regex("(\\d+) ?h ?(\\d{1,2})(?![\\d])(?! ?(min|mn|s\\b|sec))").find(s)?.let {
            total += it.groupValues[1].toInt() * 3600 + it.groupValues[2].toInt() * 60
            s = s.replace(it.value, " ")
            found = true
        }
        Regex("(\\d+) ?(?:heures?|h(?![a-z]))( et demie?)?").findAll(s).forEach {
            total += it.groupValues[1].toInt() * 3600 + if (it.groupValues[2].isNotEmpty()) 1800 else 0
            found = true
        }
        Regex("(\\d+) ?(?:minutes?|mins?|mn)\\b").findAll(s).forEach {
            total += it.groupValues[1].toInt() * 60
            found = true
        }
        Regex("(\\d+) ?(?:secondes?|secs?|s)\\b").findAll(s).forEach {
            total += it.groupValues[1].toInt()
            found = true
        }
        return if (found && total > 0) total else null
    }

    private val DAYS = listOf(
        "lundi" to Calendar.MONDAY, "mardi" to Calendar.TUESDAY, "mercredi" to Calendar.WEDNESDAY,
        "jeudi" to Calendar.THURSDAY, "vendredi" to Calendar.FRIDAY, "samedi" to Calendar.SATURDAY, "dimanche" to Calendar.SUNDAY
    )

    /** Jours de répétition d'un réveil (vide = une seule fois). */
    fun alarmDays(n: String): List<Int> {
        if (has(n, "\\b(tous les jours|chaque jour|chaque matin|tous les matins|chaque soir|tous les soirs)\\b")) return DAYS.map { it.second }
        if (has(n, "\\b(en semaine|jours de semaine|jours ouvres|du lundi au vendredi)\\b")) return DAYS.take(5).map { it.second }
        if (has(n, "\\b(tous les|chaque|les) week-?ends?\\b")) return listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        return DAYS.filter { (d, _) -> has(n, "\\b(tous les ${d}s|chaque $d|les ${d}s)\\b") }.map { it.second }
    }

    /** Libellé après « pour » : « réveille-moi à 7 h pour le sport » → « le sport ». */
    fun label(raw: String): String {
        val i = raw.lowercase(Locale.FRENCH).indexOf(" pour ")
        if (i < 0) return ""
        return raw.substring(i + 6).trim().trimEnd('.', '!', '?', ' ').take(40)
    }

    // ---------------------------------------------------------------- calculs

    private val CALC_FILLER = setOf(
        "combien", "font", "fait", "ca", "calcule", "calcul", "calcules", "calculer", "moi", "le", "la", "les", "de", "du", "des",
        "d'", "et", "egal", "egale", "egalent", "a", "est", "c'est", "resultat", "stp", "svp", "s'il", "te", "plait", "quoi",
        "donne", "fais", "peux", "tu", "me", "dis", "alors", "ok", "par", "vaut", "valent", "combien", "ça", "qu'est", "que",
        "ce", "tu", "sais", "vite", "rapide", "rapidement", "hmm", "euh", "hey", "he", "canelle", "cannelle"
    )

    /** Transforme « combien font 12 fois 4 » en « 12*4 ». Renvoie null si ce n'est pas un calcul. */
    fun calc(n0: String): String? {
        val explicit = has(n0, "\\bcalcul|\\bcombien (font|fait|ca fait|donne|vaut|valent)\\b|\\bca fait combien\\b|=|\\begal|\\bresultat\\b")
        val n = if (explicit) numify(n0) else n0
        if (n.none { it.isDigit() }) return null
        if (has(n, "\\b\\d{1,2}/\\d{1,2}(/\\d{2,4})?\\b") && !explicit) return null
        var s = " $n "
        s = s.replace(Regex("(\\d) ?[x×] ?(?=\\d)"), "$1*")
        s = s.replace(Regex("\\bmultiplie(s|e|es)? par\\b|\\bfois\\b"), "*")
        s = s.replace(Regex("\\bdivise(s|e|es)? par\\b"), "/")
        s = s.replace(Regex("(\\d) sur (?=\\d)"), "$1/")
        s = s.replace(Regex("\\bplus\\b"), "+").replace(Regex("\\bmoins\\b"), "-")
        s = s.replace(Regex("\\bau carre\\b"), "^2").replace(Regex("\\bau cube\\b"), "^3")
        s = s.replace(Regex("\\b(a la )?puissance\\b"), "^")
        s = s.replace(Regex("\\bracine (carree )?(de |d')?"), "√")
        s = s.replace(Regex("\\bvirgule\\b"), ",")
        s = s.replace(Regex("\\bfactorielle (de )?(\\d+)"), "$2!")
        s = s.replace(
            Regex("(\\d+(?:[.,]\\d+)?) ?(?:%|pour ?cents?|pourcents?) ?(?:de |du |des |d'|sur )(\\d+(?:[.,]\\d+)?)"),
            "($1/100*$2)"
        )
        s = s.replace(Regex("√ ?(\\d+(?:[.,]\\d+)?)"), "√($1)")
        val words = Regex("[a-z']+").findAll(s).map { it.value }.filter { it !in CALC_FILLER }.toList()
        if (words.isNotEmpty() && !explicit) return null
        var e = s.replace(Regex("[^0-9.,+\\-*/^()%!√ ]"), " ").replace(Regex("\\s+"), " ").trim()
        e = e.trim('+', '*', '/', '^', ' ')
        if (!has(e, "\\d ?[+\\-*/^] ?[\\d(√]|√|\\d ?!|\\) ?[+\\-*/^]|\\d ?%")) return null
        return try {
            Calculator.eval(e)
            e
        } catch (ex: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- lieux

    class Cat(val re: Regex, val query: String, val en: String?, val label: String)

    private val CATS = listOf(
        Cat(Regex("\\b(mc ?do|mac ?do|mcdonald'?s?|mac ?donald'?s?)\\b"), "McDonald's", null, "McDonald's"),
        Cat(Regex("\\bburger king\\b"), "Burger King", null, "Burger King"),
        Cat(Regex("\\bkfc\\b"), "KFC", null, "KFC"),
        Cat(Regex("\\bsubway\\b"), "Subway", null, "Subway"),
        Cat(Regex("\\bstarbucks\\b"), "Starbucks", null, "Starbucks"),
        Cat(Regex("\\b(le|un|au) quick\\b"), "Quick", null, "Quick"),
        Cat(Regex("\\bcarrefour\\b"), "Carrefour", null, "Carrefour"),
        Cat(Regex("\\b(e\\.? ?)?leclerc\\b"), "E.Leclerc", null, "Leclerc"),
        Cat(Regex("\\blidl\\b"), "Lidl", null, "Lidl"),
        Cat(Regex("\\baldi\\b"), "Aldi", null, "Aldi"),
        Cat(Regex("\\bintermarche\\b"), "Intermarché", null, "Intermarché"),
        Cat(Regex("\\bauchan\\b"), "Auchan", null, "Auchan"),
        Cat(Regex("\\b(super|hyper|systeme) u\\b"), "Super U", null, "Super U"),
        Cat(Regex("\\bmonoprix\\b"), "Monoprix", null, "Monoprix"),
        Cat(Regex("\\bfranprix\\b"), "Franprix", null, "Franprix"),
        Cat(Regex("\\bdecathlon\\b"), "Decathlon", null, "Decathlon"),
        Cat(Regex("\\bikea\\b"), "IKEA", null, "IKEA"),
        Cat(Regex("\\bpizz(a|as|eria|erias)\\b"), "pizzeria", "pizza", "pizzerias"),
        Cat(Regex("\\b(kebabs?|tacos)\\b"), "kebab", "kebab", "kebabs"),
        Cat(Regex("\\bsushis?\\b|\\b(restaurant )?japonais\\b"), "restaurant japonais", "sushi", "restaurants japonais"),
        Cat(Regex("\\b(restaurants?|restos?|manger|bouffer|dejeuner|diner)\\b"), "restaurant", "restaurant", "restaurants"),
        Cat(Regex("\\b(boulangeries?|du pain|croissants?|viennoiseries?)\\b"), "boulangerie", "bakery", "boulangeries"),
        Cat(Regex("\\b(pharmacies?|medicaments?)\\b"), "pharmacie", "pharmacy", "pharmacies"),
        Cat(Regex("\\b(supermarches?|superettes?|epiceries?|faire (les|des|mes) courses)\\b"), "supermarché", "supermarket", "supermarchés"),
        Cat(Regex("\\b(stations?[- ]service|station essence|essence|carburant|faire le plein|gasoil|diesel)\\b"), "station-service", "fuel", "stations-service"),
        Cat(Regex("\\b(cafes?|coffee shop)\\b"), "café", "cafe", "cafés"),
        Cat(Regex("\\bbars?\\b"), "bar", "bar", "bars"),
        Cat(Regex("\\b(hopital|hopitaux|urgences)\\b"), "hôpital", "hospital", "hôpitaux"),
        Cat(Regex("\\b(medecins?|docteurs?|generalistes?)\\b"), "médecin", "doctors", "médecins"),
        Cat(Regex("\\b(distributeurs?( de billets)?|dab|retirer de l'argent|retirer des sous)\\b"), "distributeur de billets", "atm", "distributeurs"),
        Cat(Regex("\\bbanques?\\b"), "banque", "bank", "banques"),
        Cat(Regex("\\bparcs?\\b|\\bjardins? publics?\\b"), "parc", "park", "parcs"),
        Cat(Regex("\\bcinemas?\\b|\\bcine\\b"), "cinéma", "cinema", "cinémas"),
        Cat(Regex("\\bgares?\\b"), "gare", "train station", "gares"),
        Cat(Regex("\\bhotels?\\b"), "hôtel", "hotel", "hôtels"),
        Cat(Regex("\\b(la poste|bureau de poste)\\b"), "bureau de poste", "post office", "bureaux de poste"),
        Cat(Regex("\\b(toilettes|wc)\\b"), "toilettes", "toilets", "toilettes"),
        Cat(Regex("\\bparkings?\\b|\\bme garer\\b|\\bstationner\\b"), "parking", "parking", "parkings"),
        Cat(Regex("\\b(commissariat|police|gendarmerie)\\b"), "police", "police", "postes de police"),
        Cat(Regex("\\b(veterinaires?|veto)\\b"), "vétérinaire", "veterinary", "vétérinaires"),
        Cat(Regex("\\b(coiffeur|coiffeuse)s?\\b"), "coiffeur", "hairdresser", "coiffeurs"),
        Cat(Regex("\\b(salles? de sport|fitness|basic fit)\\b"), "salle de sport", "fitness centre", "salles de sport"),
        Cat(Regex("\\bpiscines?\\b"), "piscine", "swimming pool", "piscines"),
        Cat(Regex("\\b(bibliotheques?|mediatheques?)\\b"), "bibliothèque", "library", "bibliothèques"),
        Cat(Regex("\\b(bureau de )?tabac\\b"), "tabac", "tobacco", "bureaux de tabac")
    )

    fun category(n: String): Cat? = CATS.firstOrNull { it.re.containsMatchIn(n) }

    // ---------------------------------------------------------------- villes

    private val TRAIL = Regex(
        "\\b(demain|apres[- ]demain|aujourd'hui|ce soir|ce matin|cet apres[- ]midi|cette nuit|ce week-?end|cette semaine|" +
            "la semaine prochaine|les prochains jours|maintenant|en ce moment|actuellement|la-bas|stp|svp|s'il te plait|" +
            "s'il vous plait|exactement|precisement|dehors|la)\\s*$"
    )
    private val CITY_STOP = setOf(
        "moi", "la maison", "maison", "ici", "ce moment", "vrai", "l'heure", "l'instant", "present", "l'exterieur",
        "chez moi", "cote", "toi", "demain", "sortir", "l'ecole", "l'aise", "fond", "peu pres", "la plage", "priori",
        "part", "savoir", "faire", "venir", "partir", "aller", "quelle heure", "midi", "minuit", "tout", "rien", "pour",
        "de", "du", "des", "a", "en", "stp", "svp", "aujourd'hui", "semaine", "week-end", "weekend", "la semaine",
        "la journee", "du jour", "ce soir", "ce week-end", "cette semaine", "la nuit", "cette nuit"
    )

    /** Ville citée en fin de phrase : « météo à Rennes demain » → « rennes ». */
    fun city(n: String): String? {
        var s = n.replace(Regex("[?!.,;:]"), " ").replace(Regex("\\s+"), " ").trim()
        repeat(3) { s = TRAIL.replace(s, "").trim() }
        val m = Regex("(?:\\b(?:a|en|au|aux|sur|pour|vers|chez)\\s+|\\b(?:temps|heure) (?:de |d'|du |des )|\\bmeteo (?:de |d'|du |des |a |en |sur )?)([a-z][a-z' -]{1,40})$").find(s)
            ?: return null
        val c = m.groupValues[1].trim()
        if (c in CITY_STOP || c.split(' ').size > 4) return null
        if (has(c, "^(quel|quelle|combien|comment|pourquoi|faire|l'heure)\\b")) return null
        return c
    }

    // ---------------------------------------------------------------- ce que l'utilisateur dit de lui

    private val NAME_STOP = setOf(
        "pas", "comment", "quoi", "qui", "un", "une", "le", "la", "rien", "bon", "ok", "pareil", "fini", "tout", "fait", "vrai",
        "cool", "nul", "ca", "ça", "moi", "toi", "juste", "trop", "super", "bien", "mal", "fatigue", "fatigué", "triste", "normal"
    )

    /** Prénom donné par l'utilisateur, ou null. */
    fun name(raw: String): String? {
        val m = Regex(
            "(?:je m['’]appelle|mon (?:pr[ée]nom|nom) (?:c['’]est|est)|appelle[- ]moi|moi c['’]est)\\s+([\\p{L}][\\p{L}'’-]{1,24})(?=\\s*[.!,?]?\\s*$|\\s*[.!,]|\\s+et\\b)",
            RegexOption.IGNORE_CASE
        ).find(raw.trim()) ?: return null
        val n = m.groupValues[1]
        if (n.lowercase(Locale.FRENCH) in NAME_STOP) return null
        return n.lowercase(Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    }

    /** « Retiens que… », « souviens-toi que… » : un fait à garder en mémoire. */
    fun fact(raw: String): String? {
        val m = Regex(
            "(?:retiens|souviens[- ]toi|rappelle[- ]toi|n['’]oublie pas|m[ée]morise|note)\\s+(?:bien\\s+)?(?:que|qu['’])\\s*(.{3,140})",
            RegexOption.IGNORE_CASE
        ).find(raw) ?: return null
        return m.groupValues[1].trim().trimEnd('.', '!', ' ').replaceFirstChar { it.uppercase() }
    }
}
