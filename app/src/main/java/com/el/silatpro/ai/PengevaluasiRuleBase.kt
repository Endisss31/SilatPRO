package com.el.silatpro.ai

import android.content.Context
import android.util.Log
import com.el.silatpro.model.DataPose
import com.el.silatpro.model.TitikTubuh
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlin.math.*

/**
 * PengevaluasiRuleBase — evaluasi gerakan berbasis aturan dari JSON reference.
 *
 * Format JSON baru (pukulan2kanan_rule_reference.json):
 *   - rule_features     : nilai acuan semua fitur geometri
 *   - suggested_rules   : kelompok fitur (kuda_kuda, arah_tangan, arah_pukulan)
 *
 * Evaluasi menghasilkan feedback SINGKAT (cocok untuk TTS):
 *   - Kuda-kuda: lebar stance & sudut lutut
 *   - Arah pukulan/tangkisan: posisi pergelangan tangan vs bahu (terlalu atas/bawah)
 *   - Semua benar → "Gerakan sudah tepat!"
 */
class PengevaluasiRuleBase(private val konteks: Context) {

    companion object {
        private const val TAG = "RuleBase"
        private const val MIN_KEYPOINT_CONF = 0.3f

        // Toleransi per fitur
        private const val TOL_SUDUT_SIKU  = 20.0  // derajat
        private const val TOL_SUDUT_LUTUT = 15.0  // derajat
        private const val TOL_STANCE      = 0.4   // normalized units
        private const val TOL_WRIST_Y     = 0.25  // normalized units

        // COCO 17 keypoint indices
        private const val IDX_BAHU_KIRI              = 5
        private const val IDX_BAHU_KANAN             = 6
        private const val IDX_SIKU_KIRI              = 7
        private const val IDX_SIKU_KANAN             = 8
        private const val IDX_PERGELANGAN_KIRI       = 9
        private const val IDX_PERGELANGAN_KANAN      = 10
        private const val IDX_PINGGUL_KIRI           = 11
        private const val IDX_PINGGUL_KANAN          = 12
        private const val IDX_LUTUT_KIRI             = 13
        private const val IDX_LUTUT_KANAN            = 14
        private const val IDX_PERGELANGAN_KAKI_KIRI  = 15
        private const val IDX_PERGELANGAN_KAKI_KANAN = 16
    }

    // ── Data class JSON (format baru) ──────────────────────────────────────────

    data class RuleReference(
        @SerializedName("label")          val label: String = "",
        @SerializedName("rule_features")  val ruleFeatures: Map<String, Double> = emptyMap(),
        @SerializedName("suggested_rules") val suggestedRules: SuggestedRules = SuggestedRules()
    )

    data class SuggestedRules(
        @SerializedName("kuda_kuda")   val kudaKuda: Map<String, Double>? = null,
        @SerializedName("arah_tangan") val arahTangan: Map<String, Double>? = null,
        @SerializedName("arah_pukulan") val arahPukulan: Map<String, Double>? = null,
        @SerializedName("arah_tangkisan") val arahTangkisan: Map<String, Double>? = null
    )

    data class HasilRuleBase(
        val skorTotal: Double,
        val kategori: String,
        val feedbackList: List<String>,  // max 2 item, singkat untuk TTS
        val skorPerFitur: Map<String, Double>
    )

    // ── State ──────────────────────────────────────────────────────────────────

    private var referensi: RuleReference? = null
    private val gson = Gson()
    private var namaFileDimuat = ""

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Muat JSON reference dari assets/movement_models/<namaFile>
     * Cache otomatis — tidak reload jika file sama.
     */
    fun muatModel(namaFile: String): Boolean {
        if (namaFile == namaFileDimuat && referensi != null) return true
        return try {
            val json = konteks.assets.open("movement_models/$namaFile")
                .bufferedReader().use { it.readText() }
            referensi = gson.fromJson(json, RuleReference::class.java)
            namaFileDimuat = namaFile
            Log.d(TAG, "Rule JSON dimuat: ${referensi?.label}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal muat JSON ($namaFile): ${e.message}")
            false
        }
    }

