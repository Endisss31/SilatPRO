package com.el.silatpro.ai

import com.el.silatpro.model.DataPose
import com.el.silatpro.model.TitikTubuh

/**
 * Membantu menstabilkan keypoint pose menggunakan algoritma One Euro Filter.
 * Menyeimbangkan antara kehalusan (saat diam) dan responsivitas (saat bergerak).
 */
class PenstabilPose(
    private val minCutoff: Float = 0.5f,
    private val beta: Float = 0.05f,
    private val dCutoff: Float = 1.0f
) {

    private val filterX = mutableMapOf<Int, OneEuroFilter>()
    private val filterY = mutableMapOf<Int, OneEuroFilter>()

    /**
     * Menstabilkan pose berdasarkan data sebelumnya.
     */
    fun stabilkan(poseBaru: DataPose): DataPose {
        val timestamp = System.currentTimeMillis()
        val daftarTitikStabil = mutableListOf<TitikTubuh>()

        for (titik in poseBaru.titikTubuh) {
            // Dapatkan atau buat filter untuk titik ini
            val fX = filterX.getOrPut(titik.indeks) { OneEuroFilter(minCutoff, beta, dCutoff) }
            val fY = filterY.getOrPut(titik.indeks) { OneEuroFilter(minCutoff, beta, dCutoff) }

            // Terapkan filter
            val xStabil = fX.filter(titik.x, timestamp)
            val yStabil = fY.filter(titik.y, timestamp)

            daftarTitikStabil.add(
                titik.copy(x = xStabil, y = yStabil)
            )
        }

        return DataPose(daftarTitikStabil, poseBaru.konfiden)
    }

    /**
     * Reset state filter (panggil saat sesi baru atau ganti kamera).
     */
    fun reset() {
        filterX.clear()
        filterY.clear()
    }

    /**
     * Inner class implementasi One Euro Filter.
     */
    private class OneEuroFilter(
        private val minCutoff: Float,
        private val beta: Float,
        private val dCutoff: Float
    ) {
        private var xPrev: Float? = null
        private var dxPrev: Float = 0f
        private var tPrev: Long? = null

        fun filter(x: Float, timestamp: Long): Float {
            if (xPrev == null || tPrev == null) {
                xPrev = x
                tPrev = timestamp
                return x
            }

            val dt = (timestamp - tPrev!!) / 1000f
            if (dt <= 0) return xPrev!!

            // 1. Hitung laju perubahan (derivative)
            val dx = (x - xPrev!!) / dt
            val edx = alpha(dt, dCutoff) * dx + (1 - alpha(dt, dCutoff)) * dxPrev
            
            // 2. Hitung cutoff adaptif berdasarkan kecepatan
            val cutoff = minCutoff + beta * Math.abs(edx)
            
            // 3. Terapkan smoothing
            val a = alpha(dt, cutoff)
            val result = a * x + (1 - a) * xPrev!!
            
            // Simpan state untuk iterasi berikutnya
            xPrev = result
            dxPrev = edx
            tPrev = timestamp
            
            return result
        }

        private fun alpha(dt: Float, cutoff: Float): Float {
            val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
            return 1f / (1f + tau / dt)
        }
    }
}
