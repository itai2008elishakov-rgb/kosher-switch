package dev.kosherswitch

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Wi-Fi network picker, since the Settings app is locked in kosher mode. */
@Suppress("DEPRECATION") // Device owners may still add and join networks with these APIs.
class WifiActivity : BaseActivity() {
    private lateinit var wifi: WifiManager
    private lateinit var connected: android.widget.TextView
    private lateinit var list: LinearLayout

    private val scanDone = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        grantLocation()
        if (!wifi.isWifiEnabled) wifi.isWifiEnabled = true

        connected = Theme.text(this, "", 15f, Theme.SUB)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 20)
            setPadding(p, Theme.dp(context, 8), p, p)
            addView(connected)
            addView(Theme.card(context, list))
            addView(Theme.button(context, getString(R.string.search_again), outline = true) { scan() })
        }
        setContentView(KosherPage.page(this, KosherPage.titleBar(this, getString(R.string.wifi)),
            ScrollView(this).apply { addView(content) }))
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanDone, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        scan()
        render()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(scanDone)
    }

    /** Wi-Fi scanning needs location access. */
    private fun grantLocation() = Place.ensureAccess(this)

    private fun scan() {
        wifi.startScan()
    }

    private fun render() {
        val current = wifi.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        connected.text = if (current != null) getString(R.string.connected_to, current) else getString(R.string.not_connected)
        list.removeAllViews()
        val networks = wifi.scanResults
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (_, same) -> same.maxBy { it.level } }
            .sortedByDescending { it.level }
        if (networks.isEmpty()) {
            list.addView(Theme.text(this, getString(R.string.searching), 15f, Theme.SUB))
            return
        }
        networks.forEach { net ->
            val secured = isSecured(net)
            val bars = "▂▄▆█".take(WifiManager.calculateSignalLevel(net.level, 4) + 1)
            list.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Theme.dp(context, 12), 0, Theme.dp(context, 12))
                addView(Theme.text(context, net.SSID, 16f,
                    if (net.SSID == current) Theme.LINK else Theme.INK,
                    if (net.SSID == current) Theme.MEDIUM else Theme.REGULAR),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Theme.text(context, (if (secured) "🔒 " else "") + bars, 14f, Theme.SUB))
                setOnClickListener {
                    if (secured) askPassword(net) else connect(net, null)
                }
            })
        }
    }

    private fun isSecured(net: ScanResult) =
        listOf("WPA", "WEP", "SAE", "PSK", "EAP").any { net.capabilities.contains(it) }

    private fun askPassword(net: ScanResult) {
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.password)
        }
        val box = LinearLayout(this).apply {
            val p = Theme.dp(context, 20)
            setPadding(p, Theme.dp(context, 8), p, 0)
            addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(net.SSID)
            .setView(box)
            .setPositiveButton(getString(R.string.connect)) { _, _ -> connect(net, field.text.toString()) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun connect(net: ScanResult, password: String?) {
        val config = WifiConfiguration().apply {
            SSID = "\"${net.SSID}\""
            when {
                password == null -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                Build.VERSION.SDK_INT >= 30 && net.capabilities.contains("SAE") && !net.capabilities.contains("PSK") -> {
                    setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                    preSharedKey = "\"$password\""
                }
                else -> preSharedKey = "\"$password\""
            }
        }
        val id = wifi.addNetwork(config)
        if (id == -1) {
            Toast.makeText(this, getString(R.string.couldnt_add), Toast.LENGTH_SHORT).show()
            return
        }
        wifi.enableNetwork(id, true)
        wifi.reconnect()
        Toast.makeText(this, getString(R.string.connecting, net.SSID), Toast.LENGTH_SHORT).show()
        connected.postDelayed(::render, 4000)
    }
}
