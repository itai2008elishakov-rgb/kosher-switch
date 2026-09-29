package dev.kosherswitch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** The Kosher Switch seal: a gold certificate stamp with כשר in the middle. */
class EmblemView(ctx: Context) : View(ctx) {
    override fun onDraw(canvas: Canvas) {
        drawEmblem(context, canvas, width / 2f, height / 2f, minOf(width, height) / 2f * 0.96f)
    }

    companion object {
        @Volatile private var seal: Bitmap? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        private fun seal(ctx: Context): Bitmap =
            seal ?: BitmapFactory.decodeResource(ctx.resources, R.drawable.seal).also { seal = it }

        /** Draws the seal filling a circle of radius [r] around ([cx], [cy]). */
        fun drawEmblem(ctx: Context, canvas: Canvas, cx: Float, cy: Float, r: Float) {
            canvas.drawBitmap(seal(ctx), null, RectF(cx - r, cy - r, cx + r, cy + r), paint)
        }
    }
}
