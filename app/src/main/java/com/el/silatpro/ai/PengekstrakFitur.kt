package com.el.silatpro.ai

import com.el.silatpro.model.DataPose
import com.el.silatpro.model.TitikTubuh
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Mengekstrak fitur dari DataPose untuk model ML.
 *
 * Normalisasi WAJIB sama persis dengan training:
 *   shoulder_center = tengah (left_shoulder, right_shoulder)
 *   hip_center      = tengah (left_hip, right_hip)
 *   body_center     = tengah (shoulder_center, hip_center)
 *   body_scale      = max(lebar_bahu, lebar_pinggul)
 *   normalized      = (keypoint - body_center) / body_scale
 *
 * Output:
 *   ekstrak()   → FloatArray(42) — untuk model global (34 keypoints + 8 geometric features)
 *   ekstrak39() → FloatArray(39) — untuk model evaluasi spesifik
 */
class PengekstrakFitur {

    companion object {
        const val MIN_KONFIDEN_KEYPOINT = 0.3f
        const val MIN_KEYPOINT_VALID = 10

        // COCO 17 keypoint indices
        private const val K_BAHU_KIRI    = 5
        private const val K_BAHU_KANAN   = 6
        private const val K_SIKU_KIRI    = 7
        private const val K_SIKU_KANAN   = 8
        private const val K_WRIST_KIRI   = 9
        private const val K_WRIST_KANAN  = 10
        private const val K_PINGGUL_KIRI = 11
        private const val K_PINGGUL_KANAN= 12
        private const val K_LUTUT_KIRI   = 13
        private const val K_LUTUT_KANAN  = 14
        private const val K_ANKLE_KIRI   = 15
        private const val K_ANKLE_KANAN  = 16
    }

    /** Minimal 10 dari 17 keypoint harus confidence >= 0.3 */
    fun apakahLayakDiklasifikasi(pose: DataPose): Boolean {
        var valid = 0
        for (i in 0..16) {
            val t = pose.ambilTitik(i)
            if (t != null && t.konfiden >= MIN_KONFIDEN_KEYPOINT) valid++
        }
        return valid >= MIN_KEYPOINT_VALID
    }

    /**
     * Ekstrak 42 fitur untuk model global:
     *   0..33  : 17 keypoint normalisasi x,y
     *   34..41 : 8 fitur geometri (sudut & jarak) ternormalisasi
     */
    fun ekstrak(pose: DataPose): FloatArray {
        val (bodyCenterX, bodyCenterY, bodyScale) = hitungBodyRef(pose)
        val fitur = FloatArray(42)
        
        // 1. Koordinat keypoint (0..33)
        for (i in 0..16) {
            val t = pose.ambilTitik(i)
            if (t != null && t.konfiden >= MIN_KONFIDEN_KEYPOINT) {
                fitur[i * 2]     = (t.x - bodyCenterX) / bodyScale
                fitur[i * 2 + 1] = (t.y - bodyCenterY) / bodyScale
            }
        }

        // 2. Fitur Geometri (34..41)
        val x = { k: Int -> fitur[k * 2] }
        val y = { k: Int -> fitur[k * 2 + 1] }

        // Helper untuk sudut / 180
        fun sudutNorm(a: Int, b: Int, c: Int) = sudut(x(a), y(a), x(b), y(b), x(c), y(c)) / 180f

        fitur[34] = sudutNorm(K_BAHU_KIRI,  K_SIKU_KIRI,  K_WRIST_KIRI)   // angle_left_arm
        fitur[35] = sudutNorm(K_BAHU_KANAN, K_SIKU_KANAN, K_WRIST_KANAN)  // angle_right_arm
        fitur[36] = sudutNorm(K_PINGGUL_KIRI,  K_LUTUT_KIRI,  K_ANKLE_KIRI)  // angle_left_leg
        fitur[37] = sudutNorm(K_PINGGUL_KANAN, K_LUTUT_KANAN, K_ANKLE_KANAN) // angle_right_leg
        fitur[38] = sudutNorm(K_BAHU_KIRI,  K_PINGGUL_KIRI,  K_LUTUT_KIRI)   // angle_left_waist
        fitur[39] = sudutNorm(K_BAHU_KANAN, K_PINGGUL_KANAN, K_LUTUT_KANAN)  // angle_right_waist
        
        // Jarak (ternormalisasi terhadap bodyScale)
        val dist = { k1: Int, k2: Int -> sqrt(((x(k1)-x(k2))*(x(k1)-x(k2)) + (y(k1)-y(k2))*(y(k1)-y(k2))).toDouble()).toFloat() }
        val lebarBahu = dist(K_BAHU_KIRI, K_BAHU_KANAN).coerceAtLeast(0.1f)
        
        fitur[40] = dist(K_ANKLE_KIRI, K_ANKLE_KANAN) / lebarBahu  // dist_feet_normalized
        fitur[41] = dist(K_WRIST_KIRI, K_WRIST_KANAN) / lebarBahu  // dist_hands_normalized

        return fitur
    }

