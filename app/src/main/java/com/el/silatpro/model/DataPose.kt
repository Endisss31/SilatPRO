package com.el.silatpro.model

/**
 * Representasi satu titik tubuh (keypoint) dari deteksi YOLOv8-Pose.
 * Standar COCO 17 keypoints.
 */
data class TitikTubuh(
    val indeks: Int,
    val x: Float,
    val y: Float,
    val konfiden: Float
) {
    companion object {
        const val HIDUNG = 0
        const val MATA_KIRI = 1
        const val MATA_KANAN = 2
        const val TELINGA_KIRI = 3
        const val TELINGA_KANAN = 4
        const val BAHU_KIRI = 5
        const val BAHU_KANAN = 6
        const val SIKU_KIRI = 7
        const val SIKU_KANAN = 8
        const val PERGELANGAN_KIRI = 9
        const val PERGELANGAN_KANAN = 10
        const val PINGGUL_KIRI = 11
        const val PINGGUL_KANAN = 12
        const val LUTUT_KIRI = 13
        const val LUTUT_KANAN = 14
        const val PERGELANGAN_KAKI_KIRI = 15
        const val PERGELANGAN_KAKI_KANAN = 16
    }
}

/**
 * Hasil deteksi pose: berisi 17 titik tubuh.
 */
data class DataPose(
    val titikTubuh: List<TitikTubuh>,
    val konfiden: Float
) {
    fun ambilTitik(indeks: Int): TitikTubuh? = titikTubuh.find { it.indeks == indeks }

    /**
     * Pose dianggap valid jika minimal 10 dari 17 keypoint COCO
     * memiliki confidence >= 0.3 (sesuai filter training pipeline).
     */
    fun apakahValid(): Boolean {
        var validCount = 0
        for (i in 0..16) {
            val titik = ambilTitik(i)
            if (titik != null && titik.konfiden >= 0.3f) {
                validCount++
            }
        }
        return validCount >= 10
    }
}
