package com.el.silatpro.ai

import android.content.Context
import android.util.Log
import com.el.silatpro.model.DataPose
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlin.math.*

/**
 * PengevaluasiRuleBase — evaluasi gerakan berbasis aturan (rule-based) yang tipis.
 *
 * Cara kerja:
 *   1. Baca movement model JSON dari assets/movement_models/<id>.json
 *   2. Hitung fitur geometri dari DataPose (sudut lengan, kaki, pinggang, jarak)
 *   3. Bandingkan setiap fitur terhadap target + toleransi dari JSON
 *   4. Hasilkan skor per fitur (0–100) dan feedback teks per fitur
 *   5. Skor total = rata-rata tertimbang skor semua fitur yang dievaluasi
 *
 * Rule-based ini MELENGKAPI model ML — bukan menggantikannya.
 * ML mendeteksi jenis gerakan, rule-based mendetail bagian mana yang kurang.
 */
class PengevaluasiRuleBase(private val konteks: Context) {

    companion object {
        private const val TAG = "RuleBase"
        private const val MIN_KEYPOINT_CONF = 0.3f

        // Indeks COCO 17 keypoints
        private const val IDX_HIDUNG        = 0
        private const val IDX_BAHU_KIRI     = 5
        private const val IDX_BAHU_KANAN    = 6
        private const val IDX_SIKU_KIRI     = 7
        private const val IDX_SIKU_KANAN    = 8
        private const val IDX_PERGELANGAN_KIRI  = 9
        private const val IDX_PERGELANGAN_KANAN = 10
        private const val IDX_PINGGUL_KIRI  = 11
        private const val IDX_PINGGUL_KANAN = 12
        private const val IDX_LUTUT_KIRI    = 13
        private const val IDX_LUTUT_KANAN   = 14
        private const val IDX_PERGELANGAN_KAKI_KIRI  = 15
        private const val IDX_PERGELANGAN_KAKI_KANAN = 16
    }

    // ── Data class untuk parsing JSON ──────────────────────────────────────────

    data class ModelGerakan(
        @SerializedName("movement") val movement: DataGerakan
    )

    data class DataGerakan(
        @SerializedName("id")       val id: String,
        @SerializedName("label")    val label: String,
        @SerializedName("features") val features: Map<String, DataFitur>,
        @SerializedName("feedback_rules") val feedbackRules: List<FeedbackRule>
    )

    data class DataFitur(
        @SerializedName("target")    val target: Double,
        @SerializedName("tolerance") val tolerance: Double,
        @SerializedName("min")       val min: Double = 0.0,
        @SerializedName("max")       val max: Double = 0.0
    )

    data class FeedbackRule(
        @SerializedName("feature")   val feature: String,
        @SerializedName("target")    val target: Double,
        @SerializedName("tolerance") val tolerance: Double,
        @SerializedName("low")       val low: String,
        @SerializedName("high")      val high: String,
        @SerializedName("ok")        val ok: String
    )

    data class HasilRuleBase(
        val skorTotal: Double,           // 0.0 – 100.0
        val kategori: String,            // "Sangat Baik" / "Baik" / "Cukup" / "Kurang"
        val feedbackPerFitur: List<String>,  // satu feedback per fitur yang dievaluasi
        val skorPerFitur: Map<String, Double> // nama_fitur → skor 0-100
    )

    // ── State internal ─────────────────────────────────────────────────────────

    private var modelGerakan: ModelGerakan? = null
    private val gson = Gson()

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Muat model JSON dari assets/movement_models/<namaFile>
     * Contoh namaFile: "pukulan_2_kanan.json"
     */
    fun muatModel(namaFile: String): Boolean {
        return try {
            val json = konteks.assets.open("movement_models/$namaFile")
                .bufferedReader().use { it.readText() }
            modelGerakan = gson.fromJson(json, ModelGerakan::class.java)
            Log.d(TAG, "Rule-base model dimuat: ${modelGerakan?.movement?.label}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal muat rule-base model ($namaFile): ${e.message}")
            false
        }
    }

    /**
     * Evaluasi pose terhadap model acuan yang sudah dimuat.
     * @return HasilRuleBase atau null jika model belum dimuat / pose tidak valid
     */
    fun evaluasi(pose: DataPose): HasilRuleBase? {
        val model = modelGerakan ?: return null
        if (!pose.apakahValid()) return null

        // Hitung semua fitur geometri dari pose
        val fiturGeometri = hitungFiturGeometri(pose)
        if (fiturGeometri.isEmpty()) return null

        val feedbackList = mutableListOf<String>()
        val skorPerFitur = mutableMapOf<String, Double>()

        // Evaluasi setiap rule yang ada di JSON
        for (rule in model.movement.feedbackRules) {
            val nilaiTerukur = fiturGeometri[rule.feature] ?: continue

            val skor = hitungSkorFitur(nilaiTerukur, rule.target, rule.tolerance)
            skorPerFitur[rule.feature] = skor

            val feedback = tentikanFeedback(nilaiTerukur, rule)
            if (feedback != null) {
                feedbackList.add("• ${namaFiturBahasa(rule.feature)}: $feedback")
            }
        }

        // Skor total = rata-rata dari semua skor per fitur
        val skorTotal = if (skorPerFitur.isEmpty()) 0.0
            else skorPerFitur.values.average()

        val kategori = tentukanKategori(skorTotal)
        Log.d(TAG, "Skor RuleBase: ${skorTotal.toInt()} ($kategori) | fitur: ${skorPerFitur.size}")

        return HasilRuleBase(
            skorTotal = skorTotal,
            kategori = kategori,
            feedbackPerFitur = feedbackList,
            skorPerFitur = skorPerFitur
        )
    }

