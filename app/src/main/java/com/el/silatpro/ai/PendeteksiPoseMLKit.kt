package com.el.silatpro.ai

import android.graphics.Bitmap
import com.el.silatpro.model.DataPose
import com.el.silatpro.model.TitikTubuh
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Pendeteksi pose menggunakan Google ML Kit Pose Detection.
 * Lebih stabil dan responsif dibandingkan YOLOv8 untuk real-time.
 */
class PendeteksiPoseMLKit {

    private val opsi = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .setPreferredHardwareConfigs(PoseDetectorOptions.CPU_GPU) // Gunakan GPU jika tersedia
        .build()


    private val detector = PoseDetection.getClient(opsi)

    /**
     * Deteksi pose dari InputImage (lebih cepat untuk camera stream).
     */
    suspend fun deteksiImage(image: InputImage): DataPose? = suspendCoroutine { kelanjutan ->
        detector.process(image)
            .addOnSuccessListener { pose ->
                kelanjutan.resume(konversiKeDataPose(pose))
            }
            .addOnFailureListener {
                kelanjutan.resume(null)
            }
    }

    /**
     * Deteksi pose dari Bitmap secara asynchronous menggunakan coroutine.
     */
    suspend fun deteksi(bitmap: Bitmap): DataPose? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return deteksiImage(image)
    }

    /**
     * Mapping dari titik ML Kit (33 titik) ke standar COCO (17 titik) yang digunakan aplikasi.
     */
    private fun konversiKeDataPose(pose: Pose): DataPose? {
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) return null

        val daftarTitik = mutableListOf<TitikTubuh>()
        
        // Mapping manual
        tambahTitik(daftarTitik, pose, PoseLandmark.NOSE, TitikTubuh.HIDUNG)
        tambahTitik(daftarTitik, pose, PoseLandmark.LEFT_EYE, TitikTubuh.MATA_KIRI)
        tambahTitik(daftarTitik, pose, PoseLandmark.RIGHT_EYE, TitikTubuh.MATA_KANAN)
        tambahTitik(daftarTitik, pose, PoseLandmark.LEFT_EAR, TitikTubuh.TELINGA_KIRI)
        tambahTitik(daftarTitik, pose, PoseLandmark.RIGHT_EAR, TitikTubuh.TELINGA_KANAN)
        tambahTitik(daftarTitik, pose, PoseLandmark.LEFT_SHOULDER, TitikTubuh.BAHU_KIRI)
        tambahTitik(daftarTitik, pose, PoseLandmark.RIGHT_SHOULDER, TitikTubuh.BAHU_KANAN)
        tambahTitik(daftarTitik, pose, PoseLandmark.LEFT_ELBOW, TitikTubuh.SIKU_KIRI)
        tambahTitik(daftarTitik, pose, PoseLandmark.RIGHT_ELBOW, TitikTubuh.SIKU_KANAN)
        tambahTitik(daftarTitik, pose, PoseLandmark.LEFT_WRIST, TitikTubuh.PERGELANGAN_KIRI)
        tambahTitik(daftarTitik, pose, PoseLandmark.RIGHT_WRIST, TitikTubuh.PERGELANGAN_KANAN)
        tambahTitik(daftarTitik, pose, PoseLandmark.LEFT_HIP, TitikTubuh.PINGGUL_KIRI)
        tambahTitik(daftarTitik, pose, PoseLandmark.RIGHT_HIP, TitikTubuh.PINGGUL_KANAN)
        tambahTitik(daftarTitik, pose, PoseLandmark.LEFT_KNEE, TitikTubuh.LUTUT_KIRI)
        tambahTitik(daftarTitik, pose, PoseLandmark.RIGHT_KNEE, TitikTubuh.LUTUT_KANAN)
        tambahTitik(daftarTitik, pose, PoseLandmark.LEFT_ANKLE, TitikTubuh.PERGELANGAN_KAKI_KIRI)
        tambahTitik(daftarTitik, pose, PoseLandmark.RIGHT_ANKLE, TitikTubuh.PERGELANGAN_KAKI_KANAN)

        return DataPose(daftarTitik, 1.0f) // ML Kit tidak memberikan skor total tunggal
    }

    private fun tambahTitik(
        list: MutableList<TitikTubuh>,
        pose: Pose,
        mlKitIdx: Int,
        cocoIdx: Int
    ) {
        val landmark = pose.getPoseLandmark(mlKitIdx)
        if (landmark != null) {
            list.add(
                TitikTubuh(
                    indeks = cocoIdx,
                    x = landmark.position.x,
                    y = landmark.position.y,
                    konfiden = landmark.inFrameLikelihood
                )
            )
        }
    }

    fun tutup() {
        detector.close()
    }
}
