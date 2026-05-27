package com.el.silatpro

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.el.silatpro.ai.PendeteksiPose
import com.el.silatpro.ai.PengekstrakFitur
import com.el.silatpro.ai.PenilaiGerakan
import com.el.silatpro.ai.PenstabilPose
import com.el.silatpro.data.BasisDataAplikasi
import com.el.silatpro.data.EntitasRiwayatLatihan
import com.el.silatpro.databinding.ActivityKameraRekamBinding
import com.el.silatpro.model.HasilEvaluasi
import com.el.silatpro.ui.kamera.ManajerKamera
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Mode Rekam — alur evaluasi murni offline (tanpa skeleton overlay saat merekam):
 *
 * 1. [STATE_SIAP]          → Preview kamera biasa (tanpa overlay), tunggu tombol
 * 2. [STATE_HITUNG_MUNDUR] → Countdown 3→2→1 di tengah layar
 * 3. [STATE_MEREKAM]       → Simpan raw bitmap frame tiap [INTERVAL_FRAME_MS] ms,
 *                            TIDAK ada YOLOv8 / ML Kit yang berjalan = ringan
 * 4. [STATE_MEMPROSES]     → Kamera berhenti, tiap frame diproses YOLOv8x → MLP evaluasi
 * 5. [STATE_SELESAI]       → Simpan hasil terbaik ke DB → ActivityHasilEvaluasi
 *
 * Keunggulan: user bebas bergerak tanpa terganggu pemrosesan realtime,
 * evaluasi dilakukan offline setelah rekaman selesai sehingga lebih akurat.
 */
class ActivityKameraRekam : AppCompatActivity() {

    private lateinit var binding: ActivityKameraRekamBinding

    // ── AI (hanya dipakai SETELAH rekaman selesai) ────────────────
    private lateinit var pendeteksi: PendeteksiPose   // YOLOv8x — akurat
    private lateinit var pengekstrak: PengekstrakFitur
    private lateinit var penilai: PenilaiGerakan
    private lateinit var penstabilYolo: PenstabilPose

    // ── Kamera ────────────────────────────────────────────────────
    private lateinit var manajerKamera: ManajerKamera
    private var kameraAktif = false

    // ── State ─────────────────────────────────────────────────────
    @Volatile private var aktif = false
    private var state = STATE_SIAP

    // ── Data sesi ─────────────────────────────────────────────────
    /** ID grup gerakan yang dipilih user, contoh: "pukulan_2" */
    private var idGrupGerakan = ""
    private var labelGerakan = ""
    private var waktuMulai: Long = 0

    // ── Frame rekaman (raw bitmap, belum diproses) ────────────────
    /**
     * Frame-frame mentah yang direkam dari kamera.
     * Tidak ada keypoint di sini — hanya gambar Bitmap biasa.
     * Keypoint baru diekstrak di [STATE_MEMPROSES] menggunakan YOLOv8x.
     */
    private val daftarFrameRekam = mutableListOf<Bitmap>()
    private var waktuFrameTerakhirDirekam = 0L

    // ── Hasil evaluasi terbaik ────────────────────────────────────
    private var skorTertinggi: Double = 0.0
    private var hasilTerbaik: HasilEvaluasi? = null
    private var pathFotoTerbaik: String? = null

    // ── Tracking gerakan salah (untuk pesan error yang informatif) ─
    /** Label gerakan salah yang paling sering terdeteksi selama rekaman */
    private var gerakanSalahTerdeteksi = ""
    private val hitungGerakanSalah = mutableMapOf<String, Int>()

    // ── Timer & Animasi ───────────────────────────────────────────
    private var timerRekam: CountDownTimer? = null
    private var animasiDot: Animation? = null

    companion object {
        private const val TAG = "KameraRekam"
        private const val DURASI_REKAM_MS     = 5_000L
        private const val INTERVAL_FRAME_MS   = 1000L   // 1 frame per 600ms → ~8 frame dari 5 detik
        private const val MAX_FRAME           = 5      // 5-10 frame sudah cukup untuk analisis akurat

        private const val STATE_SIAP          = 0
        private const val STATE_HITUNG_MUNDUR = 1
        private const val STATE_MEREKAM       = 2
        private const val STATE_MEMPROSES     = 3
        private const val STATE_SELESAI       = 4
    }

