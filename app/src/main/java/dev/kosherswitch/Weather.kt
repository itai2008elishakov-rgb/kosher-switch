package dev.kosherswitch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Live weather from Open-Meteo (free, no account). The only internet this app uses: the forecast,
 * the city name for the phone's location, and the city search. The last forecast of each place is
 * kept, so the app still shows something without a connection.
 */
object Weather {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODE = "https://geocoding-api.open-meteo.com/v1/search"
    private const val NAMES = "https://nominatim.openstreetmap.org/reverse"
    private const val ROUGH = "https://api-bdc.io/data/reverse-geocode-client"
    private const val KEY_CITIES = "weather_cities"
    private const val KEY_SELECTED = "weather_selected"

    data class City(val name: String, val lat: Double, val lon: Double, val area: String = "") {
        val key get() = "%.2f,%.2f".format(java.util.Locale.US, lat, lon)
    }

    data class Now(
        val temp: Double, val feels: Double, val humidity: Int, val code: Int, val wind: Double,
        val windDir: Int, val day: Boolean, val pressure: Double, val uv: Double,
    )

    data class Hour(val time: LocalDateTime, val temp: Double, val code: Int, val rain: Int, val day: Boolean)

    data class Day(
        val date: LocalDate, val code: Int, val max: Double, val min: Double, val rain: Int,
        val rainMm: Double, val sunrise: LocalTime?, val sunset: LocalTime?, val uv: Double,
    )

    data class Forecast(val now: Now, val hours: List<Hour>, val days: List<Day>, val fetched: Long, val utcOffset: Int) {
        /** The time right now in the forecast's place (a saved city can be in another time zone). */
        fun localNow(): LocalDateTime = LocalDateTime.now(java.time.ZoneOffset.ofTotalSeconds(utcOffset))
    }

    /** The condition's emoji and its name. */
    fun describe(code: Int, day: Boolean = true): Pair<String, Int> = when (code) {
        0 -> (if (day) "☀️" else "🌙") to R.string.w_clear
        1 -> (if (day) "🌤️" else "🌙") to R.string.w_mostly_clear
        2 -> (if (day) "⛅" else "☁️") to R.string.w_partly
        3 -> "☁️" to R.string.w_cloudy
        45, 48 -> "🌫️" to R.string.w_fog
        in 51..57 -> "🌦️" to R.string.w_drizzle
        61, 63, 66 -> "🌧️" to R.string.w_rain
        65, 67 -> "🌧️" to R.string.w_heavy_rain
        in 71..77, 85, 86 -> "🌨️" to R.string.w_snow
        in 80..82 -> "🌦️" to R.string.w_showers
        in 95..99 -> "⛈️" to R.string.w_thunder
        else -> "☁️" to R.string.w_cloudy
    }

    // ---- Network (call off the main thread) ----

    fun fetch(ctx: Context, city: City): Forecast {
        val url = "$FORECAST?latitude=${city.lat}&longitude=${city.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m," +
            "wind_direction_10m,is_day,pressure_msl,uv_index" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
            "precipitation_probability_max,precipitation_sum,uv_index_max" +
            "&timezone=auto&forecast_days=10"
        val json = get(url)
        val forecast = parse(json, System.currentTimeMillis())
        prefs(ctx).edit().putString("weather_cache_" + city.key, json)
            .putLong("weather_time_" + city.key, forecast.fetched).apply()
        return forecast
    }

    fun cached(ctx: Context, city: City): Forecast? {
        val json = prefs(ctx).getString("weather_cache_" + city.key, null) ?: return null
        return runCatching { parse(json, prefs(ctx).getLong("weather_time_" + city.key, 0)) }.getOrNull()
    }

    /** Town or city name for a location, in the app's language (OpenStreetMap). */
    fun placeName(lat: Double, lon: Double, lang: String): String? = runCatching {
        val o = JSONObject(get("$NAMES?lat=$lat&lon=$lon&format=jsonv2&zoom=10&accept-language=$lang"))
        val a = o.optJSONObject("address")
        listOf("city", "town", "village", "municipality", "county").firstNotNullOfOrNull { k -> a?.optString(k)?.ifEmpty { null } }
            ?: o.optString("name").ifEmpty { null }
    }.getOrNull()