    /**
     * Ekstrak 39 fitur untuk model evaluasi spesifik:
     *   f0–f33  : 17 keypoint normalisasi x,y (sama dengan ekstrak())
     *   f34–f38 : 5 fitur rule-based sesuai tipe gerakan + sisi
     *
     * @param pose         DataPose dari YOLO
     * @param tipeGerakan  "pukulan" | "tangkisan" | "tendangan"
     * @param sisi         "kanan" | "kiri"
     */
    fun ekstrak39(pose: DataPose, tipeGerakan: String, sisi: String): FloatArray {
        // Model evaluasi spesifik masih menggunakan basis 34 keypoint
        val (bodyCenterX, bodyCenterY, bodyScale) = hitungBodyRef(pose)
        val fitur34 = FloatArray(34)
        for (i in 0..16) {
            val t = pose.ambilTitik(i)
            if (t != null && t.konfiden >= MIN_KONFIDEN_KEYPOINT) {
                fitur34[i * 2]     = (t.x - bodyCenterX) / bodyScale
                fitur34[i * 2 + 1] = (t.y - bodyCenterY) / bodyScale
            }
        }
        val fiturRule = hitungFiturRule(fitur34, tipeGerakan, sisi)
        return fitur34 + fiturRule   // FloatArray(34) + FloatArray(5) = FloatArray(39)
    }

    // ── Rule feature computation ───────────────────────────────────────────────

    /**
     * Hitung 5 fitur rule-based dari fitur34 yang sudah ternormalisasi.
     * Menggunakan koordinat body-relative, sehingga konsisten dengan training.
     *
     * Layout (f34–f38):
     *   Pukulan/Tangkisan:
     *     f34 = elbow_angle / 180          (sisi aktif)
     *     f35 = left_knee_angle / 180
     *     f36 = right_knee_angle / 180
     *     f37 = wrist_shoulder_y_diff      (sisi aktif, normalized)
     *     f38 = wrist_distance_from_body   (sisi aktif)
     *
     *   Tendangan:
     *     f34 = active_knee_angle / 180    (kaki tendang)
     *     f35 = left_knee_angle / 180
     *     f36 = right_knee_angle / 180
     *     f37 = ankle_height_from_hip      (sisi aktif, hip_y - ankle_y)
     *     f38 = ankle_distance_from_body   (sisi aktif)
     */
    fun hitungFiturRule(fitur34: FloatArray, tipeGerakan: String, sisi: String): FloatArray {
        val rule = FloatArray(5)
        val isKanan = sisi.equals("kanan", ignoreCase = true)

        // Shorthand untuk mengambil koordinat keypoint dari fitur34
        fun x(k: Int) = fitur34[k * 2]
        fun y(k: Int) = fitur34[k * 2 + 1]

        val leftKneeAngle  = sudut(x(K_PINGGUL_KIRI),  y(K_PINGGUL_KIRI),
                                   x(K_LUTUT_KIRI),    y(K_LUTUT_KIRI),
                                   x(K_ANKLE_KIRI),    y(K_ANKLE_KIRI))
        val rightKneeAngle = sudut(x(K_PINGGUL_KANAN), y(K_PINGGUL_KANAN),
                                   x(K_LUTUT_KANAN),   y(K_LUTUT_KANAN),
                                   x(K_ANKLE_KANAN),   y(K_ANKLE_KANAN))

        when {
            tipeGerakan.equals("tendangan", ignoreCase = true) -> {
                // f34: active knee angle / 180
                val activeKnee = if (isKanan) rightKneeAngle else leftKneeAngle
                rule[0] = activeKnee / 180f

                // f35, f36: knee angles
                rule[1] = leftKneeAngle  / 180f
                rule[2] = rightKneeAngle / 180f

                // f37: ankle_height_from_hip = hip_y - ankle_y (positif = kaki terangkat)
                if (isKanan) {
                    rule[3] = y(K_PINGGUL_KANAN) - y(K_ANKLE_KANAN)
                } else {
                    rule[3] = y(K_PINGGUL_KIRI)  - y(K_ANKLE_KIRI)
                }

                // f38: ankle_distance_from_body (jarak dari origin 0,0 = body center)
                if (isKanan) {
                    rule[4] = sqrt((x(K_ANKLE_KANAN) * x(K_ANKLE_KANAN) +
                                    y(K_ANKLE_KANAN) * y(K_ANKLE_KANAN)).toDouble()).toFloat()
                } else {
                    rule[4] = sqrt((x(K_ANKLE_KIRI) * x(K_ANKLE_KIRI) +
                                    y(K_ANKLE_KIRI) * y(K_ANKLE_KIRI)).toDouble()).toFloat()
                }
            }

            else -> {
                // Pukulan dan Tangkisan
                // f34: elbow_angle / 180 (sisi aktif)
                val elbowAngle = if (isKanan) {
                    sudut(x(K_BAHU_KANAN), y(K_BAHU_KANAN),
                          x(K_SIKU_KANAN), y(K_SIKU_KANAN),
                          x(K_WRIST_KANAN),y(K_WRIST_KANAN))
                } else {
                    sudut(x(K_BAHU_KIRI), y(K_BAHU_KIRI),
                          x(K_SIKU_KIRI), y(K_SIKU_KIRI),
                          x(K_WRIST_KIRI),y(K_WRIST_KIRI))
                }
                rule[0] = elbowAngle / 180f

                // f35, f36: knee angles
                rule[1] = leftKneeAngle  / 180f
                rule[2] = rightKneeAngle / 180f

                // f37: wrist_shoulder_y_diff (wrist_y - shoulder_y, normalized coords)
                if (isKanan) {
                    rule[3] = y(K_WRIST_KANAN) - y(K_BAHU_KANAN)
                } else {
                    rule[3] = y(K_WRIST_KIRI)  - y(K_BAHU_KIRI)
                }

                // f38: wrist_distance_from_body (jarak dari origin 0,0 = body center)
                if (isKanan) {
                    rule[4] = sqrt((x(K_WRIST_KANAN) * x(K_WRIST_KANAN) +
                                    y(K_WRIST_KANAN) * y(K_WRIST_KANAN)).toDouble()).toFloat()
                } else {
                    rule[4] = sqrt((x(K_WRIST_KIRI) * x(K_WRIST_KIRI) +
                                    y(K_WRIST_KIRI) * y(K_WRIST_KIRI)).toDouble()).toFloat()
                }
            }
        }

        return rule
    }

