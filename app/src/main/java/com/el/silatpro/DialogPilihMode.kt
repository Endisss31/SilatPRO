package com.el.silatpro

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import com.el.silatpro.databinding.DialogPilihModeBinding

/**
 * Dialog pemilih mode evaluasi.
 *
 * Menampilkan dua pilihan:
 *  1. [MODE_REKAM]    → Rekam 5 detik → proses offline dengan YOLOv8x (akurat)
 *  2. [MODE_REALTIME] → Evaluasi langsung dengan YOLOv8x
 *
 * Usage:
 *  DialogPilihMode.tampilkan(context) { mode ->
 *      when (mode) {
 *          DialogPilihMode.MODE_REKAM    -> // luncurkan ActivityKameraRekam
 *          DialogPilihMode.MODE_REALTIME -> // luncurkan ActivityKameraEvaluasi
 *      }
 *  }
 */
object DialogPilihMode {

    const val MODE_REKAM = "rekam"
    const val MODE_REALTIME = "realtime"

    fun tampilkan(context: Context, onPilih: (String) -> Unit) {
        val binding = DialogPilihModeBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(binding.root)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes?.also { it.dimAmount = 0.75f }
            }
            setCancelable(true)
        }

        binding.cardModeRekam.setOnClickListener {
            dialog.dismiss()
            onPilih(MODE_REKAM)
        }

        binding.cardModeRealtime.setOnClickListener {
            dialog.dismiss()
            onPilih(MODE_REALTIME)
        }

        binding.btnBatalPilihMode.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