    // ── Fitur Geometri ─────────────────────────────────────────────────────────

    /**
     * Hitung semua fitur geometri yang dibutuhkan oleh rule-based.
     * Menghasilkan map: nama_fitur → nilai (derajat atau jarak ternormalisasi).
     */
    private fun hitungFiturGeometri(pose: DataPose): Map<String, Double> {
        val hasil = mutableMapOf<String, Double>()

        // Ambil koordinat titik-titik penting
        val bahuKiri   = pose.ambilTitikValid(IDX_BAHU_KIRI)
        val bahuKanan  = pose.ambilTitikValid(IDX_BAHU_KANAN)
        val sikuKiri   = pose.ambilTitikValid(IDX_SIKU_KIRI)
        val sikuKanan  = pose.ambilTitikValid(IDX_SIKU_KANAN)
        val perKiri    = pose.ambilTitikValid(IDX_PERGELANGAN_KIRI)
        val perKanan   = pose.ambilTitikValid(IDX_PERGELANGAN_KANAN)
        val pinKiri    = pose.ambilTitikValid(IDX_PINGGUL_KIRI)
        val pinKanan   = pose.ambilTitikValid(IDX_PINGGUL_KANAN)
        val lutKiri    = pose.ambilTitikValid(IDX_LUTUT_KIRI)
        val lutKanan   = pose.ambilTitikValid(IDX_LUTUT_KANAN)
        val kakiKiri   = pose.ambilTitikValid(IDX_PERGELANGAN_KAKI_KIRI)
        val kakiKanan  = pose.ambilTitikValid(IDX_PERGELANGAN_KAKI_KANAN)

        // Sudut lengan kiri: bahu_kiri–siku_kiri–pergelangan_kiri
        if (bahuKiri != null && sikuKiri != null && perKiri != null) {
            hasil["angle_left_arm"] = sudutTigaTitik(
                bahuKiri.x, bahuKiri.y,
                sikuKiri.x, sikuKiri.y,
                perKiri.x,  perKiri.y
            )
        }

        // Sudut lengan kanan: bahu_kanan–siku_kanan–pergelangan_kanan
        if (bahuKanan != null && sikuKanan != null && perKanan != null) {
            hasil["angle_right_arm"] = sudutTigaTitik(
                bahuKanan.x, bahuKanan.y,
                sikuKanan.x, sikuKanan.y,
                perKanan.x,  perKanan.y
            )
        }

        // Sudut kaki kiri (lutut): pinggul_kiri–lutut_kiri–pergelangan_kaki_kiri
        if (pinKiri != null && lutKiri != null && kakiKiri != null) {
            hasil["angle_left_leg"] = sudutTigaTitik(
                pinKiri.x, pinKiri.y,
                lutKiri.x, lutKiri.y,
                kakiKiri.x, kakiKiri.y
            )
        }

        // Sudut kaki kanan (lutut): pinggul_kanan–lutut_kanan–pergelangan_kaki_kanan
        if (pinKanan != null && lutKanan != null && kakiKanan != null) {
            hasil["angle_right_leg"] = sudutTigaTitik(
                pinKanan.x, pinKanan.y,
                lutKanan.x, lutKanan.y,
                kakiKanan.x, kakiKanan.y
            )
        }

        // Sudut pinggang kiri: bahu_kiri–pinggul_kiri–lutut_kiri
        if (bahuKiri != null && pinKiri != null && lutKiri != null) {
            hasil["angle_left_waist"] = sudutTigaTitik(
                bahuKiri.x, bahuKiri.y,
                pinKiri.x,  pinKiri.y,
                lutKiri.x,  lutKiri.y
            )
        }

        // Sudut pinggang kanan: bahu_kanan–pinggul_kanan–lutut_kanan
        if (bahuKanan != null && pinKanan != null && lutKanan != null) {
            hasil["angle_right_waist"] = sudutTigaTitik(
                bahuKanan.x, bahuKanan.y,
                pinKanan.x,  pinKanan.y,
                lutKanan.x,  lutKanan.y
            )
        }

        // Jarak antar kaki (ternormalisasi terhadap lebar bahu)
        if (kakiKiri != null && kakiKanan != null && bahuKiri != null && bahuKanan != null) {
            val lebarBahu = jarak(bahuKiri.x, bahuKiri.y, bahuKanan.x, bahuKanan.y)
            if (lebarBahu > 1e-6) {
                val jarakKaki = jarak(kakiKiri.x, kakiKiri.y, kakiKanan.x, kakiKanan.y)
                hasil["dist_feet_normalized"] = jarakKaki / lebarBahu
            }
        }

        // Jarak antar tangan (ternormalisasi terhadap lebar bahu)
        if (perKiri != null && perKanan != null && bahuKiri != null && bahuKanan != null) {
            val lebarBahu = jarak(bahuKiri.x, bahuKiri.y, bahuKanan.x, bahuKanan.y)
            if (lebarBahu > 1e-6) {
                val jarakTangan = jarak(perKiri.x, perKiri.y, perKanan.x, perKanan.y)
                hasil["dist_hands_normalized"] = jarakTangan / lebarBahu
            }
        }

        // Fitur khusus Pukulan 2 Kiri — cek arah pergelangan kanan terhadap bahu
        if (bahuKiri != null && bahuKanan != null && perKanan != null && perKiri != null) {
            // wrong_right_punch_alignment: pergelangan kanan sejajar bahu (nilai > 0.25 = pelanggaran)
            val midShoulderY = (bahuKiri.y + bahuKanan.y) / 2f
            val deltaY = abs(perKanan.y - midShoulderY)
            val lebarBahu = abs(bahuKiri.x - bahuKanan.x).coerceAtLeast(1f)
            hasil["wrong_right_punch_alignment"] = (deltaY / lebarBahu).toDouble()
        }

        return hasil
    }

