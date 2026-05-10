package com.el.silatpro.ui.beranda

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.el.silatpro.databinding.ItemVariasiBinding
import com.el.silatpro.model.KelasGerakan

/**
 * Adapter untuk menampilkan variasi (kiri/kanan) dari sebuah gerakan.
 */
class AdapterVariasi(
    private val daftarKelas: List<KelasGerakan>,
    private val padaKlik: (KelasGerakan) -> Unit
) : RecyclerView.Adapter<AdapterVariasi.ViewHolder>() {

    private var posisiTerpilih = daftarKelas
        .indexOfFirst { it.side.equals("kiri", ignoreCase = true) || it.id.endsWith("kiri", ignoreCase = true) }
        .takeIf { it >= 0 } ?: 0

    inner class ViewHolder(private val binding: ItemVariasiBinding) : RecyclerView.ViewHolder(binding.root) {
        fun ikat(kelas: KelasGerakan, posisi: Int) {
            binding.txtNamaKategori.text = "Variasi Gerakan"
            binding.txtNamaGerakan.text = kelas.label
            binding.txtJumlahVariasi.text = "Sisi: ${kelas.side.replaceFirstChar { it.uppercase() }}"
            
            // Load foto masing-masing
            val konteks = binding.root.context
            val idGambar = listOf(kelas.id, kelas.id.replace("_", ""))
                .firstNotNullOfOrNull { nama ->
                    konteks.resources.getIdentifier(nama, "drawable", konteks.packageName).takeIf { it != 0 }
                } ?: 0
            if (idGambar != 0) {
                binding.imgIkon.setImageResource(idGambar)
                binding.imgIkon.setPadding(0, 0, 0, 0)
                binding.imgIkon.background = null
                binding.imgIkon.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            }

            // Highlight visual jika terpilih
            if (posisi == posisiTerpilih) {
                binding.root.strokeWidth = 4
                binding.root.strokeColor = android.graphics.Color.parseColor("#0090FF")
            } else {
                binding.root.strokeWidth = 0
            }

            binding.root.setOnClickListener {
                val posisiLama = posisiTerpilih
                posisiTerpilih = posisi
                notifyItemChanged(posisiLama)
                notifyItemChanged(posisiTerpilih)
                padaKlik(kelas)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVariasiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.ikat(daftarKelas[position], position)
    }

    override fun getItemCount(): Int = daftarKelas.size
}
