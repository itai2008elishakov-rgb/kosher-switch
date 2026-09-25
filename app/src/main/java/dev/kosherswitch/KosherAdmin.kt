package dev.kosherswitch

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class KosherAdmin : DeviceAdminReceiver() {
    companion object {
        fun component(ctx: Context) = ComponentName(ctx, KosherAdmin::class.java)
    }

    override fun onEnabled(ctx: Context, intent: Intent) = ModeManager.applyOrganization(ctx)
}
