package com.el.silatpro.ui.beranda

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.el.silatpro.ActivityKlasifikasiGerakan
import com.el.silatpro.databinding.FragmenBerandaBinding
import com.google.gson.JsonParser

/**
 * Fragment Beranda — statistik gerakan dan panduan singkat.
 * Toolbar mendapat top-padding dari WindowInsets (edge-to-edge).
 */
class FragmenBeranda : Fragment() {

    private var _binding: FragmenBerandaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmenBerandaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Header edge-to-edge: padding top = tinggi status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBeranda) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBar.top, v.paddingRight, v.paddingBottom)
            insets
        }

        muatStatistik()
        aturTombolAksi()
    }

    /** Tombol ke Galeri dan Klasifikasi */
    private fun aturTombolAksi() {
        // Navigasi ke tab Galeri (index 1) via parent ViewPager2
        binding.btnKeGaleri.setOnClickListener {
            activity?.findViewById<ViewPager2>(com.el.silatpro.R.id.viewPagerUtama)
                ?.setCurrentItem(1, true)
        }

        // Buka kamera klasifikasi langsung
        binding.btnKeKlasifikasi.setOnClickListener {
            startActivity(Intent(requireContext(), ActivityKlasifikasiGerakan::class.java))
        }
    }

    private fun muatStatistik() {
        try {
            val jsonStr = requireContext().assets.open("movement_model_index.json")
                .bufferedReader().use { it.readText() }
            val root = JsonParser.parseString(jsonStr).asJsonObject
            val movements = root.getAsJsonArray("movements") ?: return

            val jumlahGerakan = movements.size()
            var jumlahVariasi = 0
            val kategoriSet = mutableSetOf<String>()

            for (mv in movements) {
                val obj = mv.asJsonObject
                val idGerakan = obj.get("id")?.asString ?: ""
                val kategori = when {
                    idGerakan.startsWith("pukulan")   -> "Pukulan"
                    idGerakan.startsWith("tangkisan") -> "Tangkisan"
                    else -> idGerakan
                }
                kategoriSet.add(kategori)
                jumlahVariasi += obj.getAsJsonArray("classes")?.size() ?: 0
            }

            binding.txtJumlahKategori.text = kategoriSet.size.toString()
            binding.txtJumlahGerakan.text  = jumlahGerakan.toString()
            binding.txtJumlahVariasi.text  = jumlahVariasi.toString()

        } catch (e: Exception) {
            binding.txtJumlahKategori.text = "3"
            binding.txtJumlahGerakan.text  = "5"
            binding.txtJumlahVariasi.text  = "10"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
