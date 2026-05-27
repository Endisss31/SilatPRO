package com.el.silatpro.ai

import com.el.silatpro.model.DataPose
import com.el.silatpro.model.ScalerLabelGlobal
import kotlin.math.sqrt

/**
 * Normalizer: pipeline normalisasi identik dengan Colab training.
 *
 * STEP 1 — Body-Relative Normalization:
 *   center  = midpoint(left_hip, right_hip)          ← bukan shoulder/hip blend
 *   scale   = jarak shoulder_left ke shoulder_right  ← fallback: jarak hip
 *   x_norm  = (x - centerX) / scale
 *   y_norm  = (y - centerY) / scale
 *
 * STEP 2 — Standard Scaler (scikit-learn identik):
 *   x_scaled = (x_norm - mean) / std
 *
 * Output: FloatArray(34) — 17 keypoints × (x, y)
 */
object Normalizer {

    // COCO 17 keypoint indices
    private const val IDX_LEFT_SHOULDER  = 5
    private const val IDX_RIGHT_SHOULDER = 6
    private const val IDX_LEFT_HIP       = 11
    private const val IDX_RIGHT_HIP      = 12

    /**
     * Normalisasi 17 keypoint dari DataPose → FloatArray(34) scaled.
     *
     * @param pose DataPose dari PoseDetector (koordinat sudah di-reverse letterbox)
     * @param minConf threshold confidence keypoint (default 0.3)
     * @return FloatArray(34) siap masuk MLP, atau null jika pose tidak valid
     */
    fun normalize(pose: DataPose, minConf: Float = 0.3f): FloatArray? {
        // Ambil koordinat yang dibutuhkan untuk center & scale
        val lHip = pose.ambilTitik(IDX_LEFT_HIP)
        val rHip = pose.ambilTitik(IDX_RIGHT_HIP)
        val lSho = pose.ambilTitik(IDX_LEFT_SHOULDER)
        val rSho = pose.ambilTitik(IDX_RIGHT_SHOULDER)

        // Validasi: minimal kedua hip harus ada
        if (lHip == null || rHip == null) return null

        // ── STEP 1A: Hitung center = midpoint(left_hip, right_hip) ──────────
        val centerX = (lHip.x + rHip.x) / 2f
        val centerY = (lHip.y + rHip.y) / 2f

        // ── STEP 1B: Hitung scale = shoulder_width, fallback = hip_width ────
        val scale = if (lSho != null && rSho != null &&
                        lSho.konfiden >= minConf && rSho.konfiden >= minConf) {
            val dx = lSho.x - rSho.x
            val dy = lSho.y - rSho.y
            sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        } else {
            val dx = lHip.x - rHip.x
            val dy = lHip.y - rHip.y
            sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }.let { if (it < 1e-6f) 1f else it }

        // ── STEP 1C: Normalisasi 17 keypoint ─────────────────────────────────
        val bodyRel = FloatArray(34)
        for (i in 0..16) {
            val t = pose.ambilTitik(i)
            if (t != null && t.konfiden >= minConf) {
                bodyRel[i * 2]     = (t.x - centerX) / scale
                bodyRel[i * 2 + 1] = (t.y - centerY) / scale
            } else {
                bodyRel[i * 2]     = 0f
                bodyRel[i * 2 + 1] = 0f
            }
        }

        // ── STEP 2: Standard Scaler ───────────────────────────────────────────
        val mean  = ScalerLabelGlobal.mean
        val std   = ScalerLabelGlobal.scale
        val scaled = FloatArray(34)
        for (i in 0..33) {
            val s = if (std[i] < 1e-9f) 1f else std[i]
            scaled[i] = (bodyRel[i] - mean[i]) / s
        }

        return scaled
    }

    /**
     * Hitung body-relative tanpa scaler (untuk rule-based evaluator).
     */
    fun bodyRelativeOnly(pose: DataPose, minConf: Float = 0.3f): FloatArray? {
        val lHip = pose.ambilTitik(IDX_LEFT_HIP)
        val rHip = pose.ambilTitik(IDX_RIGHT_HIP)
        val lSho = pose.ambilTitik(IDX_LEFT_SHOULDER)
        val rSho = pose.ambilTitik(IDX_RIGHT_SHOULDER)
        if (lHip == null || rHip == null) return null

        val centerX = (lHip.x + rHip.x) / 2f
        val centerY = (lHip.y + rHip.y) / 2f
        val scale = if (lSho != null && rSho != null &&
                        lSho.konfiden >= minConf && rSho.konfiden >= minConf) {
            val dx = lSho.x - rSho.x; val dy = lSho.y - rSho.y
            sqrt((dx*dx+dy*dy).toDouble()).toFloat()
        } else {
            val dx = lHip.x - rHip.x; val dy = lHip.y - rHip.y
            sqrt((dx*dx+dy*dy).toDouble()).toFloat()
        }.let { if (it < 1e-6f) 1f else it }

        val bodyRel = FloatArray(34)
        for (i in 0..16) {
            val t = pose.ambilTitik(i)
            if (t != null && t.konfiden >= minConf) {
                bodyRel[i * 2]     = (t.x - centerX) / scale
                bodyRel[i * 2 + 1] = (t.y - centerY) / scale
            }
        }
        return bodyRel
    }
}
