package dev.kosherswitch

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The kinds of sky the weather app draws. */
enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, HEAVY_RAIN, SNOW, THUNDER;
    companion object {
        fun of(code: Int) = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            61, 63, 66, 80, 81 -> RAIN
            65, 67, 82 -> HEAVY_RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> THUNDER
            else -> CLOUDY
        }
    }
}

/** Shared shapes: a fluffy cloud, sun, moon, drops, flakes and a bolt, on a 100×100 grid. */
private object Shapes {
    fun cloud(cx: Float, cy: Float, w: Float) = Path().apply {
        addCircle(cx - 0.28f * w, cy + 0.05f * w, 0.2f * w, Path.Direction.CW)
        addCircle(cx - 0.02f * w, cy - 0.1f * w, 0.29f * w, Path.Direction.CW)
        addCircle(cx + 0.27f * w, cy + 0.04f * w, 0.21f * w, Path.Direction.CW)
        addRoundRect(RectF(cx - 0.48f * w, cy + 0.02f * w, cx + 0.48f * w, cy + 0.25f * w), 0.12f * w, 0.12f * w, Path.Direction.CW)
    }

    fun moon(cx: Float, cy: Float, r: Float) = Path().apply {
        addCircle(cx, cy, r, Path.Direction.CW)
        op(Path().apply { addCircle(cx + r * 0.62f, cy - r * 0.42f, r * 0.86f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
    }

    fun bolt(x: Float, y: Float, s: Float) = Path().apply {
        moveTo(x + 4 * s, y); lineTo(x - 5 * s, y + 13 * s); lineTo(x + 1 * s, y + 13 * s)
        lineTo(x - 3 * s, y + 24 * s); lineTo(x + 8 * s, y + 9 * s); lineTo(x + 2 * s, y + 9 * s); lineTo(x + 6 * s, y); close()
    }
}

/** Colorful weather symbols (like the ones on an iPhone), drawn sharp at any size. */
class WeatherGlyph(private val sky: Sky, private val day: Boolean) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getIntrinsicWidth() = 96
    override fun getIntrinsicHeight() = 96

    override fun draw(c: Canvas) {
        val side = minOf(bounds.width(), bounds.height()).toFloat()
        c.save()
        c.translate(bounds.centerX() - side / 2, bounds.centerY() - side / 2)
        c.scale(side / 100f, side / 100f)
        when (sky) {
            Sky.CLEAR -> if (day) sun(c, 50f, 50f, 21f, rays = true) else moon(c, 50f, 50f, 27f)
            Sky.PARTLY -> {
                if (day) sun(c, 63f, 37f, 19f, rays = false) else moon(c, 63f, 35f, 19f)
                cloud(c, 43f, 60f, 68f, light = true)
            }
            Sky.CLOUDY -> {
                cloud(c, 60f, 40f, 50f, light = false)
                cloud(c, 44f, 58f, 66f, light = true)
            }
            Sky.FOG -> {
                cloud(c, 50f, 40f, 62f, light = true)
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = 5f
                p.color = 0xFFC9D3DE.toInt()
                c.drawLine(24f, 76f, 76f, 76f, p)
                c.drawLine(32f, 87f, 68f, 87f, p)
                p.style = Paint.Style.FILL
            }
            Sky.DRIZZLE -> { cloud(c, 50f, 38f, 66f, light = true); drops(c, 3, 9f) }
            Sky.RAIN -> { cloud(c, 50f, 38f, 66f, light = true); drops(c, 3, 15f) }
            Sky.HEAVY_RAIN -> { cloud(c, 50f, 36f, 66f, light = false); drops(c, 5, 16f) }
            Sky.SNOW -> {
                cloud(c, 50f, 38f, 66f, light = true)
                p.shader = null
                p.color = Color.WHITE
                p.setShadowLayer(1.5f, 0f, 0.5f, 0x55000000)
                for ((x, y) in listOf(34f to 74f, 50f to 82f, 66f to 74f, 42f to 91f, 58f to 91f)) c.drawCircle(x, y, 3.6f, p)
                p.clearShadowLayer()
            }
            Sky.THUNDER -> {
                cloud(c, 50f, 36f, 66f, light = false)
                p.shader = LinearGradient(0f, 60f, 0f, 94f, 0xFFFFE45C.toInt(), 0xFFFFB300.toInt(), Shader.TileMode.CLAMP)
                c.drawPath(Shapes.bolt(49f, 58f, 1.65f), p)
                p.shader = null
            }
        }
        c.restore()
    }

    private fun sun(c: Canvas, x: Float, y: Float, r: Float, rays: Boolean) {
        if (rays) {
            p.shader = null
            p.color = 0xFFFFC21A.toInt()
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = r * 0.24f
            for (i in 0 until 8) {
                val a = i * PI / 4
                c.drawLine(x + (r + r * 0.35f) * cos(a).toFloat(), y + (r + r * 0.35f) * sin(a).toFloat(),
                    x + (r + r * 0.72f) * cos(a).toFloat(), y + (r + r * 0.72f) * sin(a).toFloat(), p)
            }
            p.style = Paint.Style.FILL
        }
        if (!rays) {
            p.shader = RadialGradient(x, y, r * 1.7f, intArrayOf(0x66FFD54A, 0x00FFD54A), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r * 1.7f, p)
        }
        p.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, 0xFFFFE680.toInt(), 0xFFFFA800.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
        p.shader = null
    }

    private fun moon(c: Canvas, x: Float, y: Float, r: Float) {
        p.shader = LinearGradient(x - r, y - r, x + r, y + r, 0xFFFFF6CF.toInt(), 0xFFF2CF5B.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(Shapes.moon(x, y, r), p)
        p.shader = null
    }

    private fun cloud(c: Canvas, x: Float, y: Float, w: Float, light: Boolean) {
        p.shader = if (light) LinearGradient(0f, y - w * 0.4f, 0f, y + w * 0.25f, Color.WHITE, 0xFFD5E0EC.toInt(), Shader.TileMode.CLAMP)
            else LinearGradient(0f, y - w * 0.4f, 0f, y + w * 0.25f, 0xFFB7C3D1.toInt(), 0xFF7D8B9C.toInt(), Shader.TileMode.CLAMP)
        p.setShadowLayer(2.5f, 0f, 1.5f, 0x40000000)
        c.drawPath(Shapes.cloud(x, y, w), p)
        p.clearShadowLayer()
        p.shader = null
    }

    private fun drops(c: Canvas, n: Int, len: Float) {
        p.shader = null
        p.color = 0xFF46B4FF.toInt()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 5f
        val start = 50f - (n - 1) * 8f
        for (i in 0 until n) {
            val x = start + i * 16f
            val y = 70f + (i % 2) * 5f
            c.drawLine(x + 3f, y, x - 3f, y + len, p)
        }
        p.style = Paint.Style.FILL
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { p.colorFilter = filter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Small white line symbols for the detail cards. */
class DetailGlyph(private val kind: Kind) : Drawable() {
    enum class Kind { FEELS, HUMIDITY, WIND, UV, SUNRISE, SUNSET, RAIN, PRESSURE }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 7f
        color = Color.WHITE
    }

    override fun getIntrinsicWidth() = 40
    override fun getIntrinsicHeight() = 40

    override fun draw(c: Canvas) {
        val side = minOf(bounds.width(), bounds.height()).toFloat()
        c.save()
        c.translate(bounds.centerX() - side / 2, bounds.centerY() - side / 2)
        c.scale(side / 100f, side / 100f)
        when (kind) {
            Kind.FEELS -> {
                c.drawRoundRect(RectF(40f, 10f, 60f, 64f), 10f, 10f, p)
                c.drawCircle(50f, 76f, 14f, p)
                p.style = Paint.Style.FILL; c.drawCircle(50f, 76f, 7f, p); p.style = Paint.Style.STROKE
            }
            Kind.HUMIDITY -> c.drawPath(Path().apply {
                moveTo(50f, 10f); cubicTo(38f, 30f, 24f, 46f, 24f, 62f); cubicTo(24f, 78f, 36f, 90f, 50f, 90f)
                cubicTo(64f, 90f, 76f, 78f, 76f, 62f); cubicTo(76f, 46f, 62f, 30f, 50f, 10f)
            }, p)
            Kind.WIND -> {
                c.drawPath(SvgPath.parse("M10,38 H62 C74,38 78,22 66,18 C58,16 54,24 56,28"), p)
                c.drawPath(SvgPath.parse("M10,56 H78 C90,56 92,74 80,78 C72,80 68,72 70,68"), p)
                c.drawLine(10f, 74f, 50f, 74f, p)
            }
            Kind.UV -> {
                c.drawCircle(50f, 50f, 16f, p)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    c.drawLine(50f + 28f * cos(a).toFloat(), 50f + 28f * sin(a).toFloat(), 50f + 38f * cos(a).toFloat(), 50f + 38f * sin(a).toFloat(), p)
                }
            }
            Kind.SUNRISE, Kind.SUNSET -> {
                c.drawArc(RectF(26f, 42f, 74f, 90f), 180f, 180f, false, p)
                c.drawLine(12f, 66f, 88f, 66f, p)
                val up = kind == Kind.SUNRISE
                c.drawLine(50f, 12f, 50f, 34f, p)
                if (up) { c.drawLine(40f, 22f, 50f, 12f, p); c.drawLine(60f, 22f, 50f, 12f, p) }
                else { c.drawLine(40f, 24f, 50f, 34f, p); c.drawLine(60f, 24f, 50f, 34f, p) }
                c.drawLine(24f, 82f, 76f, 82f, p)
            }
            Kind.RAIN -> {
                c.drawArc(RectF(12f, 16f, 88f, 84f), 180f, 180f, false, p)
                c.drawLine(12f, 50f, 88f, 50f, p)
                c.drawPath(SvgPath.parse("M50,50 V78 C50,90 34,90 34,80"), p)
            }
            Kind.PRESSURE -> {
                c.drawArc(RectF(12f, 16f, 88f, 92f), 150f, 240f, false, p)
                c.drawLine(50f, 54f, 68f, 34f, p)
                p.style = Paint.Style.FILL; c.drawCircle(50f, 54f, 6f, p); p.style = Paint.Style.STROKE
            }
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { p.colorFilter = filter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * The living background: sun glare and lens flares, drifting clouds, falling rain or snow,
 * lightning, fog, or twinkling stars at night.
 */
class WeatherScene(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density
    private var sky = Sky.CLEAR
    private var day = true
    private var top = 0xFF2E6FC4.toInt()
    private var bottom = 0xFF6FA8E6.toInt()
    private var bgShader: Shader? = null
    private val rnd = Random(7)
    private val start = SystemClock.uptimeMillis()
    private var running = false

    private class Particle(var x: Float, var y: Float, val speed: Float, val size: Float, val phase: Float)
    private val particles = mutableListOf<Particle>()
    private val clouds = mutableListOf<Particle>()
    private val stars = mutableListOf<Particle>()
    private var nextFlash = 3f
    private var flashAt = -10f
    private var lastFrame = 0L
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL) }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun set(sky: Sky, day: Boolean, top: Int, bottom: Int) {
        val changed = sky != this.sky || day != this.day || particles.isEmpty() && clouds.isEmpty() && stars.isEmpty()
        this.sky = sky; this.day = day; this.top = top; this.bottom = bottom
        bgShader = null
        if (changed) seed()
        invalidate()
    }

    private fun seed() {
        particles.clear(); clouds.clear(); stars.clear()
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()
        val drops = when (sky) { Sky.DRIZZLE -> 45; Sky.RAIN -> 80; Sky.HEAVY_RAIN, Sky.THUNDER -> 130; Sky.SNOW -> 70; else -> 0 }
        repeat(drops) {
            particles += if (sky == Sky.SNOW) Particle(rnd.nextFloat() * w, rnd.nextFloat() * h, (30 + rnd.nextFloat() * 50) * d, (1.5f + rnd.nextFloat() * 2.5f) * d, rnd.nextFloat() * 6f)
            else Particle(rnd.nextFloat() * w, rnd.nextFloat() * h, (520 + rnd.nextFloat() * 380) * d, (12 + rnd.nextFloat() * 16) * d, rnd.nextFloat())
        }
        val cloudCount = when (sky) { Sky.CLEAR -> 0; Sky.PARTLY -> 3; Sky.FOG -> 0; else -> 5 }
        repeat(cloudCount) {
            clouds += Particle(rnd.nextFloat() * w, (0.04f + rnd.nextFloat() * 0.35f) * h, (5 + rnd.nextFloat() * 12) * d,
                (160 + rnd.nextFloat() * 160) * d, rnd.nextFloat())
        }
        if (!day && sky in listOf(Sky.CLEAR, Sky.PARTLY)) repeat(70) {
            stars += Particle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.7f, 0.6f + rnd.nextFloat() * 2f, (0.6f + rnd.nextFloat() * 1.2f) * d, rnd.nextFloat() * 6f)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        bgShader = null
        seed()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = true; invalidate() }
    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        running = visibility == VISIBLE
        lastFrame = 0L
        if (running) invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        val t = (now - start) / 1000f
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now

        p.color = Color.WHITE // full strength: the paint's alpha also scales gradients
        p.shader = bgShader ?: LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP).also { bgShader = it }
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        if (stars.isNotEmpty()) for (s in stars) {
            p.color = Color.WHITE
            p.alpha = (110 + 110 * sin(t * s.speed + s.phase)).toInt().coerceIn(20, 230)
            c.drawCircle(s.x, s.y, s.size, p)
        }
        if (day && sky in listOf(Sky.CLEAR, Sky.PARTLY)) sunGlare(c, w, h, t, if (sky == Sky.CLEAR) 1f else 0.7f)
        if (!day && sky in listOf(Sky.CLEAR, Sky.PARTLY)) moonGlow(c, w)

        for (cl in clouds) {
            cl.x += cl.speed * dt
            if (cl.x - cl.size > w) cl.x = -cl.size
            val dark = sky in listOf(Sky.RAIN, Sky.HEAVY_RAIN, Sky.THUNDER)
            p.color = if (dark) 0xFF2B3440.toInt() else Color.WHITE
            p.alpha = if (dark) 70 else if (sky == Sky.PARTLY) 60 else 42
            c.drawPath(Shapes.cloud(cl.x, cl.y, cl.size), p)
        }
        if (sky == Sky.FOG) fog(c, w, h, t)

        when (sky) {
            Sky.SNOW -> for (s in particles) {
                s.y += s.speed * dt
                val x = s.x + sin(t * 1.3f + s.phase) * 14 * d
                if (s.y > h + 10) { s.y = -10f; s.x = rnd.nextFloat() * w }
                p.color = Color.WHITE
                p.alpha = 200
                c.drawCircle(x, s.y, s.size, p)
            }
            Sky.DRIZZLE, Sky.RAIN, Sky.HEAVY_RAIN, Sky.THUNDER -> {
                p.strokeWidth = 1.4f * d
                p.strokeCap = Paint.Cap.ROUND
                for (r in particles) {
                    r.y += r.speed * dt
                    r.x += r.speed * dt * 0.12f
                    if (r.y > h + r.size) { r.y = -r.size; r.x = rnd.nextFloat() * (w + 60 * d) - 60 * d }
                    p.color = 0xFFD8ECFF.toInt()
                    p.alpha = (70 + r.phase * 90).toInt()
                    c.drawLine(r.x, r.y, r.x - r.size * 0.12f, r.y - r.size, p)
                }
            }
            else -> {}
        }

        if (sky == Sky.THUNDER) {
            if (t > nextFlash) { flashAt = t; nextFlash = t + 4f + rnd.nextFloat() * 6f }
            val since = t - flashAt
            val a = when {
                since < 0.08f -> 0.45f
                since < 0.16f -> 0.1f
                since < 0.24f -> 0.35f
                since < 0.7f -> 0.35f * (1f - (since - 0.24f) / 0.46f)
                else -> 0f
            }
            if (a > 0f) { p.color = Color.WHITE; p.alpha = (a * 255).toInt(); c.drawRect(0f, 0f, w, h, p) }
        }
        if (running) postInvalidateOnAnimation()
    }

    /** Sunlight from the top corner: a soft glow, slowly turning rays and a few lens flares. */
    private fun sunGlare(c: Canvas, w: Float, h: Float, t: Float, strength: Float) {
        val sx = w * 0.84f
        val sy = h * 0.06f
        p.color = Color.WHITE
        p.shader = RadialGradient(sx, sy, w * 0.75f, intArrayOf(Color.argb((150 * strength).toInt(), 255, 247, 214),
            Color.argb((50 * strength).toInt(), 255, 236, 170), Color.TRANSPARENT), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy, w * 0.75f, p)
        p.shader = null
        c.save()
        c.rotate(t * 3f, sx, sy)
        p.color = Color.WHITE
        for (i in 0 until 12) {
            p.alpha = (14 * strength * (1 + 0.5f * sin(t * 0.8f + i))).toInt()
            val a = i * 30.0 * PI / 180
            val len = w * 1.1f
            val spread = 0.05
            c.drawPath(Path().apply {
                moveTo(sx, sy)
                lineTo(sx + len * cos(a - spread).toFloat(), sy + len * sin(a - spread).toFloat())
                lineTo(sx + len * cos(a + spread).toFloat(), sy + len * sin(a + spread).toFloat())
                close()
            }, p)
        }
        c.restore()
        // Lens flares along the line from the sun through the middle of the screen.
        val cx = w * 0.5f
        val cy = h * 0.42f
        val pulse = 1f + 0.06f * sin(t * 0.9f)
        for ((f, r, a) in listOf(Triple(0.55f, 18f, 34), Triple(0.9f, 34f, 22), Triple(1.25f, 12f, 40), Triple(1.6f, 52f, 16))) {
            val x = sx + (cx - sx) * f * 1.6f
            val y = sy + (cy - sy) * f * 1.6f
            p.color = Color.WHITE
            p.alpha = (a * strength).toInt()
            c.drawCircle(x, y, r * d * pulse, p)
        }
        glow.color = Color.WHITE
        glow.alpha = (200 * strength).toInt()
        c.drawCircle(sx, sy, 26 * d, glow)
    }

    private fun moonGlow(c: Canvas, w: Float) {
        val x = w * 0.8f
        val y = 70 * d
        p.color = Color.WHITE
        p.shader = RadialGradient(x, y, 120 * d, intArrayOf(0x44FFF6D6, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, 120 * d, p)
        p.shader = null
    }

    private fun fog(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in 0 until 4) {
            val y = h * (0.2f + i * 0.2f)
            val x = ((t * (10 + i * 6) * d) % (w * 2)) - w
            p.color = Color.WHITE
            p.shader = LinearGradient(0f, y - 60 * d, 0f, y + 60 * d, intArrayOf(Color.TRANSPARENT, 0x40FFFFFF, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            c.drawOval(RectF(x - w * 0.2f, y - 60 * d, x + w * 1.4f, y + 60 * d), p)
            p.shader = null
        }
    }
}
