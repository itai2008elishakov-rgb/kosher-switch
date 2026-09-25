package dev.kosherswitch

import android.animation.ArgbEvaluator
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * מזג אוויר: live weather for the phone's location and saved cities (Open-Meteo), over a living
 * sky that shows the weather. Swipe left or right (or press the side keys) to change city.
 */
class WeatherActivity : BaseActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var scene: WeatherScene
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var chips: LinearLayout
    private lateinit var chipScroll: HorizontalScrollView
    private var hourlyStrip: View? = null
    private var city: Weather.City? = null
    private var forecast: Weather.Forecast? = null
    private var status: String? = null

    private val lang get() = if (resources.configuration.locales[0].language in listOf("iw", "he")) "he" else "en"
    private val rtl get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private fun dp(v: Int) = Theme.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setDecorFitsSystemWindows(false)
        scene = WeatherScene(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(28))
        }
        chips = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            addView(KosherPage.barButton(this@WeatherActivity, if (rtl) "→" else "←") { finish() })
            addView(chipScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(KosherPage.barButton(this@WeatherActivity, "+") { searchDialog() })
        }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(24))
            addView(content)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(top)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF1F4F8F.toInt()))
        setContentView(FrameLayout(this).apply {
            addView(scene)
            addView(column)
        })
        paintSky(null)
        selectPlace(Weather.selected(this))
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    // ---- Places ----

    /** -1 is the phone's location, then the saved cities, in order. */
    private fun pages() = listOf(-1) + Weather.cities(this).indices

    private fun fillChips() {
        chips.removeAllViews()
        val selected = Weather.selected(this)
        fun chip(label: String, index: Int) = Theme.text(this, label, 14f, Color.WHITE, Theme.MEDIUM).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = Theme.rounded(context, if (index == selected) Theme.whiteAlpha(0.32f) else Theme.blackAlpha(0.14f), 18)
            Theme.pressable(this)
            setOnClickListener { if (index != Weather.selected(context)) go(index, if (index > Weather.selected(context)) 1 else -1) }
            if (index >= 0) setOnLongClickListener {
                Theme.haptic(it, strong = true)
                val c = Weather.cities(context)[index]
                KosherPage.sheet(this@WeatherActivity, c.name, listOf(getString(R.string.w_remove), getString(R.string.cancel))) { pick ->
                    if (pick == 0) {
                        Weather.setCities(context, Weather.cities(context).filterIndexed { i, _ -> i != index })
                        selectPlace(-1)
                    }
                }
                true
            }
        }
        chips.addView(chip("➤  " + getString(R.string.w_my_location), -1))
        Weather.cities(this).forEachIndexed { i, c ->
            chips.addView(chip(c.name, i), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(8) })
        }
        // Keep the chosen city's chip in view.
        chips.post {
            chips.getChildAt(selected + 1)?.let { chipScroll.smoothScrollTo((it.left - dp(40)).coerceAtLeast(0), 0) }
        }
    }

    private fun selectPlace(index: Int) {
        val cities = Weather.cities(this)
        val i = if (index in cities.indices) index else -1
        Weather.setSelected(this, i)
        fillChips()
        if (i >= 0) show(cities[i]) else {
            val here = Place.saved(this)
            if (here != null) show(hereCity(here))
            else {
                city = null; forecast = null; status = getString(R.string.w_finding); render()
                // No location fix yet (indoors, or a phone without network location): use the rough
                // location of the internet connection until the real one arrives.
                main.postDelayed({
                    if (city != null || isDestroyed || Weather.selected(this) != -1) return@postDelayed
                    worker.execute {
                        val rough = Weather.approximate(lang)
                        main.post {
                            if (city != null || isDestroyed || Weather.selected(this) != -1) return@post
                            if (rough != null) show(rough) else { status = getString(R.string.w_no_data); render() }
                        }
                    }
                }, 4000)
            }
            Place.refresh(this) {
                val now = Place.saved(this) ?: return@refresh
                if (Weather.selected(this) == -1 && hereCity(now).key != city?.key) show(hereCity(now))
            }
        }
    }

    private fun hereCity(p: Pair<Double, Double>): Weather.City {
        val c = Weather.City(getString(R.string.w_my_location), p.first, p.second)
        return c.copy(name = Weather.hereName(this, c.key + lang) ?: c.name)
    }

    /** Shows the saved forecast right away, then loads a fresh one. */
    private fun show(c: Weather.City) {
        city = c
        forecast = Weather.cached(this, c)
        status = getString(R.string.w_loading)
        render()
        load(c)
    }

    private fun load(c: Weather.City) {
        worker.execute {
            val fresh = runCatching { Weather.fetch(this, c) }
            var named = c
            if (Weather.selected(this) == -1 && c.name == getString(R.string.w_my_location)) {
                Weather.placeName(c.lat, c.lon, lang)?.let {
                    Weather.setHereName(this, c.key + lang, it)
                    named = c.copy(name = it)
                }
            }
            main.post {
                if (isDestroyed || city?.key != c.key) return@post
                city = named
                fresh.onSuccess { forecast = it; status = null }
                    .onFailure { status = getString(if (forecast == null) R.string.w_no_data else R.string.w_offline) }
                render()
            }
        }
    }

    // ---- Changing city: swipe, keys or chips ----

    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private var blocked = false
    private var tracker: VelocityTracker? = null

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; swiping = false
                // The hourly strip scrolls sideways itself.
                blocked = hourlyStrip?.let { v -> Rect().also { v.getGlobalVisibleRect(it) }.contains(e.rawX.toInt(), e.rawY.toInt()) } ?: false
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
            }
        }
        tracker?.addMovement(e)
        val dx = e.rawX - downX
        val dy = e.rawY - downY
        if (!swiping && !blocked && e.actionMasked == MotionEvent.ACTION_MOVE &&
            abs(dx) > ViewConfiguration.get(this).scaledTouchSlop * 2 && abs(dx) > abs(dy) * 1.6f && pages().size > 1) {
            swiping = true
            super.dispatchTouchEvent(MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL })
        }
        if (!swiping) return super.dispatchTouchEvent(e)
        val w = scroll.width.toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                scroll.translationX = dx * 0.85f
                scroll.alpha = 1f - (abs(dx) / w).coerceAtMost(1f) * 0.6f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                val dir = if (dx < 0) 1 else -1 // finger to the left: the next city
                val list = pages()
                val next = list.getOrNull(list.indexOf(Weather.selected(this)) + dir)
                if (next != null && (abs(dx) > w * 0.25f || abs(vx) > 900 * resources.displayMetrics.density)) go(next, dir)
                else scroll.animate().translationX(0f).alpha(1f).setDuration(260).setInterpolator(Motion.EASE).start()
                swiping = false
            }
        }
        return true
    }

    /** Keypad phones: the side keys change city too. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val side = e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        val inChips = currentFocus?.parent == chips
        if (side && !inChips) {
            if (e.action == KeyEvent.ACTION_DOWN) {
                val dir = if ((e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl) 1 else -1
                val list = pages()
                list.getOrNull(list.indexOf(Weather.selected(this)) + dir)?.let { go(it, dir) }
            }
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    /** Slides the current city away and the next one in. */
    private fun go(index: Int, dir: Int) {
        val w = scroll.width.toFloat().coerceAtLeast(1f)
        Theme.haptic(scroll)
        scroll.animate().translationX(-dir * w * 0.6f).alpha(0f).setDuration(170).setInterpolator(Motion.EASE).withEndAction {
            selectPlace(index)
            scroll.scrollTo(0, 0)
            scroll.translationX = dir * w * 0.6f
            scroll.animate().translationX(0f).alpha(1f).setDuration(320).setInterpolator(Motion.EASE).start()
        }.start()
    }

    // ---- Screen ----

    private fun shade(v: TextView) = v.apply { setShadowLayer(8f, 0f, 1f, 0x40000000) }

    private fun render() {
        content.removeAllViews()
        hourlyStrip = null
        val f = forecast
        paintSky(f?.now)
        val c = city
        if (Weather.selected(this) == -1) content.addView(Theme.text(this, "➤  " + getString(R.string.w_my_location), 13f,
            Theme.whiteAlpha(0.85f), Theme.MEDIUM).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }, full())
        content.addView(shade(Theme.text(this, c?.name ?: "", 32f, Color.WHITE, Theme.REGULAR).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }), full())
        if (f == null) {
            content.addView(shade(Theme.text(this, status ?: "", 17f, Theme.whiteAlpha(0.9f)).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(60), 0, dp(20))
            }), full())
            if (status == getString(R.string.w_no_data)) content.addView(retryButton(), full())
            return
        }
        val n = f.now
        val today = f.days.firstOrNull()
        val (_, condition) = Weather.describe(n.code, n.day)
        content.addView(shade(Theme.text(this, " ${n.temp.roundToInt()}°", 96f, Color.WHITE, Theme.LIGHT).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
        }), full())
        content.addView(shade(Theme.text(this, getString(condition), 21f, Color.WHITE, Theme.MEDIUM).apply { gravity = Gravity.CENTER }), full())
        if (today != null) content.addView(shade(Theme.text(this,
            getString(R.string.w_high_low, today.max.roundToInt(), today.min.roundToInt()), 17f, Color.WHITE, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
        }), full())
        val age = System.currentTimeMillis() - f.fetched
        val updated = status ?: if (age < 60_000) getString(R.string.w_just_now) else getString(R.string.w_updated,
            DateUtils.getRelativeTimeSpanString(f.fetched, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
        content.addView(shade(Theme.text(this, "$updated  ↻", 12f, Theme.whiteAlpha(0.8f)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(14))
            Theme.pressable(this)
            setOnClickListener { city?.let { status = getString(R.string.w_loading); render(); load(it) } }
        }), full())

        content.addView(section(getString(R.string.w_hourly), hourly(f)), spaced())
        content.addView(section(getString(R.string.w_daily), daily(f)), spaced())
        content.addView(details(f), spaced())
        content.addView(Theme.text(this, getString(R.string.w_source), 11f, Theme.whiteAlpha(0.6f)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }, full())
        // Cards float up one after another.
        for (i in 0 until content.childCount) {
            val v = content.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(18).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 35L).setDuration(380).setInterpolator(Motion.EASE).start()
        }
    }

    private fun retryButton() = Theme.text(this, "↻  " + getString(R.string.w_retry), 16f, Color.WHITE, Theme.MEDIUM).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
        background = Theme.rounded(context, Theme.whiteAlpha(0.22f), 22)
        Theme.pressable(this)
        setOnClickListener { selectPlace(Weather.selected(context)) }
    }

    private fun glyph(d: Drawable, size: Int) = ImageView(this).apply {
        setImageDrawable(d)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun hourly(f: Weather.Forecast): View {
        val now = f.localNow().withMinute(0).withSecond(0).withNano(0)
        val row = LinearLayout(this)
        val clock = DateTimeFormatter.ofPattern("HH:mm")
        f.hours.filter { !it.time.isBefore(now) }.take(24).forEachIndexed { i, h ->
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(Theme.text(context, if (i == 0) getString(R.string.w_now) else h.time.format(clock), 14f, Color.WHITE, Theme.MEDIUM).apply {
                    maxLines = 1
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(glyph(WeatherGlyph(Sky.of(h.code), h.day), 34).apply {
                    (layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
                })
                addView(Theme.text(context, if (h.rain >= 20) "${h.rain}%" else " ", 12f, 0xFFA8DCFF.toInt(), Theme.MEDIUM))
                addView(Theme.text(context, "${h.temp.roundToInt()}°", 19f, Color.WHITE, Theme.MEDIUM))
            }, LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            hourlyStrip = this
        }
    }

    private fun daily(f: Weather.Forecast): View {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val lo = f.days.minOfOrNull { it.min } ?: 0.0
        val hi = f.days.maxOfOrNull { it.max } ?: 1.0
        val locale = resources.configuration.locales[0]
        f.days.forEachIndexed { i, d ->
            if (i > 0) list.addView(View(this).apply { setBackgroundColor(Theme.whiteAlpha(0.16f)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            list.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                val name = if (d.date == f.localNow().toLocalDate()) getString(R.string.w_today)
                    else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                addView(Theme.text(context, name, 17f, Color.WHITE, Theme.MEDIUM),
                    LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(glyph(WeatherGlyph(Sky.of(d.code), true), 28))
                    if (d.rain >= 20) addView(Theme.text(context, "${d.rain}%", 11f, 0xFFA8DCFF.toInt(), Theme.MEDIUM))
                }, LinearLayout.LayoutParams(dp(42), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(Theme.text(context, "${d.min.roundToInt()}°", 17f, Theme.whiteAlpha(0.7f), Theme.MEDIUM).apply {
                    gravity = Gravity.END
                }, LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(RangeBar(context, d.min, d.max, lo, hi, if (i == 0) f.now.temp else null),
                    LinearLayout.LayoutParams(0, dp(6), 1f).apply { marginStart = dp(10); marginEnd = dp(10) })
                addView(Theme.text(context, "${d.max.roundToInt()}°", 17f, Color.WHITE, Theme.MEDIUM),
                    LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT))
            })
        }
        return list
    }

    /** Two columns of cards, like the weather app on an iPhone. */
    private fun details(f: Weather.Forecast): View {
        val n = f.now
        val d = f.days.firstOrNull()
        val clock = DateTimeFormatter.ofPattern("HH:mm")
        val uv = d?.uv ?: n.uv
        data class Tile(val icon: DetailGlyph.Kind, val title: Int, val value: String, val sub: String)
        val tiles = listOfNotNull(
            Tile(DetailGlyph.Kind.FEELS, R.string.w_feels, "${n.feels.roundToInt()}°", feelsNote(n)),
            Tile(DetailGlyph.Kind.HUMIDITY, R.string.w_humidity, "${n.humidity}%", ""),
            Tile(DetailGlyph.Kind.WIND, R.string.w_wind, "${n.wind.roundToInt()}", getString(R.string.w_kmh) + " · " + compass(n.windDir)),
            Tile(DetailGlyph.Kind.UV, R.string.w_uv, "${uv.roundToInt()}", uvLevel(uv)),
            d?.sunrise?.let { Tile(DetailGlyph.Kind.SUNRISE, R.string.w_sunrise, it.format(clock), "") },
            d?.sunset?.let { Tile(DetailGlyph.Kind.SUNSET, R.string.w_sunset, it.format(clock), "") },
            d?.let { Tile(DetailGlyph.Kind.RAIN, R.string.w_rain_today, "%.1f".format(it.rainMm), getString(R.string.w_mm)) },
            if (n.pressure > 0) Tile(DetailGlyph.Kind.PRESSURE, R.string.w_pressure, "${n.pressure.roundToInt()}", "hPa") else null,
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tiles.chunked(2).forEachIndexed { r, pair ->
                addView(LinearLayout(context).apply {
                    pair.forEachIndexed { i, t ->
                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            background = glassCard()
                            setPadding(dp(16), dp(14), dp(16), dp(14))
                            minimumHeight = dp(128)
                            addView(LinearLayout(context).apply {
                                gravity = Gravity.CENTER_VERTICAL
                                addView(glyph(DetailGlyph(t.icon), 16).apply { alpha = 0.8f })
                                addView(Theme.text(context, getString(t.title).uppercase(), 13f, Theme.whiteAlpha(0.8f), Theme.MEDIUM).apply {
                                    setPadding(dp(6), 0, 0, 0)
                                    maxLines = 1
                                })
                            })
                            addView(shade(Theme.text(context, t.value, 38f, Color.WHITE, Theme.REGULAR)).apply { setPadding(0, dp(8), 0, 0) })
                            if (t.sub.isNotEmpty()) addView(Theme.text(context, t.sub, 14f, Theme.whiteAlpha(0.9f), Theme.MEDIUM))
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                            if (i == 1) marginStart = dp(12)
                        })
                    }
                    if (pair.size == 1) addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(12) })
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (r > 0) topMargin = dp(12)
                })
            }
        }
    }

    private fun feelsNote(n: Weather.Now) = getString(when {
        n.feels < n.temp - 1.5 -> R.string.w_feels_colder
        n.feels > n.temp + 1.5 -> R.string.w_feels_warmer
        else -> R.string.w_feels_same
    })

    private fun compass(deg: Int): String {
        val he = listOf("צפון", "צפון-מזרח", "מזרח", "דרום-מזרח", "דרום", "דרום-מערב", "מערב", "צפון-מערב")
        val en = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val i = ((deg % 360 + 360) % 360 + 22) / 45 % 8
        return if (lang == "he") he[i] else en[i]
    }

    private fun uvLevel(uv: Double) = getString(when {
        uv < 3 -> R.string.w_uv_low
        uv < 6 -> R.string.w_uv_moderate
        uv < 8 -> R.string.w_uv_high
        else -> R.string.w_uv_very_high
    })

    private fun section(title: String, body: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = glassCard()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(Theme.text(context, title.uppercase(), 13f, Theme.whiteAlpha(0.8f), Theme.MEDIUM).apply {
            setPadding(0, 0, 0, dp(8))
        })
        addView(View(context).apply { setBackgroundColor(Theme.whiteAlpha(0.18f)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { bottomMargin = dp(10) })
        addView(body)
    }

    /** Darker glass than before, so white text stands out on bright skies too. */
    private fun glassCard() = Theme.rounded(this, Theme.blackAlpha(0.2f), 22).apply {
        setStroke(dp(1), Theme.whiteAlpha(0.16f))
    }

    private fun full() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun spaced() = full().apply { topMargin = dp(12) }

    /** The sky follows the weather: clear, cloudy, rain, storm, snow or fog, by day or night. */
    private fun paintSky(n: Weather.Now?) {
        val day = n?.day ?: true
        val colors = when (n?.code) {
            null -> intArrayOf(0xFF2E6FC4.toInt(), 0xFF6FA8E6.toInt())
            0, 1 -> if (day) intArrayOf(0xFF1F6FD0.toInt(), 0xFF6DB4F2.toInt()) else intArrayOf(0xFF070F26.toInt(), 0xFF1E3263.toInt())
            2 -> if (day) intArrayOf(0xFF3D72AE.toInt(), 0xFF8BB1D8.toInt()) else intArrayOf(0xFF111A2E.toInt(), 0xFF2F3E5B.toInt())
            3, 45, 48 -> if (day) intArrayOf(0xFF566B80.toInt(), 0xFF98A8B8.toInt()) else intArrayOf(0xFF1A212D.toInt(), 0xFF394251.toInt())
            in 71..77, 85, 86 -> intArrayOf(0xFF62809E.toInt(), 0xFFB9C9DA.toInt())
            in 95..99 -> intArrayOf(0xFF1D2133.toInt(), 0xFF424663.toInt())
            else -> if (day) intArrayOf(0xFF364A61.toInt(), 0xFF6B7F95.toInt()) else intArrayOf(0xFF121925.toInt(), 0xFF2E394A.toInt())
        }
        scene.set(if (n == null) Sky.PARTLY else Sky.of(n.code), day, colors[0], colors[1])
    }

    // ---- City search ----

    private fun searchDialog() {
        val dialog = Dialog(this)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val input = EditText(this).apply {
            hint = getString(R.string.w_search_hint)
            textSize = 18f
            setSingleLine()
            setTextColor(Theme.INK)
            setHintTextColor(Theme.SUB)
            background = Theme.rounded(context, Theme.CHIP, 16)
            val p = Theme.dp(context, 14)
            setPadding(p, p, p, p)
        }
        var pending: Runnable? = null
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                pending?.let(main::removeCallbacks)
                val q = s.toString().trim()
                if (q.length < 2) { results.removeAllViews(); return }
                pending = Runnable {
                    worker.execute {
                        val found = runCatching { Weather.search(q, lang) }
                        main.post {
                            if (!dialog.isShowing || input.text.toString().trim() != q) return@post
                            results.removeAllViews()
                            val list = found.getOrNull()
                            if (list.isNullOrEmpty()) {
                                results.addView(Theme.text(this@WeatherActivity,
                                    getString(if (list == null) R.string.w_no_data else R.string.w_not_found), 15f, Theme.SUB).apply {
                                    setPadding(0, Theme.dp(context, 14), 0, 0)
                                })
                            }
                            list?.forEach { c ->
                                results.addView(LinearLayout(this@WeatherActivity).apply {
                                    orientation = LinearLayout.VERTICAL
                                    val p = Theme.dp(context, 12)
                                    setPadding(Theme.dp(context, 6), p, Theme.dp(context, 6), p)
                                    addView(Theme.text(context, c.name, 17f, Theme.INK, Theme.MEDIUM))
                                    if (c.area.isNotEmpty()) addView(Theme.text(context, c.area, 13f, Theme.SUB))
                                    Theme.pressable(this)
                                    setOnClickListener {
                                        val cities = Weather.cities(context).filter { it.key != c.key } + c
                                        Weather.setCities(context, cities)
                                        dialog.dismiss()
                                        selectPlace(cities.size - 1)
                                    }
                                })
                            }
                        }
                    }
                }.also { main.postDelayed(it, 400) }
            }
        })
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            background = Theme.rounded(context, Theme.CARD, 26)
            val p = Theme.dp(context, 18)
            setPadding(p, p, p, p)
            addView(Theme.text(context, getString(R.string.w_add_city), 20f, Theme.INK, Theme.MEDIUM).apply {
                setPadding(0, 0, 0, Theme.dp(context, 12))
            })
            addView(input)
            addView(ScrollView(context).apply { addView(results) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(context, 260)))
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(body)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.TOP)
            attributes = attributes.apply { y = Theme.dp(this@WeatherActivity, 40) }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
        input.requestFocus()
    }

    /** A day's low-to-high range on the week's scale, colored from cold blue to hot orange. */
    private class RangeBar(
        ctx: Context, val lo: Double, val hi: Double, val weekLo: Double, val weekHi: Double, val now: Double?,
    ) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val span = (weekHi - weekLo).coerceAtLeast(1.0)
            fun x(t: Double) = ((t - weekLo) / span * w).toFloat().let { if (layoutDirection == LAYOUT_DIRECTION_RTL) w - it else it }
            p.shader = null
            p.color = 0x40000000
            c.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, p)
            p.color = Color.WHITE // full strength for the colored part
            val a = x(lo)
            val b = x(hi)
            p.shader = LinearGradient(a, 0f, b, 0f, color(lo), color(hi), Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(minOf(a, b), 0f, maxOf(a, b), h), h / 2, h / 2, p)
            p.shader = null
            if (now != null) {
                p.color = Color.WHITE
                p.setShadowLayer(2f, 0f, 0f, 0x66000000)
                c.drawCircle(x(now.coerceIn(lo, hi)), h / 2, h * 0.75f, p)
                p.clearShadowLayer()
            }
        }

        private fun color(t: Double): Int {
            val stops = listOf(-5.0 to 0xFF5E9BFF.toInt(), 10.0 to 0xFF55CFE3.toInt(), 18.0 to 0xFF8BDF7A.toInt(),
                25.0 to 0xFFFFD35A.toInt(), 32.0 to 0xFFFF9A3C.toInt(), 40.0 to 0xFFFF5B45.toInt())
            if (t <= stops.first().first) return stops.first().second
            val i = stops.indexOfFirst { it.first >= t }.takeIf { it > 0 } ?: return stops.last().second
            val (t0, c0) = stops[i - 1]
            val (t1, c1) = stops[i]
            return ArgbEvaluator().evaluate(((t - t0) / (t1 - t0)).toFloat(), c0, c1) as Int
        }
    }
}
