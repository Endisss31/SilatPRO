package com.el.silatpro.model

/**
 * Hasil evaluasi dari satu sesi latihan.
 */
data class HasilEvaluasi(
    val idGerakan: String,
    val labelGerakan: String,
    val skorTotal: Double,
    val kategori: String,
    val skorPerFitur: Map<String, Double>,
    val feedbackList: List<String>,
    val durasiMs: Long,
    /** true jika user melakukan gerakan di luar kategori yang sedang dievaluasi */
    val gerakanSalah: Boolean = false,
    /** nama gerakan yang terdeteksi global model (bisa berbeda dari target) */
    val gerakanTerdeteksi: String = ""
)
