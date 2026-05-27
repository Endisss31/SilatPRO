package com.el.silatpro.ai

import android.content.Context
import android.util.Log
import com.el.silatpro.model.ScalerLabelGlobal
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MLPClassifier — global movement classification.
 *
 * Input : FloatArray(34) — sudah di-scale oleh Normalizer
 * Output: label kelas + confidence
 *
 * Label urutan (HARUS sama dengan training):
 *   0  Pukulan2_Kanan    5  Tangkisan1_Kiri
 *   1  Pukulan2_Kiri     6  Tangkisan2_Kanan
 *   2  Pukulan4_Kanan    7  Tangkisan2_Kiri
 *   3  Pukulan4_Kiri     8  Tangkisan3_Kanan
 *   4  Tangkisan1_Kanan  9  Tangkisan3_Kiri
 */
class MLPClassifier(private val context: Context) {

    companion object {
        private const val TAG          = "MLPClassifier"
        private const val MODEL_NAME   = "GlobalMovement/mlp_global_v8n.tflite"
        const val THRESHOLD            = 0.70f
        const val LABEL_UNKNOWN        = "Gerakan tidak dikenali"
    }

    private var interpreter: Interpreter? = null
    var isReady = false; private set
    private var numClasses = 0

    // Pre-allocated input buffer (34 float × 4 bytes)
    private val inputBuf = ByteBuffer.allocateDirect(34 * 4).order(ByteOrder.nativeOrder())

    data class Result(
        val label: String,
        val confidence: Float,
        val allProbs: FloatArray
    )

    fun init(): Boolean {
        return try {
            val bytes = context.assets.open(MODEL_NAME).use { it.readBytes() }
            val buf   = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes); buf.rewind()

            val opts = Interpreter.Options().apply {
                numThreads = 2
                setUseXNNPACK(true)
            }
            interpreter = Interpreter(buf, opts)

            // Baca jumlah kelas DARI model (dinamis, bukan hardcoded)
            numClasses = interpreter!!.getOutputTensor(0).shape()[1]
            val inputDim = interpreter!!.getInputTensor(0).shape()[1]

            isReady = true
            Log.d(TAG, "MLPClassifier ready: $MODEL_NAME | input=$inputDim | classes=$numClasses")
            Log.d(TAG, "Labels: ${ScalerLabelGlobal.labels.take(numClasses).toList()}")

            if (numClasses != ScalerLabelGlobal.jumlahKelas) {
                Log.w(TAG, "⚠ Model output ($numClasses) ≠ ScalerLabelGlobal (${ScalerLabelGlobal.jumlahKelas})!")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.javaClass.simpleName} — ${e.message}")
            false
        }
    }

    /**
     * Klasifikasikan 34 fitur yang sudah di-normalize+scale.
     */
    fun classify(scaledFeatures: FloatArray): Result {
        if (!isReady || interpreter == null) {
            return Result(LABEL_UNKNOWN, 0f, FloatArray(0))
        }

        // Isi input buffer
        inputBuf.rewind()
        val dim = minOf(scaledFeatures.size, 34)
        for (i in 0 until dim) inputBuf.putFloat(scaledFeatures[i])
        inputBuf.rewind()

        // Run inference
        val outputArr = Array(1) { FloatArray(numClasses) }
        interpreter!!.run(inputBuf, outputArr)

        val probs = outputArr[0]

        // Log diagnostic (hapus setelah verified)
        val probStr = probs.mapIndexed { i, p ->
            "${ScalerLabelGlobal.labels.getOrElse(i){"cls$i"}.replace("_","")}=${(p*100).toInt()}%"
        }.joinToString(" ")
        Log.d(TAG, "PROBS: $probStr")

        // Cari argmax
        var maxConf = -1f; var maxIdx = -1
        for (i in probs.indices) { if (probs[i] > maxConf) { maxConf = probs[i]; maxIdx = i } }

        val label = if (maxConf >= THRESHOLD)
            ScalerLabelGlobal.labels.getOrElse(maxIdx) { "cls$maxIdx" }
        else
            LABEL_UNKNOWN

        return Result(label, maxConf, probs)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isReady = false
    }
}
