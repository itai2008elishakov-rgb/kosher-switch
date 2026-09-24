package dev.kosherswitch

import android.app.WallpaperManager
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream

/** Puts the kosher wallpaper on home + lock screen, and puts the user's own back afterwards. */
object Wallpapers {
    private const val TAG = "KosherSwitch"
    private const val KEY_CHANGED = "wallpaper_changed"
    private const val SYSTEM_BACKUP = "wallpaper_system.bak"
    private const val LOCK_BACKUP = "wallpaper_lock.bak"

    fun applyKosher(ctx: Context) {
        val wm = WallpaperManager.getInstance(ctx)
        if (!prefs(ctx).getBoolean(KEY_CHANGED, false)) {
            backup(wm.safeFile(WallpaperManager.FLAG_SYSTEM), File(ctx.filesDir, SYSTEM_BACKUP))
            backup(wm.safeFile(WallpaperManager.FLAG_LOCK), File(ctx.filesDir, LOCK_BACKUP))
            prefs(ctx).edit().putBoolean(KEY_CHANGED, true).commit()
        }
        wm.setBitmap(Backdrop.render(ctx, emblem = true), null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
    }

    fun restore(ctx: Context) {
        if (!prefs(ctx).getBoolean(KEY_CHANGED, false)) return
        val wm = WallpaperManager.getInstance(ctx)
        val system = File(ctx.filesDir, SYSTEM_BACKUP)
        val lock = File(ctx.filesDir, LOCK_BACKUP)
        // No separate lock backup means the lock screen used the home wallpaper.
        val systemFlags = WallpaperManager.FLAG_SYSTEM or
            if (lock.exists()) 0 else WallpaperManager.FLAG_LOCK
        if (system.exists()) system.inputStream().use { wm.setStream(it, null, true, systemFlags) }
        else wm.clear(systemFlags)
        if (lock.exists()) lock.inputStream().use { wm.setStream(it, null, true, WallpaperManager.FLAG_LOCK) }
        system.delete()
        lock.delete()
        prefs(ctx).edit().putBoolean(KEY_CHANGED, false).commit()
    }

    private fun WallpaperManager.safeFile(which: Int): ParcelFileDescriptor? =
        try {
            getWallpaperFile(which)
        } catch (e: Exception) {
            Log.w(TAG, "Can't read wallpaper $which; it will reset to default", e)
            null
        }

    private fun backup(source: ParcelFileDescriptor?, target: File) {
        target.delete()
        source ?: return
        source.use { pfd ->
        FileInputStream(pfd.fileDescriptor).use { input ->
            target.outputStream().use { input.copyTo(it) }
        } }
    }

}
