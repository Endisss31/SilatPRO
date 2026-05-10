package com.el.silatpro.model

data class GrupGerakan(
    val id: String,
    val label: String,
    val classes: List<KelasGerakan>
)

data class KelasGerakan(
    val id: String,
    val label: String,
    val side: String,
    val asset: String,
    val sampleCount: Int
)