    /**
     * Evaluasi pose realtime terhadap JSON reference.
     *
     * @param pose  DataPose dari YOLO
     * @param sisi  "kanan" atau "kiri" — menentukan lengan mana yang aktif (memukul/menangkis)
     */
    fun evaluasi(pose: DataPose, sisi: String = "kanan"): HasilRuleBase? {
        val ref = referensi ?: return null
        if (!pose.apakahValid()) return null

        val fitur = hitungFiturRealtime(pose)
        if (fitur.isEmpty()) return null

        val feedbackList  = mutableListOf<String>()
        val skorPerFitur  = mutableMapOf<String, Double>()
        val rf = ref.ruleFeatures
        val sr = ref.suggestedRules

        val isKanan = sisi.equals("kanan", ignoreCase = true)

        // ── 1. Evaluasi Kuda-kuda ──────────────────────────────────────────────
        val stanceAcuan   = sr.kudaKuda?.get("stance_width_reference") ?: rf["stance_width"]
        val lutKiriAcuan  = sr.kudaKuda?.get("left_knee_angle_reference") ?: rf["left_knee_angle"]
        val lutKananAcuan = sr.kudaKuda?.get("right_knee_angle_reference") ?: rf["right_knee_angle"]

        fitur["stance_width"]?.let { stanceNow ->
            if (stanceAcuan != null) {
                val skor = hitungSkor(stanceNow, stanceAcuan, TOL_STANCE)
                skorPerFitur["kuda_kuda_stance"] = skor
                if (skor < 60.0) {
                    feedbackList.add(
                        if (stanceNow < stanceAcuan - TOL_STANCE) "Lebarkan kuda-kuda"
                        else "Kurangi lebar kuda-kuda"
                    )
                }
            }
        }

        // Lutut: aktif = sisi lengan aktif, pasif = sisi lainnya
        val namaLututAktif  = if (isKanan) "lutut kanan" else "lutut kiri"
        val namaLututPassif = if (isKanan) "lutut kiri"  else "lutut kanan"
        val lututAktifKey   = if (isKanan) "right_knee_angle" else "left_knee_angle"
        val lututPassifKey  = if (isKanan) "left_knee_angle"  else "right_knee_angle"
        val lututAktifAcuan  = if (isKanan) lutKananAcuan else lutKiriAcuan
        val lututPassifAcuan = if (isKanan) lutKiriAcuan  else lutKananAcuan

        fitur[lututAktifKey]?.let { lNow ->
            if (lututAktifAcuan != null) {
                val skor = hitungSkor(lNow, lututAktifAcuan, TOL_SUDUT_LUTUT)
                skorPerFitur["lutut_aktif"] = skor
                if (skor < 60.0 && feedbackList.size < 2) {
                    feedbackList.add(
                        if (lNow < lututAktifAcuan - TOL_SUDUT_LUTUT) "Tekuk $namaLututAktif lebih dalam"
                        else "Luruskan sedikit $namaLututAktif"
                    )
                }
            }
        }

        fitur[lututPassifKey]?.let { lNow ->
            if (lututPassifAcuan != null) {
                val skor = hitungSkor(lNow, lututPassifAcuan, TOL_SUDUT_LUTUT)
                skorPerFitur["lutut_pasif"] = skor
                if (skor < 60.0 && feedbackList.size < 2) {
                    feedbackList.add(
                        if (lNow < lututPassifAcuan - TOL_SUDUT_LUTUT) "Tekuk $namaLututPassif lebih dalam"
                        else "Luruskan sedikit $namaLututPassif"
                    )
                }
            }
        }

        // ── 2. Evaluasi Arah Tangan / Pukulan / Tangkisan ─────────────────────
        // Lengan aktif  = sisi yang memukul/menangkis
        // Lengan guard  = sisi lain (ditarik/bertahan)
        val namaAktif = if (isKanan) "tangan kanan" else "tangan kiri"
        val namaGuard = if (isKanan) "tangan kiri"  else "tangan kanan"
        val arahMap   = sr.arahPukulan ?: sr.arahTangkisan ?: sr.arahTangan

        val wristAktifAcuan = when {
            isKanan -> sr.arahTangan?.get("right_wrist_to_right_shoulder_y_reference")
                       ?: arahMap?.get("right_punch_vector_y_reference")
                       ?: rf["right_wrist_to_right_shoulder_y"]
            else    -> sr.arahTangan?.get("left_wrist_to_left_shoulder_y_reference")
                       ?: arahMap?.get("left_punch_vector_y_reference")
                       ?: rf["left_wrist_to_left_shoulder_y"]
        }
        val wristGuardAcuan = when {
            isKanan -> sr.arahTangan?.get("left_wrist_to_left_shoulder_y_reference")
                       ?: arahMap?.get("left_punch_vector_y_reference")
                       ?: rf["left_wrist_to_left_shoulder_y"]
            else    -> sr.arahTangan?.get("right_wrist_to_right_shoulder_y_reference")
                       ?: arahMap?.get("right_punch_vector_y_reference")
                       ?: rf["right_wrist_to_right_shoulder_y"]
        }

        val fiturAktifKey = if (isKanan) "right_wrist_shoulder_y" else "left_wrist_shoulder_y"
        val fiturGuardKey = if (isKanan) "left_wrist_shoulder_y"  else "right_wrist_shoulder_y"

        fitur[fiturAktifKey]?.let { wNow ->
            if (wristAktifAcuan != null) {
                val skor = hitungSkor(wNow, wristAktifAcuan, TOL_WRIST_Y)
                skorPerFitur["arah_tangan_aktif"] = skor
                if (skor < 60.0 && feedbackList.size < 2) {
                    feedbackList.add(
                        if (wNow < wristAktifAcuan - TOL_WRIST_Y) "Naikkan $namaAktif"
                        else "Turunkan $namaAktif"
                    )
                }
            }
        }

        fitur[fiturGuardKey]?.let { wNow ->
            if (wristGuardAcuan != null) {
                val skor = hitungSkor(wNow, wristGuardAcuan, TOL_WRIST_Y)
                skorPerFitur["arah_tangan_guard"] = skor
                if (skor < 60.0 && feedbackList.size < 2) {
                    feedbackList.add(
                        if (wNow < wristGuardAcuan - TOL_WRIST_Y) "Naikkan $namaGuard"
                        else "Turunkan $namaGuard"
                    )
                }
            }
        }

        // ── Hitung skor total ──────────────────────────────────────────────────
        val skorTotal = if (skorPerFitur.isEmpty()) 0.0
                        else skorPerFitur.values.average()
        val kategori = tentukanKategori(skorTotal)

        // ── Feedback akhir ────────────────────────────────────────────────────
        val feedbackFinal = when {
            feedbackList.isEmpty() -> listOf("Gerakan sudah tepat!")
            else -> feedbackList.take(2) // Maksimal 2 poin untuk TTS
        }

        Log.d(TAG, "Skor [${ref.label}]: ${skorTotal.toInt()} ($kategori) | fb: $feedbackFinal")

        return HasilRuleBase(
            skorTotal    = skorTotal,
            kategori     = kategori,
            feedbackList = feedbackFinal,
            skorPerFitur = skorPerFitur
        )
    }

