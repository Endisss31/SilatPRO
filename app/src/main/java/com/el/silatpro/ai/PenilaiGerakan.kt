package com.el.silatpro.ai

import android.content.Context
import android.util.Log
import com.el.silatpro.model.DataPose
import com.el.silatpro.model.HasilEvaluasi
import com.el.silatpro.model.ScalerLabelGlobal

/**
 * Pipeline evaluasi gerakan (Mode Realtime Only):
 *
 *   YOLOv8n-Pose (ekstraksi keypoint)
 *       ↓
 *   Body-relative normalization → 34 fitur
 *       ↓
 *   Apply ScalerLabelGlobal (StandardScaler)
 *       ↓
 *   MLP Global (global_mlp_v8n.tflite) → 10 kelas
 *       ↓
 *   Validasi: gerakan = target yang dipilih user?
 *       ↓ TIDAK → tampilkan gerakan terdeteksi
 *       ↓ YA    → identifikasi sisi → load JSON rule reference → evaluasi
 *       ↓
 *   Feedback singkat (TTS-friendly, max 2 poin)
 *
 * JSON Rule: movement_models/<gerakan><sisi>_rule_reference.json
 */
class PenilaiGerakan(private val konteks: Context) {

    companion object {
        private const val TAG              = "PenilaiGerakan"
        private const val N_FITUR          = 34        // 17 keypoints × (x, y)
        private const val THRESH_GLOBAL    = 0.70f     // 70% confidence minimum
        private const val GLOBAL_MODEL     = "GlobalMovement/mlp_global_v8n.tflite"

        /**
         * Label model global — urutan HARUS sama dengan training global_mlp_v8n.tflite.
         * Menggunakan ScalerLabelGlobal.labels sebagai sumber kebenaran.
         */
        private val GLOBAL_LABELS get() = ScalerLabelGlobal.labels.toList()

        /**
         * Ubah grupId → prefix label global
         * Contoh: "pukulan_2" → "Pukulan2", "tangkisan_1" → "Tangkisan1"
         */
        private fun labelPrefixDariGrup(grupId: String): String =
            grupId.split("_").mapIndexed { i, s ->
                if (i == 0) s.replaceFirstChar { it.uppercase() }
                else s
            }.joinToString("")
    }

    // ── MLPClassifier (gunakan class baru yang benar) ────────────────────────
    private val mlpClassifier = MLPClassifier(konteks)

    // ── State ─────────────────────────────────────────────────────────────────
    private var grupTarget        = ""
    private var labelPrefixTarget = ""

    private val ruleBase      = PengevaluasiRuleBase(konteks)
    private var ruleBaseAktif = false

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Muat model global dan set target gerakan.
     * @param idGrupGerakan contoh: "pukulan_2", "tangkisan_1"
     */
    fun muatModel(idGrupGerakan: String): Boolean {
        grupTarget        = idGrupGerakan
        labelPrefixTarget = labelPrefixDariGrup(idGrupGerakan)

        val ok = mlpClassifier.init()
        if (ok) ruleBaseAktif = true
        return ok
    }

