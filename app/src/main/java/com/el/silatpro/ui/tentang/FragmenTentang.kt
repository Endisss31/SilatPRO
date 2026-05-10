package com.el.silatpro.ui.tentang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.el.silatpro.databinding.FragmenTentangBinding

/**
 * Fragment Tentang — informasi aplikasi, pengembang, dan teknologi.
 * Toolbar mendapat top-padding dari WindowInsets (edge-to-edge).
 */
class FragmenTentang : Fragment() {

    private var _binding: FragmenTentangBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmenTentangBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Header edge-to-edge: padding top = tinggi status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerTentang) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBar.top, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
