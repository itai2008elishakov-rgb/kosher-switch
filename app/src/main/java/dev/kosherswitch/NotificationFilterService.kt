package dev.kosherswitch

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * In kosher mode, hides notifications from anything that isn't an allowed app or a core phone
 * service (e.g. Google's "back up your data" messages). Enabled once over USB during setup.
 */
class NotificationFilterService : NotificationListenerService() {
    companion object {
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        /** Calls, SMS, charging, USB and other core system messages stay visible. */
        private val SYSTEM = setOf(
            "android", "com.android.systemui", "com.android.phone", "com.android.server.telecom",
            "com.samsung.android.incallui", "com.android.bluetooth", "com.samsung.android.app.telephonyui",
            "com.android.cellbroadcastreceiver", "com.samsung.android.cellbroadcastreceiver",
        )

        private var instance: NotificationFilterService? = null

        /** Clears already-showing notifications right after switching to kosher mode. */
        fun sweep() = instance?.hideAllBlocked()
    }

    override fun onListenerConnected() {
        instance = this
        hideAllBlocked()
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (ModeManager.isClosed(this) && !allowed(sbn.packageName)) hide(sbn)
    }

    private fun hideAllBlocked() {
        if (!ModeManager.isClosed(this)) return
        runCatching { activeNotifications }.getOrNull()?.filterNot { allowed(it.packageName) }?.forEach(::hide)
    }

    private fun allowed(pkg: String) =
        pkg == packageName || pkg in SYSTEM || pkg in ModeManager.allowedInClosed(this)

    private fun hide(sbn: StatusBarNotification) {
        // Ongoing notifications can't be dismissed, but they can be snoozed out of sight.
        if (sbn.isClearable) cancelNotification(sbn.key) else snoozeNotification(sbn.key, WEEK_MS)
    }
}
