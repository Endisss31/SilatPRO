package com.el.silatpro.ui.beranda

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.el.silatpro.databinding.ItemGerakanBinding
import com.el.silatpro.model.GrupGerakan
import com.el.silatpro.model.KelasGerakan

/**
 * Adapter untuk menampilkan daftar gerakan dalam bentuk poster latihan.
 */
class AdapterGerakan(
    private val padaItemDiklik: (GrupGerakan) -> Unit
) : RecyclerView.Adapter<AdapterGerakan.GerakanViewHolder>() {

    private val daftarGerakan = mutableListOf<GrupGerakan>()

    fun aturData(grupList: List<GrupGerakan>) {
        daftarGerakan.clear()
        daftarGerakan.addAll(grupList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GerakanViewHolder {
        val binding = ItemGerakanBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GerakanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GerakanViewHolder, position: Int) {
        holder.ikat(daftarGerakan[position])
    }

    override fun getItemCount(): Int = daftarGerakan.size

    inner class GerakanViewHolder(private val binding: ItemGerakanBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun ikat(gerakan: GrupGerakan) {
            binding.txtNamaKategori.text = namaKategori(gerakan.id)
            binding.txtNamaGerakan.text = gerakan.label
//            binding.txtJumlahVariasi.text = deskripsiSingkat(gerakan.id)
            muatGambarPoster(gerakan)
            binding.root.setOnClickListener { padaItemDiklik(gerakan) }
        }

        private fun muatGambarPoster(gerakan: GrupGerakan) {
            val konteks = binding.root.context
            val kelasKiri = gerakan.classes.firstOrNull { it.apakahSisiKiri() } ?: gerakan.classes.firstOrNull()
            val targetId = kelasKiri?.id ?: gerakan.id
            val idGambar = listOf(targetId, targetId.replace("_", ""))
                .firstNotNullOfOrNull { nama ->
                    konteks.resources.getIdentifier(nama, "drawable", konteks.packageName)
                        .takeIf { it != 0 }
                } ?: 0

            if (idGambar != 0) {
                binding.imgIkon.setImageResource(idGambar)
                binding.imgIkon.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                binding.imgIkon.setImageResource(com.el.silatpro.R.drawable.logo_silatpro_tanpa_text)
                binding.imgIkon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
    }

    private fun namaKategori(idGerakan: String): String {
        return when {
            idGerakan.startsWith("pukulan") -> "Pukulan"
            idGerakan.startsWith("tangkisan") -> "Tangkisan"
            idGerakan.startsWith("tendangan") -> "Tendangan"
            else -> "Lainnya"
        }
    }

    private fun deskripsiSingkat(idGerakan: String): String {
        return when {
            idGerakan.startsWith("pukulan") -> "Melatih arah serangan, posisi tubuh, dan koordinasi tangan."
            idGerakan.startsWith("tangkisan") -> "Melatih pertahanan, reaksi, dan kestabilan kuda-kuda."
            idGerakan.startsWith("tendangan") -> "Melatih keseimbangan, kekuatan kaki, dan kontrol gerakan."
            else -> "Latihan teknik dasar Pencak Silat."
        }
    }

    private fun KelasGerakan.apakahSisiKiri(): Boolean {
        return side.equals("kiri", ignoreCase = true) || id.endsWith("kiri", ignoreCase = true)
    }
}
