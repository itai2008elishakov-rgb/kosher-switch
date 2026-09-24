package dev.kosherswitch

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** The swipe-down button. Entering kosher mode is one tap; leaving asks for the code. */
class ModeTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        when {
            ModeManager.isClosed(this) ->
                open(Intent(this, UnlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            !ModeManager.isReady(this) ->
                open(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> open(SwitchActivity.intent(this, toClosed = true))
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val closed = ModeManager.isClosed(this)
        tile.state = if (closed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "כשר"
        tile.subtitle = if (closed) "פעיל" else "כבוי"
        tile.updateTile()
    }

    private fun open(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, intent.component.hashCode(), intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
