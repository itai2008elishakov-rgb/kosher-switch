package dev.kosherswitch

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService

/** After an update Android can leave the swipe-down tile grey until the app is opened; this wakes it. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        ModeManager.applyOrganization(ctx)
        runCatching { TileService.requestListeningState(ctx, ComponentName(ctx, ModeTileService::class.java)) }
    }
}
