package com.el.silatpro

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.el.silatpro.databinding.ActivityTentangBinding

class ActivityTentang : AppCompatActivity() {

    private lateinit var binding: ActivityTentangBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aturEdgeToEdge()
        binding = ActivityTentangBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aturToolbar()
        aturInsets()


    }
    private fun aturEdgeToEdge() {
        // Matikan padding otomatis sistem — kita handle manual
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Status bar transparan agar hero image menyatu
        window.statusBarColor = Color.TRANSPARENT
        // Ikon status bar tetap putih (kontras dengan hero gelap)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }
    private fun aturToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun aturInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())

            // Toolbar: tambah padding-top agar judul/back icon tidak tertimpa ikon status bar
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                statusBar.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )

            insets
        }
    }
}