    /**
     * Evaluasi satu frame pose.
     * @param fiturGlobal FloatArray(≥34) dari PengekstrakFitur.ekstrak()
     * @param durasiMs    durasi sesi dalam ms
     * @param pose        DataPose dari YOLO (untuk rule-based)
     */
    fun evaluasi(
        fiturGlobal: FloatArray,   // kept for API compat — pose sekarang sumber utama
        durasiMs: Long,
        pose: DataPose? = null
    ): HasilEvaluasi {
        if (!mlpClassifier.isReady) return gagal("Model tidak dimuat", durasiMs)
        if (grupTarget.isEmpty())   return gagal("Target gerakan belum ditentukan", durasiMs)
        if (pose == null)           return gagal("Pose tidak tersedia", durasiMs)

        // ── Step 1: Normalisasi body-relative (center=hip, scale=shoulder) ────
        val bodyRel = Normalizer.bodyRelativeOnly(pose) ?: return gagalDenganKonteks(
            pesan = "Pastikan seluruh tubuh terlihat di kamera",
            durasiMs = durasiMs, salah = false, terdeteksi = ""
        )

        // ── Step 2: StandardScaler → MLP inference ────────────────────────────
        val scaled = FloatArray(34) { i ->
            val s = if (ScalerLabelGlobal.scale[i] < 1e-9f) 1f else ScalerLabelGlobal.scale[i]
            (bodyRel[i] - ScalerLabelGlobal.mean[i]) / s
        }
        val mlpResult    = mlpClassifier.classify(scaled)
        val labelDeteksi = mlpResult.label
        val probMax      = mlpResult.confidence

        Log.d(TAG, "Pred: $labelDeteksi (${(probMax*100).toInt()}%) target=$labelPrefixTarget")

        // ── Step 3: Validasi gerakan vs target ────────────────────────────────
        // Label format: "Pukulan2_Kanan" → prefix "Pukulan2"
        val gerakanCocok = labelDeteksi.startsWith(labelPrefixTarget, ignoreCase = true)

        if (!gerakanCocok) {
            if (probMax < THRESH_GLOBAL) {
                return gagalDenganKonteks(
                    pesan      = "Pastikan seluruh tubuh terlihat di kamera",
                    durasiMs   = durasiMs,
                    salah      = false,
                    terdeteksi = ""
                )
            }
            val namaTarget  = namaRamahGrup(grupTarget)
            val namaDeteksi = namaRamahLabel(labelDeteksi)
            return gagalDenganKonteks(
                pesan      = "Terdeteksi: $namaDeteksi. Lakukan $namaTarget",
                durasiMs   = durasiMs,
                salah      = true,
                terdeteksi = labelDeteksi
            )
        }

        // ── Step 4: Confidence di bawah threshold ─────────────────────────────
        if (probMax < THRESH_GLOBAL) {
            return gagalDenganKonteks(
                pesan      = "Lakukan ${namaRamahGrup(grupTarget)} dengan lebih jelas",
                durasiMs   = durasiMs,
                salah      = false,
                terdeteksi = labelDeteksi
            )
        }

        // ── Step 5: Identifikasi sisi → load JSON ─────────────────────────────
        // Label format: "Pukulan2_Kanan" atau "Pukulan2_Kiri"
        val sisi   = if (labelDeteksi.endsWith("Kiri", ignoreCase = true)) "kiri" else "kanan"
        // JSON file: e.g. "pukulan2kanan_rule_reference.json"
        val prefix = labelPrefixTarget.lowercase() // "pukulan2", "tangkisan1", dst
        val namaJson = "${prefix}${sisi}_rule_reference.json"

        if (ruleBaseAktif) {
            ruleBase.muatModel(namaJson)
        }

        val hasilRule = if (ruleBaseAktif && pose != null) ruleBase.evaluasi(pose, sisi) else null

        // ── Step 6: Bangun HasilEvaluasi ──────────────────────────────────────
        val namaLabel      = namaRamahLabel(labelDeteksi)
        val confidencePct  = (probMax * 100).toInt()
        val feedbackList   = mutableListOf<String>()
        val skorPerFitur   = mutableMapOf<String, Double>()
        skorPerFitur["Confidence"] = probMax.toDouble()

        val skorTotal: Double
        val kategori: String

        if (hasilRule != null) {
            skorTotal = hasilRule.skorTotal
            kategori  = hasilRule.kategori
            skorPerFitur.putAll(hasilRule.skorPerFitur)

            if (hasilRule.feedbackList.isEmpty() ||
                hasilRule.feedbackList.first() == "Gerakan sudah tepat!") {
                // Semua benar — pesan positif singkat
                feedbackList.add("$namaLabel — Gerakan sudah tepat!")
            } else {
                // Ada poin yang perlu diperbaiki
                feedbackList.add("$namaLabel ($confidencePct%)")
                feedbackList.addAll(hasilRule.feedbackList)
            }
        } else {
            skorTotal = 0.0
            kategori  = "Kurang"
            feedbackList.add("Menghitung posisi...")
        }

        return HasilEvaluasi(
            idGerakan         = grupTarget,
            labelGerakan      = namaLabel,
            skorTotal         = skorTotal,
            kategori          = kategori,
            skorPerFitur      = skorPerFitur,
            feedbackList      = feedbackList,
            durasiMs          = durasiMs,
            gerakanSalah      = false,
            gerakanTerdeteksi = labelDeteksi
        )
    }

    fun tutup() {
        mlpClassifier.close()
    }


    /**
     * Nama ramah dari grupId.
     * "pukulan_2" → "Pukulan 2"
     */
    private fun namaRamahGrup(grupId: String): String =
        grupId.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    /**
     * Nama ramah dari label global.
     * "Pukulan2_Kanan" → "Pukulan 2 Kanan"
     */
    private fun namaRamahLabel(label: String): String =
        label.replace("_", " ")
            .replace(Regex("(\\D)(\\d)")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
            .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    private fun gagal(msg: String, durasiMs: Long) = HasilEvaluasi(
        idGerakan    = grupTarget,
        labelGerakan = namaRamahGrup(grupTarget),
        skorTotal    = 0.0,
        kategori     = "Kurang",
        skorPerFitur = emptyMap(),
        feedbackList = listOf(msg),
        durasiMs     = durasiMs
    )

    private fun gagalDenganKonteks(
        pesan: String, durasiMs: Long, salah: Boolean, terdeteksi: String
    ) = HasilEvaluasi(
        idGerakan         = grupTarget,
        labelGerakan      = namaRamahGrup(grupTarget),
        skorTotal         = 0.0,
        kategori          = "Kurang",
        skorPerFitur      = emptyMap(),
        feedbackList      = listOf(pesan),
        durasiMs          = durasiMs,
        gerakanSalah      = salah,
        gerakanTerdeteksi = terdeteksi
    )
}