    /** Rough location from the internet connection, for when the phone has no location fix yet. */
    fun approximate(lang: String): City? = runCatching {
        val o = JSONObject(get("$ROUGH?localityLanguage=$lang"))
        if (!o.has("latitude")) return@runCatching null
        val lat = o.getDouble("latitude")
        val lon = o.getDouble("longitude")
        City(placeName(lat, lon, lang) ?: o.optString("city").ifEmpty { return@runCatching null }, lat, lon)
    }.getOrNull()

    fun search(query: String, lang: String): List<City> {
        val o = JSONObject(get("$GEOCODE?name=${URLEncoder.encode(query, "UTF-8")}&count=8&language=$lang"))
        val results = o.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { results.getJSONObject(it) }.map {
            City(it.getString("name"), it.getDouble("latitude"), it.getDouble("longitude"),
                listOf(it.optString("admin1"), it.optString("country")).filter(String::isNotEmpty).distinct().joinToString(", "))
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "KosherSwitch/1.2 (Android; weather)")
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: String, fetched: Long): Forecast {
        val o = JSONObject(json)
        val c = o.getJSONObject("current")
        val now = Now(
            c.getDouble("temperature_2m"), c.getDouble("apparent_temperature"), c.getInt("relative_humidity_2m"),
            c.getInt("weather_code"), c.getDouble("wind_speed_10m"), c.optInt("wind_direction_10m"),
            c.optInt("is_day", 1) == 1, c.optDouble("pressure_msl", 0.0), c.optDouble("uv_index", 0.0),
        )
        val h = o.getJSONObject("hourly")
        val hTimes = h.getJSONArray("time")
        val hours = (0 until hTimes.length()).map { i ->
            Hour(LocalDateTime.parse(hTimes.getString(i)), h.getJSONArray("temperature_2m").optDouble(i),
                h.getJSONArray("weather_code").optInt(i), h.getJSONArray("precipitation_probability").optInt(i),
                h.getJSONArray("is_day").optInt(i, 1) == 1)
        }
        val d = o.getJSONObject("daily")
        val dTimes = d.getJSONArray("time")
        fun time(a: JSONArray, i: Int) = runCatching { LocalDateTime.parse(a.getString(i)).toLocalTime() }.getOrNull()
        val days = (0 until dTimes.length()).map { i ->
            Day(LocalDate.parse(dTimes.getString(i)), d.getJSONArray("weather_code").optInt(i),
                d.getJSONArray("temperature_2m_max").optDouble(i), d.getJSONArray("temperature_2m_min").optDouble(i),
                d.getJSONArray("precipitation_probability_max").optInt(i), d.getJSONArray("precipitation_sum").optDouble(i, 0.0),
                time(d.getJSONArray("sunrise"), i), time(d.getJSONArray("sunset"), i), d.getJSONArray("uv_index_max").optDouble(i, 0.0))
        }
        return Forecast(now, hours, days, fetched, o.optInt("utc_offset_seconds"))
    }

    // ---- Saved cities ----

    fun cities(ctx: Context): List<City> = runCatching {
        val a = JSONArray(prefs(ctx).getString(KEY_CITIES, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }
            .map { City(it.getString("name"), it.getDouble("lat"), it.getDouble("lon"), it.optString("area")) }
    }.getOrDefault(emptyList())

    fun setCities(ctx: Context, list: List<City>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon).put("area", it.area)) }
        prefs(ctx).edit().putString(KEY_CITIES, a.toString()).apply()
    }

    /** -1 = the phone's location, otherwise an index into [cities]. */
    fun selected(ctx: Context) = prefs(ctx).getInt(KEY_SELECTED, -1)
    fun setSelected(ctx: Context, i: Int) = prefs(ctx).edit().putInt(KEY_SELECTED, i).apply()

    fun hereName(ctx: Context, key: String) = prefs(ctx).getString("weather_town_$key", null)
    fun setHereName(ctx: Context, key: String, name: String) = prefs(ctx).edit().putString("weather_town_$key", name).apply()
}
