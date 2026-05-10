package com.el.silatpro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database utama aplikasi SilatPRO.
 */
@Database(entities = [EntitasRiwayatLatihan::class], version = 2, exportSchema = false)
abstract class BasisDataAplikasi : RoomDatabase() {

    abstract fun daoRiwayatLatihan(): DaoRiwayatLatihan

    companion object {
        @Volatile
        private var INSTANCE: BasisDataAplikasi? = null

        /**
         * Migration dari versi 1 → 2: Menambahkan kolom path_foto untuk menyimpan
         * path foto pose terbaik yang diambil saat skor tertinggi dicapai.
         */
        private val MIGRASI_1_KE_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE riwayat_latihan ADD COLUMN path_foto TEXT"
                )
            }
        }

        fun dapatkanInstans(konteks: Context): BasisDataAplikasi {
            return INSTANCE ?: synchronized(this) {
                val instans = Room.databaseBuilder(
                    konteks.applicationContext,
                    BasisDataAplikasi::class.java,
                    "silatpro_database"
                )
                    .addMigrations(MIGRASI_1_KE_2)
                    .build()
                INSTANCE = instans
                instans
            }
        }
    }
}
