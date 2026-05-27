package com.el.silatpro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.el.silatpro.databinding.ActivityDetailGerakanBinding
import com.el.silatpro.model.KelasGerakan
import com.el.silatpro.ui.beranda.AdapterVariasi

class ActivityDetailGerakan : AppCompatActivity() {

    private lateinit var binding: ActivityDetailGerakanBinding
    private var daftarKelas = emptyList<KelasGerakan>()
    private var idGerakanTerpilih = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aturEdgeToEdge()
        binding = ActivityDetailGerakanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aturInsets()
        aturToolbar()
        ambilDataIntent()
        aturAksiTombol()
    }

    /** Status bar transparan, konten extend ke baliknya, toolbar & bottom bar aware terhadap insets */
    private fun aturEdgeToEdge() {
        // Matikan padding otomatis sistem — kita handle manual
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Status bar transparan agar hero image menyatu
        window.statusBarColor = Color.TRANSPARENT
        // Ikon status bar tetap putih (kontras dengan hero gelap)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    /** Geser toolbar ke bawah status bar & angkat bottom bar dari atas nav bar */
    private fun aturInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar    = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Toolbar: tambah padding-top agar judul/back icon tidak tertimpa ikon status bar
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                statusBar.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )

            // BottomBar: tambah padding-bottom agar tombol tidak tertutup nav bar / gesture bar HP
            binding.layoutBottomBar.setPadding(
                binding.layoutBottomBar.paddingLeft,
                binding.layoutBottomBar.paddingTop,
                binding.layoutBottomBar.paddingRight,
                navBar.bottom
            )

            insets
        }
    }

    private fun aturToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun ambilDataIntent() {
        val idGerakan = intent.getStringExtra("ID_GERAKAN") ?: ""
        val labelGerakan = intent.getStringExtra("LABEL_GERAKAN") ?: ""
        val kelasJson = intent.getStringExtra("KELAS_GERAKAN") ?: "[]"
        val kategoriGerakan = namaKategori(idGerakan)

        binding.toolbar.title = ""
        binding.txtKategoriDetail.text = kategoriGerakan
        binding.txtNamaGerakan.text = labelGerakan
        binding.txtHeroDeskripsi.text = deskripsiHero(idGerakan)
        binding.txtTargetGerakan.text = fokusGerakan(idGerakan)
        binding.txtLangkahGerakan.text = panduanSingkat(idGerakan)
        binding.txtDeskripsi.text = definisiGerakan(idGerakan, labelGerakan)

        // We no longer have variations (left/right)
        idGerakanTerpilih = idGerakan
        muatFotoHeader(idGerakanTerpilih)
    }

    private fun muatFotoHeader(id: String) {
        val idGambar = cariDrawableGerakan(id)
        if (idGambar != 0) {
            binding.imgHeader.setImageResource(idGambar)
        }
    }

    private fun cariDrawableGerakan(id: String): Int {
        return listOf(id, id.replace("_", ""))
            .firstNotNullOfOrNull { nama ->
                resources.getIdentifier(nama, "drawable", packageName).takeIf { it != 0 }
            } ?: 0
    }

    // Removed aturVariasi since we don't have variations anymore

    private fun aturAksiTombol() {
        binding.fabMulaiLatihan.setOnClickListener {
            if (idGerakanTerpilih.isNotEmpty()) {
                // Langsung masuk mode realtime — mode rekam sudah dihapus
                startActivity(
                    Intent(this, ActivityKameraEvaluasi::class.java).apply {
                        putExtra("ID_GRUP_GERAKAN", idGerakanTerpilih)
                    }
                )
            } else {
                Toast.makeText(this, "Pilih gerakan terlebih dahulu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Peta id gerakan → nama file JSON rule-based di assets/movement_models/.
     * Secara default menggunakan variasi "kanan" karena rule-based bersifat simetris
     * dan akan disesuaikan lagi oleh kamera evaluasi jika model mendeteksi sisi kiri.
     */
    private fun ruleJsonDariId(id: String): String = when (id) {
        "pukulan_2"   -> "pukulan2kanan_rule_reference.json"
        "pukulan_4"   -> "pukulan4kanan_rule_reference.json"
        "tangkisan_1" -> "tangkisan1kanan_rule_reference.json"
        "tangkisan_2" -> "tangkisan2kanan_rule_reference.json"
        "tangkisan_3" -> "tangkisan3kanan_rule_reference.json"
        else          -> ""
    }

    // Removed apakahSisiKiri

    private fun namaKategori(idGerakan: String): String {
        return when {
            idGerakan.startsWith("pukulan")   -> "Pukulan"
            idGerakan.startsWith("tangkisan") -> "Tangkisan"
            else -> "Silat"
        }
    }

    private fun deskripsiHero(idGerakan: String): String {
        return when {
            idGerakan.startsWith("pukulan")   -> "Melatih arah serangan, koordinasi tangan, dan posisi tubuh."
            idGerakan.startsWith("tangkisan") -> "Melatih pertahanan, reaksi, dan kestabilan kuda-kuda."
            else -> "Latihan teknik dasar Pencak Silat."
        }
    }

    private fun fokusGerakan(idGerakan: String): String {
        return when {
            idGerakan.startsWith("pukulan")   -> "Target: Tangan & Bahu"
            idGerakan.startsWith("tangkisan") -> "Target: Lengan & Kuda-kuda"
            else -> "Target: Tubuh"
        }
    }

    private fun panduanSingkat(idGerakan: String): String {
        val arahan = when {
            idGerakan.startsWith("pukulan")   -> "Pastikan tubuh terlihat jelas, bahu rileks, dan arah pukulan mengikuti panduan."
            idGerakan.startsWith("tangkisan") -> "Pastikan posisi kuda-kuda stabil dan gerakan tangan terlihat penuh oleh kamera."
            else -> "Pastikan seluruh tubuh terlihat jelas oleh kamera sebelum memulai evaluasi."
        }
        return "Panduan Gerakan\n$arahan"
    }

    private fun definisiGerakan(idGerakan: String, labelGerakan: String): String {
        return when (idGerakan) {
            "tangkisan_1" -> "Tangan yang melakukan tangkisan dalam posisi terbuka menyerupai sikap hormat. Posisi bahu, siku, dan pergelangan tangan membentuk sudut sekitar 90 derajat. Tangan lainnya mengepal dan berada di samping pinggang sebagai posisi siap. Posisi kaki menggunakan kuda-kuda depan dengan lutut menekuk sekitar 130 derajat untuk menjaga keseimbangan tubuh."
            "tangkisan_2" -> "Tangkisan dilakukan dengan tangan bergerak ke arah samping untuk menghalau serangan dari sisi. Posisi tangan aktif membentuk sudut yang mengalihkan serangan, sementara tangan lainnya bersiap di pinggang. Kuda-kuda menjaga keseimbangan selama gerakan berlangsung."
            "tangkisan_3" -> "Tangan yang melakukan tangkisan dalam posisi mengepal dan bergerak melewati atas kepala untuk melindungi bagian kepala dari serangan. Posisi bahu, siku, dan pergelangan tangan membentuk sudut sekitar 90 sampai 100 derajat. Tangan lainnya terbuka seperti sikap hormat dan berada di samping pinggang. Posisi kaki menggunakan kuda-kuda dengan lutut menekuk sekitar 130 derajat."
            "pukulan_2" -> "Pukulan dilakukan dengan arah lurus ke samping, sejajar dengan bahu. Tangan yang memukul dalam posisi mengepal dengan lengan lurus ke arah sasaran. Tangan lainnya berada di samping pinggang dalam posisi siap sebagai penyeimbang gerakan."
            "pukulan_4" -> "Pukulan diarahkan ke bagian tulang rusuk lawan. Arah pukulan menyilang dengan tangan yang digunakan. Jika menggunakan tangan kanan, maka pukulan diarahkan sejajar dengan rusuk kiri lawan, dan sebaliknya jika menggunakan tangan kiri."
            else -> "Gerakan $labelGerakan merupakan bagian dari teknik dasar yang perlu dikuasai. Pastikan postur tubuh tegap dan kuda-kuda stabil sebelum melakukan gerakan."
        }
    }
}
