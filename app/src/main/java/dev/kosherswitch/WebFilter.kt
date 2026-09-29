package dev.kosherswitch

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Filtering for the apps that are allowed online in kosher mode (Chrome, Google, Waze…).
 * - The whole phone uses CleanBrowsing's Family filter as its DNS ("Private DNS", locked): it blocks
 *   pornography and adult sites, mixed-content sites, proxies and VPNs that get around filters, and
 *   forces SafeSearch on Google, Bing and YouTube, for every app.
 * - Chrome gets its official managed settings: SafeSearch and strict YouTube always on, Google's adult
 *   filter on, no incognito, no secure DNS of its own (so it can't skip the filter), and no pictures on
 *   websites unless a parent allows them.
 */
object WebFilter {
    private const val TAG = "KosherWebFilter"
    const val FAMILY_DNS = "family-filter-dns.cleanbrowsing.org"
    private val CHROMES = listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary")

    fun apply(ctx: Context) {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = KosherAdmin.component(ctx)
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val result = dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, FAMILY_DNS)
                if (result != DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) Log.w(TAG, "Family DNS not set: $result")
            }.onFailure { Log.e(TAG, "Family DNS failed", it) }
        }
        val chrome = chromePolicy(ctx)
        CHROMES.forEach { pkg -> runCatching { dpm.setApplicationRestrictions(admin, pkg, chrome) } }
    }

    fun clear(ctx: Context) {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admin = KosherAdmin.component(ctx)
        if (Build.VERSION.SDK_INT >= 29) runCatching { dpm.setGlobalPrivateDnsModeOpportunistic(admin) }
        CHROMES.forEach { pkg -> runCatching { dpm.setApplicationRestrictions(admin, pkg, Bundle()) } }
    }

    /** Chrome's enterprise policies (the same ones schools and companies use). */
    private fun chromePolicy(ctx: Context) = Bundle().apply {
        putBoolean("ForceGoogleSafeSearch", true)
        putInt("ForceYouTubeRestrict", 2)            // strict
        putInt("SafeSitesFilterBehavior", 1)         // Google's adult-content filter
        putInt("IncognitoModeAvailability", 1)       // no incognito
        putBoolean("BrowserGuestModeEnabled", false)
        putString("DnsOverHttpsMode", "off")         // use the phone's (filtered) DNS
        putBoolean("BuiltInDnsClientEnabled", false)
        putBoolean("PasswordManagerEnabled", false)
        if (!Looks.webPictures(ctx)) putInt("DefaultImagesSetting", 2)   // no pictures on websites
    }
}