    // ── Geometry helpers ───────────────────────────────────────────────────────

    /** Sudut di titik B dari segitiga A–B–C, dalam derajat (0–180). */
    private fun sudut(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        cx: Float, cy: Float
    ): Float {
        val bax = ax - bx; val bay = ay - by
        val bcx = cx - bx; val bcy = cy - by
        val dot  = bax * bcx + bay * bcy
        val magA = sqrt((bax * bax + bay * bay).toDouble()).toFloat()
        val magC = sqrt((bcx * bcx + bcy * bcy).toDouble()).toFloat()
        if (magA < 1e-9f || magC < 1e-9f) return 0f
        val cos = (dot / (magA * magC)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos.toDouble())).toFloat()
    }

    /** Hitung body reference (center + scale) dari pose. */
    private fun hitungBodyRef(pose: DataPose): Triple<Float, Float, Float> {
        fun kpVal(idx: Int, selector: (com.el.silatpro.model.TitikTubuh) -> Float): Float {
            val t = pose.ambilTitik(idx)
            return if (t != null && t.konfiden >= MIN_KONFIDEN_KEYPOINT) selector(t) else 0f
        }
        val bkX  = kpVal(K_BAHU_KIRI)    { it.x }; val bkY  = kpVal(K_BAHU_KIRI)    { it.y }
        val bkaX = kpVal(K_BAHU_KANAN)   { it.x }; val bkaY = kpVal(K_BAHU_KANAN)   { it.y }
        val pkX  = kpVal(K_PINGGUL_KIRI) { it.x }; val pkY  = kpVal(K_PINGGUL_KIRI) { it.y }
        val pkaX = kpVal(K_PINGGUL_KANAN){ it.x }; val pkaY = kpVal(K_PINGGUL_KANAN){ it.y }

        val scX = (bkX + bkaX) / 2f; val scY = (bkY + bkaY) / 2f
        val hcX = (pkX + pkaX) / 2f; val hcY = (pkY + pkaY) / 2f
        val bcX = (scX + hcX)  / 2f; val bcY = (scY + hcY)  / 2f

        val lBahu    = sqrt(((bkX - bkaX).let { it * it } + (bkY - bkaY).let { it * it }).toDouble()).toFloat()
        val lPinggul = sqrt(((pkX - pkaX).let { it * it } + (pkY - pkaY).let { it * it }).toDouble()).toFloat()
        val scale    = max(lBahu, lPinggul).let { if (it < 1e-6f) 1f else it }

        return Triple(bcX, bcY, scale)
    }

    // Extension: concatenate two FloatArrays
    private operator fun FloatArray.plus(other: FloatArray): FloatArray {
        val result = FloatArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}
