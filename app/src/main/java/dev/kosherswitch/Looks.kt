package dev.kosherswitch

import android.content.Context
import android.content.res.Configuration

/** The look-and-feel choices from Settings → Appearance. */
object Looks {
    enum class Mode { SYSTEM, LIGHT, DARK }
    enum class Icons { KOSHER, GLASS, ORIGINAL }

    /** Home and wallpaper backgrounds: (name, top color, bottom color, glow colors). */
    class Scene(val key: String, val label: String, val top: Int, val bottom: Int, val glows: IntArray)

    val SCENES = listOf(
        Scene("blue", "כחול", 0xFF0B2A5B.toInt(), 0xFF1E4E9C.toInt(), intArrayOf(0x55E0B64A, 0x4D4FA3FF, 0x406A5CFF)),
        Scene("night", "לילה", 0xFF05060C.toInt(), 0xFF1B1733.toInt(), intArrayOf(0x406A5CFF, 0x33E0B64A, 0x3348C6FF)),
        Scene("jerusalem", "ירושלים", 0xFF5B3A1E.toInt(), 0xFFC9A46A.toInt(), intArrayOf(0x66FFE2A8, 0x40FFFFFF, 0x33D98A3A)),
        Scene("sea", "ים", 0xFF003B46.toInt(), 0xFF07889B.toInt(), intArrayOf(0x5566E0D8, 0x40A4F0FF, 0x33FFD58A)),
        Scene("forest", "יער", 0xFF0E2A1C.toInt(), 0xFF2F6B45.toInt(), intArrayOf(0x55B7E07A, 0x40FFE08A, 0x3366C2A0)),
    )

    private fun p(ctx: Context) = prefs(ctx)

    fun mode(ctx: Context) = Mode.valueOf(p(ctx).getString("look_mode", Mode.SYSTEM.name)!!)
    fun setMode(ctx: Context, m: Mode) = p(ctx).edit().putString("look_mode", m.name).apply()

    fun isDark(ctx: Context) = when (mode(ctx)) {
        Mode.DARK -> true
        Mode.LIGHT -> false
        Mode.SYSTEM -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun scene(ctx: Context) = SCENES.firstOrNull { it.key == p(ctx).getString("look_scene", "blue") } ?: SCENES[0]
    fun setScene(ctx: Context, key: String) = p(ctx).edit().putString("look_scene", key).apply()

    /** Glass strength 0–100; 0 turns the glass effect off (solid panels). */
    fun glass(ctx: Context) = p(ctx).getInt("look_glass", 60)
    fun setGlass(ctx: Context, v: Int) = p(ctx).edit().putInt("look_glass", v).apply()

    fun icons(ctx: Context) = Icons.valueOf(p(ctx).getString("look_icons", Icons.KOSHER.name)!!)
    fun setIcons(ctx: Context, i: Icons) = p(ctx).edit().putString("look_icons", i.name).apply()

    fun widget(ctx: Context) = p(ctx).getBoolean("look_widget", true)
    fun setWidget(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("look_widget", on).apply()

    fun haptics(ctx: Context) = p(ctx).getBoolean("look_haptics", true)
    fun setHaptics(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("look_haptics", on).apply()

    // Home screen layout
    fun clockSize(ctx: Context) = p(ctx).getInt("home_clock_size", 0) // 0 large, 1 medium, 2 small
    fun setClockSize(ctx: Context, v: Int) = p(ctx).edit().putInt("home_clock_size", v).apply()
    fun clockFont(ctx: Context) = p(ctx).getInt("home_clock_font", 0) // 0 thin, 1 bold, 2 classic
    fun setClockFont(ctx: Context, v: Int) = p(ctx).edit().putInt("home_clock_font", v).apply()
    fun showDate(ctx: Context) = p(ctx).getBoolean("home_show_date", true)
    fun setShowDate(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("home_show_date", on).apply()
    fun showHebrewDate(ctx: Context) = p(ctx).getBoolean("home_show_hebrew", true)
    fun setShowHebrewDate(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("home_show_hebrew", on).apply()
    fun showBadge(ctx: Context) = p(ctx).getBoolean("home_show_badge", true)
    fun setShowBadge(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("home_show_badge", on).apply()
    fun lockMessage(ctx: Context) = p(ctx).getBoolean("lock_message", true)
    fun setLockMessage(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("lock_message", on).apply()

    /** Whether the assistant exists in kosher mode at all. Only changeable in open mode. */
    fun assistantAllowed(ctx: Context) = p(ctx).getBoolean("assistant_allowed", true)
    fun setAssistantAllowed(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("assistant_allowed", on).apply()

    /** Whether the weather app (the only part of this app that uses the internet) exists in kosher mode. */
    fun weatherAllowed(ctx: Context) = p(ctx).getBoolean("weather_allowed", true)
    fun setWeatherAllowed(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("weather_allowed", on).apply()

    fun assistantPill(ctx: Context) = p(ctx).getBoolean("look_assistant_pill", true)
    fun setAssistantPill(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("look_assistant_pill", on).apply()

    /** The three dock apps (app keys), or null for the default picks. */
    fun dock(ctx: Context): List<String>? = p(ctx).getString("home_dock", null)?.split('|')
    fun setDock(ctx: Context, keys: List<String>) = p(ctx).edit().putString("home_dock", keys.joinToString("|")).apply()

    /** The drawer order the user arranged (app keys); new apps go at the end. */
    fun order(ctx: Context): List<String> = p(ctx).getString("home_order", "")!!.split('|').filter { it.isNotEmpty() }
    fun setOrder(ctx: Context, keys: List<String>) = p(ctx).edit().putString("home_order", keys.joinToString("|")).apply()
}
