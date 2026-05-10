package com.el.silatpro.data

import androidx.room.*

/**
 * Entity untuk menyimpan riwayat latihan di database lokal.
 */
@Entity(tableName = "riwayat_latihan")
data class EntitasRiwayatLatihan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "id_gerakan") val idGerakan: String,
    @ColumnInfo(name = "label_gerakan") val labelGerakan: String,
    @ColumnInfo(name = "skor") val skor: Double,
    @ColumnInfo(name = "kategori") val kategori: String,
    @ColumnInfo(name = "skor_per_fitur") val skorPerFitur: String,  // JSON
    @ColumnInfo(name = "feedback") val feedback: String,             // JSON
    @ColumnInfo(name = "durasi_ms") val durasiMs: Long,
    @ColumnInfo(name = "waktu") val waktu: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "path_foto") val pathFoto: String? = null   // Path foto pose terbaik
)
