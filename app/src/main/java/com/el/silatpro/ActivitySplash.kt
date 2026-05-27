package com.el.silatpro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.el.silatpro.databinding.ActivityOnBoardBinding
import com.el.silatpro.ui.onboarding.AdapterOnboarding

/**
 * Aktivitas Splash — menampilkan onboarding saat pertama kali buka aplikasi.
 */
class ActivitySplash : AppCompatActivity() {

    private lateinit var binding: ActivityOnBoardBinding
    private lateinit var adapter: AdapterOnboarding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aturEdgeToEdge()

        // Cek apakah sudah pernah onboarding
        val prefs = getSharedPreferences("silatpro_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("sudah_onboarding", false)) {
            bukaBerandaLangsung()
            return
        }

        binding = ActivityOnBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aturInsets()
        aturOnboarding()
        aturTombol()
    }

    private fun aturOnboarding() {
        adapter = AdapterOnboarding()
        binding.pagerOnboarding.adapter = adapter
        buatIndikatorDot(0)

        binding.pagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                buatIndikatorDot(position)
                binding.btnSelanjutnya.text = if (position == adapter.itemCount - 1)
                    getString(R.string.btn_mulai) else getString(R.string.btn_selanjutnya)
            }
        })
    }

    private fun aturTombol() {
        binding.btnSelanjutnya.setOnClickListener {
            val posisi = binding.pagerOnboarding.currentItem
            if (posisi < adapter.itemCount - 1) {
                binding.pagerOnboarding.currentItem = posisi + 1
            } else {
                selesaiOnboarding()
            }
        }

        binding.btnLewati.setOnClickListener {
            selesaiOnboarding()
        }
    }

    private fun buatIndikatorDot(posisiAktif: Int) {
        binding.layoutIndikator.removeAllViews()
        val jumlahDot = adapter.itemCount

        for (i in 0 until jumlahDot) {
            val dot = ImageView(this).apply {
                val size = if (i == posisiAktif) 12 else 8
                layoutParams = LinearLayout.LayoutParams(
                    dpKePx(size), dpKePx(size)
                ).apply {
                    marginStart = dpKePx(4)
                    marginEnd = dpKePx(4)
                }
                setImageDrawable(
                    ContextCompat.getDrawable(
                        this@ActivitySplash,
                        if (i == posisiAktif) R.drawable.dot_aktif else R.drawable.dot_tidak_aktif
                    )
                )
            }
            binding.layoutIndikator.addView(dot)
        }
    }

    private fun selesaiOnboarding() {
        getSharedPreferences("silatpro_prefs", MODE_PRIVATE)
            .edit().putBoolean("sudah_onboarding", true).apply()
        bukaBerandaLangsung()
    }

    private fun bukaBerandaLangsung() {
        startActivity(Intent(this, ActivityMain::class.java))
        finish()
    }

    /** Angkat area bawah (indikator + tombol) agar tidak tertutup navigation bar HP */
    private fun aturInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            // Tambah padding bawah sesuai tinggi nav bar HP (gesture bar / tombol navigasi)
            // + 36dp base agar tetap ada jarak nyaman di atas nav bar
            binding.layoutAreaBawah.setPadding(
                binding.layoutAreaBawah.paddingLeft,
                binding.layoutAreaBawah.paddingTop,
                binding.layoutAreaBawah.paddingRight,
                navBar.bottom + dpKePx(36)
            )
            insets
        }
    }

    private fun aturEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun dpKePx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
