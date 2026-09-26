package fr.canelle.compagnon

import android.content.Context
import android.location.Location
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Les outils du téléphone : batterie, météo, heure, lieux, cartes, réveils, calculs.
 * foreground = l'application est ouverte (sinon : réponse depuis une notification).
 */
class Tools(private val ctx: Context, private val foreground: Boolean, private val host: ToolHost?) {

    /** Derniers lieux trouvés, affichés sous forme de liste cliquable dans l'application. */
    var places: JSONArray? = null
        private set
    var placesQuery: String? = null
        private set

    /** Carte animée à afficher à côté de Canelle (météo, batterie, heure…). */
    var card: JSONObject? = null

    // ---------------------------------------------------------------- calcul

    fun battery(): JSONObject = Device.battery(ctx)

    fun calculate(expr: String): JSONObject = try {
        JSONObject().put("expression", expr).put("resultat", Calculator.format(Calculator.eval(expr)))
    } catch (e: Exception) {
        JSONObject().put("erreur", "expression invalide : ${e.message}")
    }

    // ---------------------------------------------------------------- lieu et heure

    private class Place(val lat: Double, val lon: Double, val label: String, val timezone: String?)

    private suspend fun resolve(city: String): Place? {
        if (city.isBlank()) {
            val pos = Device.location(ctx, foreground, host) ?: return null
            return Place(pos.first, pos.second, placeName(pos), null)
        }
        return withContext(Dispatchers.IO) {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(city)}&count=1&language=fr&format=json"
            val r = JSONObject(Net.get(url)).optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
            val label = r.optString("name").ifBlank { city }
            Place(r.getDouble("latitude"), r.getDouble("longitude"), label, r.optString("timezone").ifBlank { null })
        }
    }

    /** Nom de la ville où se trouve l'utilisateur (mis à jour dès qu'il bouge de plus d'un kilomètre). */
    private suspend fun placeName(pos: Pair<Double, Double>): String =
        LocationKeeper.updateCity(pos.first, pos.second) ?: Store.placeName.ifBlank { "chez toi" }

    suspend fun time(city: String): JSONObject {
        val zone: ZoneId
        val label: String
        if (city.isBlank()) {
            zone = ZoneId.systemDefault()
            label = "ici"
        } else {
            val p = resolve(city) ?: return JSONObject().put("erreur", "ville")
            zone = p.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: return JSONObject().put("erreur", "ville")
            label = p.label
        }
        val now = ZonedDateTime.now(zone)
        return JSONObject()
            .put("lieu", label)
            .put("heure", now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)))
            .put("date", now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Lang.locale())))
            .put("fuseau", zone.id)
    }

    // ---------------------------------------------------------------- météo (Open-Meteo, sans clé)
    //
    // « Maintenant » : modèle AROME de Météo-France (maille de 1,5 km, pas de 15 minutes, recalculé chaque heure)
    // quand on est en France ou autour ; ailleurs, le meilleur modèle local choisi par Open-Meteo.
    // Prévisions des jours suivants : meilleur modèle Open-Meteo (avec la probabilité de pluie).

    private fun inAromeZone(lat: Double, lon: Double) = lat in 37.5..55.4 && lon in -12.0..16.0

    private val CURRENT_VARS = "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,rain,showers,snowfall," +
        "weather_code,cloud_cover,wind_speed_10m,wind_gusts_10m,is_day"

    /** Corrige le code du ciel avec ce qui tombe vraiment et la couverture nuageuse du moment. */
    private fun realCode(cur: JSONObject): Int {
        var code = cur.optInt("weather_code", -1)
        val precip = cur.optDouble("precipitation", 0.0).let { if (it.isNaN()) 0.0 else it }
        val snow = cur.optDouble("snowfall", 0.0).let { if (it.isNaN()) 0.0 else it }
        val cloud = cur.optDouble("cloud_cover", Double.NaN)
        if (code in 0..3 && !cloud.isNaN()) code = when {
            cloud < 15 -> 0
            cloud < 40 -> 1
            cloud < 75 -> 2
            else -> 3
        }
        if (code < 50 && snow >= 0.1) code = 71
        else if (code < 50 && precip >= 0.3) code = if (precip >= 2.0) 63 else 61
        else if (code < 50 && precip >= 0.1) code = 51
        return code
    }

    suspend fun weather(city: String, daysAsked: Int): JSONObject {
        val days = daysAsked.coerceIn(1, 7)
        val place = resolve(city) ?: return JSONObject().put(
            "erreur", if (city.isBlank()) "position" else "ville")
        return withContext(Dispatchers.IO) {
            val base = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}&timezone=auto"
            // 1) prévisions jour par jour + « maintenant » de secours
            val j = JSONObject(Net.get(base + "&current=$CURRENT_VARS" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum" +
                "&forecast_days=$days"))
            var cur = j.getJSONObject("current")
            var source = "Open-Meteo"
            // 2) « maintenant » précis : AROME Météo-France
            if (inAromeZone(place.lat, place.lon)) {
                runCatching {
                    val a = JSONObject(Net.get(base + "&current=$CURRENT_VARS&models=meteofrance_seamless&forecast_days=1"))
                    val c = a.getJSONObject("current")
                    if (!c.optDouble("temperature_2m").isNaN()) {
                        cur = c
                        source = "Météo-France (AROME)"
                    }
                }
            }
            val code = realCode(cur)
            val now = JSONObject()
                .num("temperature_c", cur.optDouble("temperature_2m"))
                .num("ressenti_c", cur.optDouble("apparent_temperature"))
                .num("humidite_pct", cur.optDouble("relative_humidity_2m"))
                .num("vent_kmh", cur.optDouble("wind_speed_10m"))
                .num("rafales_kmh", cur.optDouble("wind_gusts_10m"))
                .num("nuages_pct", cur.optDouble("cloud_cover"))
                .num("precipitations_mm", cur.optDouble("precipitation"))
                .put("ciel", wmo(code))
                .put("code", code)
                .put("jour", cur.optInt("is_day", 1) == 1)
                .put("heure_donnees", cur.optString("time").substringAfter('T'))
                .put("source", source)
            val d = j.getJSONObject("daily")
            val dates = d.getJSONArray("time")
            val list = JSONArray()
            for (i in 0 until dates.length()) {
                list.put(JSONObject()
                    .put("date", dates.getString(i))
                    .put("ciel", wmo(d.getJSONArray("weather_code").optInt(i, -1)))
                    .put("code", d.getJSONArray("weather_code").optInt(i, -1))
                    .num("min_c", d.getJSONArray("temperature_2m_min").optDouble(i))
                    .num("max_c", d.getJSONArray("temperature_2m_max").optDouble(i))
                    .num("pluie_proba_pct", d.getJSONArray("precipitation_probability_max").optDouble(i))
                    .num("pluie_mm", d.getJSONArray("precipitation_sum").optDouble(i)))
            }
            JSONObject().put("lieu", place.label).put("maintenant", now).put("jours", list)
        }
    }

    private fun wmo(code: Int): String = when (code) {
        0 -> "ciel dégagé"
        1 -> "plutôt dégagé"
        2 -> "partiellement nuageux"
        3 -> "couvert"
        45, 48 -> "brouillard"
        51, 53, 55 -> "bruine"
        56, 57 -> "bruine verglaçante"
        61 -> "pluie faible"
        63 -> "pluie"
        65 -> "forte pluie"
        66, 67 -> "pluie verglaçante"
        71 -> "neige faible"
        73 -> "neige"
        75 -> "forte neige"
        77 -> "grésil"
        80 -> "averses faibles"
        81 -> "averses"
        82 -> "violentes averses"
        85, 86 -> "averses de neige"
        95 -> "orage"
        96, 99 -> "orage avec grêle"
        else -> "temps variable"
    }

    // ---------------------------------------------------------------- lieux (OpenStreetMap, sans clé)

    /**
     * Cherche des lieux autour de l'utilisateur. [english] : même recherche en anglais,
     * essayée si la recherche en français ne donne rien (OpenStreetMap connaît mieux certains mots).
     */
    suspend fun findPlaces(query: String, english: String?, radiusKm: Double = 3.0): JSONObject {
        if (query.isBlank()) return JSONObject().put("erreur", "rien à chercher")
        val pos = Device.location(ctx, foreground, host)
            ?: return JSONObject().put("erreur", "position")
        val found = withContext(Dispatchers.IO) {
            val attempts = listOfNotNull(query to radiusKm, english?.let { it to radiusKm }, query to (radiusKm * 4).coerceIn(5.0, 30.0))
            var result: List<JSONObject> = emptyList()
            for ((i, a) in attempts.withIndex()) {
                if (i > 0) Thread.sleep(1100) // le service demande au plus une requête par seconde
                result = runCatching { nominatim(a.first, pos, a.second) }.getOrDefault(emptyList())
                if (result.isNotEmpty()) break
            }
            result
        }
        placesQuery = query
        places = JSONArray(found)
        return JSONObject().put("nombre", found.size).put("resultats", JSONArray(found))
    }

    private fun nominatim(query: String, pos: Pair<Double, Double>, rKm: Double): List<JSONObject> {
        val (lat, lon) = pos
        val dLat = rKm / 111.0
        val dLon = rKm / (111.0 * cos(Math.toRadians(lat))).coerceAtLeast(0.01)
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=20&bounded=1&addressdetails=1" +
            "&accept-language=fr&q=${Uri.encode(query)}&viewbox=${lon - dLon},${lat + dLat},${lon + dLon},${lat - dLat}"
        val arr = JSONArray(Net.get(url))
        val out = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val plat = o.optString("lat").toDoubleOrNull() ?: continue
            val plon = o.optString("lon").toDoubleOrNull() ?: continue
            val dist = FloatArray(1)
            Location.distanceBetween(lat, lon, plat, plon, dist)
            val name = o.optString("name").ifBlank { o.optString("display_name").substringBefore(",") }
            out.add(
                JSONObject()
                    .put("name", name)
                    .put("address", shortAddress(o))
                    .put("distance", dist[0].roundToInt())
                    .put("lat", plat)
                    .put("lon", plon)
            )
        }
        return out.sortedBy { it.getInt("distance") }
            .distinctBy { it.getString("name") + "|" + it.getString("address") }
            .take(8)
    }

    private fun shortAddress(o: JSONObject): String {
        val a = o.optJSONObject("address")
            ?: return o.optString("display_name").split(",").drop(1).take(2).joinToString(",").trim()
        val street = listOf(a.optString("house_number"), a.optString("road")).filter { it.isNotBlank() }.joinToString(" ")
        val town = listOf("city", "town", "village", "suburb").map { a.optString(it) }.firstOrNull { it.isNotBlank() } ?: ""
        return listOf(street, town).filter { it.isNotBlank() }.joinToString(", ")
    }

    // ---------------------------------------------------------------- actions (application ouverte seulement)

    suspend fun mapsSearch(query: String): Boolean = withContext(Dispatchers.Main) {
        Device.mapsSearch(ctx, query, Store.lastPosition())
    }

    suspend fun openMaps(): Boolean = withContext(Dispatchers.Main) {
        Device.openMaps(ctx, Store.lastPosition())
    }

    suspend fun navigateTo(destination: String): Boolean = withContext(Dispatchers.Main) {
        Device.mapsNavigateQuery(ctx, destination)
    }

    suspend fun navigateTo(lat: Double, lon: Double): Boolean = withContext(Dispatchers.Main) {
        Device.mapsNavigate(ctx, lat, lon)
    }

    suspend fun setAlarm(hour: Int, minute: Int, label: String, days: List<Int>): Boolean = withContext(Dispatchers.Main) {
        Device.setAlarm(ctx, hour, minute, label, days.ifEmpty { null })
    }

    suspend fun setTimer(seconds: Int, label: String): Boolean = withContext(Dispatchers.Main) {
        Device.setTimer(ctx, seconds, label)
    }

    suspend fun showAlarms(): Boolean = withContext(Dispatchers.Main) {
        Device.showAlarms(ctx)
    }

    // ---------------------------------------------------------------- petites aides

    private fun JSONObject.num(k: String, v: Double): JSONObject = put(k, if (v.isNaN()) JSONObject.NULL else v)
}
