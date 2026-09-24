package dev.kosherswitch

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Log
import java.util.concurrent.Executors

/**
 * Switches the phone between open mode and closed (kosher) mode.
 * Requires this app to be the device owner. All switching runs on one background thread,
 * so two switches can never overlap.
 */
object ModeManager {
    private const val TAG = "KosherSwitch"
    private const val KEY_CLOSED = "closed"

    /** Always hidden in kosher mode, whatever the allowlist says. */
    val ALWAYS_BLOCKED = setOf("com.android.settings")

    /** Background system apps that open their own screens (Samsung software update). */
    private val SYSTEM_POPUPS = listOf("com.wssyncmldm", "com.sec.android.soagent")

    private val ALL_RESTRICTIONS = listOf(
        UserManager.DISALLOW_INSTALL_APPS,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_UNINSTALL_APPS,
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_MODIFY_ACCOUNTS,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_NETWORK_RESET,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_CONFIG_VPN,
        UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
        UserManager.DISALLOW_CONFIG_TETHERING,
        UserManager.DISALLOW_CONFIG_CREDENTIALS,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_REMOVE_USER,
        UserManager.DISALLOW_USER_SWITCH,
        UserManager.DISALLOW_BLUETOOTH_SHARING,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
    )

    // Debug builds keep USB debugging available so a bad build can't lock us out of adb.
    private val ACTIVE_RESTRICTIONS =
        if (BuildConfig.DEBUG) ALL_RESTRICTIONS - UserManager.DISALLOW_DEBUGGING_FEATURES
        else ALL_RESTRICTIONS

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Apps that stay usable in closed mode. */
    fun allowedInClosed(ctx: Context) = Allowlist.apps(ctx) - ALWAYS_BLOCKED

    fun isClosed(ctx: Context) = prefs(ctx).getBoolean(KEY_CLOSED, false)

    fun isDeviceOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    fun isReady(ctx: Context) = isDeviceOwner(ctx) && PinStore.isSet(ctx)

    /** Runs [work] on the switching thread, then [done] on the main thread. */
    fun inBackground(work: () -> Unit, done: () -> Unit = {}) {
        worker.execute {
            try {
                work()
            } catch (e: Exception) {
                Log.e(TAG, "Switch failed", e)
            }
            main.post(done)
        }
    }

    /** Undoes any leftovers from closed mode, if the phone is in open mode. */
    fun repairIfOpen(ctx: Context) = inBackground({ if (!isClosed(ctx)) restoreOpen(ctx) })

    fun enterClosed(ctx: Context) {
        check(isDeviceOwner(ctx)) { "Kosher Switch is not the device owner" }
        val dpm = dpm(ctx)
        val admin = KosherAdmin.component(ctx)
        val keep = allowedInClosed(ctx) + ctx.packageName

        // Home screen first, so the phone always has a launcher.
        setClosedHomeEnabled(ctx, true)
        dpm.addPersistentPreferredActivity(
            admin, homeFilter(), ComponentName(ctx, ClosedHomeActivity::class.java)
        )
        prefs(ctx).edit().putBoolean(KEY_CLOSED, true).commit()

        // Suspended apps can't open, but keep their place on the normal home screen.
        // A few system apps can't be suspended; those get hidden instead.
        val installed = ctx.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
        val blocked = launchableApps(ctx).map { it.pkg }.filterNot { it in keep } + SYSTEM_POPUPS.filter { it in installed }
        dpm.setPackagesSuspended(admin, blocked.toTypedArray(), true)
            .forEach { dpm.setApplicationHidden(admin, it, true) }

        ACTIVE_RESTRICTIONS.forEach { dpm.addUserRestriction(admin, it) }

        // Always-on VPN in lockdown: every app is cut off except the network allowlist.
        var networkApps = Allowlist.networkApps(ctx).filter { it in keep }.toSet()
        if (networkApps.isNotEmpty()) networkApps = networkApps + Allowlist.SUPPORT_SERVICES
        attempt("network block") { dpm.setAlwaysOnVpnPackage(admin, ctx.packageName, true, networkApps) }

        applyLockMessage(ctx)
        dpm.setShortSupportMessage(admin, ctx.getString(R.string.blocked_app))
        NotificationFilterService.sweep()
        attempt("wallpaper") {
            if (Build.VERSION.SDK_INT < 33) {
                dpm.setPermissionGrantState(
                    admin, ctx.packageName, Manifest.permission.READ_EXTERNAL_STORAGE,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            }
            Wallpapers.applyKosher(ctx)
        }
    }

    /** Shows or clears the "מכשיר כשר" line on the lock screen. */
    fun applyLockMessage(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        dpm(ctx).setDeviceOwnerLockScreenInfo(KosherAdmin.component(ctx),
            if (isClosed(ctx) && Looks.lockMessage(ctx)) ctx.getString(R.string.kosher_device) else null)
    }

    fun exitClosed(ctx: Context) {
        restoreOpen(ctx)
        prefs(ctx).edit().putBoolean(KEY_CLOSED, false).commit()
    }

    /**
     * Undoes everything closed mode does. It doesn't rely on remembered state,
     * so apps can never stay hidden by mistake.
     */
    private fun restoreOpen(ctx: Context) {
        if (!isDeviceOwner(ctx)) return
        val dpm = dpm(ctx)
        val admin = KosherAdmin.component(ctx)
        attempt("network unblock") { dpm.setAlwaysOnVpnPackage(admin, null, false) }
        ALL_RESTRICTIONS.forEach { dpm.clearUserRestriction(admin, it) }
        val installed = ctx.packageManager.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .map { it.packageName }
        installed.filter { dpm.isApplicationHidden(admin, it) }
            .forEach { dpm.setApplicationHidden(admin, it, false) }
        val suspended = installed.filter { runCatching { dpm.isPackageSuspended(admin, it) }.getOrDefault(false) }
        if (suspended.isNotEmpty()) dpm.setPackagesSuspended(admin, suspended.toTypedArray(), false)
        dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
        setClosedHomeEnabled(ctx, false)
        dpm.setDeviceOwnerLockScreenInfo(admin, null)
        dpm.setShortSupportMessage(admin, null)
        attempt("wallpaper restore") { Wallpapers.restore(ctx) }
    }

    private inline fun attempt(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Failed: $what", e)
        }
    }

    private fun setClosedHomeEnabled(ctx: Context, enabled: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx, ClosedHomeActivity::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun homeFilter() = IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
}
