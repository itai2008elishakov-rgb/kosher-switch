package dev.kosherswitch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/**
 * Decides whether a picture from the web may be shown in the Kosher Browser, like a kosher computer
 * filter: any picture with a person in it is covered. Two checks, both on the phone:
 * an object detector that spots people (even small or partly visible), and a skin-colour check that
 * catches close-ups the detector might miss. When unsure, the picture stays covered.
 */
object PictureCheck {
    private const val TAG = "KosherPictures"
    private const val MODEL = "efficientdet_lite0.tflite"
    @Volatile private var detector: ObjectDetector? = null

    fun prepare(ctx: Context) {
        if (detector != null) return
        synchronized(this) {
            if (detector != null) return
            detector = runCatching {
                ObjectDetector.createFromOptions(ctx.applicationContext, ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setScoreThreshold(0.18f)
                    .setMaxResults(8)
                    .setCategoryAllowlist(listOf("person"))
                    .build())
            }.onFailure { Log.e(TAG, "Detector failed to load", it) }.getOrNull()
        }
    }

    /** Checks encoded picture bytes. Anything that can't be read (or checked) counts as not safe. */
    fun safe(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        // Only the tiniest pictures are skipped (icons); small thumbnails of people are still covered.
        if (bounds.outWidth <= 16 && bounds.outHeight <= 16) return true
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 320 && bounds.outHeight / (sample * 2) >= 320) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return false
        return safe(bmp)
    }

    @Synchronized
    fun safe(bmp: Bitmap): Boolean {
        val det = detector ?: return false
        val found = runCatching { det.detect(BitmapImageBuilder(bmp).build()) }.getOrNull() ?: return false
        if (found.detections().any { d -> d.categories().any { it.categoryName() == "person" && it.score() >= 0.2f } }) return false
        // Small thumbnails are too small for the detector to be sure, so they get a stricter skin check.
        return skinShare(bmp) < if (maxOf(bmp.width, bmp.height) < 120) 0.08f else 0.16f
    }

    /** How much of the picture is skin-coloured (a close-up of a body shows up here). */
    private fun skinShare(src: Bitmap): Float {
        val b = Bitmap.createScaledBitmap(src, 64, 64, true)
        val px = IntArray(64 * 64)
        b.getPixels(px, 0, 64, 0, 0, 64, 64)
        var skin = 0
        for (c in px) {
            val r = Color.red(c); val g = Color.green(c); val bl = Color.blue(c)
            val y = 0.299 * r + 0.587 * g + 0.114 * bl
            val cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * bl
            val cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * bl
            if (y > 60 && cb in 77.0..127.0 && cr in 136.0..173.0 && r > g && r > bl) skin++
        }
        return skin / px.size.toFloat()
    }
}
