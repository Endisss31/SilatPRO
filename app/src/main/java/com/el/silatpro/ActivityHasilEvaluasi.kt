package com.el.silatpro

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.el.silatpro.data.BasisDataAplikasi
import com.el.silatpro.databinding.ActivityHasilEvaluasiBinding
import com.el.silatpro.databinding.DialogPratinjauFotoBinding
import com.el.silatpro.ui.riwayat.AdapterDetailFitur
import com.el.silatpro.ui.riwayat.AdapterFeedback
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ActivityHasilEvaluasi : AppCompatActivity() {

    private lateinit var binding: ActivityHasilEvaluasiBinding
    private var idRiwayat: Int = -1
    private var isModeRiwayat: Boolean = false

    /** Simpan bitmap yang sudah di-load agar bisa dibuka di popup tanpa decode ulang */
    private var bitmapFotoTerbaik: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHasilEvaluasiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aturEdgeToEdge()
        aturInsets()

        idRiwayat = intent.getIntExtra("ID_RIWAYAT", -1)
        isModeRiwayat = intent.getBooleanExtra("MODE_RIWAYAT", false)

        if (isModeRiwayat) {
            binding.btnUlangi.visibility = View.GONE
            binding.btnKembali.text = "Tutup"
            binding.toolbarHasil.title = getString(R.string.hasil_riwayat_judul)
        }

        aturAksiTombol()
        
        // Setup recyclerView early to avoid "No adapter attached" warning
        binding.rvDetailFitur.layoutManager = LinearLayoutManager(this)
        binding.rvFeedback.layoutManager = LinearLayoutManager(this)
        
        muatDataRiwayat()
    }

    private fun aturAksiTombol() {
        binding.toolbarHasil.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnKembali.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnUlangi.setOnClickListener {
            finish()
        }
    }

    /** Status bar transparan, konten extend ke baliknya, toolbar & bottom bar aware terhadap insets */
    private fun aturEdgeToEdge() {
        // Matikan padding otomatis sistem — kita handle manual
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Status bar transparan agar hero header menyatu
        window.statusBarColor = Color.TRANSPARENT
        // Ikon status bar tetap putih (kontras dengan hero biru gelap)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    /** Geser toolbar ke bawah status bar & angkat bottom bar dari atas nav bar */
    private fun aturInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar   = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Toolbar: tambah padding-top agar judul/back icon tidak tertimpa ikon status bar
            binding.toolbarHasil.setPadding(
                binding.toolbarHasil.paddingLeft,
                statusBar.top,
                binding.toolbarHasil.paddingRight,
                binding.toolbarHasil.paddingBottom
            )

            // Bottom action bar: tambah padding-bottom agar tombol tidak tertutup nav bar
            binding.layoutBottomBar.setPadding(0, 0, 0, navBar.bottom)

            insets
        }
    }

    private fun muatDataRiwayat() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = BasisDataAplikasi.dapatkanInstans(this@ActivityHasilEvaluasi).daoRiwayatLatihan()
            val riwayat = dao.ambilBerdasarkanId(idRiwayat)

            withContext(Dispatchers.Main) {
                if (riwayat != null) {
                    val gson = Gson()

                    val tipeMap = object : TypeToken<Map<String, Double>>() {}.type
                    val skorFitur: Map<String, Double> = gson.fromJson(riwayat.skorPerFitur, tipeMap)

                    val tipeList = object : TypeToken<List<String>>() {}.type
                    val feedbackMentah: List<String> = gson.fromJson(riwayat.feedback, tipeList)
                    val feedback = olahFeedback(riwayat.skor, feedbackMentah)

                    binding.txtNamaGerakan.text = riwayat.labelGerakan
                    binding.txtSkorAngka.text = riwayat.skor.toInt().toString()
                    binding.txtKategori.text = riwayat.kategori

                    // Setup adapters
                    binding.rvDetailFitur.adapter = AdapterDetailFitur(skorFitur)
                    binding.rvFeedback.adapter = AdapterFeedback(feedback)

                    // Tampilkan foto pose terbaik jika tersedia
                    tampilkanFotoPoseTerbaik(riwayat.pathFoto)
                }
            }
        }
    }

    private fun olahFeedback(skor: Double, feedback: List<String>): List<String> {
        return when {
            skor >= 85.0 -> listOf("Gerakan sudah sangat baik. Pertahankan kestabilan, tempo, dan posisi tubuh.")
            skor >= 70.0 -> {
                val koreksi = feedback.filterNot { it.contains("sudah", ignoreCase = true) }
                if (koreksi.isEmpty()) {
                    listOf("Gerakan sudah baik. Lanjutkan latihan untuk menjaga konsistensi.")
                } else {
                    koreksi.take(3)
                }
            }
            feedback.isEmpty() -> listOf("Belum ada feedback detail untuk sesi ini.")
            else -> feedback.take(4)
        }
    }

    /**
     * Memuat dan menampilkan foto pose terbaik dari path file lokal.
     * Menggunakan inSampleSize agar tidak OOM saat memuat gambar besar.
     */
    private fun tampilkanFotoPoseTerbaik(pathFoto: String?) {
        if (pathFoto.isNullOrEmpty()) return

        val fileFoto = File(pathFoto)
        if (!fileFoto.exists()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Decode ukuran asli dulu
                val opsiUkuran = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(pathFoto, opsiUkuran)

                // Hitung inSampleSize agar sesuai tampilan (maks 720px lebar)
                val skalaTarget = 720
                var inSampleSize = 1
                if (opsiUkuran.outWidth > skalaTarget) {
                    inSampleSize = opsiUkuran.outWidth / skalaTarget
                }

                // Decode bitmap dengan sampling
                val opsiBitmap = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeFile(pathFoto, opsiBitmap)

                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        bitmapFotoTerbaik = bitmap
                        binding.imgFotoTerbaik.setImageBitmap(bitmap)
                        // Tampilkan kartu foto dengan animasi fade-in
                        binding.kartuFotoTerbaik.apply {
                            alpha = 0f
                            visibility = View.VISIBLE
                            animate()
                                .alpha(1f)
                                .setDuration(400)
                                .start()
                        }
                        // Pasang click listener untuk popup pratinjau
                        binding.imgFotoTerbaik.setOnClickListener {
                            tampilkanPopupFoto(bitmap)
                        }
                        binding.imgFotoTerbaik.isClickable = true
                        binding.imgFotoTerbaik.isFocusable = true
                    }
                }
            } catch (e: Exception) {
                // Jika gagal load foto, cukup sembunyikan kartu
                withContext(Dispatchers.Main) {
                    binding.kartuFotoTerbaik.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Tampilkan popup pratinjau foto pose — floating card, bukan full screen.
     * Pengguna bisa menutup dengan tombol X atau tap di luar dialog.
     */
    private fun tampilkanPopupFoto(bitmap: Bitmap) {
        if (isDestroyed || isFinishing) return

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)   // tap luar = tutup

        val popupBinding = DialogPratinjauFotoBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(popupBinding.root)

        // ── Styling window: transparan + lebar 90% + dim di belakang ──────────
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes?.also { it.dimAmount = 0.65f }
        }

        // ── Pasang bitmap ke ImageView popup ─────────────────────────────────
        popupBinding.imgPratinjauFoto.setImageBitmap(bitmap)

        // ── Animasi masuk: scale dari 0.85 + fade-in ─────────────────────────
        popupBinding.root.apply {
            scaleX = 0.85f
            scaleY = 0.85f
            alpha  = 0f
            animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(220)
                .start()
        }

        // ── Tombol tutup ─────────────────────────────────────────────────────
        popupBinding.btnTutupFoto.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