    private val izinKamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { diberikan ->
        if (diberikan) manajerKamera.mulai()
        else {
            Toast.makeText(this, R.string.izin_kamera_dibutuhkan, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKameraRekamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        idGrupGerakan = intent.getStringExtra("ID_GRUP_GERAKAN") ?: ""
        labelGerakan  = intent.getStringExtra("LABEL_GERAKAN") ?: "Gerakan"
        waktuMulai    = System.currentTimeMillis()

        binding.txtNamaGerakan.text = labelGerakan

        inisialisasiAI()
        inisialisasiKamera()
        aturTombol()

        DialogKetentuanKamera.tampilkan(this, kunci = "rekam") {
            periksaIzin()
        }
    }

    override fun onStart()  { super.onStart();  aktif = true }
    override fun onStop()   { aktif = false; super.onStop() }

    override fun onDestroy() {
        super.onDestroy()
        timerRekam?.cancel()
        animasiDot?.cancel()
        pendeteksi.tutup()
        penilai.tutup()
        if (kameraAktif) {
            manajerKamera.lepas()
            kameraAktif = false
        }
        bebaskanFrameRekam()
    }

    // ─────────────────────────────────────────────────────────────
    // Inisialisasi
    // ─────────────────────────────────────────────────────────────

    private fun inisialisasiAI() {
        // YOLOv8x hanya untuk proses offline setelah rekaman selesai
        pendeteksi   = PendeteksiPose(this, PendeteksiPose.MODEL_AKURAT)
        pengekstrak  = PengekstrakFitur()
        penilai      = PenilaiGerakan(this)
        penstabilYolo = PenstabilPose()

        // Muat model global di background agar tidak blokir UI
        lifecycleScope.launch(Dispatchers.IO) {
            pendeteksi.inisialisasi()
            if (idGrupGerakan.isNotEmpty()) {
                val dimuat = penilai.muatModel(idGrupGerakan)
                if (!dimuat) withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ActivityKameraRekam,
                        "Gagal memuat model global",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            Log.d(TAG, "YOLOv8x + Model Global siap untuk proses offline")
        }
    }

    private fun inisialisasiKamera() {
        // Kamera hanya untuk preview + capture frame mentah
        // Tidak ada analisis apapun yang berjalan saat preview/rekam
        manajerKamera = ManajerKamera(
            context        = this,
            lifecycleOwner = this,
            previewView    = binding.previewKamera,
            onFrameSiap    = { gambar -> tangkapFrame(gambar) }
        )
        manajerKamera.setModePerforma(ManajerKamera.ModePerforma.LIVE)
        kameraAktif = true
    }

    private fun aturTombol() {
        binding.btnKembali.setOnClickListener { finish() }
        binding.btnMulaiRekam.setOnClickListener { handleTombolRekam() }
        binding.btnSwitchKamera.setOnClickListener {
            if (state == STATE_SIAP) {
                manajerKamera.gantiKamera()
            }
        }
    }

    private fun periksaIzin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) manajerKamera.mulai()
        else izinKamera.launch(Manifest.permission.CAMERA)
    }

    // ─────────────────────────────────────────────────────────────
    // Capture Frame — sangat ringan, hanya saat STATE_MEREKAM
    // ─────────────────────────────────────────────────────────────

    /**
     * Callback dari ManajerKamera setiap frame datang.
     *
     * - [STATE_SIAP] / [STATE_HITUNG_MUNDUR]: tutup frame langsung (hanya preview, tidak diproses)
     * - [STATE_MEREKAM]: simpan salinan bitmap jika sudah waktunya (throttled per [INTERVAL_FRAME_MS])
     * - [STATE_MEMPROSES] / [STATE_SELESAI]: tutup frame langsung
     */
    private fun tangkapFrame(gambar: ImageProxy) {
        try {
            if (!aktif || state != STATE_MEREKAM) {
                gambar.close()
                return
            }

            val sekarang = System.currentTimeMillis()
            if (daftarFrameRekam.size < MAX_FRAME &&
                sekarang - waktuFrameTerakhirDirekam >= INTERVAL_FRAME_MS
            ) {
                waktuFrameTerakhirDirekam = sekarang
                // Konversi ke Bitmap dengan rotasi yang benar, lalu simpan
                val bitmap = salinBitmapDenganRotasi(gambar)
                synchronized(daftarFrameRekam) {
                    daftarFrameRekam.add(bitmap)
                }
                Log.v(TAG, "Frame direkam: ${daftarFrameRekam.size}/$MAX_FRAME")
            }
        } finally {
            // SELALU tutup frame agar CameraX tidak stuck
            gambar.close()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // State Machine
    // ─────────────────────────────────────────────────────────────

    private fun handleTombolRekam() {
        when (state) {
            STATE_SIAP    -> mulaiHitungMundur()
            STATE_SELESAI -> resetKeSiap()
        }
    }

    /** Step 1: Tampilkan countdown 3→2→1 dengan animasi scale+fade per angka */
    private fun mulaiHitungMundur() {
        state = STATE_HITUNG_MUNDUR
        binding.btnMulaiRekam.isEnabled   = false
        binding.btnSwitchKamera.isEnabled = false
        binding.txtStatusRekam.text = "Bersiaplah..."
        binding.txtStatusRekam.setTextColor(android.graphics.Color.parseColor("#FFD700"))

        // Tampilkan overlay gelap + kontainer countdown
        binding.overlayCountdown.visibility  = View.VISIBLE
        binding.layoutCountdown.visibility   = View.VISIBLE

        val handler = Handler(Looper.getMainLooper())
        val angkaList = listOf("3", "2", "1")

        fun animasiAngka(index: Int) {
            if (index >= angkaList.size) {
                // Selesai countdown → sembunyikan overlay & mulai rekam
                binding.overlayCountdown.visibility = View.GONE
                binding.layoutCountdown.visibility  = View.GONE
                mulaiMerekam()
                return
            }

            binding.txtCountdown.text = angkaList[index]

            // Reset transformasi sebelum animasi
            binding.txtCountdown.alpha  = 0f
            binding.txtCountdown.scaleX = 2.0f
            binding.txtCountdown.scaleY = 2.0f

            // Animasi muncul: skala 2x → 1x + fade in (350ms)
            val munculAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofPropertyValuesHolder(
                        binding.txtCountdown,
                        PropertyValuesHolder.ofFloat("scaleX", 2.0f, 1.0f),
                        PropertyValuesHolder.ofFloat("scaleY", 2.0f, 1.0f),
                        PropertyValuesHolder.ofFloat("alpha",  0f,   1.0f)
                    )
                )
                duration = 350L
            }

            // Animasi menghilang: skala 1x → 0.6x + fade out (200ms)
            val hilangAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofPropertyValuesHolder(
                        binding.txtCountdown,
                        PropertyValuesHolder.ofFloat("scaleX", 1.0f, 0.6f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.0f, 0.6f),
                        PropertyValuesHolder.ofFloat("alpha",  1.0f, 0f)
                    )
                )
                duration = 200L
            }

            munculAnimator.start()

            // Setelah 780ms tampil: mulai hilang, lalu tampilkan angka berikutnya
            handler.postDelayed({
                hilangAnimator.start()
                handler.postDelayed({ animasiAngka(index + 1) }, 220L)
            }, 780L)
        }

