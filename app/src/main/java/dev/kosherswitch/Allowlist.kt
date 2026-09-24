package dev.kosherswitch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager

/** Which apps stay visible in closed mode, and which of those may use the internet. */
object Allowlist {
    private const val KEY_APPS = "allowed_apps"
    private const val KEY_NETWORK = "network_apps"
    const val WAZE = "com.waze"

    /** Background services that internet apps (Maps, Waze sign-in) need to connect. They show nothing on screen. */
    val SUPPORT_SERVICES = setOf("com.google.android.gms", "com.google.android.gsf")

    // Basic phone apps on Samsung One UI and on AOSP/LineageOS-based GSIs
    private val KNOWN_BASICS = listOf(
        "com.samsung.android.dialer", "com.samsung.android.messaging",
        "com.samsung.android.app.contacts", "com.sec.android.app.camera",
        "com.sec.android.gallery3d", "com.sec.android.app.clockpackage",
        "com.sec.android.app.popupcalculator", "com.android.settings",
        "com.google.android.dialer", "com.google.android.apps.messaging",
        "com.google.android.contacts", "com.google.android.deskclock",
        "com.google.android.calculator", "com.android.dialer", "com.android.messaging",
        "com.android.contacts", "com.android.camera2", "org.lineageos.aperture",
        "org.lineageos.glimpse", "com.android.deskclock", "com.android.calculator2",
        WAZE,
    )

    fun apps(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_APPS, null)?.toSet() ?: defaults(ctx)

    fun setApps(ctx: Context, apps: Set<String>) =
        prefs(ctx).edit().putStringSet(KEY_APPS, apps).apply()

    fun networkApps(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_NETWORK, null)?.toSet() ?: setOf(WAZE)

    fun setNetworkApps(ctx: Context, apps: Set<String>) =
        prefs(ctx).edit().putStringSet(KEY_NETWORK, apps).apply()

    private fun defaults(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val found = mutableSetOf<String>()
        ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(found::add)
        Telephony.Sms.getDefaultSmsPackage(ctx)?.let(found::add)
        listOf(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI),
        ).mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .filter { it != "android" } // "android" means a chooser, not a real app
            .forEach(found::add)
        KNOWN_BASICS.filter { isInstalled(pm, it) }.forEach(found::add)
        return found
    }

    private fun isInstalled(pm: PackageManager, pkg: String) =
        runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
}
