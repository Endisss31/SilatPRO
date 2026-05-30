package com.el.silatpro.ai

import android.content.Context
import android.util.Log
import com.el.silatpro.model.DataPose
import com.el.silatpro.model.ScalerLabelGlobal

class MesinKlasifikasi(context: Context) {

    companion object {
        private const val TAG = "MesinKlasifikasi"
    }

    private val mlp = MLPClassifier(context)
    val isReady get() = mlp.isReady

    fun inisialisasi(): Boolean {
        val ok = mlp.init()
        if (ok) Log.d(TAG, "MesinKlasifikasi siap (via MLPClassifier)")
        return ok
    }

    /**
     * Klasifikasikan gerakan dari pose.
     * @return Pair(label, confidence) — label = "Gerakan tidak dikenali" jika < threshold
     */
    fun klasifikasi(pose: DataPose): Pair<String, Double>? {
        // 1. Normalisasi body-relative + StandardScaler
        val scaled = Normalizer.normalize(pose) ?: run {
            Log.w(TAG, "Normalisasi gagal — pose tidak cukup keypoint")
            return null
        }

        // 2. MLP inference
        val result = mlp.classify(scaled)
        Log.d(TAG, "→ ${result.label} (${(result.confidence*100).toInt()}%)")

        return Pair(result.label, result.confidence.toDouble())
    }

    /**
     * Klasifikasikan dari FloatArray(34) yang sudah body-relative tapi BELUM di-scale.
     * Digunakan oleh PenilaiGerakan.
     */
    fun klasifikasiDariBodyRel(bodyRel34: FloatArray): MLPClassifier.Result {
        // Apply StandardScaler
        val mean  = ScalerLabelGlobal.mean
        val std   = ScalerLabelGlobal.scale
        val scaled = FloatArray(34) { i ->
            val s = if (std[i] < 1e-9f) 1f else std[i]
            (bodyRel34[i] - mean[i]) / s
        }
        return mlp.classify(scaled)
    }

    fun tutup() = mlp.close()
}
