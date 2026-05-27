package com.el.silatpro.ui.galeri

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.el.silatpro.ActivityDetailGerakan
import com.el.silatpro.databinding.FragmenGaleriBinding
import com.el.silatpro.model.GrupGerakan
import com.el.silatpro.model.KelasGerakan
import com.el.silatpro.ui.beranda.AdapterGerakan
import com.google.gson.JsonParser

/**
 * Fragment Galeri — grid 2 kolom semua gerakan + chip filter biru.
 * Toolbar mendapat top-padding dari WindowInsets (edge-to-edge).
 */
class FragmenGaleri : Fragment() {

    private var _binding: FragmenGaleriBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapterGerakan: AdapterGerakan
    private val semuaGerakan = mutableListOf<GrupGerakan>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmenGaleriBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Header edge-to-edge: padding top = tinggi status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerGaleri) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBar.top, v.paddingRight, v.paddingBottom)
            insets
        }

        aturRecyclerView()
        aturChipFilter()
        muatDataGerakan()
    }

    private fun aturRecyclerView() {
        adapterGerakan = AdapterGerakan { gerakan -> bukaDetailGerakan(gerakan) }
        binding.rvGerakan.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = adapterGerakan
            setHasFixedSize(true)
        }
    }

    private fun aturChipFilter() {
        binding.chipSemua.setOnClickListener     { setFilter("Semua") }
        binding.chipPukulan.setOnClickListener   { setFilter("pukulan") }
        binding.chipTangkisan.setOnClickListener { setFilter("tangkisan") }
    }

    private fun setFilter(kategori: String) {
        binding.chipSemua.isChecked     = kategori == "Semua"
        binding.chipPukulan.isChecked   = kategori == "pukulan"
        binding.chipTangkisan.isChecked = kategori == "tangkisan"
        filterGerakan(kategori)
    }

    private fun muatDataGerakan() {
        try {
            val jsonStr = requireContext().assets.open("movement_model_index.json")
                .bufferedReader().use { it.readText() }
            val root = JsonParser.parseString(jsonStr).asJsonObject
            val movements = root.getAsJsonArray("movements")

            semuaGerakan.clear()
            for (mv in movements) {
                val obj = mv.asJsonObject
                val classes = obj.getAsJsonArray("classes")?.mapNotNull { kls ->
                    try {
                        val k = kls.asJsonObject
                        KelasGerakan(
                            id          = k.get("id")?.asString ?: "",
                            label       = k.get("label")?.asString ?: "",
                            side        = k.get("side")?.asString ?: "",
                            asset       = k.get("asset")?.asString ?: "",
                            sampleCount = k.get("sample_count")?.asInt ?: 0
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()

                semuaGerakan.add(
                    GrupGerakan(
                        id      = obj.get("id")?.asString ?: "",
                        label   = obj.get("label")?.asString ?: "",
                        classes = classes
                    )
                )
            }
            setFilter("Semua")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun filterGerakan(kategori: String) {
        val hasil = if (kategori == "Semua") semuaGerakan.toList()
        else semuaGerakan.filter { it.id.startsWith(kategori, ignoreCase = true) }
        adapterGerakan.aturData(hasil)
    }

    private fun bukaDetailGerakan(gerakan: GrupGerakan) {
        val intent = Intent(requireContext(), ActivityDetailGerakan::class.java).apply {
            putExtra("ID_GERAKAN", gerakan.id)
            putExtra("LABEL_GERAKAN", gerakan.label)
            putExtra("KELAS_GERAKAN", com.google.gson.Gson().toJson(gerakan.classes))
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
