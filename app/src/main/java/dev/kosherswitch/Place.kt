package dev.kosherswitch

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.LocalDate
import java.time.ZoneId
import java.util.GregorianCalendar
import java.util.TimeZone

/** The phone's location for prayer times: remembered, so it works offline and without asking. */
object Place {
    private const val KEY_LAT = "zmanim_lat"
    private const val KEY_LON = "zmanim_lon"

    fun saved(ctx: Context): Pair<Double, Double>? {
        val p = prefs(ctx)
        if (!p.contains(KEY_LAT)) return null
        return p.getFloat(KEY_LAT, 0f).toDouble() to p.getFloat(KEY_LON, 0f).toDouble()
    }

    fun zmanim(ctx: Context, date: LocalDate): ComplexZmanimCalendar? {
        val (lat, lon) = saved(ctx) ?: return null
        return ComplexZmanimCalendar(GeoLocation("", lat, lon, TimeZone.getDefault())).apply {
            calendar = GregorianCalendar.from(date.atStartOfDay(ZoneId.systemDefault()))
            candleLightingOffset = if (JewishDates.inIsrael) 30.0 else 18.0
        }
    }

    /**
     * Grants location to this app and switches location on, but only when needed:
     * doing it every time makes Android show an "allowed by your admin" message.
     */
    fun ensureAccess(ctx: Context) {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        val admin = KosherAdmin.component(ctx)
        runCatching {
            if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                dpm.setPermissionGrantState(admin, ctx.packageName, Manifest.permission.ACCESS_FINE_LOCATION,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
            val lm = ctx.getSystemService(LocationManager::class.java)
            if (Build.VERSION.SDK_INT >= 30 && !lm.isLocationEnabled) dpm.setLocationEnabled(admin, true)
        }
    }

    /** Uses the last known location right away, then refreshes it; [onUpdate] runs when it changes. */
    @Suppress("MissingPermission")
    fun refresh(ctx: Context, onUpdate: () -> Unit) {
        ensureAccess(ctx)
        val lm = ctx.getSystemService(LocationManager::class.java)
        runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { save(ctx, it); onUpdate() }
            val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, ctx.mainExecutor) { loc ->
                    if (loc != null) { save(ctx, loc); onUpdate() }
                }
            }
        }
    }

    private fun save(ctx: Context, loc: Location) {
        prefs(ctx).edit().putFloat(KEY_LAT, loc.latitude.toFloat()).putFloat(KEY_LON, loc.longitude.toFloat()).apply()
    }
}
