package com.el.silatpro

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.el.silatpro.ai.MesinKlasifikasi
import com.el.silatpro.ai.PendeteksiPose
import com.el.silatpro.ai.PendeteksiPoseMLKit
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.async
import com.el.silatpro.ai.PenstabilPose
import com.el.silatpro.databinding.ActivityKlasifikasiGerakanBinding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity Kamera Terbuka - mode deteksi global tanpa evaluasi spesifik.
 */
class ActivityKlasifikasiGerakan : AppCompatActivity() {

    private lateinit var binding: ActivityKlasifikasiGerakanBinding

    private lateinit var eksekutorKamera: ExecutorService
    private lateinit var pendeteksi: PendeteksiPose
    private lateinit var pendeteksiMLKit: PendeteksiPoseMLKit
    private lateinit var penstabilYolo: PenstabilPose
    private lateinit var penstabilMLKit: PenstabilPose
    private lateinit var mesinKlasifikasi: MesinKlasifikasi

    private var providerKamera: ProcessCameraProvider? = null
    @Volatile private var aktif = false
    @Volatile private var sedangMemproses = false
    @Volatile private var yoloSedangMemproses = false
    private var waktuYoloTerakhir = 0L
    private var waktuFpsTerakhir = 0L
    private var jumlahFrameFps = 0

    companion object {
        // Kamera 9:16 portrait agar mengisi layar penuh (fillCenter)
        private val UKURAN_KAMERA = Size(720, 1280)
        // YOLOv8x lebih berat → interval lebih panjang untuk kurangi GC pressure
        private const val INTERVAL_YOLO_MS = 1000L
    }

    // ── State kamera ──────────────────────────────────────────────
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    // Cache bitmap untuk YOLO agar tidak selalu alokasi baru → kurangi GC
    @Volatile private var yoloBitmapCache: Bitmap? = null

    private val izinKamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { diberikan ->
        if (diberikan) {
            mulaiKamera()
        } else {
            Toast.makeText(this, R.string.izin_kamera_dibutuhkan, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKlasifikasiGerakanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eksekutorKamera = Executors.newSingleThreadExecutor()
        pendeteksi = PendeteksiPose(this, PendeteksiPose.MODEL_RINGAN)
        pendeteksiMLKit = PendeteksiPoseMLKit()
        penstabilYolo = PenstabilPose()
        penstabilMLKit = PenstabilPose(minCutoff = 0.1f, beta = 0.01f, dCutoff = 1.0f)
        mesinKlasifikasi = MesinKlasifikasi(this)

        // Atur overlay ke mode fillCenter (sesuai PreviewView kamera klasifikasi)
        binding.overlayPose.setModeFillCenter(true)
        binding.overlayPose.setKameraDepan(false)

        // Tampilkan status loading
        binding.txtGerakanTerdeteksi.text = "Memuat model..."
        binding.txtKonfiden.text = ""

        // Inisialisasi model di background — JANGAN di main thread
        lifecycleScope.launch(Dispatchers.Default) {
            pendeteksi.inisialisasi()
            val mlpOk = mesinKlasifikasi.inisialisasi()
            withContext(Dispatchers.Main) {
                if (mlpOk) {
                    binding.txtGerakanTerdeteksi.text = getString(R.string.kamera_mendeteksi)
                    binding.txtKonfiden.text = "Arahkan kamera ke tubuh"
                } else {
                    binding.txtGerakanTerdeteksi.text = "⚠ Gagal memuat model klasifikasi"
                    binding.txtKonfiden.text = "Periksa file model di assets"
                }
            }
        }

        // Fungsi tombol
        binding.btnKembali.setOnClickListener { finish() }
        binding.btnSwitchKamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            binding.overlayPose.setKameraDepan(lensFacing == CameraSelector.LENS_FACING_FRONT)
            penstabilYolo.reset()
            penstabilMLKit.reset()
            mulaiKamera()
        }

        aktif = true

        // Tampilkan popup ketentuan penggunaan sebelum kamera dimulai
        DialogKetentuanKamera.tampilkan(this, kunci = "klasifikasi") {
            periksaIzinKamera()
        }
    }

    override fun onStart() {
        super.onStart()
        aktif = true
    }

    override fun onStop() {
        aktif = false
        providerKamera?.unbindAll()
        super.onStop()
    }

    override fun onDestroy() {
        aktif = false
        providerKamera?.unbindAll()
        eksekutorKamera.shutdown()
        pendeteksi.tutup()
        pendeteksiMLKit.tutup()
        mesinKlasifikasi.tutup()
        yoloBitmapCache?.recycle()
        yoloBitmapCache = null
        super.onDestroy()
    }

    private fun periksaIzinKamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mulaiKamera()
        } else {
            izinKamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun mulaiKamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            providerKamera = provider

            val preview = Preview.Builder()
                .setTargetResolution(UKURAN_KAMERA)
                .build()
                .also {
                it.surfaceProvider = binding.previewKamera.surfaceProvider
            }

            val analisis = ImageAnalysis.Builder()
                .setTargetResolution(UKURAN_KAMERA)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(eksekutorKamera) { gambar -> prosesGambar(gambar) } }

