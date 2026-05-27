package com.el.silatpro.model

/**
 * Scaler dan label untuk model MLP global (mlp_global_v8n.tflite).
 *
 * ⚠ WAJIB: nilai mean[] dan scale[] di sini HARUS sama persis dengan
 *           StandardScaler yang digunakan saat training di Colab.
 *           Export dengan: scaler.mean_.tolist() dan scaler.scale_.tolist()
 *
 * Normalisasi sebelum scaler (Normalizer.kt):
 *   center = midpoint(left_hip, right_hip)
 *   scale  = shoulder_width  (fallback: hip_width)
 *   x_norm = (x - centerX) / scale
 *
 * Urutan label HARUS sama dengan le.classes_ dari label_encoder_global_v8n.pkl
 */
object ScalerLabelGlobal {

    // ── Label Kelas (urutan = training LabelEncoder.classes_) ─────────────────
    // Verifikasi dengan: python -c "import pickle; le=pickle.load(open('label_encoder_global_v8n.pkl','rb')); print(list(le.classes_))"
    val labels = arrayOf(
        "Pukulan2_Kanan", "Pukulan2_Kiri",
        "Pukulan4_Kanan", "Pukulan4_Kiri",
        "Tangkisan1_Kanan", "Tangkisan1_Kiri",
        "Tangkisan2_Kanan", "Tangkisan2_Kiri",
        "Tangkisan3_Kanan", "Tangkisan3_Kiri"
    )

    val jumlahKelas: Int get() = labels.size

    // ── Standard Scaler ───────────────────────────────────────────────────────
    // DARI: scaler.mean_.tolist() di Colab training notebook
    // Normalisasi input: center=hip_midpoint, scale=shoulder_width
    val mean = floatArrayOf(
        0.00819612f, -2.62168797f, 0.14639840f, -2.75742109f, -0.10973167f, -2.76161175f,
        0.34608318f, -2.65471453f, -0.31037836f, -2.67509990f, 0.46974047f, -1.99620146f,
        -0.50960819f, -1.99166714f, 0.64023624f, -1.45689057f, -0.54376173f, -1.51945620f,
        0.58719517f, -1.50835881f, -0.49408358f, -1.65116524f, 0.45344611f, -0.01177401f,
        -0.45344613f,  0.01177400f, 1.33793010f,  0.71601499f, -1.25027145f, 0.81312764f,
        1.86531038f,  2.06437016f, -1.83988839f,  2.26180391f
    )

    // DARI: scaler.scale_.tolist() di Colab training notebook
    val scale = floatArrayOf(
        0.37303290f, 5.00858563f, 0.37334798f, 5.30433135f, 0.50443134f, 5.20298932f,
        0.31430771f, 5.13511991f, 1.12094479f, 4.96864458f, 0.45258699f, 3.81074711f,
        0.43337482f, 3.80716137f, 1.06011839f, 2.77549850f, 1.44579581f, 4.10271494f,
        1.59000932f, 2.62318703f, 1.60821017f, 5.98490661f, 0.29299112f, 0.13497273f,
        0.29299215f, 0.13497269f, 2.16531278f, 1.47076338f, 1.24077391f, 2.82341771f,
        2.70020792f, 3.39207820f, 3.32858508f, 5.57088160f
    )
}