    // ── Helper Geometri ────────────────────────────────────────────────────────

    /** Hitung sudut di titik B (vertex) dari segitiga A–B–C, dalam derajat (0–180) */
    private fun sudutTigaTitik(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        cx: Float, cy: Float
    ): Double {
        val ba = doubleArrayOf((ax - bx).toDouble(), (ay - by).toDouble())
        val bc = doubleArrayOf((cx - bx).toDouble(), (cy - by).toDouble())
        val dot = ba[0] * bc[0] + ba[1] * bc[1]
        val magBa = sqrt(ba[0].pow(2) + ba[1].pow(2))
        val magBc = sqrt(bc[0].pow(2) + bc[1].pow(2))
        if (magBa < 1e-9 || magBc < 1e-9) return 0.0
        val cosine = (dot / (magBa * magBc)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    private fun jarak(x1: Float, y1: Float, x2: Float, y2: Float): Double {
        return sqrt(((x2 - x1).toDouble().pow(2) + (y2 - y1).toDouble().pow(2)))
    }

    // ── Scoring & Feedback ─────────────────────────────────────────────────────

    /**
     * Hitung skor fitur tunggal (0–100):
     *  - dalam toleransi → 100
     *  - di luar toleransi → menurun linear, minimal 0 di 2× toleransi
     */
    private fun hitungSkorFitur(nilai: Double, target: Double, toleransi: Double): Double {
        val selisih = abs(nilai - target)
        return when {
            selisih <= toleransi -> 100.0
            toleransi <= 0.0    -> if (selisih < 1e-9) 100.0 else 0.0
            else -> {
                val overshoot = selisih - toleransi
                val maksimalOvershoot = toleransi  // di 2× toleransi → skor = 0
                val penurunan = (overshoot / maksimalOvershoot) * 100.0
                (100.0 - penurunan).coerceAtLeast(0.0)
            }
        }
    }

    /**
     * Tentukan feedback teks dari FeedbackRule:
     * - nilai < target - tolerance → "low" message
     * - nilai > target + tolerance → "high" message
     * - dalam toleransi → null (tidak perlu feedback)
     */
    private fun tentikanFeedback(nilai: Double, rule: FeedbackRule): String? {
        return when {
            nilai < rule.target - rule.tolerance -> rule.low
            nilai > rule.target + rule.tolerance -> rule.high
            else -> null  // Sudah dalam toleransi, tidak perlu ditampilkan
        }
    }

    /** Konversi nama fitur internal ke bahasa Indonesia yang ramah pengguna */
    private fun namaFiturBahasa(feature: String): String = when (feature) {
        "angle_left_arm"              -> "Lengan Kiri"
        "angle_right_arm"             -> "Lengan Kanan"
        "angle_left_leg"              -> "Kaki Kiri"
        "angle_right_leg"             -> "Kaki Kanan"
        "angle_left_waist"            -> "Pinggang Kiri"
        "angle_right_waist"           -> "Pinggang Kanan"
        "dist_feet_normalized"        -> "Jarak Kaki"
        "dist_hands_normalized"       -> "Jarak Tangan"
        "wrong_right_punch_alignment" -> "Arah Pukulan"
        else                          -> feature
    }

    /** Tentukan kategori berdasarkan skor total */
    private fun tentukanKategori(skor: Double): String = when {
        skor >= 85 -> "Sangat Baik"
        skor >= 70 -> "Baik"
        skor >= 55 -> "Cukup"
        else       -> "Kurang"
    }

    // ── Extension helper ───────────────────────────────────────────────────────

    /** Ambil titik hanya jika confidence memenuhi threshold */
    private fun DataPose.ambilTitikValid(indeks: Int) =
        ambilTitik(indeks)?.takeIf { it.konfiden >= MIN_KEYPOINT_CONF }
}