    // ── Hitung Fitur Realtime ──────────────────────────────────────────────────

    /**
     * Hitung fitur geometri dari DataPose realtime menggunakan koordinat body-relative normalized.
     * Menghasilkan map nama_fitur → nilai yang sebanding dengan rule_features di JSON.
     */
    private fun hitungFiturRealtime(pose: DataPose): Map<String, Double> {
        val hasil = mutableMapOf<String, Double>()

        // Ambil semua titik yang valid
        val bahuKiri  = pose.ambilTitikValid(IDX_BAHU_KIRI)
        val bahuKanan = pose.ambilTitikValid(IDX_BAHU_KANAN)
        val sikuKiri  = pose.ambilTitikValid(IDX_SIKU_KIRI)
        val sikuKanan = pose.ambilTitikValid(IDX_SIKU_KANAN)
        val perKiri   = pose.ambilTitikValid(IDX_PERGELANGAN_KIRI)
        val perKanan  = pose.ambilTitikValid(IDX_PERGELANGAN_KANAN)
        val pinKiri   = pose.ambilTitikValid(IDX_PINGGUL_KIRI)
        val pinKanan  = pose.ambilTitikValid(IDX_PINGGUL_KANAN)
        val lutKiri   = pose.ambilTitikValid(IDX_LUTUT_KIRI)
        val lutKanan  = pose.ambilTitikValid(IDX_LUTUT_KANAN)
        val kakiKiri  = pose.ambilTitikValid(IDX_PERGELANGAN_KAKI_KIRI)
        val kakiKanan = pose.ambilTitikValid(IDX_PERGELANGAN_KAKI_KANAN)

        // Hitung body reference (center + scale) — sama dengan normalisasi training
        val (bcX, bcY, bodyScale) = hitungBodyRef(pose)
        if (bodyScale < 1e-6) return hasil

        // Fungsi normalisasi koordinat
        fun nx(t: TitikTubuh) = ((t.x - bcX) / bodyScale).toDouble()
        fun ny(t: TitikTubuh) = ((t.y - bcY) / bodyScale).toDouble()

        // Sudut siku kiri: bahu_kiri–siku_kiri–pergelangan_kiri
        if (bahuKiri != null && sikuKiri != null && perKiri != null) {
            hasil["left_elbow_angle"] = sudutTigaTitik(
                nx(bahuKiri), ny(bahuKiri),
                nx(sikuKiri), ny(sikuKiri),
                nx(perKiri),  ny(perKiri)
            )
        }

        // Sudut siku kanan: bahu_kanan–siku_kanan–pergelangan_kanan
        if (bahuKanan != null && sikuKanan != null && perKanan != null) {
            hasil["right_elbow_angle"] = sudutTigaTitik(
                nx(bahuKanan), ny(bahuKanan),
                nx(sikuKanan), ny(sikuKanan),
                nx(perKanan),  ny(perKanan)
            )
        }

        // Sudut lutut kiri: pinggul_kiri–lutut_kiri–pergelangan_kaki_kiri
        if (pinKiri != null && lutKiri != null && kakiKiri != null) {
            hasil["left_knee_angle"] = sudutTigaTitik(
                nx(pinKiri), ny(pinKiri),
                nx(lutKiri), ny(lutKiri),
                nx(kakiKiri), ny(kakiKiri)
            )
        }

        // Sudut lutut kanan: pinggul_kanan–lutut_kanan–pergelangan_kaki_kanan
        if (pinKanan != null && lutKanan != null && kakiKanan != null) {
            hasil["right_knee_angle"] = sudutTigaTitik(
                nx(pinKanan), ny(pinKanan),
                nx(lutKanan), ny(lutKanan),
                nx(kakiKanan), ny(kakiKanan)
            )
        }

        // Stance width (ternormalisasi terhadap lebar bahu)
        if (kakiKiri != null && kakiKanan != null && bahuKiri != null && bahuKanan != null) {
            val lebarBahu = jarak(nx(bahuKiri), ny(bahuKiri), nx(bahuKanan), ny(bahuKanan))
            if (lebarBahu > 1e-6) {
                hasil["stance_width"] = jarak(nx(kakiKiri), ny(kakiKiri), nx(kakiKanan), ny(kakiKanan)) / lebarBahu
            }
        }

        // Wrist-to-shoulder Y diff (untuk arah tangan/pukulan)
        // Nilai positif = wrist lebih rendah dari bahu (ke bawah)
        if (perKanan != null && bahuKanan != null) {
            hasil["right_wrist_shoulder_y"] = ny(perKanan) - ny(bahuKanan)
        }
        if (perKiri != null && bahuKiri != null) {
            hasil["left_wrist_shoulder_y"] = ny(perKiri) - ny(bahuKiri)
        }

        return hasil
    }

