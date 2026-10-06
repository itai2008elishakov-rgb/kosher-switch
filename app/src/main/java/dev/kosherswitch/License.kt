package dev.kosherswitch

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Paid plans on Android, sold through Polar (polar.sh): each purchase gives a licence key.
 * The key is activated on this phone (Personal: 1 phone, Family: up to 6) and checked again from time to
 * time while the phone is open. Turning kosher mode ON needs a valid plan; turning it OFF never does.
 * Polar's licence endpoints are public, so no secret lives in the app.
 */
object License {
    /** The Polar organisation. Until it's set, plans aren't live yet and kosher mode stays free. */
    const val ORG_ID = ""
    const val BUY_URL = "https://itai2008elishakov-rgb.github.io/kosher-switch/#pricing"
    private const val API = "https://api.polar.sh/v1/customer-portal/license-keys"
    /** Without internet, a plan that was valid stays valid this long. */
    private const val GRACE_DAYS = 35L

    private const val K_KEY = "license_key"
    private const val K_ACTIVATION = "license_activation"
    private const val K_OK_AT = "license_ok_at"
    private const val K_EXPIRES = "license_expires"

    val live get() = ORG_ID.isNotEmpty()
    fun key(ctx: Context): String? = prefs(ctx).getString(K_KEY, null)

    /** May kosher mode be turned on? */
    fun allowed(ctx: Context): Boolean {
        if (!live) return true
        val p = prefs(ctx)
        if (p.getString(K_KEY, null) == null) return false
        val okAt = p.getLong(K_OK_AT, 0)
        if (System.currentTimeMillis() - okAt > GRACE_DAYS * 86_400_000L) return false
        val expires = p.getLong(K_EXPIRES, 0)
        return expires == 0L || System.currentTimeMillis() < expires
    }

    /** Short status for the screens, e.g. "Active" or "No plan". */
    fun status(ctx: Context): String {
        val he = Lang.isHebrew(ctx)
        return when {
            !live -> if (he) "חינם כרגע" else "Free for now"
            allowed(ctx) -> if (he) "פעיל" else "Active"
            key(ctx) != null -> if (he) "צריך לחדש" else "Needs renewing"
            else -> if (he) "אין מסלול" else "No plan"
        }
    }

    sealed class Result {
        object Ok : Result()
        data class Error(val message: String) : Result()
    }

    /** Activates [key] on this phone. Runs on a background thread; [done] is called on it too. */
    fun activate(ctx: Context, key: String, done: (Result) -> Unit) = Thread {
        val he = Lang.isHebrew(ctx)
        val body = JSONObject().put("key", key.trim()).put("organization_id", ORG_ID).put("label", Build.MANUFACTURER + " " + Build.MODEL)
        val (code, json) = post("$API/activate", body)
        done(when (code) {
            200 -> { save(ctx, key.trim(), json?.optString("id"), json?.optJSONObject("license_key")); Result.Ok }
            403 -> Result.Error(
                if (json?.toString()?.contains("limit", true) == true)
                    (if (he) "המפתח כבר בשימוש במספר הטלפונים המרבי." else "This key is already used on the maximum number of phones.")
                else (if (he) "המפתח פג תוקף או בוטל." else "This key has expired or was cancelled.")
            )
            404 -> Result.Error(if (he) "המפתח לא נמצא. בדקו שהעתקתם אותו בדיוק." else "Key not found. Check that you copied it exactly.")
            -1 -> Result.Error(if (he) "אין חיבור לאינטרנט. נסו שוב." else "No internet connection. Please try again.")
            else -> Result.Error(if (he) "משהו השתבש ($code). נסו שוב." else "Something went wrong ($code). Please try again.")
        })
    }.start()

    /** Checks the plan again (while the phone is open). A cancelled or expired plan stops kosher mode from turning on. */
    fun refresh(ctx: Context) {
        if (!live) return
        val p = prefs(ctx)
        val key = p.getString(K_KEY, null) ?: return
        Thread {
            val body = JSONObject().put("key", key).put("organization_id", ORG_ID)
            p.getString(K_ACTIVATION, null)?.let { body.put("activation_id", it) }
            val (code, json) = post("$API/validate", body)
            when (code) {
                200 -> save(ctx, key, p.getString(K_ACTIVATION, null), json)
                404 -> p.edit().putLong(K_OK_AT, 0).apply()
                else -> Unit // offline or a hiccup: keep what we know
            }
        }.start()
    }

    private fun save(ctx: Context, key: String, activation: String?, licence: JSONObject?) {
        val expires = licence?.optString("expires_at")?.takeIf { it.isNotEmpty() && it != "null" }
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        prefs(ctx).edit().putString(K_KEY, key).putString(K_ACTIVATION, activation)
            .putLong(K_OK_AT, System.currentTimeMillis()).putLong(K_EXPIRES, expires).apply()
    }

    private fun post(url: String, body: JSONObject): Pair<Int, JSONObject?> = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 15000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        code to runCatching { JSONObject(text) }.getOrNull()
    } catch (e: Exception) { -1 to null }
}
