package com.el.silatpro

import android.app.Activity
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat

@Suppress("DEPRECATION")
fun Activity.sinkronkanStatusBarDenganToolbar() {
    val nilaiWarna = TypedValue()
    theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, nilaiWarna, true)
    val warnaToolbar = if (nilaiWarna.resourceId != 0) {
        ContextCompat.getColor(this, nilaiWarna.resourceId)
    } else {
        nilaiWarna.data
    }

    window.statusBarColor = warnaToolbar
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
}
