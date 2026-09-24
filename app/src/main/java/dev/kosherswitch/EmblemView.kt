package dev.kosherswitch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Gold ring with "כשר" in the middle. */
class EmblemView(ctx: Context) : View(ctx) {
    override fun onDraw(canvas: Canvas) {
        drawEmblem(canvas, width / 2f, height / 2f, minOf(width, height) / 2f * 0.84f)
    }

    companion object {
        fun drawEmblem(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Theme.GOLD
                strokeWidth = r * 0.07f
            }
            canvas.drawCircle(cx, cy, r, ring)
            ring.strokeWidth = r * 0.025f
            canvas.drawCircle(cx, cy, r * 0.84f, ring)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Theme.GOLD
                textSize = r * 0.62f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("כשר", cx, cy - (label.descent() + label.ascent()) / 2, label)
        }
    }
}
