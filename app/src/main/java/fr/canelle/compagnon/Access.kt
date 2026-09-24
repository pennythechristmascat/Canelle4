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

    /** Moment où le code gérant a été entré : le nom doit être donné dans les 5 minutes. */
    @Volatile private var ownerPendingAt = 0L

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Renvoie "dev", "owner_name" (il faut maintenant le nom du gérant) ou "invalid". */
    fun check(code: String): String {
        val c = code.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), "")
        return when (sha(c)) {
            DEV -> {
                Store.rank = "dev"
                "dev"
            }
            OWNER -> {
                ownerPendingAt = System.currentTimeMillis()
                "owner_name"
            }
            else -> "invalid"
        }
    }

    /** Vérifie le nom du gérant après le code gérant. */
    fun confirmOwner(answer: String): Boolean {
        val pending = System.currentTimeMillis() - ownerPendingAt < 5 * 60_000L
        ownerPendingAt = 0L
        if (!pending) return false
        val words = answer.split(Regex("[^\\p{L}'-]+")).filter { it.isNotBlank() }
        val match = words.firstOrNull { sha(Intents.norm(it).replace(Regex("[^a-z]"), "")) == OWNER_NAME } ?: return false
        val name = match.lowercase(Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }
        Store.rank = "owner"
        Store.ownerName = name
        Store.userName = name
        return true
    }

    fun clear() {
        Store.rank = ""
        Store.ownerName = ""
    }
}
