package dev.kosherswitch

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The kosher-mode home screen: a big clock, an optional glass widget and a 3-app dock.
 * Swiping up pulls in a frosted-glass drawer with every allowed app; hold an app to drag
 * it to a new spot. Hold a dock icon to choose a different app for that spot.
 */
class ClosedHomeActivity : BaseActivity() {
    private companion object {
        const val COLUMNS = 3
        val DEFAULT_DOCK = listOf("dialer", "messag", "siddur")
    }

    private class App(val label: String, val icon: Drawable, val key: String, val open: () -> Unit)

    private lateinit var root: DrawerFrame
    private lateinit var sharp: ImageView
    private lateinit var blur: ImageView
    private lateinit var home: LinearLayout
    private lateinit var widget: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerGrid: LinearLayout
    private lateinit var hebrew: TextView
    private lateinit var clock: TextClock
    private lateinit var date: TextClock
    private lateinit var hint: TextView
    private var iconsShown = false
    private lateinit var badge: TextView
    private lateinit var askPill: TextView
    private var apps: List<App> = emptyList()
    private var shownState: String? = null
    private var animator: ValueAnimator? = null
    private var passedHalf = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setDecorFitsSystemWindows(false)

        root = DrawerFrame(this)
        sharp = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        blur = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        root.addView(sharp)
        root.addView(blur)
        buildHome()
        buildDrawer()
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            home.setPadding(Theme.dp(this, 22), bars.top + Theme.dp(this, 28), Theme.dp(this, 22), bars.bottom + Theme.dp(this, 14))
            drawerScroll.setPadding(0, 0, 0, bars.bottom + Theme.dp(this, 16))
            (drawer.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + Theme.dp(this, 12)
            insets
        }
        setContentView(root)
        root.post {
            setProgress(0f)
            enter()
        }
    }

    override fun onResume() {
        super.onResume()
        Theme.load(this) // Appearance may have changed in Settings.
        val (s, b) = Backdrop.homeLayers(this)
        sharp.setImageBitmap(s)
        blur.setImageBitmap(b)
        hebrew.text = hebrewDate()
        badge.background = Theme.glass(this, 16)
        askPill.background = Theme.glass(this, 26)
        askPill.visibility = if (Looks.assistantAllowed(this) && Looks.assistantPill(this)) View.VISIBLE else View.GONE
        applyLayout()
        // Match the navigation bar to the bottom of the background (no dark band).
        window.navigationBarColor = if (Theme.dark) Theme.blackAlpha(0.35f) else Looks.scene(this).bottom
        refreshWidget()
        apps = loadApps()
        // Rebuild icons only when something that affects them changed.
        val state = apps.joinToString { it.key } + Looks.icons(this) + Looks.glass(this) + Looks.dock(this) + Looks.assistantAllowed(this) +
            Theme.dark + LocalDate.now() // the calendar icon shows today's date
        if (state != shownState) {
            shownState = state
            dock.background = Theme.glass(this, 30)
            styleDrawer()
            fillDock()
            fillDrawer()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        animateDrawer(open = false) // Home button closes the drawer.
    }

    @Deprecated("Back closes the drawer; the home screen itself ignores back")
    override fun onBackPressed() {
        if (progress() > 0f) animateDrawer(open = false)
    }

    // ---- Home ----

    private fun buildHome() {
        clock = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 92f
            typeface = Theme.LIGHT
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        date = TextClock(this).apply {
            format24Hour = "EEEE, d MMMM"
            format12Hour = "EEEE, d MMMM"
            textSize = 18f
            typeface = Theme.REGULAR
            setTextColor(0xE6FFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        hebrew = Theme.text(this, "", 17f, Theme.GOLD, Theme.MEDIUM).apply { gravity = Gravity.CENTER }
        badge = Theme.text(this, "✓  " + getString(R.string.kosher_device), 12f, Color.WHITE, Theme.MEDIUM).apply {
            val h = Theme.dp(context, 12)
            setPadding(h, Theme.dp(context, 4), h, Theme.dp(context, 4))
        }
        widget = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL // Hebrew content
            val p = Theme.dp(context, 18)
            setPadding(p, p, p, p)
            Theme.pressable(this)
            setOnClickListener { startActivity(ownApp(TimesActivity::class.java)) }
        }
        hint = Theme.text(this, "︿\n" + getString(R.string.swipe_up_hint), 13f, 0xB3FFFFFF.toInt()).apply {
            gravity = Gravity.CENTER
            setOnClickListener { animateDrawer(open = true) }
        }
        ValueAnimator.ofFloat(0f, -Theme.dp(this, 6).toFloat()).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { hint.translationY = it.animatedValue as Float }
            start()
        }
        askPill = Theme.text(this, "✦   " + getString(R.string.ask_pill), 15f, Color.WHITE, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            setPadding(0, Theme.dp(context, 13), 0, Theme.dp(context, 13))
            Theme.pressable(this)
            setOnClickListener { startActivity(ownApp(AssistantActivity::class.java)) }
        }
        dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            val p = Theme.dp(context, 12)
            setPadding(p, p, p, p)
        }
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(clock)
            addView(date)
            addView(hebrew)
            addView(badge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 10) })
            addView(widget, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 26) })
            addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            addView(hint)
            addView(askPill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 10) })
            addView(dock, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 12) })
        }
        root.addView(home)
    }

    /** The glass widget: today's occasion or parsha, what's coming up, and the next prayer time. */
    private fun refreshWidget() {
        widget.removeAllViews()
        widget.visibility = if (Looks.widget(this)) View.VISIBLE else View.GONE
        if (!Looks.widget(this)) return
        widget.background = Theme.glass(this, 26)
        val today = LocalDate.now()
        val soft = 0xE6FFFFFF.toInt()
        fun line(text: String, size: Float, color: Int = soft, bold: Boolean = false) =
            widget.addView(Theme.text(this, text, size, color, if (bold) Theme.MEDIUM else Theme.REGULAR).apply {
                setPadding(0, Theme.dp(context, 3), 0, Theme.dp(context, 3))
            })

        val occasion = JewishDates.occasion(today)
        line(occasion ?: JewishDates.parsha(today), 20f, Color.WHITE, bold = true)
        if (occasion != null) line(JewishDates.parsha(today), 15f)
        upcoming(today)?.let { (days, name) ->
            line((if (days == 1L) "מחר" else "בעוד $days ימים") + " · " + name, 15f, Theme.GOLD, bold = true)
        }
        JewishDates.omer(today)?.let { line(it, 14f) }

        val zc = Place.zmanim(this, today) ?: return
        val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
        val jc = JewishDates.calendar(today)
        if (jc.hasCandleLighting()) zc.candleLighting?.let { line("הדלקת נרות · ${clock.format(it)}", 15f, Theme.GOLD, bold = true) }
        val now = Date()
        val next = listOf(
            "הנץ החמה" to zc.sunrise, "סוף זמן ק״ש" to zc.sofZmanShmaGRA, "סוף זמן תפילה" to zc.sofZmanTfilaGRA,
            "חצות היום" to zc.chatzos, "מנחה גדולה" to zc.minchaGedola, "שקיעה" to zc.sunset, "צאת הכוכבים" to zc.tzais,
        ).firstOrNull { it.second?.after(now) == true } ?: return
        val mins = (next.second!!.time - now.time) / 60000
        val left = if (mins >= 60) "%d:%02d".format(mins / 60, mins % 60) else "$mins דק׳"
        line("${next.first} ${clock.format(next.second!!)} · בעוד $left", 15f)
    }

    /** The next holiday within two weeks, as (days from today, name). */
    private fun upcoming(today: LocalDate): Pair<Long, String>? {
        val todays = JewishDates.occasion(today)
        for (i in 1L..14L) {
            val d = today.plusDays(i)
            val o = JewishDates.occasion(d) ?: continue
            if (o != todays) return ChronoUnit.DAYS.between(today, d) to o
        }
        return null
    }

    private fun fillDock() {
        dock.removeAllViews()
        val keys = Looks.dock(this) ?: DEFAULT_DOCK
        keys.take(3).forEachIndexed { slot, k ->
            val app = apps.firstOrNull { it.key == k } ?: apps.firstOrNull { it.key.contains(k) } ?: return@forEachIndexed
            dock.addView(ImageView(this).apply {
                setImageDrawable(app.icon)
                contentDescription = app.label
                Theme.pressable(this)
                setOnClickListener { app.open() }
                setOnLongClickListener {
                    Theme.haptic(it, strong = true)
                    chooseDockApp(slot)
                    true
                }
            }, LinearLayout.LayoutParams(0, Theme.dp(this, 66), 1f))
        }
    }

    private fun chooseDockApp(slot: Int) {
        KosherPage.sheet(this, getString(R.string.choose_dock_app), apps.map { it.label }) { i ->
            val keys = (Looks.dock(this) ?: DEFAULT_DOCK).toMutableList()
            while (keys.size < 3) keys += ""
            keys[slot] = apps[i].key
            Looks.setDock(this, keys)
            shownState = null
            onResume()
        }
    }

    // ---- Drawer ----

    private fun buildDrawer() {
        drawerGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 14)
            setPadding(p, 0, p, 0)
        }
        drawerScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = true // so the bottom fade finishes cleanly above the navigation buttons
            addView(drawerGrid)
        }
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(context).apply { background = Theme.rounded(context, Theme.whiteAlpha(0.6f), 3) },
                LinearLayout.LayoutParams(Theme.dp(context, 40), Theme.dp(context, 5)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = Theme.dp(context, 10)
                })
            addView(Theme.text(context, getString(R.string.all_apps), 24f, Color.WHITE, Theme.MEDIUM).apply {
                setPadding(Theme.dp(context, 22), Theme.dp(context, 12), Theme.dp(context, 22), 0)
            })
            addView(Theme.text(context, getString(R.string.hold_to_move), 13f, 0x99FFFFFF.toInt()).apply {
                setPadding(Theme.dp(context, 22), 0, Theme.dp(context, 22), Theme.dp(context, 10))
            })
            addView(drawerScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        // Apps fade out softly at the top and bottom edges while scrolling.
        drawerScroll.isVerticalFadingEdgeEnabled = true
        drawerScroll.setFadingEdgeLength(Theme.dp(this, 48))
        drawer.translationY = resources.displayMetrics.heightPixels * 2f // off-screen until laid out
        root.addView(drawer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun styleDrawer() {
        val g = Theme.glass
        drawer.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            when {
                g <= 0.01f -> intArrayOf(0xF0101828.toInt(), 0xF0101828.toInt())
                Theme.dark -> intArrayOf(Theme.blackAlpha(0.25f + 0.25f * g), Theme.blackAlpha(0.35f + 0.25f * g))
                else -> intArrayOf(Theme.whiteAlpha(0.08f + 0.2f * g), Theme.whiteAlpha(0.03f + 0.07f * g))
            }).apply {
            val r = Theme.dp(this@ClosedHomeActivity, 34).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setStroke(Theme.dp(this@ClosedHomeActivity, 1), Theme.whiteAlpha(0.12f + 0.25f * g))
        }
    }

    private fun fillDrawer() {
        drawerGrid.removeAllViews()
        apps.chunked(COLUMNS).forEach { row ->
            drawerGrid.addView(LinearLayout(this).apply {
                row.forEach { addView(appCell(it), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
                repeat(COLUMNS - row.size) { addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
            })
        }
    }

    private fun appCell(app: App) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, Theme.dp(context, 12), 0, Theme.dp(context, 12))
        addView(ImageView(context).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(Theme.dp(context, 76), Theme.dp(context, 76)))
        addView(Theme.text(context, app.label, 14f, Color.WHITE, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(Theme.dp(context, 2), Theme.dp(context, 6), Theme.dp(context, 2), 0)
            setShadowLayer(4f, 0f, 1f, 0x66000000)
        })
        Theme.pressable(this)
        setOnClickListener { app.open() }
        // Hold to pick the app up, drop it on another app to take its place.
        setOnLongClickListener { v ->
            Theme.haptic(v, strong = true)
            v.startDragAndDrop(ClipData.newPlainText("app", app.key), View.DragShadowBuilder(v), app.key, 0)
            v.alpha = 0.3f
            true
        }
        setOnDragListener { v, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_ENTERED -> { v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start(); Theme.haptic(v) }
                DragEvent.ACTION_DRAG_EXITED -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                DragEvent.ACTION_DROP -> move(e.localState as String, app.key)
                DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; v.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
            }
            true
        }
    }

    private fun move(from: String, to: String) {
        if (from == to) return
        val keys = apps.map { it.key }.toMutableList()
        keys.remove(from)
        keys.add(keys.indexOf(to).coerceAtLeast(0), from)
        Looks.setOrder(this, keys)
        apps = loadApps()
        root.post { fillDrawer() } // after the drag finishes
    }

    /** Allowed apps plus the built-in kosher apps, in the order the user arranged. */
    private fun loadApps(): List<App> {
        val allowed = ModeManager.allowedInClosed(this)
        val installed = launchableApps(this).filter { it.pkg in allowed }.map { a ->
            app(a.label, a.pkg, packageManager.getApplicationIcon(a.pkg)) {
                packageManager.getLaunchIntentForPackage(a.pkg)?.let(::startActivity)
            }
        }
        fun own(label: String, cls: Class<*>, key: String) =
            app(label, key, packageManager.getActivityIcon(ComponentName(this, cls))) { startActivity(ownApp(cls)) }
        val all = installed + listOf(
            own(getString(R.string.siddur_label), SiddurActivity::class.java, "siddur"),
            own(getString(R.string.times_label), TimesActivity::class.java, "times"),
            own(getString(R.string.notes), NotesActivity::class.java, "notes"),
            app(getString(R.string.settings), "kosher_settings", getDrawable(R.drawable.ic_settings_app)!!) {
                startActivity(ownApp(KosherSettingsActivity::class.java))
            },
        ) + if (Looks.assistantAllowed(this)) listOf(own(getString(R.string.assistant), AssistantActivity::class.java, "assistant")) else emptyList()
        val order = Looks.order(this)
        return all.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    /** A built-in app in its own task (the home screen's task is hidden from recent apps). */
    private fun ownApp(cls: Class<*>) = Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun app(label: String, key: String, original: Drawable, open: () -> Unit) =
        App(label, Icons.forApp(this, key, original), key, open)

    // ---- Layout and motion ----

    /** Clock size/style and which lines show, from Settings → Home & lock screen. */
    private fun applyLayout() {
        clock.textSize = floatArrayOf(92f, 68f, 50f)[Looks.clockSize(this)]
        clock.typeface = when (Looks.clockFont(this)) {
            1 -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            2 -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
            else -> Theme.LIGHT
        }
        date.visibility = if (Looks.showDate(this)) View.VISIBLE else View.GONE
        hebrew.visibility = if (Looks.showHebrewDate(this)) View.VISIBLE else View.GONE
        badge.visibility = if (Looks.showBadge(this)) View.VISIBLE else View.GONE
    }

    /** The home screen builds itself in, piece by piece, when kosher mode starts. */
    private fun enter() {
        listOf(clock, date, hebrew, badge, widget, askPill, dock).forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = Theme.dp(this, 36).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(80L + i * 55L).setDuration(520)
                .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.1f, 1f)).start()
        }
        hint.alpha = 0f
        hint.animate().alpha(1f).setStartDelay(600).setDuration(500).start()
    }

    /** Drawer rows flow in one after another as it opens. */
    private fun flowIcons() {
        for (i in 0 until drawerGrid.childCount) {
            val row = drawerGrid.getChildAt(i)
            row.alpha = 0f
            row.translationY = Theme.dp(this, 26).toFloat()
            row.animate().alpha(1f).translationY(0f).setStartDelay(i * 45L).setDuration(360)
                .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.1f, 1f)).start()
        }
    }

    // ---- Drawer motion ----

    private fun progress() = if (root.height == 0) 0f else 1f - drawer.translationY / root.height

    /** 0 = closed, 1 = open. Moves the drawer and fades/blurs the home screen to match. */
    private fun setProgress(p: Float) {
        val h = root.height.toFloat()
        drawer.translationY = h * (1f - p)
        blur.alpha = p
        home.alpha = 1f - p
        home.scaleX = 1f - 0.06f * p
        home.scaleY = 1f - 0.06f * p
        home.translationY = -Theme.dp(this, 40) * p
        if ((p > 0.5f) != passedHalf) {
            passedHalf = p > 0.5f
            Theme.haptic(root)
        }
        if (p > 0.15f && !iconsShown) {
            iconsShown = true
            flowIcons()
        } else if (p < 0.02f) iconsShown = false
    }

    private fun animateDrawer(open: Boolean) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress(), if (open) 1f else 0f).apply {
            duration = 380
            interpolator = PathInterpolator(0.2f, 0.9f, 0.1f, 1f)
            addUpdateListener { setProgress(it.animatedValue as Float) }
            start()
        }
        if (!open) drawerScroll.smoothScrollTo(0, 0)
    }

    /** Follows the finger: up on the home screen opens, down at the top of the drawer closes. */
    private inner class DrawerFrame(ctx: Context) : FrameLayout(ctx) {
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private var startY = 0f
        private var startProgress = 0f
        private var dragging = false
        private var tracker: VelocityTracker? = null

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    dragging = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    tracker?.addMovement(e)
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(e)
                    maybeStartDrag(e)
                }
            }
            return dragging
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            tracker?.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) maybeStartDrag(e)
                    if (dragging) setProgress((startProgress - (e.rawY - startY) / height).coerceIn(0f, 1f))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        tracker?.computeCurrentVelocity(1000)
                        val vy = tracker?.yVelocity ?: 0f
                        animateDrawer(open = if (abs(vy) > 900) vy < 0 else progress() > 0.5f)
                    }
                    dragging = false
                }
            }
            return true
        }

        private fun maybeStartDrag(e: MotionEvent) {
            val dy = e.rawY - startY
            if (abs(dy) < slop) return
            val open = progress() > 0.5f
            if ((!open && dy < 0) || (open && dy > 0 && drawerScroll.scrollY == 0)) {
                animator?.cancel()
                dragging = true
                startY = e.rawY
                startProgress = progress()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
    }
}
