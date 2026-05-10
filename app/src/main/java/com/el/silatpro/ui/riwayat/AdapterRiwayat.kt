package com.el.silatpro.ui.riwayat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.el.silatpro.R
import com.el.silatpro.data.EntitasRiwayatLatihan
import com.el.silatpro.databinding.ItemRiwayatBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter untuk menampilkan daftar riwayat latihan.
 */
class AdapterRiwayat(
    private val padaItemDiklik: (EntitasRiwayatLatihan) -> Unit
) : ListAdapter<EntitasRiwayatLatihan, AdapterRiwayat.RiwayatViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiwayatViewHolder {
        val binding = ItemRiwayatBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RiwayatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RiwayatViewHolder, position: Int) {
        holder.ikat(getItem(position))
    }

    inner class RiwayatViewHolder(private val binding: ItemRiwayatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun ikat(riwayat: EntitasRiwayatLatihan) {
            binding.txtNamaGerakan.text = riwayat.labelGerakan
            binding.txtSkor.text = riwayat.skor.toInt().toString()
            binding.txtKategori.text = riwayat.kategori
            binding.txtDurasi.text = formatDurasi(riwayat.durasiMs)

            // Warna kategori
            val warnaKategori = when {
                riwayat.skor >= 85 -> ContextCompat.getColor(itemView.context, R.color.skor_sangat_baik)
                riwayat.skor >= 70 -> ContextCompat.getColor(itemView.context, R.color.skor_baik)
                riwayat.skor >= 55 -> ContextCompat.getColor(itemView.context, R.color.skor_cukup)
                else -> ContextCompat.getColor(itemView.context, R.color.skor_kurang)
            }
            binding.txtKategori.setTextColor(warnaKategori)

            // Format waktu
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            binding.txtWaktu.text = sdf.format(Date(riwayat.waktu))

            binding.root.setOnClickListener { padaItemDiklik(riwayat) }
        }

        private fun formatDurasi(durasiMs: Long): String {
            val totalDetik = (durasiMs / 1000).coerceAtLeast(0)
            val menit = totalDetik / 60
            val detik = totalDetik % 60
            return if (menit > 0) "${menit}m ${detik}s" else "${detik}s"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<EntitasRiwayatLatihan>() {
        override fun areItemsTheSame(a: EntitasRiwayatLatihan, b: EntitasRiwayatLatihan) = a.id == b.id
        override fun areContentsTheSame(a: EntitasRiwayatLatihan, b: EntitasRiwayatLatihan) = a == b
    }
}
