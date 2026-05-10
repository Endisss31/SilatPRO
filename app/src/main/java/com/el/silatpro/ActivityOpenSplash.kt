package com.el.silatpro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.el.silatpro.databinding.ActivitySplashBinding

class ActivityOpenSplash : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aturEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            val prefs = getSharedPreferences("silatpro_prefs", MODE_PRIVATE)
            val tujuan = if (prefs.getBoolean("sudah_onboarding", false)) {
                ActivityMain::class.java
            } else {
                ActivitySplash::class.java
            }

            startActivity(Intent(this, tujuan))
            finish()
        }, DURASI_SPLASH_MS)
    }

    private fun aturEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    companion object {
        private const val DURASI_SPLASH_MS = 1500L
    }
}
