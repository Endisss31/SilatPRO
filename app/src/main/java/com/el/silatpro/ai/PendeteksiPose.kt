package com.el.silatpro.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.el.silatpro.model.DataPose
import com.el.silatpro.model.TitikTubuh
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Mendeteksi pose tubuh menggunakan model YOLOv8-Pose TFLite.
 * Input: Bitmap → [1, 640, 640, 3] Float32
 * Output: [1, 56, 8400] Float32
 *
 * @param konteks Android context
 * @param namaModel Nama file model di assets. Mode realtime memakai YOLOv8n (hemat RAM).
 */
class PendeteksiPose(
    private val konteks: Context,
    private val namaModel: String = MODEL_RINGAN
) {

    private var interpreter: Interpreter? = null
    private var sudahSiap = false
    private val lock = Any()

    companion object {
        private const val TAG = "PendeteksiPose"
        private const val UKURAN_INPUT = 640
        private const val JUMLAH_HASIL = 8400
        private const val JUMLAH_FITUR = 56  // 4(box) + 1(conf) + 17*3(kpts)
        const val MODEL_RINGAN  = "yolov8n_pose_float16.tflite"
        const val MODEL_AKURAT  = "yolov8n_pose_float16.tflite"
        private const val AMBANG_KONFIDEN = 0.30f  // sesuai training conf=0.3
    }

    private val inputBuffer = ByteBuffer.allocateDirect(1 * UKURAN_INPUT * UKURAN_INPUT * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val outputBuffer = Array(1) { Array(JUMLAH_FITUR) { FloatArray(JUMLAH_HASIL) } }
    private val piksel = IntArray(UKURAN_INPUT * UKURAN_INPUT)

    data class LetterboxResult(
        val bitmap: Bitmap,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    private fun letterboxBitmap(src: Bitmap, targetSize: Int = UKURAN_INPUT): LetterboxResult {
        val srcWidth = src.width
        val srcHeight = src.height

        val scale = minOf(
            targetSize.toFloat() / srcWidth,
            targetSize.toFloat() / srcHeight
        )

        val newWidth = (srcWidth * scale).toInt()
        val newHeight = (srcHeight * scale).toInt()

        val resized = Bitmap.createScaledBitmap(src, newWidth, newHeight, true)

        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val padX = (targetSize - newWidth) / 2f
        val padY = (targetSize - newHeight) / 2f

        canvas.drawBitmap(resized, padX, padY, null)
        if (resized != src) resized.recycle()

        return LetterboxResult(output, scale, padX, padY)
    }

    private fun reverseLetterboxKeypoint(x: Float, y: Float, scale: Float, padX: Float, padY: Float): FloatArray {
        val originalX = (x - padX) / scale
        val originalY = (y - padY) / scale
        return floatArrayOf(originalX, originalY)
    }

    /**
     * Inisialisasi model TFLite dengan akselerasi hardware.
     * - 2 CPU threads (YOLOv8n ringan, tidak perlu 4 thread)
     * - NNAPI delegate: memanfaatkan GPU/DSP/NPU jika tersedia di device
     */
    fun inisialisasi() {
        try {
            val opsi = Interpreter.Options().apply {
                setNumThreads(4)
                // Gunakan XNNPACK — akselerasi CPU yang stabil di semua device
                // NNAPI dinonaktifkan karena menyebabkan SIGABRT di beberapa device OPPO/Realme
                setUseXNNPACK(true)
            }
            interpreter = Interpreter(muatModelDariAset(konteks, namaModel), opsi)
            sudahSiap = true
            Log.d(TAG, "YOLOv8n pose model siap (CPU+XNNPACK): $namaModel")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat model pose: ${e.message}")
            sudahSiap = false
        }
    }


    fun apakahSiap(): Boolean = sudahSiap

    /**
     * Deteksi pose dari Bitmap.
     */
    fun deteksi(bitmap: Bitmap): DataPose? {
        synchronized(lock) {
            if (!sudahSiap || interpreter == null) return null

        try {
            // 1. Pre-process: Bitmap → tensor input (Letterbox)
            val letterbox = letterboxBitmap(bitmap, UKURAN_INPUT)
            val bitmapResize = letterbox.bitmap
            inputBuffer.rewind()
            
            bitmapResize.getPixels(piksel, 0, UKURAN_INPUT, 0, 0, UKURAN_INPUT, UKURAN_INPUT)
            
            for (p in piksel) {
                inputBuffer.putFloat(((p shr 16) and 0xFF) / 255.0f)
                inputBuffer.putFloat(((p shr 8) and 0xFF) / 255.0f)
                inputBuffer.putFloat((p and 0xFF) / 255.0f)
            }
            bitmapResize.recycle()

            // 2. Jalankan inference
            interpreter!!.run(inputBuffer, outputBuffer)

            // 3. Post-process: ambil deteksi terbaik dan reverse koordinat
            return pascaProses(outputBuffer, letterbox)
        } catch (e: Exception) {
            Log.e(TAG, "Error saat deteksi: ${e.message}")
            return null
        }
        }
    }



    /**
     * Post-process: ambil deteksi dengan konfiden tertinggi, ekstrak 17 keypoints.
     */
    private fun pascaProses(output: Array<Array<FloatArray>>, letterbox: LetterboxResult): DataPose? {
        val layer = output[0] // [56, 8400]

        var konfidenMaks = 0f
        var indeksTerbaik = -1

        // Cari deteksi dengan confidence tertinggi
        for (i in 0 until JUMLAH_HASIL) {
            val konfiden = layer[4][i]
            if (konfiden > konfidenMaks) {
                konfidenMaks = konfiden
                indeksTerbaik = i
            }
        }

        if (indeksTerbaik == -1 || konfidenMaks < AMBANG_KONFIDEN) return null

        // Ekstrak 17 keypoints dan kembalikan posisinya dari koordinat letterbox ke frame asli
        val daftarTitik = mutableListOf<TitikTubuh>()
        for (k in 0 until 17) {
            val baseIdx = 5 + (k * 3)
            val bx = layer[baseIdx][indeksTerbaik]
            val by = layer[baseIdx + 1][indeksTerbaik]
            val conf = layer[baseIdx + 2][indeksTerbaik]

            val originalPoint = reverseLetterboxKeypoint(bx, by, letterbox.scale, letterbox.padX, letterbox.padY)

            daftarTitik.add(TitikTubuh(indeks = k, x = originalPoint[0], y = originalPoint[1], konfiden = conf))
        }

        return DataPose(titikTubuh = daftarTitik, konfiden = konfidenMaks)
    }

    /**
     * Muat file model dari folder assets.
     */
    private fun muatModelDariAset(konteks: Context, namaFile: String): MappedByteBuffer {
        val deskriptor = konteks.assets.openFd(namaFile)
        val inputStream = FileInputStream(deskriptor.fileDescriptor)
        val channel = inputStream.channel
        val mulai = deskriptor.startOffset
        val panjang = deskriptor.declaredLength
        return channel.map(FileChannel.MapMode.READ_ONLY, mulai, panjang)
    }

    /**
     * Tutup interpreter.
     */
    fun tutup() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
            sudahSiap = false
        }
    }
}