            val selektor = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selektor, preview, analisis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    private fun prosesGambar(gambar: ImageProxy) {
        if (!aktif || sedangMemproses) {
            gambar.close()
            return
        }
        val mediaImage = gambar.image
        if (mediaImage == null) {
            gambar.close()
            return
        }
        sedangMemproses = true

        val rotationDegrees = gambar.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // 1. Ambil salinan frame (Bitmap) untuk YOLO secara terpisah (hanya jika YOLO nganggur)
        val sekarang = System.currentTimeMillis()
        val jalankanYolo = !yoloSedangMemproses &&
                sekarang - waktuYoloTerakhir >= INTERVAL_YOLO_MS

        if (jalankanYolo) {
            waktuYoloTerakhir = sekarang
            val bitmapUntukYolo = salinBitmapUntukYolo(gambar)
            yoloSedangMemproses = true
            
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    if (aktif) {
                        val poseYolo = pendeteksi.deteksi(bitmapUntukYolo)
                        val poseYoloStabil = poseYolo?.let { penstabilYolo.stabilkan(it) }

                        withContext(Dispatchers.Main) {
                            if (aktif && poseYoloStabil != null) {
                                // Langsung kirim DataPose ke MesinKlasifikasi (tanpa PengekstrakFitur)
                                val hasil = mesinKlasifikasi.klasifikasi(poseYoloStabil)

                                if (hasil != null) {
                                    binding.txtGerakanTerdeteksi.text = hasil.first
                                    binding.txtKonfiden.text = String.format("Konfiden: %d%%", (hasil.second * 100).toInt())
                                } else {
                                    binding.txtGerakanTerdeteksi.text = getString(R.string.kamera_mendeteksi)
                                    binding.txtKonfiden.text = "Posisikan tubuh sepenuhnya"
                                }
                            } else if (aktif) {
                                binding.txtGerakanTerdeteksi.text = getString(R.string.kamera_mendeteksi)
                                binding.txtKonfiden.text = "Posisikan tubuh sepenuhnya"
                            }
                        }
                    }
                } finally {
                    bitmapUntukYolo.recycle()
                    yoloSedangMemproses = false
                }
            }
        }

        // 2. ML Kit memproses frame utama untuk visual secepat mungkin
        lifecycleScope.launch(Dispatchers.Default) {
            if (!aktif) {
                withContext(Dispatchers.Main) {
                    sedangMemproses = false
                    gambar.close()
                }
                return@launch
            }

            val poseMLKit = pendeteksiMLKit.deteksiImage(inputImage)
            val poseMLKitStabil = poseMLKit?.let { penstabilMLKit.stabilkan(it) }

            withContext(Dispatchers.Main) {
                if (!aktif || isDestroyed || isFinishing) {
                    sedangMemproses = false
                    gambar.close()
                    return@withContext
                }

                perbaruiFps()
                
                // Gambar overlay dengan MLKit yang sudah dismoothing
                if (poseMLKitStabil != null) {
                    val isRotated = rotationDegrees == 90 || rotationDegrees == 270
                    val lebar = if (isRotated) gambar.height.toFloat() else gambar.width.toFloat()
                    val tinggi = if (isRotated) gambar.width.toFloat() else gambar.height.toFloat()
                    binding.overlayPose.perbaruiPose(poseMLKitStabil, lebar, tinggi)
                } else {
                    binding.overlayPose.bersihkan()
                }

                // Segera bebaskan frame setelah MLKit selesai agar CameraX tidak drop frame
                sedangMemproses = false
                gambar.close()
            }
        }
    }

    /**
     * Buat salinan bitmap untuk YOLO dengan rotasi yang benar.
     * Reuse cache bitmap jika ukuran cocok untuk kurangi alokasi heap.
     */
    private fun salinBitmapUntukYolo(gambar: ImageProxy): Bitmap {
        val bitmap = gambar.toBitmap()
        val rotasi = gambar.imageInfo.rotationDegrees

        val hasilBitmap = if (rotasi == 0) {
            bitmap
        } else {
            val matriks = Matrix().apply { postRotate(rotasi.toFloat()) }
            val bitmapBerotasi = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matriks, true
            )
            if (bitmapBerotasi != bitmap) bitmap.recycle()
            bitmapBerotasi
        }

        return hasilBitmap
    }

    private fun perbaruiFps() {
        val sekarang = System.currentTimeMillis()
        if (waktuFpsTerakhir == 0L) {
            waktuFpsTerakhir = sekarang
            jumlahFrameFps = 0
            return
        }

        jumlahFrameFps++
        val selisih = sekarang - waktuFpsTerakhir
        if (selisih >= 1000L) {
            val fps = jumlahFrameFps * 1000f / selisih
            binding.txtFps.text = String.format("%.1f FPS", fps)
            waktuFpsTerakhir = sekarang
            jumlahFrameFps = 0
        }
    }
}
