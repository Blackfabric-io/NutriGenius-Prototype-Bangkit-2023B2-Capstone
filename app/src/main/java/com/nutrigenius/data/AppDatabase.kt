package com.nutrigenius.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * AppDatabase - Database Room untuk aplikasi NutriGenius
 *
 * Database ini menyimpan hasil-hasil deteksi yang dilakukan oleh aplikasi.
 */
@Database(entities = [DetectionEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * Memberikan akses ke DetectionDao
     */
    abstract fun detectionDao(): DetectionDao
    
    companion object {
        // Singleton instance
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Mendapatkan instance database atau membuatnya jika belum ada
         * 
         * @param context Context aplikasi
         * @return Instance dari AppDatabase
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nutrigenius_database"
                )
                .fallbackToDestructiveMigration() // Hapus database jika versi berubah
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
} 