    // ── Helper Geometri ────────────────────────────────────────────────────────

    private fun hitungBodyRef(pose: DataPose): Triple<Float, Float, Float> {
        fun kpX(idx: Int): Float = pose.ambilTitik(idx)
            ?.takeIf { it.konfiden >= MIN_KEYPOINT_CONF }?.x ?: 0f
        fun kpY(idx: Int): Float = pose.ambilTitik(idx)
            ?.takeIf { it.konfiden >= MIN_KEYPOINT_CONF }?.y ?: 0f

        val pkX  = kpX(IDX_PINGGUL_KIRI);  val pkY  = kpY(IDX_PINGGUL_KIRI)
        val pkaX = kpX(IDX_PINGGUL_KANAN); val pkaY = kpY(IDX_PINGGUL_KANAN)
        val bkX  = kpX(IDX_BAHU_KIRI);     val bkY  = kpY(IDX_BAHU_KIRI)
        val bkaX = kpX(IDX_BAHU_KANAN);    val bkaY = kpY(IDX_BAHU_KANAN)

        // center = midpoint(left_hip, right_hip) — identik dengan Normalizer.kt
        val bcX = (pkX + pkaX) / 2f
        val bcY = (pkY + pkaY) / 2f

        // scale = shoulder_width, fallback = hip_width
        val lBahu    = sqrt(((bkX - bkaX).pow(2) + (bkY - bkaY).pow(2)).toDouble()).toFloat()
        val lPinggul = sqrt(((pkX - pkaX).pow(2) + (pkY - pkaY).pow(2)).toDouble()).toFloat()
        val scale    = (if (lBahu > 1e-6f) lBahu else lPinggul).let { if (it < 1e-6f) 1f else it }

        return Triple(bcX, bcY, scale)
    }

