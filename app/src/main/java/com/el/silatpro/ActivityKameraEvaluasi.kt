package com.el.silatpro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.el.silatpro.ai.PendeteksiPose
import com.el.silatpro.ai.PengekstrakFitur
import com.el.silatpro.ai.PenilaiGerakan
import com.el.silatpro.ai.PenstabilPose
import com.el.silatpro.data.BasisDataAplikasi
import com.el.silatpro.data.EntitasRiwayatLatihan
import com.el.silatpro.databinding.ActivityKameraEvaluasiBinding
import com.el.silatpro.model.DataPose
import com.el.silatpro.model.HasilEvaluasi
import com.el.silatpro.ui.kamera.ManajerKamera
import com.google.gson.Gson
import com.el.silatpro.ai.PendeteksiPoseMLKit
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.async

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ActivityKameraEvaluasi : AppCompatActivity() {

    private lateinit var binding: ActivityKameraEvaluasiBinding

    // ── AI & Evaluasi ─────────────────────────────────────────────
    // ── AI & Evaluasi ─────────────────────────────────────────────
    private lateinit var pendeteksi: PendeteksiPose
    private lateinit var pendeteksiMLKit: PendeteksiPoseMLKit
    private lateinit var pengekstrak: PengekstrakFitur
    private lateinit var penilai: PenilaiGerakan
    private lateinit var penstabilYolo: PenstabilPose
    private lateinit var penstabilMLKit: PenstabilPose

    // ── Manajemen Kamera ──────────────────────────────────────────
    /** ManajerKamera menangani pemilihan resolusi + fallback + eksposur */
    private lateinit var manajerKamera: ManajerKamera

    // ── Flag Thread-Safety (Cegah SIGSEGV) ───────────────────────
    /**
     * Harus FALSE sebelum camera surface dilepas (di onStop).
     * Mencegah race condition antara frame processing dan teardown native TFLite.
     */
    @Volatile private var aktif = false
    @Volatile private var sedangMemproses = false
    @Volatile private var yoloSedangMemproses = false
    @Volatile private var sedangAmbilFoto = false
    private var waktuEvaluasiTerakhir = 0L

    companion object {
        // Interval 500ms = max ~2 evaluasi/detik, cukup responsif untuk penilaian gerakan
        private const val INTERVAL_EVALUASI_MS = 500L
    }

    // ── State Sesi ────────────────────────────────────────────────
    private var assetModel = ""
    private var assetRuleJson = ""   // nama file JSON rule-based (contoh: "pukulan_2_kanan.json")
    private var waktuMulai: Long = 0
    private var skorTertinggi: Double = 0.0
    private var hasilTerbaik: HasilEvaluasi? = null
    private var pathFotoTerbaik: String? = null
    private var waktuFpsTerakhir = 0L
    private var jumlahFrameFps = 0

    private val izinKamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { diberikan ->
        if (diberikan) {
            manajerKamera.mulai()
        } else {
            Toast.makeText(this, R.string.izin_kamera_dibutuhkan, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKameraEvaluasiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        assetModel = intent.getStringExtra("ASSET_MODEL") ?: ""
        assetRuleJson = intent.getStringExtra("ASSET_RULE_JSON") ?: ""
        waktuMulai = System.currentTimeMillis()

        inisialisasiAI()
        inisialisasiManajerKamera()
        aturAksiTombol()

        binding.overlayPose.bringToFront()
        binding.overlayPose.setModeFillCenter(true)
        binding.overlayPose.setKameraDepan(false)

        periksaIzinKamera()
    }

    override fun onStart() {
        super.onStart()
        aktif = true
    }

    /**
     * onStop dipanggil SEBELUM CameraX melepas surface.
     * WAJIB set aktif=false di sini agar tidak ada frame baru
     * yang masuk ke ML Kit / TFLite native saat surface sedang dilepas.
     * Melanggar urutan ini → Fatal Signal 11 (SIGSEGV) di libtensorflowlite_jni.so
     */
    override fun onStop() {
        aktif = false
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        pendeteksi.tutup()
        pendeteksiMLKit.tutup()
        penilai.tutup()
        manajerKamera.lepas()
    }

    // ─────────────────────────────────────────────────────────────
    // Inisialisasi
    // ─────────────────────────────────────────────────────────────

    private fun inisialisasiAI() {
        pendeteksi = PendeteksiPose(this)
        pendeteksi.inisialisasi()
        pendeteksiMLKit = PendeteksiPoseMLKit()
        pengekstrak = PengekstrakFitur()
        penilai = PenilaiGerakan(this)
        penstabilYolo = PenstabilPose()
        penstabilMLKit = PenstabilPose(minCutoff = 0.1f, beta = 0.01f, dCutoff = 1.0f)

        // Muat model TFLite + rule-based JSON di background thread
        lifecycleScope.launch(Dispatchers.IO) {
            if (assetModel.isNotEmpty()) {
                val dimuat = penilai.muatModel(assetModel, assetRuleJson)
                if (!dimuat) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ActivityKameraEvaluasi, "Gagal memuat model acuan", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun inisialisasiManajerKamera() {
        manajerKamera = ManajerKamera(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewKamera,
            onFrameSiap = { gambar -> prosesGambar(gambar) }
        )
        // Gunakan LIVE untuk FPS lebih tinggi — keseimbangan kecepatan + akurasi
        manajerKamera.setModePerforma(ManajerKamera.ModePerforma.LIVE)
    }

    private fun aturAksiTombol() {
        binding.btnSelesai.setOnClickListener { selesaiLatihan() }
        binding.btnKembali.setOnClickListener { finish() }
        binding.btnSwitchKamera.setOnClickListener {
            manajerKamera.gantiKamera()
            binding.overlayPose.setKameraDepan(manajerKamera.adalahKameraDepan())
            penstabilYolo.reset()
            penstabilMLKit.reset()
        }
    }

    private fun periksaIzinKamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            manajerKamera.mulai()
        } else {
            izinKamera.launch(Manifest.permission.CAMERA)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Pemrosesan Frame
    // ─────────────────────────────────────────────────────────────

    @OptIn(ExperimentalGetImage::class)
    private fun prosesGambar(gambar: ImageProxy) {
        // GATE UTAMA: tolak frame baru jika activity tidak aktif.
        // Mencegah native TFLite mengakses buffer kamera yang sudah dilepas (SIGSEGV).
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
        // Ini mencegah antrean frame bertumpuk dan mencegah frame drop
        val sekarang = System.currentTimeMillis()
        val jalankanYolo = !yoloSedangMemproses &&
                sekarang - waktuEvaluasiTerakhir >= INTERVAL_EVALUASI_MS
        
        if (jalankanYolo) {
            waktuEvaluasiTerakhir = sekarang
            val bitmapUntukYolo = salinBitmapUntukYolo(gambar)
            yoloSedangMemproses = true
            
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    if (aktif) {
                        val poseYolo = pendeteksi.deteksi(bitmapUntukYolo)
                        val poseYoloStabil = poseYolo?.let { penstabilYolo.stabilkan(it) }
                        
                        withContext(Dispatchers.Main) {
                            if (aktif && poseYoloStabil != null) {
                                evaluasiPose(poseYoloStabil)
                            } else if (aktif && poseYoloStabil == null) {
                                // Pose tidak valid
                                binding.txtStatus.text = "Pastikan seluruh tubuh terlihat di kamera"
                                binding.txtStatus.setTextColor(android.graphics.Color.parseColor("#FF4444"))
                                binding.txtAlertPeringatan.text = "Pastikan seluruh tubuh terlihat di kamera"
                                binding.txtAlertPeringatan.visibility = android.view.View.VISIBLE
                                binding.txtAlertPeringatan.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#CCFF0000"))
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
                if (!aktif) {
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

    // ─────────────────────────────────────────────────────────────
    // Evaluasi Pose
    // ─────────────────────────────────────────────────────────────

    private fun salinBitmapUntukYolo(gambar: ImageProxy): Bitmap {
        val bitmap = gambar.toBitmap()
        val rotasi = gambar.imageInfo.rotationDegrees
        if (rotasi == 0) return bitmap

        val matriks = Matrix().apply { postRotate(rotasi.toFloat()) }
        val bitmapBerotasi = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matriks,
            true
        )
        if (bitmapBerotasi != bitmap) bitmap.recycle()
        return bitmapBerotasi
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

    private fun evaluasiPose(pose: DataPose) {
        if (pose.apakahValid() && assetModel.isNotEmpty()) {
            val fitur = pengekstrak.ekstrak(pose)   // 34 fitur untuk pipeline evaluasi
            val durasi = System.currentTimeMillis() - waktuMulai
            // pose diteruskan agar PenilaiGerakan dapat membangun 39 fitur + rule-based feedback
            val hasil = penilai.evaluasi(fitur, durasi, assetModel, pose)

            binding.txtNamaGerakan.text = hasil.labelGerakan
            binding.txtSkorLangsung.text = hasil.skorTotal.toInt().toString()

            // Tampilkan feedback
            val feedbackDisplay = hasil.feedbackList.joinToString("\n")
            binding.txtAlertPeringatan.text = feedbackDisplay
            binding.txtAlertPeringatan.visibility = android.view.View.VISIBLE

            // ── Warna & pesan status berdasarkan kondisi ──────────────────
            val warnaTeks: Int
            val warnaBg: Int

            when {
                hasil.gerakanSalah -> {
                    // Gerakan SALAH KATEGORI — oranye terang, peringatan jelas
                    warnaTeks = android.graphics.Color.parseColor("#FF8C00")
                    warnaBg   = android.graphics.Color.parseColor("#CC7A3000")
                    binding.txtStatus.text = "⚠ Gerakan Salah!"
                }
                hasil.skorTotal == 0.0 -> {
                    // Gerakan belum terdeteksi / pose tidak valid
                    warnaTeks = android.graphics.Color.parseColor("#AAAAAA")
                    warnaBg   = android.graphics.Color.parseColor("#AA333333")
                    binding.txtStatus.text = hasil.feedbackList.firstOrNull() ?: ""
                }
                hasil.kategori == "Sangat Baik" || hasil.kategori == "Baik" -> {
                    warnaTeks = android.graphics.Color.parseColor("#00FF88")
                    warnaBg   = android.graphics.Color.parseColor("#CC006600")
                    binding.txtStatus.text = hasil.feedbackList.firstOrNull() ?: ""
                }
                hasil.kategori == "Cukup" -> {
                    warnaTeks = android.graphics.Color.parseColor("#FFD700")
                    warnaBg   = android.graphics.Color.parseColor("#CC887700")
                    binding.txtStatus.text = hasil.feedbackList.firstOrNull() ?: ""
                }
                else -> {
                    warnaTeks = android.graphics.Color.parseColor("#FF4444")
                    warnaBg   = android.graphics.Color.parseColor("#CCFF0000")
                    binding.txtStatus.text = hasil.feedbackList.firstOrNull() ?: ""
                }
            }

            binding.txtStatus.setTextColor(warnaTeks)
            binding.txtAlertPeringatan.backgroundTintList =
                android.content.res.ColorStateList.valueOf(warnaBg)

            // Hanya simpan skor terbaik jika bukan gerakan salah kategori
            if (!hasil.gerakanSalah && hasil.skorTotal > skorTertinggi) {
                skorTertinggi = hasil.skorTotal
                hasilTerbaik = hasil
                ambilFotoSkorTerbaik()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Foto Otomatis Skor Tertinggi
    // ─────────────────────────────────────────────────────────────

    /**
     * Ambil foto saat skor tertinggi tercapai.
     *
     * Menggunakan [PreviewView.getBitmap()] yang bekerja karena kita sudah set
     * implementationMode="compatible" (TextureView) di layout XML.
     * TextureView memungkinkan akses langsung ke frame kamera sebagai Bitmap —
     * lebih reliable dari PixelCopy yang sering tidak bisa capture hardware camera surface.
     *
     * Hasilnya: foto kamera + overlay skeleton yang digabung menjadi satu JPEG.
     */
    private fun ambilFotoSkorTerbaik() {
        if (sedangAmbilFoto) return
        if (!aktif || isDestroyed || isFinishing) return
        sedangAmbilFoto = true

        // Ambil bitmap langsung dari PreviewView (TextureView mode)
        val bitmapPreview = binding.previewKamera.bitmap
        if (bitmapPreview == null) {
            Log.w("DeteksiKamera", "previewView.bitmap null — pastikan implementationMode=compatible di XML")
            sedangAmbilFoto = false
            return
        }

        // Buat bitmap gabungan: kamera + skeleton
        // Bitmap dari previewView sudah seukuran view (mis. 1080×1080)
        // overlayPose juga seukuran view → bisa langsung digambar tanpa scaling
        val bitmapFinal = bitmapPreview.copy(Bitmap.Config.ARGB_8888, true)
        bitmapPreview.recycle()

        val canvas = Canvas(bitmapFinal)
        binding.overlayPose.draw(canvas)  // Gambar skeleton di atas frame kamera

        // Simpan di background thread
        lifecycleScope.launch(Dispatchers.IO) {
            val pathBaru = simpanBitmapKeFoto(bitmapFinal)
            bitmapFinal.recycle()
            pathFotoTerbaik?.let { lama ->
                try { File(lama).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
            }
            pathFotoTerbaik = pathBaru
            sedangAmbilFoto = false
        }
    }

    private fun simpanBitmapKeFoto(bitmap: Bitmap): String? {
        return try {
            val dir = File(filesDir, "foto_latihan").also { if (!it.exists()) it.mkdirs() }
            val file = File(dir, "pose_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            }
            Log.d("DeteksiKamera", "Foto disimpan: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e("DeteksiKamera", "Gagal simpan foto: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Selesai Latihan
    // ─────────────────────────────────────────────────────────────

    private fun selesaiLatihan() {
        if (hasilTerbaik == null) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = BasisDataAplikasi.dapatkanInstans(this@ActivityKameraEvaluasi)
            val dao = db.daoRiwayatLatihan()
            val gson = Gson()

            val riwayat = EntitasRiwayatLatihan(
                idGerakan = assetModel,
                labelGerakan = hasilTerbaik!!.labelGerakan,
                skor = skorTertinggi,
                kategori = hasilTerbaik!!.kategori,
                skorPerFitur = gson.toJson(hasilTerbaik!!.skorPerFitur),
                feedback = gson.toJson(hasilTerbaik!!.feedbackList),
                durasiMs = System.currentTimeMillis() - waktuMulai,
                pathFoto = pathFotoTerbaik
            )

            val idBaru = dao.simpan(riwayat)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@ActivityKameraEvaluasi, "Latihan disimpan!", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this@ActivityKameraEvaluasi, ActivityHasilEvaluasi::class.java).apply {
                        putExtra("ID_RIWAYAT", idBaru.toInt())
                        putExtra("MODE_RIWAYAT", false)
                    }
                )
                finish()
            }
        }
    }
}
