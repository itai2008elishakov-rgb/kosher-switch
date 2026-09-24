package dev.kosherswitch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/** The deep-blue background with soft colored glows, shared by the home screen and the wallpaper. */
object Backdrop {
    private var cache: Triple<String, Bitmap, Bitmap>? = null

    fun render(ctx: Context, emblem: Boolean, scene: Looks.Scene = Looks.scene(ctx)): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = minOf(dm.widthPixels, dm.heightPixels)
        val h = maxOf(dm.widthPixels, dm.heightPixels)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), scene.top, scene.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // Soft glows give the glass panels something to refract.
        glow(c, p, w * 0.95f, h * 0.18f, w * 0.75f, scene.glows[0])
        glow(c, p, w * 0.05f, h * 0.55f, w * 0.8f, scene.glows[1])
        glow(c, p, w * 0.7f, h * 0.95f, w * 0.7f, scene.glows[2])
        if (emblem) EmblemView.drawEmblem(c, w / 2f, h * 0.72f, w * 0.13f)
        return bmp
    }

    /** Sharp and blurred versions of the home background (the blurred one sits behind the glass). */
    fun homeLayers(ctx: Context): Pair<Bitmap, Bitmap> {
        val scene = Looks.scene(ctx)
        val key = scene.key + Theme.dark
        cache?.let { if (it.first == key) return it.second to it.third }
        val sharp = render(ctx, emblem = false, scene = scene)
        if (Theme.dark) Canvas(sharp).drawColor(0x59000000)
        // Cheap, smooth blur: shrink a lot, then scale back up with filtering, twice.
        var small = Bitmap.createScaledBitmap(sharp, sharp.width / 24, sharp.height / 24, true)
        small = Bitmap.createScaledBitmap(small, small.width * 3, small.height * 3, true)
        val blurred = Bitmap.createScaledBitmap(small, sharp.width, sharp.height, true)
        Canvas(blurred).drawColor(0x33061A3D)
        cache = Triple(key, sharp, blurred)
        return sharp to blurred
    }

    private fun glow(c: Canvas, p: Paint, x: Float, y: Float, r: Float, color: Int) {
        p.shader = RadialGradient(x, y, r, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }
}