    private fun sudutTigaTitik(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double {
        val ba = doubleArrayOf(ax - bx, ay - by)
        val bc = doubleArrayOf(cx - bx, cy - by)
        val dot = ba[0] * bc[0] + ba[1] * bc[1]
        val magBa = sqrt(ba[0].pow(2) + ba[1].pow(2))
        val magBc = sqrt(bc[0].pow(2) + bc[1].pow(2))
        if (magBa < 1e-9 || magBc < 1e-9) return 0.0
        return Math.toDegrees(acos((dot / (magBa * magBc)).coerceIn(-1.0, 1.0)))
    }

    private fun jarak(x1: Double, y1: Double, x2: Double, y2: Double): Double =
        sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))

    // ── Scoring ────────────────────────────────────────────────────────────────

    /** Skor 0–100: dalam toleransi = 100, menurun linear sampai 0 di 2× toleransi */
    private fun hitungSkor(nilai: Double, target: Double, toleransi: Double): Double {
        val selisih = abs(nilai - target)
        return when {
            selisih <= toleransi -> 100.0
            toleransi <= 0.0    -> if (selisih < 1e-9) 100.0 else 0.0
            else -> (100.0 - ((selisih - toleransi) / toleransi) * 100.0).coerceAtLeast(0.0)
        }
    }

    private fun tentukanKategori(skor: Double): String = when {
        skor >= 85 -> "Sangat Baik"
        skor >= 70 -> "Baik"
        skor >= 55 -> "Cukup"
        else       -> "Kurang"
    }

    // ── Extension ─────────────────────────────────────────────────────────────

    private fun DataPose.ambilTitikValid(indeks: Int) =
        ambilTitik(indeks)?.takeIf { it.konfiden >= MIN_KEYPOINT_CONF }
}
