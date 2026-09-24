package dev.kosherswitch

import android.content.Context
import android.content.Intent

data class AppEntry(val pkg: String, val label: String)

/** Apps with a home-screen icon, excluding this app. Hidden apps are not returned. */
fun launchableApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .sortedBy { it.label.lowercase() }
}