        animasiAngka(0)   // Mulai dari "3"
    }

    /** Step 2: Rekam frame mentah selama 5 detik — tidak ada AI yang jalan */
    private fun mulaiMerekam() {
        state = STATE_MEREKAM
        daftarFrameRekam.clear()
        waktuFrameTerakhirDirekam = 0L

        binding.layoutProgressRekam.visibility = View.VISIBLE
        binding.txtStatusRekam.text = "⏺ Sedang merekam..."
        binding.txtStatusRekam.setTextColor(android.graphics.Color.parseColor("#FF4444"))

        // Animasi kedip titik merah
        animasiDot = AlphaAnimation(1f, 0f).apply {
            duration = 500
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        binding.dotRekam.startAnimation(animasiDot)

        // Progress bar maju 0→100 selama 5 detik
        ObjectAnimator.ofInt(binding.progressRekam, "progress", 0, 100).apply {
            duration = DURASI_REKAM_MS
            start()
        }

        timerRekam = object : CountDownTimer(DURASI_REKAM_MS, 100L) {
            override fun onTick(sisa: Long) {
                binding.txtSisaWaktu.text = String.format("%.1f dtk", sisa / 1_000f)
            }
            override fun onFinish() {
                binding.txtSisaWaktu.text = "0.0 dtk"
                animasiDot?.cancel()
                mulaiMemproses()
            }
        }
        timerRekam!!.start()
    }

    /**
     * Step 3: Hentikan kamera, proses semua frame mentah dengan YOLOv8x.
     *
     * Ini adalah satu-satunya tempat AI (YOLO + MLP) dijalankan.
     * Karena offline, tidak ada batasan waktu → bisa pakai model besar (YOLOv8x).
     */
    private fun mulaiMemproses() {
        state = STATE_MEMPROSES

        // Hentikan kamera — tidak perlu lagi setelah rekaman selesai
        manajerKamera.hentikan()

        binding.layoutProgressRekam.visibility = View.GONE
        binding.layoutMemproses.visibility = View.VISIBLE
        binding.txtStatusRekam.text = "Memproses..."
        binding.txtStatusRekam.setTextColor(android.graphics.Color.parseColor("#FFD700"))

        val totalFrame = synchronized(daftarFrameRekam) { daftarFrameRekam.size }
        Log.d(TAG, "Proses $totalFrame frame mentah dengan YOLOv8x...")

        if (totalFrame == 0) {
            binding.txtProgressMemproses.text = "Tidak ada frame yang terekam"
            lifecycleScope.launch(Dispatchers.Main) {
                selesaiLatihan()
            }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val durasi = System.currentTimeMillis() - waktuMulai

            // Ambil salinan daftar agar bisa di-iterate dengan aman
            val frameUntukDiproses = synchronized(daftarFrameRekam) {
                daftarFrameRekam.toList()
            }

            frameUntukDiproses.forEachIndexed { index, bitmap ->
                // Update progres di UI
                withContext(Dispatchers.Main) {
                    binding.txtProgressMemproses.text = "Menganalisis gerakan..."
                    binding.txtSubProgressMemproses.text =
                        "Memproses frame ${index + 1} / $totalFrame"
                }

                try {
                    // 1. Deteksi keypoint dari frame mentah menggunakan YOLOv8x
                    val pose = pendeteksi.deteksi(bitmap)
                    val poseStabil = pose?.let { penstabilYolo.stabilkan(it) }

                    // 2. Evaluasi jika pose valid
                    if (poseStabil != null && poseStabil.apakahValid() && idGrupGerakan.isNotEmpty()) {
                        val fitur = pengekstrak.ekstrak(poseStabil)
                        val hasil = penilai.evaluasi(fitur, durasi, poseStabil)

                        // Simpan hasil terbaik (bukan gerakan salah & skor lebih tinggi)
                        if (!hasil.gerakanSalah && hasil.skorTotal > skorTertinggi) {
                            skorTertinggi = hasil.skorTotal
                            hasilTerbaik  = hasil
                            // Hapus foto lama, simpan foto frame terbaik
                            pathFotoTerbaik?.let { lama ->
                                try { File(lama).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
                            }
                            pathFotoTerbaik = simpanBitmapKeFoto(bitmap)
                            Log.d(TAG, "Skor baru terbaik: $skorTertinggi (frame ${index + 1})")
                        }

                        // Catat gerakan salah yang terdeteksi untuk pesan error
                        if (hasil.gerakanSalah && hasil.gerakanTerdeteksi.isNotEmpty()) {
                            val label = hasil.gerakanTerdeteksi
                            hitungGerakanSalah[label] = (hitungGerakanSalah[label] ?: 0) + 1
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error proses frame $index: ${e.message}")
                }
                // Bebaskan memori bitmap setelah diproses
                if (!bitmap.isRecycled) bitmap.recycle()
            }

            // Kosongkan daftar frame setelah semua diproses
            synchronized(daftarFrameRekam) { daftarFrameRekam.clear() }

            withContext(Dispatchers.Main) { selesaiLatihan() }
        }
    }

    /** Step 4: Simpan ke DB & pindah ke HasilEvaluasi */
    private fun selesaiLatihan() {
        state = STATE_SELESAI
        binding.layoutMemproses.visibility = View.GONE

        if (hasilTerbaik == null) {
            // Tentukan judul & pesan berdasarkan apakah ada gerakan salah yang terdeteksi
            val judulDialog: String
            val pesanDialog: String

            if (hitungGerakanSalah.isNotEmpty()) {
                val labelSalah  = hitungGerakanSalah.maxByOrNull { it.value }?.key ?: ""
                val namaDeteksi = namaRamahDariLabel(labelSalah)
                val namaTarget  = namaRamahDariGrup(idGrupGerakan)
                judulDialog = "⚠ Gerakan Tidak Sesuai"
                pesanDialog = "Sistem mendeteksi $namaDeteksi selama rekaman.\n\n" +
                              "Pastikan Anda melakukan $namaTarget, kemudian rekam ulang."
            } else {
                judulDialog = "Pose Tidak Terdeteksi"
                pesanDialog = "Tubuh Anda tidak terdeteksi dengan baik selama rekaman.\n\n" +
                              "Pastikan seluruh tubuh terlihat jelas di kamera, kemudian coba lagi."
            }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(judulDialog)
                .setMessage(pesanDialog)
                .setCancelable(false)
                .setPositiveButton("Rekam Ulang") { dialog, _ ->
                    dialog.dismiss()
                    resetKeSiap()
                }
                .setNegativeButton("Tutup") { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
                .show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db   = BasisDataAplikasi.dapatkanInstans(this@ActivityKameraRekam)
            val dao  = db.daoRiwayatLatihan()
            val gson = Gson()

            val riwayat = EntitasRiwayatLatihan(
                idGerakan    = idGrupGerakan,
                labelGerakan = hasilTerbaik!!.labelGerakan,
                skor         = skorTertinggi,
                kategori     = hasilTerbaik!!.kategori,
                skorPerFitur = gson.toJson(hasilTerbaik!!.skorPerFitur),
                feedback     = gson.toJson(hasilTerbaik!!.feedbackList),
                durasiMs     = System.currentTimeMillis() - waktuMulai,
                pathFoto     = pathFotoTerbaik
            )

            val idBaru = dao.simpan(riwayat)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@ActivityKameraRekam, "Evaluasi selesai!", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this@ActivityKameraRekam, ActivityHasilEvaluasi::class.java).apply {
                        putExtra("ID_RIWAYAT", idBaru.toInt())
                        putExtra("MODE_RIWAYAT", false)
                    }
                )
                finish()
            }
        }
    }

    /** Reset ke STATE_SIAP agar user bisa rekam ulang */
    private fun resetKeSiap() {
        state         = STATE_SIAP
        skorTertinggi = 0.0
        hasilTerbaik  = null
        gerakanSalahTerdeteksi = ""
        hitungGerakanSalah.clear()
        penstabilYolo.reset()
        bebaskanFrameRekam()

        binding.btnMulaiRekam.isEnabled     = true
        binding.btnSwitchKamera.isEnabled   = true
        binding.btnMulaiRekam.text          = "● Mulai Rekam"
        binding.txtStatusRekam.text         = "Siap merekam..."
        binding.txtStatusRekam.setTextColor(android.graphics.Color.parseColor("#00CCFF"))
        binding.layoutProgressRekam.visibility = View.GONE
        binding.layoutMemproses.visibility     = View.GONE

        // Buat ManajerKamera baru agar executor fresh
        if (kameraAktif) {
            manajerKamera.lepas()
            kameraAktif = false
        }
        inisialisasiKamera()
        periksaIzin()
    }

    // ─────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────

    /** Konversi ImageProxy → Bitmap dengan rotasi yang benar */
    private fun salinBitmapDenganRotasi(gambar: ImageProxy): Bitmap {
        val bitmap = gambar.toBitmap()
        val rotasi = gambar.imageInfo.rotationDegrees
        if (rotasi == 0) return bitmap
        val matriks = Matrix().apply { postRotate(rotasi.toFloat()) }
        val hasil = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriks, true)
        if (hasil != bitmap) bitmap.recycle()
        return hasil
    }

    private fun simpanBitmapKeFoto(bitmap: Bitmap): String? {
        return try {
            val dir  = File(filesDir, "foto_latihan").also { if (!it.exists()) it.mkdirs() }
            val file = File(dir, "rekam_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Gagal simpan foto: ${e.message}")
            null
        }
    }

    private fun bebaskanFrameRekam() {
        synchronized(daftarFrameRekam) {
            daftarFrameRekam.forEach { if (!it.isRecycled) it.recycle() }
            daftarFrameRekam.clear()
        }
    }

    // ── Nama Gerakan Helper ───────────────────────────────────────

    /**
     * Nama ramah dari label global model.
     * Contoh: "PUKULAN2KANAN" → "Pukulan 2 Kanan"
     */
    private fun namaRamahDariLabel(label: String): String {
        val sb = StringBuilder()
        for (i in label.indices) {
            val prev = if (i > 0) label[i - 1] else '\u0000'
            val curr = label[i]
            val next = if (i + 1 < label.length) label[i + 1] else '\u0000'
            val sisip = prev.isLetter() && curr.isDigit() ||
                        prev.isDigit()  && curr.isLetter()
            if (sisip) sb.append(' ')
            sb.append(curr)
        }
        return sb.toString().lowercase().split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Nama ramah dari ID grup gerakan.
     * Contoh: "pukulan_2" → "Pukulan 2"
     */
    private fun namaRamahDariGrup(grupId: String): String =
        grupId.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

