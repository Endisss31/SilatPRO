package com.el.silatpro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.el.silatpro.databinding.ActivityMainBinding
import com.el.silatpro.ui.beranda.FragmenBeranda
import com.el.silatpro.ui.galeri.FragmenGaleri
import com.el.silatpro.ui.riwayat.FragmenRiwayat
import com.el.silatpro.ui.tentang.FragmenTentang

/**
 * Aktivitas Utama — menggunakan ViewPager2 dengan 4 fragment:
 * Beranda | Galeri | (FAB Kamera) | Riwayat | Tentang
 *
 * Swipe dinonaktifkan untuk mencegah swipe tidak sengaja ke FAB kamera.
 */
class ActivityMain : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Indeks halaman — FAB kamera tidak punya indeks fragment sendiri
    private val IDX_BERANDA  = 0
    private val IDX_GALERI   = 1
    private val IDX_RIWAYAT  = 2
    private val IDX_TENTANG  = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: status bar transparan, konten extend ke balik status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Angkat bottom nav bar dari atas navigation bar sistem
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.root.setPadding(0, 0, 0, navBar.bottom)
            insets
        }

        aturViewPager()
        aturTombolNavigasi()
    }

    // ── ViewPager2 ──────────────────────────────────────────────────────────

    private fun aturViewPager() {
        val adapter = AdapterHalamanUtama(this)
        binding.viewPagerUtama.adapter = adapter
        binding.viewPagerUtama.offscreenPageLimit = 4
        // Swipe aktif kembali seperti sebelumnya
        binding.viewPagerUtama.isUserInputEnabled = true

        binding.viewPagerUtama.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                perbaruiTabAktif(position)
            }
        })

        perbaruiTabAktif(IDX_BERANDA)
    }

    // ── Adapter Fragment ────────────────────────────────────────────────────

    inner class AdapterHalamanUtama(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment = when (position) {
            IDX_BERANDA  -> FragmenBeranda()
            IDX_GALERI   -> FragmenGaleri()
            IDX_RIWAYAT  -> FragmenRiwayat()
            IDX_TENTANG  -> FragmenTentang()
            else         -> FragmenBeranda()
        }
    }

    // ── Tombol Navigasi Bawah ───────────────────────────────────────────────

    private fun aturTombolNavigasi() {
        binding.tabBeranda.setOnClickListener  { pindahKe(IDX_BERANDA) }
        binding.tabGaleri.setOnClickListener   { pindahKe(IDX_GALERI) }
        binding.tabRiwayat.setOnClickListener  { pindahKe(IDX_RIWAYAT) }
        binding.tabTentang.setOnClickListener  { pindahKe(IDX_TENTANG) }

        binding.fabKamera.setOnClickListener {
            val animasi = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
            binding.fabKamera.startAnimation(animasi)
            startActivity(Intent(this, ActivityKlasifikasiGerakan::class.java))
        }
    }

    private fun pindahKe(posisi: Int) {
        binding.viewPagerUtama.setCurrentItem(posisi, false)
        perbaruiTabAktif(posisi)
    }

    /**
     * Perbarui warna tab aktif (biru) vs tidak aktif (abu-abu).
     */
    private fun perbaruiTabAktif(posisi: Int) {
        val tabs = listOf(IDX_BERANDA, IDX_GALERI, IDX_RIWAYAT, IDX_TENTANG)
        val imgs  = listOf(binding.imgTabBeranda, binding.imgTabGaleri, binding.imgTabRiwayat, binding.imgTabTentang)
        val txts  = listOf(binding.txtTabBeranda, binding.txtTabGaleri, binding.txtTabRiwayat, binding.txtTabTentang)

        for (i in tabs.indices) {
            val aktif = tabs[i] == posisi
            val warna = if (aktif) R.color.biru_utama else R.color.abu_terang
            imgs[i].setColorFilter(ContextCompat.getColor(this, warna))
            txts[i].setTextColor(ContextCompat.getColor(this, warna))
            txts[i].setTypeface(
                null,
                if (aktif) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
        }
    }
}
