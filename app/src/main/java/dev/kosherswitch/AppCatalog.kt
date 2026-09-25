package dev.kosherswitch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * The apps a screen may show: every app in open mode, only the allowed ones in kosher mode,
 * plus Kosher Switch's own apps. Icons follow the chosen icon style.
 */
object AppCatalog {
    class Entry(val label: String, val key: String, val icon: Drawable, val intent: Intent)

    fun load(ctx: Context): List<Entry> {
        val pm = ctx.packageManager
        val closed = ModeManager.isClosed(ctx)
        val allowed = ModeManager.allowedInClosed(ctx)
        val installed = launchableApps(ctx).filter { !closed || it.pkg in allowed }.mapNotNull { a ->
            val intent = pm.getLaunchIntentForPackage(a.pkg) ?: return@mapNotNull null
            Entry(a.label, a.pkg, Icons.forApp(ctx, a.pkg, pm.getApplicationIcon(a.pkg)), intent)
        }
        fun own(label: Int, cls: Class<*>, key: String) = Entry(ctx.getString(label), key,
            Icons.forApp(ctx, key, pm.getActivityIcon(ComponentName(ctx, cls))), Intent(ctx, cls))
        val mine = listOfNotNull(
            own(R.string.siddur_label, SiddurActivity::class.java, "siddur"),
            own(R.string.times_label, TimesActivity::class.java, "times"),
            own(R.string.notes, NotesActivity::class.java, "notes"),
            if (Looks.weatherAllowed(ctx)) own(R.string.weather, WeatherActivity::class.java, "weather") else null,
            if (Looks.desktopAllowed(ctx)) own(R.string.desktop, DesktopActivity::class.java, "desktop") else null,
            if (Looks.assistantAllowed(ctx)) own(R.string.assistant, AssistantActivity::class.java, "assistant") else null,
            Entry(ctx.getString(R.string.settings), "kosher_settings",
                Icons.forApp(ctx, "kosher_settings", ctx.getDrawable(R.drawable.ic_settings_app)!!),
                Intent(ctx, KosherSettingsActivity::class.java)),
        )
        val order = Looks.order(ctx)
        return (installed + mine).sortedBy { order.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }
}
