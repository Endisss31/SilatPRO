package com.el.silatpro.ui.riwayat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.el.silatpro.databinding.ItemDetailFiturBinding

class AdapterDetailFitur(private val skorFitur: Map<String, Double>) :
    RecyclerView.Adapter<AdapterDetailFitur.ViewHolder>() {

    private val daftarKunci = skorFitur.keys.toList()

    companion object {
        /** Kamus translasi nama fitur teknis ke Bahasa Indonesia */
        val TRANSLASI_FITUR = mapOf(
            "angle_left_arm"         to "Sudut Lengan Kiri",
            "angle_right_arm"        to "Sudut Lengan Kanan",
            "angle_left_leg"         to "Sudut Kaki Kiri",
            "angle_right_leg"        to "Sudut Kaki Kanan",
            "angle_left_waist"       to "Sudut Pinggang Kiri",
            "angle_right_waist"      to "Sudut Pinggang Kanan",
            "dist_feet_normalized"   to "Jarak Antar Kaki",
            "dist_hands_normalized"  to "Jarak Antar Tangan"
        )
    }

    inner class ViewHolder(private val binding: ItemDetailFiturBinding) : RecyclerView.ViewHolder(binding.root) {
        fun ikat(kunci: String, nilai: Double) {
            // Gunakan label Bahasa Indonesia jika tersedia, fallback ke format default
            val labelIndonesia = TRANSLASI_FITUR[kunci]
                ?: kunci.replace("_", " ").replaceFirstChar { it.uppercase() }
            binding.txtNamaFitur.text = labelIndonesia
            binding.txtSkorFitur.text = String.format("%.1f", nilai)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDetailFiturBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val kunci = daftarKunci[position]
        val nilai = skorFitur[kunci] ?: 0.0
        holder.ikat(kunci, nilai)
    }

    override fun getItemCount(): Int = daftarKunci.size
}
