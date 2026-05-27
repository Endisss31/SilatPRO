package com.el.silatpro.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

/**
 * Letterbox preprocessing — identik dengan Ultralytics YOLOv8.
 *
 * Mempertahankan aspect ratio dengan padding abu-abu (114,114,114).
 * Simpan scale, padX, padY untuk reverse-mapping keypoint ke koordinat asli.
 */
object Letterbox {

    data class Result(
        val bitmap: Bitmap,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    /**
     * Resize bitmap dengan letterbox ke [targetSize x targetSize].
     * Padding menggunakan warna (114,114,114) sesuai Ultralytics default.
     */
    fun resize(src: Bitmap, targetSize: Int = 640): Result {
        val scale = minOf(
            targetSize.toFloat() / src.width,
            targetSize.toFloat() / src.height
        )

        val newW = (src.width  * scale).toInt()
        val newH = (src.height * scale).toInt()

        val resized = Bitmap.createScaledBitmap(src, newW, newH, true)

        // Padding simetris (seperti Ultralytics auto=False)
        val padX = (targetSize - newW) / 2f
        val padY = (targetSize - newH) / 2f

        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // Fill dengan (114,114,114) — Ultralytics default pad color
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(resized, padX, padY, null)

        if (resized != src) resized.recycle()

        return Result(out, scale, padX, padY)
    }

    /**
     * Reverse-mapping: koordinat letterbox → koordinat frame asli.
     *   x_original = (x_letterbox - padX) / scale
     *   y_original = (y_letterbox - padY) / scale
     */
    fun reversePoint(x: Float, y: Float, result: Result): FloatArray {
        return floatArrayOf(
            (x - result.padX) / result.scale,
            (y - result.padY) / result.scale
        )
    }
}
