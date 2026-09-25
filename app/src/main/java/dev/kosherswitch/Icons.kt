package dev.kosherswitch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kosher Switch's own detailed icons for the built-in phone apps, in two styles:
 * KOSHER (glossy colored tile) and GLASS (frosted tile that turns light or dark with the theme).
 * Downloaded apps keep their own icon (on a frosted tile in GLASS style).
 */
object Icons {
    enum class Kind(val top: Int, val bottom: Int) {
        PHONE(0xFF6BE584.toInt(), 0xFF1E9E47.toInt()),
        MESSAGES(0xFF72D5FF.toInt(), 0xFF1473F0.toInt()),
        CONTACTS(0xFFCDD2DA.toInt(), 0xFF868D99.toInt()),
        CAMERA(0xFF6A717E.toInt(), 0xFF1F2329.toInt()),
        GALLERY(0xFFFFB85E.toInt(), 0xFFFF4D6D.toInt()),
        CLOCK(0xFF3C414C.toInt(), 0xFF0C0E12.toInt()),
        CALCULATOR(0xFFFFB44D.toInt(), 0xFFEA6A00.toInt()),
        FILES(0xFF79BCFF.toInt(), 0xFF1D6FDD.toInt()),
        MAPS(0xFF7EE0B0.toInt(), 0xFF12946F.toInt()),
        SETTINGS(0xFFB9BFC9.toInt(), 0xFF626A77.toInt()),
        SIDDUR(0xFF24569F.toInt(), 0xFF0A2350.toInt()),
        TIMES(0xFF2B63B5.toInt(), 0xFF0B2A5B.toInt()),
        NOTES(0xFFFFD66B.toInt(), 0xFFF5A623.toInt()),
        ASSISTANT(0xFFA88BFF.toInt(), 0xFF4B2FC9.toInt()),
        WEATHER(0xFF5DB2FF.toInt(), 0xFF1F6FD1.toInt()),
    }

    private val OWN = setOf("siddur", "times", "notes", "weather", "assistant", "kosher_settings")

    private val MATCH = listOf(
        "dialer" to Kind.PHONE, "incallui" to Kind.PHONE, "messag" to Kind.MESSAGES, "mms" to Kind.MESSAGES,
        "contacts" to Kind.CONTACTS, "camera" to Kind.CAMERA, "gallery" to Kind.GALLERY, "photos" to Kind.GALLERY,
        "clock" to Kind.CLOCK, "calculator" to Kind.CALCULATOR, "files" to Kind.FILES, "maps" to Kind.MAPS,
        "kosher_settings" to Kind.SETTINGS, "siddur" to Kind.SIDDUR, "times" to Kind.TIMES,
        "notes" to Kind.NOTES, "memo" to Kind.NOTES, "assistant" to Kind.ASSISTANT, "weather" to Kind.WEATHER,
    )

    /** One icon in a given style, for the icon test screen. */
    internal fun sample(kind: Kind, glass: Boolean, dark: Boolean): Drawable = Tile(kind, null, glass, dark, 0.6f)

    /** The icon to show for an app [key] (package name or built-in key). */
    fun forApp(ctx: Context, key: String, original: Drawable): Drawable {
        val style = Looks.icons(ctx)
        val kind = MATCH.firstOrNull { key.contains(it.first) }?.second
        // Kosher Switch's own apps: their designed icon *is* the original.
        if (style == Looks.Icons.ORIGINAL) return if (key in OWN && kind != null) Tile(kind, null, false, Theme.dark, Theme.glass) else original
        val glass = style == Looks.Icons.GLASS
        return when {
            kind != null -> Tile(kind, null, glass, Theme.dark, Theme.glass)
            glass -> Tile(null, original, true, Theme.dark, Theme.glass)
            else -> original
        }
    }

    /** One app tile, drawn on a 100×100 grid and scaled to any size. */
    /** Just the symbol, without the tile (for the launcher icons' foreground layer). */
    internal fun glyphOnly(kind: Kind): Drawable = Tile(kind, null, false, false, 0.6f, plate = false)

