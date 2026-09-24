package dev.kosherswitch

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** The settings kosher mode allows, since the real Settings app is locked. */
class KosherSettingsActivity : BaseActivity() {
    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val admin by lazy { KosherAdmin.component(this) }
    private var torchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        render() // Wi-Fi/Bluetooth state may have changed in their own screens.
    }

    private fun render() {
        val audio = getSystemService(AudioManager::class.java)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 16)
            setPadding(p, 0, p, Theme.dp(context, 32))
            addView(connectionsCard())
            addView(appearanceCard())
            addView(homeCard())
            // Only shown when the assistant was allowed from open mode.
            if (Looks.assistantAllowed(context)) addView(Theme.card(context,
                heading(R.string.assistant_section),
                Theme.text(context, getString(if (Ai.installed(context)) R.string.assistant_installed else R.string.assistant_not_installed), 14f, Theme.SUB),
                switchRow(getString(R.string.assistant_on_home), Looks.assistantPill(context)) { Looks.setAssistantPill(context, it) },
            ))
            addView(soundCard(audio))
            addView(displayCard())
            addView(languageCard())
            addView(aboutCard())
        }
        setContentView(KosherPage.page(this, KosherPage.titleBar(this, getString(R.string.settings)),
            ScrollView(this).apply { addView(content) }))
    }

    // ---- Connections: Wi-Fi, Bluetooth, flashlight ----

    @Suppress("DEPRECATION") // Device owners may still switch Wi-Fi and Bluetooth.
    private fun connectionsCard(): LinearLayout {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val bt = getSystemService(BluetoothManager::class.java)?.adapter
        return Theme.card(this,
            heading(R.string.connections),
            switchRow(getString(R.string.wifi), wifi.isWifiEnabled, open = { startActivity(Intent(this, WifiActivity::class.java)) }) {
                wifi.isWifiEnabled = it
            },
            divider(),
            switchRow(getString(R.string.bluetooth), bt?.isEnabled == true, open = { startActivity(Intent(this, BluetoothActivity::class.java)) }) {
                grantBluetooth(this)
                runCatching { if (it) bt?.enable() else bt?.disable() }
            },
            divider(),
            switchRow(getString(R.string.flashlight), torchOn) { on -> setTorch(on) },
        )
    }

    private fun setTorch(on: Boolean) {
        val cm = getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        runCatching { cm.setTorchMode(id, on); torchOn = on }
    }

    // ---- Appearance ----

    private fun appearanceCard(): LinearLayout {
        val modes = Looks.Mode.values()
        val iconStyles = listOf(Looks.Icons.KOSHER, Looks.Icons.GLASS, Looks.Icons.ORIGINAL)
        val glassSlider = slider(100, Looks.glass(this)) { Looks.setGlass(this, it.coerceAtLeast(1)) }.apply {
            visibility = if (Looks.glass(this@KosherSettingsActivity) > 0) View.VISIBLE else View.GONE
        }
        return Theme.card(this,
            heading(R.string.appearance),
            segmented(listOf(getString(R.string.mode_system), getString(R.string.mode_light), getString(R.string.mode_dark)),
                modes.indexOf(Looks.mode(this))) { i ->
                Looks.setMode(this, modes[i])
                recreate()
            },
            Theme.text(this, getString(R.string.dark_mode_hint), 13f, Theme.SUB).apply {
                setPadding(0, Theme.dp(context, 8), 0, 0)
            },
            label(R.string.background),
            sceneRow(),
            switchRow(getString(R.string.glass_effect), Looks.glass(this) > 0) { on ->
                Looks.setGlass(this, if (on) 60 else 0)
                glassSlider.progress = if (on) 60 else 0
                glassSlider.visibility = if (on) View.VISIBLE else View.GONE
            },
            glassSlider,
            label(R.string.icons),
            segmented(listOf(getString(R.string.icons_kosher), getString(R.string.icons_glass), getString(R.string.icons_original)),
                iconStyles.indexOf(Looks.icons(this))) { i -> Looks.setIcons(this, iconStyles[i]) },
            divider(),
            switchRow(getString(R.string.haptics), Looks.haptics(this)) { Looks.setHaptics(this, it) },
        )
    }

    // ---- Home & lock screen ----

    private fun homeCard() = Theme.card(this,
        heading(R.string.home_lock),
        label(R.string.clock_size),
        segmented(listOf(getString(R.string.size_large), getString(R.string.size_medium), getString(R.string.size_small)),
            Looks.clockSize(this)) { Looks.setClockSize(this, it) },
        label(R.string.clock_font),
        segmented(listOf(getString(R.string.font_thin), getString(R.string.font_bold), getString(R.string.font_classic)),
            Looks.clockFont(this)) { Looks.setClockFont(this, it) },
        switchRow(getString(R.string.show_date), Looks.showDate(this)) { Looks.setShowDate(this, it) },
        switchRow(getString(R.string.show_hebrew_date), Looks.showHebrewDate(this)) { Looks.setShowHebrewDate(this, it) },
        switchRow(getString(R.string.show_badge), Looks.showBadge(this)) { Looks.setShowBadge(this, it) },
        switchRow(getString(R.string.home_widget), Looks.widget(this)) { Looks.setWidget(this, it) },
        divider(),
        switchRow(getString(R.string.lock_message), Looks.lockMessage(this)) {
            Looks.setLockMessage(this, it)
            ModeManager.applyLockMessage(this)
        },
        Theme.text(this, getString(R.string.lock_note), 13f, Theme.SUB),
    )

    /** Round swatches for the home/wallpaper backgrounds. */
    private fun sceneRow() = LinearLayout(this).apply {
        setPadding(0, Theme.dp(context, 10), 0, Theme.dp(context, 6))
        val current = Looks.scene(context).key
        Looks.SCENES.forEach { scene ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(View(context).apply {
                    background = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(scene.top, scene.bottom),
                    ).apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        if (scene.key == current) setStroke(Theme.dp(context, 3), Theme.GOLD)
                    }
                }, LinearLayout.LayoutParams(Theme.dp(context, 46), Theme.dp(context, 46)))
                addView(Theme.text(context, scene.label, 12f, if (scene.key == current) Theme.INK else Theme.SUB).apply {
                    gravity = Gravity.CENTER
                })
                Theme.pressable(this)
                setOnClickListener {
                    Looks.setScene(context, scene.key)
                    // Kosher mode shows this background as the wallpaper too.
                    if (ModeManager.isClosed(context)) ModeManager.inBackground({ Wallpapers.applyKosher(context) })
                    recreate()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    // ---- Sound ----

    private fun soundCard(audio: AudioManager) = Theme.card(this,
        heading(R.string.sound),
        label(R.string.ringtone), volumeSlider(audio, AudioManager.STREAM_RING),
        label(R.string.media), volumeSlider(audio, AudioManager.STREAM_MUSIC),
        segmented(
            listOf(getString(R.string.mode_sound), getString(R.string.mode_vibrate)),
            if (audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE) 1 else 0,
        ) { i -> audio.ringerMode = if (i == 0) AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_VIBRATE },
    )

    // ---- Display ----

    private fun displayCard(): LinearLayout {
        val brightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val timeouts = listOf(30, 60, 120, 300, 600)
        val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000) / 1000
        return Theme.card(this,
            heading(R.string.display),
            label(R.string.brightness),
            slider(255, brightness) {
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE, "0")
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS, it.coerceAtLeast(5).toString())
            },
            label(R.string.screen_timeout),
            segmented(
                timeouts.map { if (it < 60) getString(R.string.seconds_short, it) else getString(R.string.minutes_short, it / 60) },
                timeouts.indexOfFirst { it >= current }.coerceAtLeast(0),
            ) { i -> dpm.setSystemSetting(admin, Settings.System.SCREEN_OFF_TIMEOUT, (timeouts[i] * 1000).toString()) },
        )
    }

    // ---- Language ----

    private fun languageCard(): LinearLayout {
        val names = listOf(getString(R.string.lang_system), "עברית", "English")
        return Theme.card(this,
            heading(R.string.language),
            segmented(names, Lang.CHOICES.indexOf(Lang.get(this)).coerceAtLeast(0)) { i ->
                if (Lang.CHOICES[i] != Lang.get(this)) {
                    Lang.set(this, Lang.CHOICES[i])
                    recreate()
                }
            },
        )
    }

    private fun aboutCard(): LinearLayout {
        val battery = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return Theme.card(this,
            heading(R.string.about),
            Theme.text(this, getString(R.string.battery, battery), 15f, Theme.SUB),
            Theme.text(this, "Android ${Build.VERSION.RELEASE} · ${Build.MODEL}", 15f, Theme.SUB),
        )
    }

    // ---- Pieces ----

    private fun heading(res: Int) = Theme.text(this, getString(res), 18f, Theme.INK, Theme.MEDIUM)

    private fun label(res: Int) = Theme.text(this, getString(res), 14f, Theme.SUB).apply {
        setPadding(0, Theme.dp(context, 12), 0, 0)
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(Theme.LINE)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(context, 1))
    }

    /** A row with a switch; tapping the name opens [open] (e.g. the network list). */
    private fun switchRow(name: String, on: Boolean, open: (() -> Unit)? = null, onChange: (Boolean) -> Unit) =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Theme.dp(context, 10), 0, Theme.dp(context, 10))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(Theme.text(context, name, 17f).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START })
                if (open != null) addView(Theme.text(context, getString(R.string.devices_or_networks), 13f, Theme.LINK)
                    .apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START })
                if (open != null) setOnClickListener { open() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(context).apply {
                isChecked = on
                thumbTintList = Theme.THUMB_TINT
                trackTintList = Theme.TRACK_TINT
                setOnCheckedChangeListener { _, v -> onChange(v) }
            })
        }

    /** iOS-style segmented control. */
    private fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val bar = LinearLayout(this).apply {
            background = Theme.rounded(context, Theme.CHIP, 16)
            val p = Theme.dp(context, 3)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 10) }
        }
        fun paint(sel: Int) {
            for (i in 0 until bar.childCount) {
                val t = bar.getChildAt(i) as TextView
                t.background = if (i == sel) Theme.rounded(this, Theme.CARD, 13) else null
                t.setTextColor(if (i == sel) Theme.HEADLINE else Theme.SUB)
            }
        }
        options.forEachIndexed { i, o ->
            bar.addView(Theme.text(this, o, 14f, Theme.SUB, Theme.MEDIUM).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, Theme.dp(context, 8), 0, Theme.dp(context, 8))
                setOnClickListener { paint(i); onSelect(i) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        paint(selected)
        return bar
    }

    private fun volumeSlider(audio: AudioManager, stream: Int) =
        slider(audio.getStreamMaxVolume(stream), audio.getStreamVolume(stream)) {
            audio.setStreamVolume(stream, it, AudioManager.FLAG_PLAY_SOUND)
        }

    private fun slider(max: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max
        progress = value
        progressTintList = Theme.BLUE_TINT
        thumbTintList = Theme.BLUE_TINT
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    companion object {
        /** Bluetooth needs runtime permissions on Android 12+; as device owner we grant them silently, once. */
        fun grantBluetooth(ctx: Context) {
            if (Build.VERSION.SDK_INT < 31) return
            val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
            if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                .forEach {
                    dpm.setPermissionGrantState(KosherAdmin.component(ctx), ctx.packageName, it,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                }
        }
    }
}
