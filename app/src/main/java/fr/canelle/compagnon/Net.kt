package fr.canelle.compagnon

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Petites requêtes web (météo, lieux). Aucune clé n'est nécessaire pour ces services. */
object Net {
    private const val USER_AGENT = "CanelleCompagnon/5.0 (application Android personnelle)"

    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", USER_AGENT)
        c.setRequestProperty("Accept-Language", "fr")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
