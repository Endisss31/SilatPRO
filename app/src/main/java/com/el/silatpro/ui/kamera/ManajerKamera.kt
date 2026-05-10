package com.el.silatpro.ui.kamera

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Mengelola lifecycle CameraX: Preview + ImageAnalysis.
 *
 * Terinspirasi dari pola CameraManager profesional dengan:
 * - Sistem profile resolusi dengan fallback otomatis (DEFAULT → LIVE → CONSERVATIVE)
 * - Resolusi square (1:1) untuk analisis pose agar konsisten dengan tampilan 1:1
 * - Exposure compensation untuk stabilitas brightness
 * - Thread-safe binding dengan timeout
 *
 * Tidak menyertakan VideoCapture (tidak dibutuhkan SilatPRO).
 */
class ManajerKamera(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFrameSiap: (ImageProxy) -> Unit
) {
    // ──────────────────────────────────────────────────────────────
    // Mode performa
    // ──────────────────────────────────────────────────────────────

    enum class ModePerforma {
        /** Prioritas kualitas — resolusi tinggi, cocok untuk HP kencang */
        DEFAULT,

        /** Seimbang — kualitas cukup, frame rate lebih tinggi */
        LIVE,

        /** Konservatif — cocok untuk HP low-end, prioritas stabilitas */
        KONSERVATIF
    }

    // ──────────────────────────────────────────────────────────────
    // Profile resolusi
    // ──────────────────────────────────────────────────────────────

    private data class ProfilResolusi(
        val previewBelakang: List<Size>,
        val previewDepan: List<Size>,
        val analisiBelakang: List<Size>,
        val analisiDepan: List<Size>
    )

    companion object {
        private const val TAG = "ManajerKamera"
        private const val TIMEOUT_BIND_MS = 5000L

        /**
         * DEFAULT: Resolusi 9:16 portrait penuh — seperti kamera klasifikasi.
         */
        private val PROFIL_DEFAULT = ProfilResolusi(
            previewBelakang = listOf(
                Size(1080, 1920), // Full HD portrait
                Size(720, 1280),
                Size(480, 854)
            ),
            previewDepan = listOf(
                Size(720, 1280),
                Size(480, 854)
            ),
            analisiBelakang = listOf(
                Size(720, 1280),  // 9:16 portrait — cocok untuk analisis full-body
                Size(480, 854),
                Size(480, 640),
                Size(320, 240)
            ),
            analisiDepan = listOf(
                Size(720, 1280),
                Size(480, 854),
                Size(320, 240)
            )
        )

        /**
         * LIVE: 9:16 seimbang — frame rate tinggi, tetap portrait penuh.
         */
        private val PROFIL_LIVE = ProfilResolusi(
            previewBelakang = listOf(
                Size(720, 1280),
                Size(480, 854)
            ),
            previewDepan = listOf(
                Size(720, 1280),
                Size(480, 854)
            ),
            analisiBelakang = listOf(
                Size(480, 854),
                Size(320, 480),
                Size(240, 320)
            ),
            analisiDepan = listOf(
                Size(480, 854),
                Size(320, 480)
            )
        )

        /**
         * KONSERVATIF: HP low-end, 9:16 minimal — prioritas stabilitas.
         */
        private val PROFIL_KONSERVATIF = ProfilResolusi(
            previewBelakang = listOf(
                Size(480, 854),
                Size(320, 480)
            ),
            previewDepan = listOf(
                Size(480, 854),
                Size(320, 480)
            ),
            analisiBelakang = listOf(
                Size(320, 480),
                Size(240, 320)
            ),
            analisiDepan = listOf(
                Size(320, 480)
            )
        )
    }


    // ──────────────────────────────────────────────────────────────
    // State
    // ──────────────────────────────────────────────────────────────

    private var cameraProvider: ProcessCameraProvider? = null
    private val eksekutorKamera: ExecutorService = Executors.newSingleThreadExecutor()
    private val eksekutorMain = ContextCompat.getMainExecutor(context)

    private var kameraAktif: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var modePerforma = ModePerforma.DEFAULT

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    /** Mulai kamera — panggil setelah izin kamera diberikan */
    fun mulai() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                ikatUseCaseDiMainThread()
            } catch (e: Exception) {
                Log.e(TAG, "Inisialisasi kamera gagal: ${e.message}")
            }
        }, eksekutorMain)
    }

    /** Hentikan semua use case kamera */
    fun hentikan() {
        cameraProvider?.unbindAll()
        kameraAktif = null
    }

    /** Toggle kamera depan ↔ belakang */
    fun gantiKamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        ikatUseCaseDiMainThread()
    }

    /** Apakah kamera depan yang aktif? */
    fun adalahKameraDepan(): Boolean = lensFacing == CameraSelector.LENS_FACING_FRONT

    /**
     * Ubah mode performa.
     * Jika kamera sudah berjalan, akan rebind otomatis dengan profil baru.
     */
    fun setModePerforma(mode: ModePerforma) {
        if (modePerforma == mode) return
        modePerforma = mode
        if (cameraProvider != null) ikatUseCaseDiMainThread()
    }

    /** Lepas semua resource — panggil di onDestroy */
    fun lepas() {
        hentikan()
        eksekutorKamera.shutdown()
    }

    // ──────────────────────────────────────────────────────────────
    // Internal: Binding dengan fallback resolusi
    // ──────────────────────────────────────────────────────────────

    private fun ikatUseCases(): Boolean {
        val provider = cameraProvider ?: run {
            Log.w(TAG, "CameraProvider belum siap")
            return false
        }

        val selektor = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val rotasi = previewView.display?.rotation ?: Surface.ROTATION_0

        // Coba semua kandidat profil + pasangan resolusi secara berurutan
        for ((namaProfile, pasanganResolusi) in bangunKandidatResolusi()) {
            for ((ukuranPreview, ukuranAnalisis) in pasanganResolusi) {
                try {
                    val preview = Preview.Builder()
                        .setTargetRotation(rotasi)
                        .setTargetResolution(ukuranPreview)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val analisis = ImageAnalysis.Builder()
                        .setTargetRotation(rotasi)
                        .setTargetResolution(ukuranAnalisis)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                        .also { it.setAnalyzer(eksekutorKamera, onFrameSiap) }

                    provider.unbindAll()
                    kameraAktif = provider.bindToLifecycle(
                        lifecycleOwner, selektor, preview, analisis
                    )

                    terapkanEksposur()

                    Log.d(
                        TAG,
                        "Kamera berhasil diikat. " +
                        "Profil=$namaProfile | " +
                        "Preview=${ukuranPreview.width}×${ukuranPreview.height} | " +
                        "Analisis=${ukuranAnalisis.width}×${ukuranAnalisis.height}"
                    )
                    return true

                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Gagal ikat dengan profil=$namaProfile, " +
                        "Preview=${ukuranPreview.width}×${ukuranPreview.height}, " +
                        "Analisis=${ukuranAnalisis.width}×${ukuranAnalisis.height}: ${e.message}"
                    )
                    // Lanjut ke resolusi berikutnya
                }
            }
        }

        Log.e(TAG, "Semua kandidat resolusi gagal diikat!")
        return false
    }

    private fun ikatUseCaseDiMainThread(): Boolean {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return ikatUseCases()
        }

        val latch = CountDownLatch(1)
        var berhasil = false
        eksekutorMain.execute {
            try {
                berhasil = ikatUseCases()
            } finally {
                latch.countDown()
            }
        }

        val selesai = latch.await(TIMEOUT_BIND_MS, TimeUnit.MILLISECONDS)
        if (!selesai) Log.e(TAG, "Timeout menunggu bind kamera di main thread")
        return selesai && berhasil
    }

    // ──────────────────────────────────────────────────────────────
    // Internal: Bangun pasangan kandidat resolusi
    // ──────────────────────────────────────────────────────────────

    private fun bangunKandidatResolusi(): List<Pair<String, List<Pair<Size, Size>>>> {
        return kandidatProfil().map { (nama, profil) ->
            nama to bangunPasanganResolusi(profil)
        }
    }

    private fun bangunPasanganResolusi(profil: ProfilResolusi): List<Pair<Size, Size>> {
        val pasangan = mutableListOf<Pair<Size, Size>>()
        val resolusiPreview = resolusiPreviewTerpilih(profil)
        val resolusiAnalisis = resolusiAnalisisTerpilih(profil)

        // Kombinasi: setiap preview × setiap analisis (prioritas dari kiri ke kanan)
        for (preview in resolusiPreview) {
            for (analisis in resolusiAnalisis) {
                pasangan += preview to analisis
            }
        }
        return pasangan
    }

    private fun resolusiPreviewTerpilih(profil: ProfilResolusi): List<Size> {
        return if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            profil.previewDepan
        } else {
            profil.previewBelakang
        }
    }

    private fun resolusiAnalisisTerpilih(profil: ProfilResolusi): List<Size> {
        return if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            profil.analisiDepan
        } else {
            profil.analisiBelakang
        }
    }

    private fun kandidatProfil(): List<Pair<String, ProfilResolusi>> {
        return when (modePerforma) {
            ModePerforma.DEFAULT -> listOf(
                "default" to PROFIL_DEFAULT,
                "live" to PROFIL_LIVE
            )
            ModePerforma.LIVE -> listOf(
                "live" to PROFIL_LIVE,
                "konservatif" to PROFIL_KONSERVATIF
            )
            ModePerforma.KONSERVATIF -> listOf(
                "konservatif" to PROFIL_KONSERVATIF
            )
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Internal: Eksposur
    // ──────────────────────────────────────────────────────────────

    /**
     * Set eksposur ke nilai netral (0) untuk stabilitas brightness.
     * Beberapa kamera depan bisa menjadi terlalu gelap jika eksposur dipaksa positif.
     */
    private fun terapkanEksposur() {
        val kamera = kameraAktif ?: return
        val stateEksposur = kamera.cameraInfo.exposureState
        if (!stateEksposur.isExposureCompensationSupported) return
        if (stateEksposur.exposureCompensationIndex == 0) return

        kamera.cameraControl.setExposureCompensationIndex(0)
        Log.d(TAG, "Eksposur di-reset ke 0 untuk stabilitas")
    }
}
