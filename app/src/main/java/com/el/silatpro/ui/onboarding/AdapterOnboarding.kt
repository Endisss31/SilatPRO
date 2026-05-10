package com.el.silatpro.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.el.silatpro.R
import com.el.silatpro.databinding.ItemOnboardingBinding

/**
 * Adapter untuk slide onboarding (ViewPager2).
 * Slide 1 menggunakan logo SilatPRO (PNG), slide 2 & 3 menggunakan ikon vektor.
 */
class AdapterOnboarding : RecyclerView.Adapter<AdapterOnboarding.HalViewHolder>() {

    data class HalamanOnboarding(
        val judul: String,
        val deskripsi: String,
        val ikonResId: Int? = null,
        val gunakanTint: Boolean = false,
        val multiLogos: List<Int>? = null
    )

    private val daftarHalaman = listOf(
        HalamanOnboarding(
            judul = "Selamat Datang di SilatPRO",
            deskripsi = "Aplikasi analisis gerakan Pencak Silat berbasis AI.",
            ikonResId = R.drawable.logo_silatpro_tanpa_text
        ),
        HalamanOnboarding(
            judul = "Tujuan",
            deskripsi = "Aplikasi ini diperuntukan sebagai bahan penelitian skripsi.",
            multiLogos = listOf(R.drawable.logouniku, R.drawable.logo_fkom, R.drawable.logo_ti)
        ),
        HalamanOnboarding(
            judul = "Perguruan BIMA SUCI",
            deskripsi = "Sistem cerdas yang dirancang khusus untuk mendukung evaluasi gerakan pada Perguruan Pencak Silat BIMA SUCI.",
            ikonResId = R.drawable.logo_bimasuci,
            gunakanTint = false
        )
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HalViewHolder {
        val binding = ItemOnboardingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HalViewHolder, position: Int) {
        holder.ikat(daftarHalaman[position])
    }

    override fun getItemCount(): Int = daftarHalaman.size

    inner class HalViewHolder(private val binding: ItemOnboardingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun ikat(halaman: HalamanOnboarding) {
            binding.txtJudul.text = halaman.judul
            binding.txtDeskripsi.text = halaman.deskripsi
            
            if (halaman.multiLogos != null && halaman.multiLogos.size == 3) {
                binding.layoutMultiLogo.visibility = android.view.View.VISIBLE
                binding.imgOnboarding.visibility = android.view.View.GONE
                
                binding.imgLogo1.setImageResource(halaman.multiLogos[0])
                binding.imgLogo2.setImageResource(halaman.multiLogos[1])
                binding.imgLogo3.setImageResource(halaman.multiLogos[2])
            } else if (halaman.ikonResId != null) {
                binding.layoutMultiLogo.visibility = android.view.View.GONE
                binding.imgOnboarding.visibility = android.view.View.VISIBLE
                
                binding.imgOnboarding.setImageResource(halaman.ikonResId)
                
                if (halaman.gunakanTint) {
                    binding.imgOnboarding.setColorFilter(
                        android.graphics.Color.WHITE,
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                } else {
                    binding.imgOnboarding.clearColorFilter()
                }
            }
        }
    }
}
