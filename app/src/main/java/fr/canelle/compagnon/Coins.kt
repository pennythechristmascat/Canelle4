package fr.canelle.compagnon

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Les pièces de Canelle : on en gagne en jouant, en revenant chaque jour et en marchant,
 * et on les dépense dans la boutique de skins. Tout reste sur le téléphone.
 */
object Coins {
    const val START = 100
    /** Maximum gagné par jour en jouant et en interagissant. */
    const val PLAY_CAP = 200
    /** Maximum gagné par jour grâce aux pas (10 pièces tous les 1 000 pas). */
    const val STEPS_CAP = 100

    /** Prix des skins (le look classique est gratuit). */
    val PRICES = linkedMapOf(
        "classique" to 0,
        "neige" to 50, "minuit" to 50, "menthe" to 50, "chocolat" to 50, "ocean" to 50, "foret" to 50,
        "sakura" to 120, "panda" to 120, "bonbon" to 120, "chrome" to 120, "aurore" to 120, "magma" to 120,
        "dore" to 200, "cyber" to 200, "pirate" to 200, "galaxie" to 200,
        "fantome" to 300, "citrouille" to 300, "noel" to 300
    )

    private fun today() = LocalDate.now().toString()

    private fun rollDay() {
        if (Store.coinsDay != today()) {
            Store.coinsDay = today()
            Store.coinsToday = 0
            Store.stepsCoinsToday = 0
        }
    }

    /** Première ouverture après la mise à jour : un petit pécule, et le skin déjà porté reste acquis. */
    fun init() {
        if (Store.coinsInit) return
        Store.coins = START
        Store.coinsInit = true
        if (Store.skin.isNotBlank()) own(Store.skin)
    }

    private fun ownedList(): MutableList<String> = runCatching {
        val a = JSONArray(Store.ownedSkins)
        MutableList(a.length()) { a.getString(it) }
    }.getOrDefault(mutableListOf())

    private fun own(id: String) {
        val l = ownedList()
        if (id !in l) {
            l.add(id)
            Store.ownedSkins = JSONArray(l).toString()
        }
    }

    /** Le gérant et les développeurs ont tous les skins. */
    fun owns(id: String): Boolean = id == "classique" || Store.rank == "dev" || Store.rank == "owner" || id in ownedList()

    /** Bonus du jour (20 pièces, +5 par jour d'affilée, jusqu'à 50). Renvoie 0 s'il a déjà été donné aujourd'hui. */
    fun daily(): Int {
        val t = today()
        if (Store.lastDaily == t) return 0
        Store.streak = if (Store.lastDaily == LocalDate.now().minusDays(1).toString()) Store.streak + 1 else 1
        Store.lastDaily = t
        val amount = 20 + minOf(Store.streak - 1, 6) * 5
        Store.coins = Store.coins + amount
        return amount
    }

    /** Pièces gagnées en jouant : "win" (partie gagnée), "game" (partie jouée), "interaction". */
    fun earn(reason: String): Int {
        rollDay()
        val amount = when (reason) {
            "win" -> 15
            "game" -> 5
            "interaction" -> 1
            else -> 0
        }
        val gain = minOf(amount, PLAY_CAP - Store.coinsToday).coerceAtLeast(0)
        if (gain > 0) {
            Store.coins = Store.coins + gain
            Store.coinsToday = Store.coinsToday + gain
        }
        return gain
    }

    /** Pièces des pas d'aujourd'hui pas encore récupérées. */
    fun stepsDue(): Int {
        rollDay()
        val earned = minOf((Steps.lastToday / 1000L).toInt() * 10, STEPS_CAP)
        return (earned - Store.stepsCoinsToday).coerceAtLeast(0)
    }

    fun claimSteps(): Int {
        val due = stepsDue()
        if (due > 0) {
            Store.coins = Store.coins + due
            Store.stepsCoinsToday = Store.stepsCoinsToday + due
        }
        return due
    }

    /** "ok", "pauvre" (pas assez de pièces) ou "inconnu". */
    fun buy(id: String): String {
        val price = PRICES[id] ?: return "inconnu"
        if (owns(id)) return "ok"
        if (Store.coins < price) return "pauvre"
        Store.coins = Store.coins - price
        own(id)
        return "ok"
    }

    fun state(): JSONObject {
        rollDay()
        val prices = JSONObject()
        PRICES.forEach { (k, v) -> prices.put(k, v) }
        val owned = JSONArray()
        PRICES.keys.filter { owns(it) }.forEach { owned.put(it) }
        return JSONObject()
            .put("coins", Store.coins)
            .put("prices", prices)
            .put("owned", owned)
            .put("streak", Store.streak)
            .put("playToday", Store.coinsToday).put("playCap", PLAY_CAP)
            .put("stepsToday", Store.stepsCoinsToday).put("stepsCap", STEPS_CAP)
            .put("stepsDue", stepsDue())
    }
}
