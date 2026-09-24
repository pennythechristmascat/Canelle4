package fr.canelle.compagnon

import java.security.MessageDigest
import java.util.Locale

/**
 * Codes d'accès (développeur, gérant). Les codes ne sont pas écrits en clair dans l'application :
 * on compare seulement leur empreinte SHA-256, pour qu'on ne puisse pas les lire dans le code.
 */
object Access {
    private const val DEV = "12677ab08a6a7f89eb6ad7dcf453e9972f535c78ba4aedb7497fc9bed8b58ef9"
    private const val OWNER = "c530b3144ff2930286c045bc74f9bf28b7092df65edf5bc045ac0087a39cc5b9"
    private const val OWNER_NAME = "55de0843e14ab79decddbb86ee039f7efb9c882bf7abb60c5719601e24d0678e"
    private const val DAUGHTER = "1de2e4fa195c0b375a915ec6a3d1ef5095b9c9138fcb814475f41a900a235026"
    private const val DAUGHTER_NAME = "540b99c2726b737f781105e152629194e58138fa3630bf2c7437db074e659ead"
    private const val VIP = "592df82290d8f46a0370d6730ac6d09afad55db56b743bf572442a9163aab862"

    /** Rang en attente de confirmation par le prénom : "owner" (gérant) ou "daughter" (fille du patron). */
    @Volatile private var pendingRole = "owner"

    /** Moment où le code gérant a été entré : le nom doit être donné dans les 5 minutes. */
    @Volatile private var ownerPendingAt = 0L

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    const val MAX_FAILS = 3
    private const val LOCK_MS = 24 * 3_600_000L

    /**
     * Fin de la punition (ms), ou 0 si Canelle n'est pas fâché.
     * Si l'heure du téléphone a été reculée avant le début de la punition, elle reste active.
     */
    fun lockedUntil(): Long {
        val until = Store.lockUntil
        if (until == 0L) return 0L
        val now = System.currentTimeMillis()
        if (now < Store.lockStart) return until
        if (now >= until) {
            Store.lockUntil = 0L
            Store.lockStart = 0L
            Store.ownerFails = 0
            return 0L
        }
        return until
    }

    fun isLocked(): Boolean = lockedUntil() > 0L

    /**
     * Renvoie "dev", "vip", "owner_name" (il faut maintenant le nom du gérant),
     * "daughter_name" (il faut le prénom de la fille du patron), "locked" ou "invalid".
     */
    fun check(code: String): String {
        if (isLocked()) return "locked"
        val c = code.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), "")
        return when (sha(c)) {
            DEV -> {
                Store.rank = "dev"
                "dev"
            }
            OWNER -> {
                ownerPendingAt = System.currentTimeMillis()
                pendingRole = "owner"
                "owner_name"
            }
            DAUGHTER -> {
                ownerPendingAt = System.currentTimeMillis()
                pendingRole = "daughter"
                "daughter_name"
            }
            VIP -> {
                Store.rank = "vip"
                Store.ownerName = ""
                "vip"
            }
            else -> "invalid"
        }
    }

    /** Vérifie le nom du gérant après le code gérant. */
    fun confirmOwner(answer: String): Boolean {
        val pending = System.currentTimeMillis() - ownerPendingAt < 5 * 60_000L
        ownerPendingAt = 0L
        if (!pending || isLocked()) return false
        val words = answer.split(Regex("[^\\p{L}'-]+")).filter { it.isNotBlank() }
        val role = pendingRole
        val expected = if (role == "daughter") DAUGHTER_NAME else OWNER_NAME
        val match = words.firstOrNull { sha(Intents.norm(it).replace(Regex("[^a-z]"), "")) == expected }
        if (match == null) {
            // Mauvais nom : au 3e mensonge (même après avoir relancé l'appli), Canelle boude 24 h.
            Store.ownerFails = Store.ownerFails + 1
            if (Store.ownerFails >= MAX_FAILS) {
                val now = System.currentTimeMillis()
                Store.lockStart = now
                Store.lockUntil = now + LOCK_MS
            }
            return false
        }
        Store.ownerFails = 0
        val name = match.lowercase(Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }
        Store.rank = role
        Store.ownerName = name
        Store.userName = name
        return true
    }

    fun clear() {
        Store.rank = ""
        Store.ownerName = ""
    }
}