    private class Tile(
        val kind: Kind?, val inner: Drawable?, val glass: Boolean, val dark: Boolean, val strength: Float,
        val plate: Boolean = true,
    ) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun getIntrinsicWidth() = 192
        override fun getIntrinsicHeight() = 192

        override fun draw(c: Canvas) {
            val side = minOf(bounds.width(), bounds.height()).toFloat()
            c.save()
            c.translate(bounds.centerX() - side / 2, bounds.centerY() - side / 2)
            c.scale(side / 100f, side / 100f)
            if (plate) drawTile(c)
            when {
                kind != null -> drawGlyph(c, kind)
                inner != null -> {
                    c.restore()
                    val inset = (side * 0.15f).toInt()
                    val s = side.toInt()
                    val l = (bounds.centerX() - s / 2) + inset
                    val t = (bounds.centerY() - s / 2) + inset
                    inner.setBounds(l, t, l + s - 2 * inset, t + s - 2 * inset)
                    inner.draw(c)
                    return
                }
            }
            c.restore()
        }

        // ---- Tile background ----

        private fun drawTile(c: Canvas) {
            val r = RectF(0f, 0f, 100f, 100f)
            val radius = 23f
            p.style = Paint.Style.FILL
            if (glass) {
                val g = strength.coerceAtLeast(0.2f)
                p.shader = if (dark) LinearGradient(0f, 0f, 0f, 100f,
                    Color.argb((70 + 60 * g).toInt(), 10, 14, 22), Color.argb((110 + 70 * g).toInt(), 0, 0, 0), Shader.TileMode.CLAMP)
                else LinearGradient(0f, 0f, 0f, 100f,
                    Theme.whiteAlpha(0.35f + 0.35f * g), Theme.whiteAlpha(0.14f + 0.20f * g), Shader.TileMode.CLAMP)
                c.drawRoundRect(r, radius, radius, p)
            } else {
                p.shader = LinearGradient(0f, 0f, 0f, 100f, kind!!.top, kind.bottom, Shader.TileMode.CLAMP)
                c.drawRoundRect(r, radius, radius, p)
                // Soft shade toward the bottom for depth.
                p.shader = LinearGradient(0f, 60f, 0f, 100f, Color.TRANSPARENT, 0x26000000, Shader.TileMode.CLAMP)
                c.drawRoundRect(r, radius, radius, p)
            }
            // Gloss over the top half, like light on glass.
            p.shader = LinearGradient(0f, 0f, 0f, 50f, Theme.whiteAlpha(if (glass && dark) 0.16f else 0.30f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(0f, 0f, 100f, 52f), radius, radius, p)
            p.shader = null
            // Thin bright edge.
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.4f
            p.color = Theme.whiteAlpha(if (glass) (if (dark) 0.30f else 0.80f) else 0.22f)
            c.drawRoundRect(RectF(0.7f, 0.7f, 99.3f, 99.3f), radius, radius, p)
            p.style = Paint.Style.FILL
        }

        // ---- Symbols ----

        /** Main symbol color: white on colored tiles, the app's own color on glass. */
        private fun fg(k: Kind) = if (!glass) Color.WHITE else if (dark) k.top else k.bottom

        private fun shadow(on: Boolean) {
            if (on) p.setShadowLayer(3f, 0f, 1.5f, 0x40000000) else p.clearShadowLayer()
        }

        private fun drawGlyph(c: Canvas, k: Kind) {
            when (k) {
                Kind.PHONE -> phone(c, k)
                Kind.MESSAGES -> messages(c, k)
                Kind.CONTACTS -> contacts(c, k)
                Kind.CAMERA -> camera(c)
                Kind.GALLERY -> gallery(c, k)
                Kind.CLOCK -> clock(c)
                Kind.CALCULATOR -> calculator(c, k)
                Kind.FILES -> files(c, k)
                Kind.MAPS -> maps(c)
                Kind.SETTINGS -> settings(c, k)
                Kind.SIDDUR -> siddur(c)
                Kind.TIMES -> times(c)
                Kind.NOTES -> notes(c, k)
                Kind.ASSISTANT -> assistant(c, k)
                Kind.WEATHER -> weather(c)
            }
            p.shader = null
            shadow(false)
        }

        private fun phone(c: Canvas, k: Kind) {
            shadow(true)
            p.color = fg(k)
            c.save()
            c.translate(21f, 21f)
            c.scale(58f / 24f, 58f / 24f)
            c.drawPath(SvgPath.parse(HANDSET), p)
            c.restore()
        }

        private fun messages(c: Canvas, k: Kind) {
            shadow(true)
            p.color = fg(k)
            c.drawOval(RectF(16f, 20f, 84f, 72f), p)
            c.drawPath(Path().apply { moveTo(28f, 60f); lineTo(20f, 82f); lineTo(46f, 69f); close() }, p)
            shadow(false)
            p.color = if (glass) Color.WHITE else k.bottom
            listOf(34f, 50f, 66f).forEach { c.drawCircle(it, 46f, 4.8f, p) }
        }

        private fun contacts(c: Canvas, k: Kind) {
            shadow(true)
            p.color = if (glass) fg(k) else Color.WHITE
            c.drawCircle(50f, 50f, 32f, p)
            shadow(false)
            c.save()
            c.clipPath(Path().apply { addCircle(50f, 50f, 32f, Path.Direction.CW) })
            p.shader = LinearGradient(0f, 30f, 0f, 90f,
                if (glass) Color.WHITE else 0xFFA4ABB6.toInt(), if (glass) 0xFFE8ECF2.toInt() else 0xFF6C737F.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(50f, 42f, 11.5f, p)
            c.drawOval(RectF(27f, 58f, 73f, 96f), p)
            c.restore()
            p.shader = null
        }

        private fun camera(c: Canvas) {
            val body = if (glass) (if (dark) 0xFFE9ECF1.toInt() else 0xFF3A3F48.toInt()) else Color.WHITE
            shadow(true)
            p.color = body
            c.drawRoundRect(RectF(15f, 32f, 85f, 77f), 11f, 11f, p)
            c.drawRoundRect(RectF(35f, 24f, 65f, 38f), 5f, 5f, p)
            shadow(false)
            p.color = 0xFF262A31.toInt()
            c.drawCircle(50f, 55f, 17f, p)
            p.color = 0xFF5C6573.toInt()
            c.drawCircle(50f, 55f, 13f, p)
            p.shader = RadialGradient(47f, 52f, 11f, 0xFF4FA8FF.toInt(), 0xFF0B1E4A.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(50f, 55f, 9.5f, p)
            p.shader = null
            p.color = Theme.whiteAlpha(0.85f)
            c.drawCircle(46f, 51f, 2.8f, p)
            p.color = 0xFFFFD34E.toInt()
            c.drawRoundRect(RectF(69f, 38f, 78f, 44f), 1.5f, 1.5f, p)
        }

        private fun gallery(c: Canvas, k: Kind) {
            shadow(true)
            p.color = if (glass) fg(k) else Color.WHITE
            c.drawRoundRect(RectF(18f, 22f, 82f, 78f), 9f, 9f, p)
            shadow(false)
            val inner = RectF(23.5f, 27.5f, 76.5f, 72.5f)
            c.save()
            c.clipPath(Path().apply { addRoundRect(inner, 5f, 5f, Path.Direction.CW) })
            p.shader = LinearGradient(0f, inner.top, 0f, inner.bottom, 0xFF6FCBFF.toInt(), 0xFFD6F1FF.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(inner, p)
            p.shader = null
            p.color = 0xFFFFD34E.toInt()
            c.drawCircle(63f, 39f, 6f, p)
            p.color = 0xFF2FB872.toInt()
            c.drawPath(Path().apply { moveTo(22f, 74f); lineTo(41f, 49f); lineTo(58f, 74f); close() }, p)
            p.color = 0xFF1C8C55.toInt()
            c.drawPath(Path().apply { moveTo(44f, 74f); lineTo(61f, 55f); lineTo(80f, 74f); close() }, p)
            c.restore()
        }

        private fun clock(c: Canvas) {
            shadow(true)
            p.color = if (glass && !dark) 0xFF1C1F26.toInt() else Color.WHITE
            c.drawCircle(50f, 50f, 35f, p)
            shadow(false)
            val ink = if (glass && !dark) Color.WHITE else 0xFF1C1F26.toInt()
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.color = ink
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                val quarter = i % 3 == 0
                p.strokeWidth = if (quarter) 3f else 1.6f
                val r1 = if (quarter) 25f else 28f
                c.drawLine(50f + r1 * sin(a).toFloat(), 50f - r1 * cos(a).toFloat(),
                    50f + 31f * sin(a).toFloat(), 50f - 31f * cos(a).toFloat(), p)
            }
            fun hand(deg: Double, len: Float, w: Float, color: Int) {
                val a = Math.toRadians(deg)
                p.strokeWidth = w
                p.color = color
                c.drawLine(50f, 50f, 50f + len * sin(a).toFloat(), 50f - len * cos(a).toFloat(), p)
            }
            hand(-60.0, 15f, 4.5f, ink)   // 10 o'clock
            hand(60.0, 23f, 3.2f, ink)    // :10
            hand(160.0, 27f, 1.4f, 0xFFFF9500.toInt())
            p.style = Paint.Style.FILL
            p.color = 0xFFFF9500.toInt()
            c.drawCircle(50f, 50f, 3.2f, p)
            p.color = Color.WHITE
            c.drawCircle(50f, 50f, 1.2f, p)
        }

        private fun calculator(c: Canvas, k: Kind) {
            shadow(true)
            p.color = if (glass) fg(k) else 0xFF2A2D33.toInt()
            c.drawRoundRect(RectF(22f, 16f, 78f, 84f), 10f, 10f, p)
            shadow(false)
            p.color = 0xFFE9F5E4.toInt()
            c.drawRoundRect(RectF(28f, 22f, 72f, 36f), 4f, 4f, p)
            for (row in 0 until 3) for (col in 0 until 3) {
                p.color = when {
                    col == 2 -> 0xFFFF9F0A.toInt()
                    else -> 0xFFD9DDE3.toInt()
                }
                val x = 28f + col * 15.5f
                val y = 42f + row * 13.5f
                c.drawRoundRect(RectF(x, y, x + 13f, y + 10f), 3f, 3f, p)
            }
        }

        private fun files(c: Canvas, k: Kind) {
            val main = if (glass) fg(k) else Color.WHITE
            p.color = main
            p.alpha = 150
            c.drawPath(SvgPath.parse("M18,34 Q18,26 25,26 H40 L47,33 H75 Q82,33 82,40 V70 Q82,77 75,77 H25 Q18,77 18,70 Z"), p)
            p.alpha = 255
            p.color = if (glass) Color.WHITE else 0xFFDDEBFF.toInt()
            c.drawRoundRect(RectF(25f, 36f, 75f, 62f), 3f, 3f, p)
            shadow(true)
            p.color = main
            c.drawPath(SvgPath.parse("M18,47 Q18,42 23,42 H77 Q82,42 82,47 V70 Q82,77 75,77 H25 Q18,77 18,70 Z"), p)
        }

        /** A whole map filling the tile (inset on glass): land, park, water, streets, a highway and a pin. */
        private fun maps(c: Canvas) {
            val area = if (glass || !plate) RectF(16f, 16f, 84f, 84f) else RectF(0f, 0f, 100f, 100f)
            c.save()
            c.clipPath(Path().apply { addRoundRect(area, if (glass || !plate) 12f else 23f, if (glass || !plate) 12f else 23f, Path.Direction.CW) })
            c.translate(area.left, area.top)
            c.scale(area.width() / 100f, area.height() / 100f)
            p.shader = LinearGradient(0f, 0f, 0f, 100f, 0xFFF8F5EE.toInt(), 0xFFECE6D8.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, 100f, 100f, p)
            p.shader = null
            p.color = 0xFFBFE4A6.toInt()
            c.drawPath(SvgPath.parse("M0,56 C12,52 24,58 32,70 C38,80 36,100 36,100 L0,100 Z"), p)
            p.shader = LinearGradient(60f, 0f, 100f, 36f, 0xFF9AD8FF.toInt(), 0xFF5BB6F4.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(SvgPath.parse("M56,0 C60,14 76,25 100,27 L100,0 Z"), p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            fun road(d: String, edge: Int, fill: Int, w: Float) {
                val path = SvgPath.parse(d)
                p.color = edge; p.strokeWidth = w + 2.2f; c.drawPath(path, p)
                p.color = fill; p.strokeWidth = w; c.drawPath(path, p)
            }
            road("M0,40 C30,45 58,34 100,50", 0xFFDAD3C3.toInt(), Color.WHITE, 6.5f)
            road("M30,0 C38,30 46,62 72,100", 0xFFDAD3C3.toInt(), Color.WHITE, 6.5f)
            road("M52,100 C62,82 80,72 100,70", 0xFFDAD3C3.toInt(), Color.WHITE, 4.5f)
            road("M0,86 C36,72 66,80 100,62", 0xFFE9A100.toInt(), 0xFFFFD04A.toInt(), 8f)
            p.style = Paint.Style.FILL
            c.restore()
            // The pin, with a soft shadow on the map.
            p.color = 0x33000000
            c.drawOval(RectF(54f, 60f, 66f, 64f), p)
            p.color = Color.WHITE // opaque, so the gradient pin is solid
            shadow(true)
            p.shader = LinearGradient(0f, 24f, 0f, 62f, 0xFFFF6259.toInt(), 0xFFE0261B.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(Path().apply {
                addCircle(60f, 36f, 12f, Path.Direction.CW)
                moveTo(49.6f, 42f); quadTo(56f, 52f, 60f, 62f); quadTo(64f, 52f, 70.4f, 42f); close()
            }, p)
            p.shader = null
            shadow(false)
            p.color = Color.WHITE
            c.drawCircle(60f, 36f, 4.8f, p)
        }

        private fun settings(c: Canvas, k: Kind) {
            shadow(true)
            p.shader = if (glass) null else LinearGradient(0f, 15f, 0f, 85f, 0xFFF7F8FA.toInt(), 0xFFB9BFC8.toInt(), Shader.TileMode.CLAMP)
            p.color = fg(k)
            c.save()
            c.translate(14f, 14f)
            c.scale(72f / 24f, 72f / 24f)
            c.drawPath(SvgPath.parse(GEAR), p)
            c.restore()
            p.shader = null
            shadow(false)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.2f
            p.color = if (glass) fg(k) else 0xFF8B929E.toInt()
            c.drawCircle(50f, 50f, 15f, p)
            p.style = Paint.Style.FILL
        }

        private fun siddur(c: Canvas) {
            val navy = 0xFF0B2A5B.toInt()
            // Pages peeking out on the right.
            p.color = 0xFFFFF4DA.toInt()
            c.drawRoundRect(RectF(30f, 19f, 79f, 83f), 4f, 4f, p)
            p.color = 0xFFE3D3AE.toInt()
            p.strokeWidth = 0.8f
            for (y in 24..78 step 6) c.drawLine(75f, y.toFloat(), 79f, y.toFloat(), p)
            // Gold cover and spine.
            shadow(true)
            p.shader = LinearGradient(0f, 15f, 0f, 85f, 0xFFF4D682.toInt(), 0xFFC4952B.toInt(), Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(23f, 15f, 75f, 85f), 5f, 5f, p)
            shadow(false)
            p.shader = null
            p.color = 0xFFA87B1C.toInt()
            c.drawRoundRect(RectF(23f, 15f, 31f, 85f), 4f, 4f, p)
            // Magen David inside a double ring.
            p.style = Paint.Style.STROKE
            p.color = navy
            p.strokeWidth = 2f
            c.drawCircle(53f, 50f, 15f, p)
            p.strokeWidth = 1f
            c.drawCircle(53f, 50f, 12f, p)
            p.strokeWidth = 1.6f
            p.strokeJoin = Paint.Join.ROUND
            c.drawPath(Path().apply { moveTo(53f, 40.5f); lineTo(61.2f, 54.8f); lineTo(44.8f, 54.8f); close() }, p)
            c.drawPath(Path().apply { moveTo(53f, 59.5f); lineTo(61.2f, 45.2f); lineTo(44.8f, 45.2f); close() }, p)
            p.style = Paint.Style.FILL
        }

        /** A notepad: lined page with a yellow top. */
        private fun notes(c: Canvas, k: Kind) {
            val page = RectF(22f, 16f, 78f, 84f)
            shadow(true)
            p.color = Color.WHITE
            c.drawRoundRect(page, 8f, 8f, p)
            shadow(false)
            c.save()
            c.clipPath(Path().apply { addRoundRect(page, 8f, 8f, Path.Direction.CW) })
            p.shader = LinearGradient(0f, 16f, 0f, 30f, k.top, k.bottom, Shader.TileMode.CLAMP)
            c.drawRect(22f, 16f, 78f, 30f, p)
            p.shader = null
            c.restore()
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 2.2f
            p.color = 0xFFD9DDE3.toInt()
            for (y in listOf(42f, 52f, 62f, 72f)) c.drawLine(30f, y, if (y == 72f) 56f else 70f, y, p)
            p.style = Paint.Style.FILL
        }

        /** Weather: the weather app's own sun-behind-a-cloud symbol. */
        private fun weather(c: Canvas) {
            WeatherGlyph(Sky.PARTLY, true).apply { setBounds(8, 10, 92, 94) }.draw(c)
        }

        /** The assistant: a large and a small sparkle. */
        private fun assistant(c: Canvas, k: Kind) {
            fun star(cx: Float, cy: Float, r: Float) = Path().apply {
                moveTo(cx, cy - r)
                cubicTo(cx + r * 0.12f, cy - r * 0.3f, cx + r * 0.3f, cy - r * 0.12f, cx + r, cy)
                cubicTo(cx + r * 0.3f, cy + r * 0.12f, cx + r * 0.12f, cy + r * 0.3f, cx, cy + r)
                cubicTo(cx - r * 0.12f, cy + r * 0.3f, cx - r * 0.3f, cy + r * 0.12f, cx - r, cy)
                cubicTo(cx - r * 0.3f, cy - r * 0.12f, cx - r * 0.12f, cy - r * 0.3f, cx, cy - r)
                close()
            }
            shadow(true)
            p.color = fg(k)
            c.drawPath(star(46f, 54f, 26f), p)
            shadow(false)
            p.color = if (glass) 0xFFFFC94D.toInt() else 0xFFFFE08A.toInt()
            c.drawPath(star(70f, 28f, 11f), p)
        }

        /** A calendar page showing today's Hebrew date. */
        private fun times(c: Canvas) {
            val page = RectF(20f, 17f, 80f, 83f)
            shadow(true)
            p.color = Color.WHITE
            c.drawRoundRect(page, 10f, 10f, p)
            shadow(false)
            c.save()
            c.clipPath(Path().apply { addRoundRect(page, 10f, 10f, Path.Direction.CW) })
            p.shader = LinearGradient(0f, 17f, 0f, 37f, 0xFFF0C95E.toInt(), 0xFFC9982C.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(20f, 17f, 80f, 37f, p)
            p.shader = null
            c.restore()
            val today = LocalDate.now()
            val jc = JewishDates.calendar(today)
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.color = Color.WHITE
            p.textSize = 11f
            c.drawText(JewishDates.formatter.formatMonth(jc), 50f, 31f, p)
            p.color = 0xFF0B2A5B.toInt()
            p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            p.textSize = 29f
            c.drawText(JewishDates.dayNumber(today), 50f, 70f, p)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(cf: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    // Symbol outlines (Material Design icons, Apache 2.0, on a 24×24 grid).
    private const val HANDSET = "M20.01,15.38c-1.23,0 -2.42,-0.2 -3.53,-0.56 -0.35,-0.12 -0.74,-0.03 -1.01,0.24l-1.57,1.97c-2.83,-1.35 -5.48,-3.9 -6.89,-6.83l1.95,-1.66c0.27,-0.28 0.35,-0.67 0.24,-1.02 -0.37,-1.11 -0.56,-2.3 -0.56,-3.53 0,-0.54 -0.45,-0.99 -0.99,-0.99H4.19C3.65,3 3,3.24 3,3.99 3,13.28 10.73,21 20.01,21c0.71,0 0.99,-0.63 0.99,-1.18v-3.45c0,-0.54 -0.45,-0.99 -0.99,-0.99z"
    private const val GEAR = "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
}
