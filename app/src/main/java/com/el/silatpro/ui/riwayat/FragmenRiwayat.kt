package com.el.silatpro.ui.riwayat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.el.silatpro.ActivityHasilEvaluasi
import com.el.silatpro.data.BasisDataAplikasi
import com.el.silatpro.databinding.FragmenRiwayatBinding

/**
 * Fragmen Riwayat — menampilkan daftar riwayat latihan dari database lokal.
 */
class FragmenRiwayat : Fragment() {

    private var _binding: FragmenRiwayatBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapterRiwayat: AdapterRiwayat

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmenRiwayatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Header edge-to-edge: padding top = tinggi status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerRiwayat) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBar.top, v.paddingRight, v.paddingBottom)
            insets
        }

        aturRecyclerView()
        amatiBasisData()
    }

    private fun aturRecyclerView() {
        adapterRiwayat = AdapterRiwayat { riwayat ->
            bukaDetailRiwayat(riwayat.id)
        }
        binding.rvRiwayat.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapterRiwayat
        }
    }

    private fun amatiBasisData() {
        val dao = BasisDataAplikasi.dapatkanInstans(requireContext()).daoRiwayatLatihan()
        dao.ambilSemua().observe(viewLifecycleOwner) { daftar ->
            adapterRiwayat.submitList(daftar)
            val adaData = daftar.isNotEmpty()
            binding.layoutKosong.visibility = if (adaData) View.GONE else View.VISIBLE
            binding.rvRiwayat.visibility = if (adaData) View.VISIBLE else View.GONE
        }
    }

    private fun bukaDetailRiwayat(idRiwayat: Int) {
        val intent = Intent(requireContext(), ActivityHasilEvaluasi::class.java).apply {
            putExtra("ID_RIWAYAT", idRiwayat)
            putExtra("MODE_RIWAYAT", true)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
