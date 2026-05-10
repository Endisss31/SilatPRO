package com.el.silatpro.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Mesin klasifikasi untuk mode Open Camera (global).
 * Menggunakan model TFLite Global (model_global_mlp.tflite) + StandardScaler
 * yang sudah di-embed sebagai konstanta dari global_scaler.pkl training.
 *
 * Flow: fitur 34 (normalized) → StandardScaler → MLP TFLite → softmax → label
 */
class MesinKlasifikasi(private val konteks: Context) {

    companion object {
        private const val TAG = "MesinKlasifikasi"
        private const val JUMLAH_FITUR = 34
        private const val JUMLAH_KELAS = 10
        private const val AMBANG_KONFIDEN = 0.70f   // 70% sesuai ketentuan

        // === StandardScaler dari global_scaler.pkl (training) ===
        // Diekstrak dengan script Python dari scaler.mean_ dan scaler.scale_
        // Urutan: x0,y0,x1,y1,...,x16,y16 (34 nilai, COCO 17 keypoint)
        private val SCALER_MEAN = floatArrayOf(
            -0.03124308f,  -1.20373208f,  // 0  nose
             0.05666338f,  -1.31635167f,  // 1  left_eye
            -0.14504454f,  -1.31262917f,  // 2  right_eye
             0.21926405f,  -1.25274766f,  // 3  left_ear
            -0.32682752f,  -1.23831859f,  // 4  right_ear
             0.41792515f,  -0.67626064f,  // 5  left_shoulder
            -0.50093278f,  -0.63830166f,  // 6  right_shoulder
             0.85208593f,  -0.28356158f,  // 7  left_elbow
            -0.76537178f,  -0.19856007f,  // 8  right_elbow
             0.84190639f,  -0.24062342f,  // 9  left_wrist
            -0.74734138f,  -0.19924445f,  // 10 right_wrist
             0.45043870f,   0.65175862f,  // 11 left_hip
            -0.36731219f,   0.66284585f,  // 12 right_hip
             1.32369148f,   1.10081402f,  // 13 left_knee
            -1.17932781f,   1.12756614f,  // 14 right_knee
             1.79062666f,   2.14989084f,  // 15 left_ankle
            -1.55695225f,   2.26514246f   // 16 right_ankle
        )

        private val SCALER_SCALE = floatArrayOf(
            0.45086230f,  0.25760755f,  // 0  nose
            0.48683637f,  0.26588502f,  // 1  left_eye
            0.48438382f,  0.26156461f,  // 2  right_eye
            0.48739988f,  0.25662335f,  // 3  left_ear
            0.48280296f,  0.25153702f,  // 4  right_ear
            0.32690599f,  0.26230234f,  // 5  left_shoulder
            0.31956444f,  0.24147858f,  // 6  right_shoulder
            0.20536008f,  0.59496765f,  // 7  left_elbow
            0.22335525f,  0.66082728f,  // 8  right_elbow
            0.54581090f,  0.82344910f,  // 9  left_wrist
            0.54625885f,  0.84026623f,  // 10 right_wrist
            0.32782304f,  0.22839098f,  // 11 left_hip
            0.32216363f,  0.24796392f,  // 12 right_hip
            0.67490568f,  0.48181399f,  // 13 left_knee
            0.56497743f,  0.54271814f,  // 14 right_knee
            0.86245270f,  0.86678741f,  // 15 left_ankle
            0.89617494f,  0.94543721f   // 16 right_ankle
        )
    }

    private var interpreter: Interpreter? = null
    private var sudahSiap = false
    private val labelList = mutableListOf<String>()

    /**
     * Muat model klasifikasi dan label dari assets.
     */
    fun inisialisasi(): Boolean {
        return try {
            // Muat label dari labels_global.txt
            muatLabel()

            // Muat model TFLite
            val assetFileDescriptor = konteks.assets.openFd("GlobalMovement/model_global_mlp.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val opsi = Interpreter.Options().apply {
                numThreads = 2
            }
            interpreter = Interpreter(buffer, opsi)

            sudahSiap = true
            Log.d(TAG, "Klasifikasi Global TFLite dimuat. Jumlah label: ${labelList.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat klasifikasi global: ${e.message}")
            false
        }
    }

    private fun muatLabel() {
        try {
            labelList.clear()
            konteks.assets.open("GlobalMovement/labels_global.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        labelList.add(trimmed)
                    }
                }
            }
            Log.d(TAG, "Label dimuat: $labelList")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat label: ${e.message}")
            // Fallback: label hardcoded sesuai labels_global.txt
            labelList.addAll(listOf(
                "Pukulan2Kanan", "Pukulan2Kiri",
                "Pukulan4Kanan", "Pukulan4Kiri",
                "Tangkisan1Kanan", "Tangkisan1Kiri",
                "Tangkisan3Kanan", "Tangkisan3Kiri",
                "Tendangan2Kanan", "Tendangan2Kiri"
            ))
        }
    }

    /**
     * Terapkan StandardScaler: x_scaled = (x_normalized - mean) / scale
     */
    private fun terapkanScaler(fiturNormalized: FloatArray): FloatArray {
        val hasil = FloatArray(JUMLAH_FITUR)
        for (i in 0 until JUMLAH_FITUR) {
            val scale = if (SCALER_SCALE[i] == 0f) 1f else SCALER_SCALE[i]
            hasil[i] = (fiturNormalized[i] - SCALER_MEAN[i]) / scale
        }
        return hasil
    }

    /**
     * Klasifikasikan fitur FloatArray(34) → (label, probabilitas)
     * Fitur masuk sudah dalam bentuk normalisasi body-relative (belum di-scale).
     * @return Pair(label gerakan, probabilitas) atau null jika tidak dikenali / di bawah threshold
     */
    fun klasifikasi(fiturNormalized: FloatArray): Pair<String, Double>? {
        if (!sudahSiap || interpreter == null) return null
        if (fiturNormalized.size != JUMLAH_FITUR) {
            Log.w(TAG, "Jumlah fitur tidak sesuai: ${fiturNormalized.size}, expected $JUMLAH_FITUR")
            return null
        }

        // Terapkan StandardScaler sesuai training
        val fiturScaled = terapkanScaler(fiturNormalized)

        val inputBuffer = ByteBuffer.allocateDirect(1 * JUMLAH_FITUR * 4).order(ByteOrder.nativeOrder())
        for (f in fiturScaled) {
            inputBuffer.putFloat(f)
        }
        inputBuffer.rewind()

        val jumlahKelas = if (labelList.size > 0) labelList.size else JUMLAH_KELAS
        val output = Array(1) { FloatArray(jumlahKelas) }
        interpreter?.run(inputBuffer, output)
        val probabilitas = output[0]

        var probTertinggi = -1f
        var indeksTertinggi = -1
        for (i in probabilitas.indices) {
            if (probabilitas[i] > probTertinggi) {
                probTertinggi = probabilitas[i]
                indeksTertinggi = i
            }
        }

        // Threshold 70% sesuai ketentuan
        if (probTertinggi < AMBANG_KONFIDEN) {
            return Pair("Gerakan tidak dikenali / Unknown", probTertinggi.toDouble())
        }

        val label = labelList.getOrNull(indeksTertinggi) ?: "Tidak Dikenali"
        return Pair(label, probTertinggi.toDouble())
    }

    fun tutup() {
        interpreter?.close()
        interpreter = null
        sudahSiap = false
    }
}
