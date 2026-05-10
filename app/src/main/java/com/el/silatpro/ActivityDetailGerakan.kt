package com.el.silatpro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.el.silatpro.databinding.ActivityDetailGerakanBinding
import com.el.silatpro.model.KelasGerakan
import com.el.silatpro.ui.beranda.AdapterVariasi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
            val navBar   = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Toolbar: tambah padding-top agar judul/back icon tidak tertimpa ikon status bar
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                statusBar.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
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
                val intent = Intent(this, ActivityKameraEvaluasi::class.java).apply {
                    putExtra("ID_VARIASI", idGerakanTerpilih)
                    // Mapping id gerakan → path model spesifik yang benar di assets/Model/
                    val assetModel = modelAsetDariId(idGerakanTerpilih)
                    putExtra("ASSET_MODEL", assetModel)
                    // Kirim juga nama file JSON rule-based (default: variasi kanan)
                    val ruleJson = ruleJsonDariId(idGerakanTerpilih)
                    putExtra("ASSET_RULE_JSON", ruleJson)
                }
                startActivity(intent)
            }
        }
    }

    /**
     * Peta id gerakan → path model TFLite spesifik di assets/Model/<Folder>/.
     * Nama file mengikuti konvensi: model_<gerakan_lowercase>_mlp.tflite
     */
    private fun modelAsetDariId(id: String): String = when (id) {
        "pukulan_2"   -> "Model/Pukulan2/model_pukulan2_mlp.tflite"
        "pukulan_4"   -> "Model/Pukulan4/model_pukulan4_mlp.tflite"
        "tangkisan_1" -> "Model/Tangkisan1/model_tangkisan1_mlp.tflite"
        "tangkisan_3" -> "Model/Tangkisan3/model_tangkisan3_mlp.tflite"
        "tendangan_2" -> "Model/Tendangan2/model_tendangan2_mlp.tflite"
        else -> {
            // Fallback: konstruksi dari id
            val folder = id.replace("_", "").replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }
            "Model/$folder/model_${id.replace("_", "")}_mlp.tflite"
        }
    }

    /**
     * Peta id gerakan → nama file JSON rule-based di assets/movement_models/.
     * Secara default menggunakan variasi "kanan" karena rule-based bersifat simetris
     * dan akan disesuaikan lagi oleh kamera evaluasi jika model mendeteksi sisi kiri.
     */
    private fun ruleJsonDariId(id: String): String = when (id) {
        "pukulan_2"   -> "pukulan_2_kanan.json"
        "pukulan_4"   -> "pukulan_4_kanan.json"
        "tangkisan_1" -> "tangkisan_1_kanan.json"
        "tangkisan_3" -> "tangkisan_3_kanan.json"
        "tendangan_2" -> "tendangan_2_kanan.json"
        else          -> ""
    }

    // Removed apakahSisiKiri

    private fun namaKategori(idGerakan: String): String {
        return when {
            idGerakan.startsWith("pukulan") -> "Pukulan"
            idGerakan.startsWith("tangkisan") -> "Tangkisan"
            idGerakan.startsWith("tendangan") -> "Tendangan"
            else -> "Silat"
        }
    }

    private fun deskripsiHero(idGerakan: String): String {
        return when {
            idGerakan.startsWith("pukulan") -> "Melatih arah serangan, koordinasi tangan, dan posisi tubuh."
            idGerakan.startsWith("tangkisan") -> "Melatih pertahanan, reaksi, dan kestabilan kuda-kuda."
            idGerakan.startsWith("tendangan") -> "Melatih keseimbangan, kekuatan kaki, dan kontrol tendangan."
            else -> "Latihan teknik dasar Pencak Silat."
        }
    }

    private fun fokusGerakan(idGerakan: String): String {
        return when {
            idGerakan.startsWith("pukulan") -> "Target: Tangan & Bahu"
            idGerakan.startsWith("tangkisan") -> "Target: Lengan & Kuda-kuda"
            idGerakan.startsWith("tendangan") -> "Target: Kaki & Core"
            else -> "Target: Tubuh"
        }
    }

    private fun panduanSingkat(idGerakan: String): String {
        val arahan = when {
            idGerakan.startsWith("pukulan") -> "Pastikan tubuh terlihat jelas, bahu rileks, dan arah pukulan mengikuti panduan."
            idGerakan.startsWith("tangkisan") -> "Pastikan posisi kuda-kuda stabil dan gerakan tangan terlihat penuh oleh kamera."
            idGerakan.startsWith("tendangan") -> "Pastikan seluruh tubuh terlihat, jaga keseimbangan, dan lakukan tendangan secara terkontrol."
            else -> "Pastikan seluruh tubuh terlihat jelas oleh kamera sebelum memulai evaluasi."
        }
        return "8 Langkah Gerakan\n$arahan"
    }

    private fun definisiGerakan(idGerakan: String, labelGerakan: String): String {
        return when (idGerakan) {
            "tangkisan_1" -> "Tangan yang melakukan tangkisan dalam posisi terbuka menyerupai sikap hormat. Posisi bahu, siku, dan pergelangan tangan membentuk sudut sekitar 90 derajat. Tangan lainnya mengepal dan berada di samping pinggang sebagai posisi siap. Posisi kaki menggunakan kuda-kuda depan dengan lutut menekuk sekitar 130 derajat untuk menjaga keseimbangan tubuh."
            "tangkisan_3" -> "Tangan yang melakukan tangkisan dalam posisi mengepal dan bergerak melewati atas kepala untuk melindungi bagian kepala dari serangan. Posisi bahu, siku, dan pergelangan tangan membentuk sudut sekitar 90 sampai 100 derajat. Tangan lainnya terbuka seperti sikap hormat dan berada di samping pinggang. Posisi kaki menggunakan kuda-kuda dengan lutut menekuk sekitar 130 derajat."
            "pukulan_2" -> "Pukulan dilakukan dengan arah lurus ke depan sejajar dengan bahu. Tangan yang memukul dalam posisi mengepal dengan lengan lurus ke arah sasaran. Tangan lainnya berada di samping pinggang dalam posisi siap sebagai penyeimbang gerakan."
            "pukulan_4" -> "Pukulan diarahkan ke bagian tulang rusuk lawan. Arah pukulan menyesuaikan dengan tangan yang digunakan. Jika menggunakan tangan kanan, maka pukulan diarahkan sejajar dengan rusuk kanan lawan, dan sebaliknya jika menggunakan tangan kiri."
            "tendangan_2" -> "Tendangan dilakukan dengan mengangkat kaki dan mengarahkannya ke bagian dada lawan. Posisi tubuh tetap seimbang dengan satu kaki sebagai tumpuan dan kedua tangan berada pada posisi siap untuk menjaga keseimbangan dan pertahanan."
            else -> "Gerakan $labelGerakan merupakan bagian dari teknik dasar yang perlu dikuasai. Pastikan postur tubuh tegap dan kuda-kuda stabil sebelum melakukan gerakan."
        }
    }
}
