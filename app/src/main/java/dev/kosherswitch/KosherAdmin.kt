package dev.kosherswitch

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

class KosherAdmin : DeviceAdminReceiver() {
    companion object {
        fun component(ctx: Context) = ComponentName(ctx, KosherAdmin::class.java)
    }
}
