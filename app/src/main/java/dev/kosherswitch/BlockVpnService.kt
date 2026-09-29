package dev.kosherswitch

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * A "VPN" that goes nowhere: all traffic is routed into it and never read, so it is dropped.
 * Apps in the network allowlist (Waze) and, for the weather app, this app itself are excluded and
 * use the real connection.
 */
class BlockVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ModeManager.isClosed(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (tun == null) {
            val builder = Builder()
                .setSession("Kosher mode")
                .addAddress("10.111.222.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:6b6f:7368::1", 128)
                .addRoute("::", 0)
            val networkApps = Allowlist.onlineApps(this)
            var bypass = if (networkApps.isEmpty()) networkApps else networkApps + Allowlist.SUPPORT_SERVICES
            // This app's own traffic: the weather forecast, and the Kosher Browser if a parent allowed it
            // (its pictures are checked on the phone, and it still goes through the family filter).
            if (Looks.weatherAllowed(this) || Looks.browserAllowed(this)) bypass = bypass + packageName
            bypass.forEach { runCatching { builder.addDisallowedApplication(it) } }
            tun = builder.establish()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        close()
        stopSelf()
    }

    override fun onDestroy() {
        close()
        super.onDestroy()
    }

    private fun close() {
        tun?.close()
        tun = null
    }
}
