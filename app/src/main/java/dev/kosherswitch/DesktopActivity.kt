package dev.kosherswitch

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * שולחן עבודה: the phone as a computer, like Samsung DeX. A wallpaper with desktop icons and
 * widgets, a taskbar with a start menu, pinned and open apps, and the clock. Works with touch or
 * a Bluetooth mouse and keyboard (hover, right-click, the Windows/Search key). Kosher mode shows
 * only the allowed apps.
 */
class DesktopActivity : BaseActivity() {
    override val glides = false

    private lateinit var root: FrameLayout
    private lateinit var desk: LinearLayout
    private lateinit var taskApps: LinearLayout
    private lateinit var startMenu: LinearLayout
    private lateinit var menuGrid: GridLayout
    private lateinit var search: EditText
    private lateinit var battery: TextView
    private var apps = listOf<AppCatalog.Entry>()
    private val opened = linkedSetOf<String>()
    private var popup: PopupWindow? = null
    private var cascade = 0

    private fun dp(v: Int) = Theme.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        root = FrameLayout(this)
        root.addView(Wallpaper(this))
        desk = LinearLayout(this).apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
        root.addView(desk, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            .apply { bottomMargin = dp(56) })
        root.addView(buildStartMenu(), FrameLayout.LayoutParams(minOf(dp(440), (resources.displayMetrics.widthPixels * 0.62f).toInt()),
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = dp(62); marginStart = dp(8)
        })
        root.addView(buildTaskbar(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(52), Gravity.BOTTOM))
        // Tapping the empty desktop closes the start menu.
        desk.setOnClickListener { showStart(false) }
        setContentView(root)
        intro()
    }

    override fun onResume() {
        super.onResume()
        apps = AppCatalog.load(this).filter { it.key != "desktop" }
        fillDesk()
        fillTaskApps()
        fillMenu("")
        updateBattery()
    }

    @Deprecated("Back closes the start menu first")
    override fun onBackPressed() {
        if (startMenu.visibility == View.VISIBLE) showStart(false) else super.onBackPressed()
    }

    /** Keyboard: the Windows/Search key opens the start menu; typing on the desktop starts a search. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_UP && (e.keyCode == KeyEvent.KEYCODE_META_LEFT || e.keyCode == KeyEvent.KEYCODE_META_RIGHT ||
                e.keyCode == KeyEvent.KEYCODE_SEARCH)) {
            showStart(startMenu.visibility != View.VISIBLE)
            return true
        }
        if (e.action == KeyEvent.ACTION_DOWN && startMenu.visibility != View.VISIBLE && e.isPrintingKey && !e.isCtrlPressed) {
            showStart(true)
            search.dispatchKeyEvent(e)
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    // ---- Desktop ----

    /** Icons down the left in columns (like a computer), widgets on the right. */
    private fun fillDesk() {
        desk.removeAllViews()
        val icons = GridLayout(this).apply {
            orientation = GridLayout.VERTICAL
            val usable = resources.displayMetrics.heightPixels - dp(56 + 32)
            rowCount = (usable / dp(92)).coerceAtLeast(1)
        }
        apps.forEach { a -> icons.addView(deskIcon(a), GridLayout.LayoutParams().apply { width = dp(84); height = dp(92) }) }
        desk.addView(icons, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        desk.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clockWidget())
            weatherWidget()?.let { addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) }) }
        }, LinearLayout.LayoutParams(dp(250), LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun deskIcon(a: AppCatalog.Entry) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(8), dp(4), dp(4))
        addView(ImageView(context).apply { setImageDrawable(a.icon) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(Theme.text(context, a.label, 12f, Color.WHITE, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
            setPadding(0, dp(4), 0, 0)
        })
        hoverable(this, 12)
        Theme.pressable(this)
        setOnClickListener { launch(a) }
        menuOn(this, a)
    }

    private fun clockWidget() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = glass(22)
        setPadding(dp(20), dp(16), dp(20), dp(18))
        addView(TextClock(context).apply {
            format24Hour = "HH:mm"; format12Hour = "HH:mm"
            textSize = 54f
            typeface = Theme.LIGHT
            setTextColor(Color.WHITE)
            includeFontPadding = false
        })
        addView(TextClock(context).apply {
            format24Hour = "EEEE, d MMMM"; format12Hour = "EEEE, d MMMM"
            textSize = 15f
            setTextColor(Theme.whiteAlpha(0.9f))
        })
        addView(Theme.text(context, hebrewDate(), 15f, Theme.GOLD, Theme.MEDIUM))
        hoverable(this, 22)
        setOnClickListener { launchKey("times") }
    }

    /** Last known weather of the chosen place, if the weather app has loaded it once. */
    private fun weatherWidget(): View? {
        if (!Looks.weatherAllowed(this)) return null
        val cities = Weather.cities(this)
        val i = Weather.selected(this)
        val city = if (i in cities.indices) cities[i] else Place.saved(this)?.let { Weather.City("", it.first, it.second) }
        val f = city?.let { Weather.cached(this, it) }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = glass(22)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            if (f == null) {
                addView(ImageView(context).apply { setImageDrawable(WeatherGlyph(Sky.PARTLY, true)) }, LinearLayout.LayoutParams(dp(40), dp(40)))
                addView(Theme.text(context, getString(R.string.weather), 17f, Color.WHITE, Theme.MEDIUM).apply { setPadding(dp(12), 0, 0, 0) })
            } else {
                val n = f.now
                addView(ImageView(context).apply { setImageDrawable(WeatherGlyph(Sky.of(n.code), n.day)) }, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                    addView(Theme.text(context, "${n.temp.roundToInt()}°  " + getString(Weather.describe(n.code, n.day).second), 18f, Color.WHITE, Theme.MEDIUM))
                    f.days.firstOrNull()?.let {
                        addView(Theme.text(context, getString(R.string.w_high_low, it.max.roundToInt(), it.min.roundToInt()), 13f, Theme.whiteAlpha(0.85f)))
                    }
                })
            }
            hoverable(this, 22)
            setOnClickListener { launchKey("weather") }
        }
    }

    // ---- Taskbar ----

    private fun buildTaskbar() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC0E1C38.toInt(), 0xE60A1428.toInt()))
        setPadding(dp(8), 0, dp(12), 0)
        // Start button: the כשר emblem.
        addView(FrameLayout(context).apply {
            addView(EmblemView(context), FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
            hoverable(this, 10)
            Theme.pressable(this)
            setOnClickListener { showStart(startMenu.visibility != View.VISIBLE) }
            contentDescription = getString(R.string.desktop_start)
        }, LinearLayout.LayoutParams(dp(48), dp(44)))
        addView(Theme.text(context, "⌕  " + getString(R.string.desktop_search), 14f, Theme.whiteAlpha(0.8f)).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = Theme.rounded(context, Theme.whiteAlpha(0.1f), 18, Theme.whiteAlpha(0.12f))
            hoverable(this, 18)
            setOnClickListener { showStart(true); focusSearch() }
        }, LinearLayout.LayoutParams(dp(180), dp(36)).apply { marginStart = dp(6); marginEnd = dp(10) })
        taskApps = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        addView(taskApps, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        // Tray: battery, date and time.
        battery = Theme.text(context, "", 13f, Color.WHITE, Theme.MEDIUM).apply { setPadding(dp(8), 0, dp(12), 0) }
        addView(battery)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(TextClock(context).apply { format24Hour = "HH:mm"; format12Hour = "HH:mm"; textSize = 14f; setTextColor(Color.WHITE); typeface = Theme.MEDIUM })
            addView(TextClock(context).apply { format24Hour = "d.M.yyyy"; format12Hour = "d.M.yyyy"; textSize = 11f; setTextColor(Theme.whiteAlpha(0.8f)) })
            setPadding(dp(8), dp(4), dp(8), dp(4))
            hoverable(this, 8)
            setOnClickListener { launchKey("times") }
        })
    }

    /** Pinned apps, then apps opened from the desktop this session (with a small dot). */
    private fun fillTaskApps() {
        taskApps.removeAllViews()
        val keys = (pins() + opened).distinct()
        keys.mapNotNull { k -> apps.firstOrNull { it.key == k } }.forEach { a ->
            taskApps.addView(FrameLayout(this).apply {
                addView(ImageView(context).apply { setImageDrawable(a.icon) }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
                if (a.key in opened) addView(View(context).apply { background = Theme.rounded(context, Theme.GOLD, 2) },
                    FrameLayout.LayoutParams(dp(14), dp(3), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(3) })
                contentDescription = a.label
                tooltipText = a.label
                hoverable(this, 10)
                Theme.pressable(this)
                setOnClickListener { launch(a) }
                menuOn(this, a)
            }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(2) })
        }
    }

    private fun updateBattery() {
        val level = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        battery.text = "▮ $level%"
    }

    // ---- Start menu ----

    private fun buildStartMenu(): View {
        search = EditText(this).apply {
            hint = getString(R.string.desktop_search)
            setSingleLine()
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Theme.whiteAlpha(0.6f))
            background = Theme.rounded(context, Theme.whiteAlpha(0.12f), 14, Theme.whiteAlpha(0.18f))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = fillMenu(s.toString())
            })
            setOnEditorActionListener { _, _, _ ->
                apps.firstOrNull { it.label.contains(text.toString().trim(), ignoreCase = true) }?.let(::launch)
                true
            }
        }
        menuGrid = GridLayout(this).apply { columnCount = 5 }
        startMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Theme.rounded(context, 0xEE0D1A33.toInt(), 22, Theme.whiteAlpha(0.14f))
            elevation = dp(16).toFloat()
            setPadding(dp(18), dp(18), dp(18), dp(12))
            visibility = View.GONE
            isClickable = true // taps inside don't fall through to the desktop
            addView(search)
            addView(Theme.text(context, getString(R.string.desktop_all_apps), 13f, Theme.whiteAlpha(0.7f), Theme.MEDIUM).apply {
                setPadding(dp(4), dp(14), 0, dp(6))
            })
            addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(menuGrid)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                minOf(dp(300), resources.displayMetrics.heightPixels - dp(62 + 160))))
            addView(View(context).apply { setBackgroundColor(Theme.whiteAlpha(0.12f)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { topMargin = dp(8) })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
                addView(EmblemView(context), LinearLayout.LayoutParams(dp(26), dp(26)))
                addView(Theme.text(context, getString(if (ModeManager.isClosed(context)) R.string.kosher_device else R.string.app_name), 14f,
                    Color.WHITE, Theme.MEDIUM).apply { setPadding(dp(10), 0, 0, 0) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Theme.text(context, "⏻  " + getString(R.string.desktop_exit), 14f, Color.WHITE, Theme.MEDIUM).apply {
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    hoverable(this, 12)
                    Theme.pressable(this)
                    setOnClickListener { finish() }
                })
            })
        }
        return startMenu
    }

    private fun fillMenu(query: String) {
        menuGrid.removeAllViews()
        val q = query.trim()
        val cell = (minOf(dp(440), (resources.displayMetrics.widthPixels * 0.62f).toInt()) - dp(36)) / 5
        apps.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }.forEach { a ->
            menuGrid.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(8), dp(2), dp(6))
                addView(ImageView(context).apply { setImageDrawable(a.icon) }, LinearLayout.LayoutParams(dp(40), dp(40)))
                addView(Theme.text(context, a.label, 11f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
                hoverable(this, 12)
                Theme.pressable(this)
                setOnClickListener { launch(a) }
                menuOn(this, a)
            }, GridLayout.LayoutParams().apply { width = cell; height = LinearLayout.LayoutParams.WRAP_CONTENT })
        }
    }

    private fun showStart(show: Boolean) {
        if (show == (startMenu.visibility == View.VISIBLE)) return
        if (show) {
            search.setText("")
            startMenu.visibility = View.VISIBLE
            startMenu.alpha = 0f
            startMenu.translationY = dp(40).toFloat()
            startMenu.scaleX = 0.96f; startMenu.scaleY = 0.96f
            startMenu.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Motion.EASE).start()
        } else {
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken, 0)
            startMenu.animate().alpha(0f).translationY(dp(30).toFloat()).setDuration(170).setInterpolator(Motion.EASE)
                .withEndAction { startMenu.visibility = View.GONE }.start()
        }
    }

    private fun focusSearch() {
        search.requestFocus()
        search.post { getSystemService(InputMethodManager::class.java).showSoftInput(search, 0) }
    }

    // ---- Opening apps ----

    private fun launchKey(key: String) = apps.firstOrNull { it.key == key }?.let(::launch)

    /**
     * Opens an app. Where the phone supports free-floating windows, it opens as a window
     * (cascading like on a computer); otherwise full screen, and Back returns to the desktop.
     */
    private fun launch(a: AppCatalog.Entry) {
        showStart(false)
        opened += a.key
        fillTaskApps()
        val intent = Intent(a.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
        if (freeform()) {
            val w = resources.displayMetrics.widthPixels
            val h = resources.displayMetrics.heightPixels - dp(56)
            val step = dp(32) * (cascade++ % 5)
            options.launchBounds = Rect(w / 6 + step, h / 10 + step, w / 6 + step + w * 2 / 3, h / 10 + step + h * 4 / 5)
            runCatching { ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(options, 5) }
        }
        runCatching { startActivity(intent, options.toBundle()) }.onFailure { runCatching { startActivity(intent) } }
    }

    private fun freeform() = packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
        Settings.Global.getInt(contentResolver, "enable_freeform_support", 0) == 1

    // ---- Pins and the right-click menu ----

    private fun pins(): List<String> = prefs(this).getString("desktop_pins", null)?.split('|')?.filter { it.isNotEmpty() }
        ?: (Looks.dock(this) ?: ClosedHomeActivity.DEFAULT_DOCK).mapNotNull { k -> apps.firstOrNull { it.key == k || it.key.contains(k) }?.key }

    private fun setPins(keys: List<String>) = prefs(this).edit().putString("desktop_pins", keys.joinToString("|")).apply()

    /** Right-click with a mouse, or long-press with a finger: open, pin or unpin. */
    private fun menuOn(v: View, a: AppCatalog.Entry) {
        val show = { _: View ->
            Theme.haptic(v, strong = true)
            val pinned = a.key in pins()
            popupMenu(v, listOf(getString(R.string.desktop_open) to { launch(a) },
                getString(if (pinned) R.string.desktop_unpin else R.string.desktop_pin) to {
                    setPins(if (pinned) pins() - a.key else pins() + a.key)
                    fillTaskApps()
                }))
            true
        }
        v.setOnLongClickListener(show)
        v.setOnContextClickListener(show)
    }

    private fun popupMenu(anchor: View, items: List<Pair<String, () -> Unit>>) {
        popup?.dismiss()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Theme.rounded(context, 0xF20D1A33.toInt(), 14, Theme.whiteAlpha(0.16f))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val window = PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(12).toFloat()
            animationStyle = android.R.style.Animation_Dialog
        }
        items.forEach { (label, action) ->
            list.addView(Theme.text(this, label, 14f, Color.WHITE, Theme.MEDIUM).apply {
                setPadding(dp(14), dp(10), dp(24), dp(10))
                hoverable(this, 10)
                setOnClickListener { window.dismiss(); action() }
            }, LinearLayout.LayoutParams(dp(170), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        popup = window
        window.showAsDropDown(anchor, dp(20), -anchor.height / 2)
    }

    // ---- Look and motion ----

    private fun glass(radius: Int) = Theme.rounded(this, 0x59101C33, radius, Theme.whiteAlpha(0.16f))

    /** A soft highlight under the mouse pointer, like on a computer. */
    private fun hoverable(v: View, radius: Int) {
        val base = v.background
        val hover = Theme.rounded(this, Theme.whiteAlpha(0.16f), radius)
        v.setOnHoverListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> view.background = if (base == null) hover
                    else android.graphics.drawable.LayerDrawable(arrayOf(base, hover))
                MotionEvent.ACTION_HOVER_EXIT -> view.background = base
            }
            false
        }
    }

    /** The desktop builds in: wallpaper fades, icons drop in column by column, the taskbar slides up. */
    private fun intro() {
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(300).start()
        val bar = root.getChildAt(root.childCount - 1)
        bar.translationY = dp(56).toFloat()
        bar.animate().translationY(0f).setStartDelay(120).setDuration(420).setInterpolator(Motion.EASE).start()
        desk.translationY = dp(24).toFloat()
        desk.alpha = 0f
        desk.animate().translationY(0f).alpha(1f).setStartDelay(200).setDuration(480).setInterpolator(Motion.EASE).start()
    }

    /** The kosher background, laid out wide, with the emblem faintly in the middle. */
    private class Wallpaper(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val scene = Looks.scene(ctx)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            p.color = Color.WHITE
            p.shader = LinearGradient(0f, 0f, w, h, scene.top, scene.bottom, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
            for ((i, g) in listOf(Triple(0.9f, 0.1f, 0.6f), Triple(0.08f, 0.7f, 0.7f), Triple(0.6f, 1.05f, 0.55f)).withIndex()) {
                p.shader = RadialGradient(w * g.first, h * g.second, w * g.third, scene.glows[i], Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawCircle(w * g.first, h * g.second, w * g.third, p)
            }
            p.shader = null
            c.saveLayerAlpha(0f, 0f, w, h, 40)
            EmblemView.drawEmblem(c, w * 0.5f, h * 0.45f, h * 0.16f)
            c.restore()
        }
    }
}
