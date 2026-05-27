package com.el.silatpro.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.el.silatpro.model.DataPose
import com.el.silatpro.model.TitikTubuh
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PoseDetector — YOLOv8n-Pose TFLite inference.
 *
 * Pipeline:
 *   Bitmap → Letterbox (640×640) → Float32 tensor → YOLO → parse output
 *   → reverse letterbox → DataPose(17 keypoints)
 *
 * Output tensor shape: [1, 56, 8400]
 *   row 0-3  : box (cx, cy, w, h)
 *   row 4    : person confidence
 *   row 5-55 : 17 keypoints × (x, y, conf)
 */
class PoseDetector(private val context: Context) {

    companion object {
        private const val TAG           = "PoseDetector"
        const val MODEL_NAME           = "yolov8n_pose_float32.tflite"
        private const val INPUT_SIZE   = 640
        private const val NUM_ANCHORS  = 8400
        private const val NUM_ROWS     = 56   // 4 box + 1 conf + 17*3 kpts
        private const val PERSON_CONF  = 0.30f
        private const val KPT_CONF     = 0.30f
    }

    private var interpreter: Interpreter? = null
    var isReady = false; private set

    // Pre-allocated buffers — tidak ada alokasi ulang saat inference
    private val inputBuf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        .apply { order(ByteOrder.nativeOrder()) }
    private val outputBuf = Array(1) { Array(NUM_ROWS) { FloatArray(NUM_ANCHORS) } }
    private val pixels    = IntArray(INPUT_SIZE * INPUT_SIZE)

    fun init() {
        try {
            val bytes = context.assets.open(MODEL_NAME).use { it.readBytes() }
            val buf   = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes); buf.rewind()

            val opts = Interpreter.Options().apply {
                numThreads = 4
                setUseXNNPACK(true)
            }
            interpreter = Interpreter(buf, opts)
            isReady = true
            Log.d(TAG, "PoseDetector ready: $MODEL_NAME")
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}")
            isReady = false
        }
    }

    /**
     * Deteksi pose dari Bitmap (ukuran berapa pun).
     * @return DataPose dengan 17 keypoints dalam koordinat frame asli, atau null
     */
    fun detect(bitmap: Bitmap): DataPose? {
        if (!isReady || interpreter == null) return null
        return try {
            // 1. Letterbox
            val lb = Letterbox.resize(bitmap, INPUT_SIZE)

            // 2. Bitmap → Float32 tensor (RGB / 255.0)
            inputBuf.rewind()
            lb.bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            for (p in pixels) {
                inputBuf.putFloat(((p shr 16) and 0xFF) / 255f)
                inputBuf.putFloat(((p shr  8) and 0xFF) / 255f)
                inputBuf.putFloat(( p         and 0xFF) / 255f)
            }
            lb.bitmap.recycle()

            // 3. Inference
            interpreter!!.run(inputBuf, outputBuf)

            // 4. Post-process
            parseOutput(outputBuf, lb)
        } catch (e: Exception) {
            Log.e(TAG, "Detect error: ${e.message}")
            null
        }
    }

    private fun parseOutput(
        out: Array<Array<FloatArray>>,
        lb: Letterbox.Result
    ): DataPose? {
        val layer = out[0] // [56, 8400]

        // Cari anchor dengan person confidence tertinggi
        var maxConf = PERSON_CONF
        var bestIdx = -1
        for (i in 0 until NUM_ANCHORS) {
            val c = layer[4][i]
            if (c > maxConf) { maxConf = c; bestIdx = i }
        }
        if (bestIdx == -1) return null

        // Parse 17 keypoints, reverse letterbox ke koordinat frame asli
        val keypoints = ArrayList<TitikTubuh>(17)
        for (k in 0..16) {
            val base = 5 + k * 3
            val kx   = layer[base    ][bestIdx]
            val ky   = layer[base + 1][bestIdx]
            val kc   = layer[base + 2][bestIdx]

            // Reverse letterbox: letterbox coords → original frame coords
            val orig = Letterbox.reversePoint(kx, ky, lb)
            keypoints.add(TitikTubuh(indeks = k, x = orig[0], y = orig[1], konfiden = kc))
        }

        return DataPose(titikTubuh = keypoints, konfiden = maxConf)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isReady = false
    }
}
