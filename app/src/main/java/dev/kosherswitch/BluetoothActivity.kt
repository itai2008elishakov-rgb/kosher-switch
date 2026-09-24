package dev.kosherswitch

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Pair headphones, a car or a speaker, since the Settings app is locked in kosher mode. */
@SuppressLint("MissingPermission") // Granted by KosherSettingsActivity.grantBluetooth.
class BluetoothActivity : BaseActivity() {
    private val adapter by lazy { getSystemService(BluetoothManager::class.java).adapter }
    private val found = linkedMapOf<String, BluetoothDevice>()
    private lateinit var content: LinearLayout

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == BluetoothDevice.ACTION_FOUND) {
                val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (d.name != null && d.bondState != BluetoothDevice.BOND_BONDED) found[d.address] = d
            }
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        KosherSettingsActivity.grantBluetooth(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 16)
            setPadding(p, 0, p, Theme.dp(context, 32))
        }
        setContentView(KosherPage.page(this, KosherPage.titleBar(this, getString(R.string.bluetooth)),
            ScrollView(this).apply { addView(content) }))
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        @Suppress("DEPRECATION")
        if (!adapter.isEnabled) adapter.enable()
        render()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
        runCatching { adapter.cancelDiscovery() }
    }

    private fun render() {
        content.removeAllViews()
        val paired = adapter.bondedDevices.orEmpty()
        content.addView(Theme.card(this,
            Theme.text(this, getString(R.string.paired_devices), 17f, Theme.INK, Theme.MEDIUM),
            *(if (paired.isEmpty()) listOf(Theme.text(this, getString(R.string.no_paired), 15f, Theme.SUB))
            else paired.map { row(it, paired = true) }).toTypedArray(),
        ))
        content.addView(Theme.card(this,
            Theme.text(this, getString(R.string.new_devices), 17f, Theme.INK, Theme.MEDIUM),
            *(if (found.isEmpty()) listOf(Theme.text(this,
                getString(if (adapter.isDiscovering) R.string.searching else R.string.search_devices), 15f, Theme.SUB))
            else found.values.map { row(it, paired = false) }).toTypedArray(),
            Theme.button(this, getString(R.string.search_devices), outline = true) {
                found.clear()
                adapter.startDiscovery()
                render()
            },
        ))
    }

    private fun row(d: BluetoothDevice, paired: Boolean) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Theme.dp(context, 12), 0, Theme.dp(context, 12))
        addView(Theme.text(context, d.name ?: d.address, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (!paired) {
            addView(Theme.text(context, getString(R.string.connect), 14f, Theme.LINK, Theme.MEDIUM))
            setOnClickListener {
                adapter.cancelDiscovery()
                d.createBond()
                Toast.makeText(context, getString(R.string.pairing, d.name), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
