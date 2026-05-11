package com.el.silatpro

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import com.el.silatpro.databinding.DialogKetentuanKameraBinding

/**
 * DialogKetentuanKamera
 * ─────────────────────────────────────────────────────────
 * Popup onboarding yang tampil saat pertama kali pengguna masuk ke
 * halaman kamera evaluasi atau klasifikasi.
 *
 * Fitur:
 * - Tampil otomatis saat activity dibuka
 * - Checkbox "Jangan tampilkan lagi" disimpan via SharedPreferences
 * - Background gelap semi-transparan tanpa dialog frame bawaan
 *
 * Penggunaan:
 * ```kotlin
 * DialogKetentuanKamera.tampilkan(this, kunci = "evaluasi") {
 *     // callback setelah pengguna tekan "Siap, Mulai!"
 * }
 * ```
 */
object DialogKetentuanKamera {

    private const val PREF_NAME = "pref_ketentuan_kamera"

    /**
     * Tampilkan dialog jika belum pernah di-dismiss dengan "jangan tampilkan lagi".
     *
     * @param context  Activity context
     * @param kunci    Kunci unik per halaman (mis. "evaluasi" / "klasifikasi")
     *                 agar masing-masing halaman bisa punya preferensi sendiri.
     * @param onMulai  Lambda yang dipanggil saat pengguna menekan tombol "Siap, Mulai!"
     */
    fun tampilkan(context: Context, kunci: String = "kamera", onMulai: () -> Unit = {}) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val sudahDismiss = prefs.getBoolean("dismiss_$kunci", false)

        if (sudahDismiss) {
            // Pengguna sudah pilih "jangan tampilkan lagi" — langsung jalankan callback
            onMulai()
            return
        }

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)   // wajib tekan tombol, tidak bisa dismiss dengan back/tap luar

        val binding = DialogKetentuanKameraBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        // ── Styling Window: background transparan + full width ──────────
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            // Efek dim di belakang dialog
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes?.also { it.dimAmount = 0.70f }
        }

        // ── Tombol Mulai ─────────────────────────────────────────────────
        binding.btnMengerti.setOnClickListener {
            // Simpan preferensi jika checkbox dicentang
            if (binding.cbJanganTampilLagi.isChecked) {
                prefs.edit().putBoolean("dismiss_$kunci", true).apply()
            }
            dialog.dismiss()
            onMulai()
        }

        dialog.show()
    }

    /**
     * Reset preferensi "jangan tampilkan lagi" untuk semua kunci.
     * Berguna untuk keperluan debug / reset dari menu pengaturan.
     */
    fun resetSemua(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
