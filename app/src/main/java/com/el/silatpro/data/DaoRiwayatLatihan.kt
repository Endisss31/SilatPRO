package com.el.silatpro.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * DAO untuk operasi database riwayat latihan.
 */
@Dao
interface DaoRiwayatLatihan {

    @Query("SELECT * FROM riwayat_latihan ORDER BY waktu DESC")
    fun ambilSemua(): LiveData<List<EntitasRiwayatLatihan>>

    @Query("SELECT * FROM riwayat_latihan WHERE id = :id")
    suspend fun ambilBerdasarkanId(id: Int): EntitasRiwayatLatihan?

    @Insert
    suspend fun simpan(riwayat: EntitasRiwayatLatihan): Long

    @Delete
    suspend fun hapus(riwayat: EntitasRiwayatLatihan)

    @Query("DELETE FROM riwayat_latihan")
    suspend fun hapusSemua()
}
