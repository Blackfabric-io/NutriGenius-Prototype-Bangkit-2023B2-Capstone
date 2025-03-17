package com.nutrigenius.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nutrigenius.ml.DetectionViewModel

/**
 * DetectionDao - Data Access Object untuk mengakses data deteksi
 *
 * Interface ini menyediakan metode untuk menyimpan dan mengambil data deteksi dari database Room.
 */
@Dao
interface DetectionDao {
    /**
     * Menyimpan hasil deteksi baru ke database
     * 
     * @param detectionEntity objek entity yang berisi data deteksi
     * @return ID dari record yang disimpan
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetection(detectionEntity: DetectionEntity): Long
    
    /**
     * Mengambil semua hasil deteksi, diurutkan berdasarkan waktu terbaru
     * 
     * @return LiveData berisi list semua hasil deteksi
     */
    @Query("SELECT * FROM detection_results ORDER BY timestamp DESC")
    fun getAllDetections(): LiveData<List<DetectionEntity>>
    
    /**
     * Mengambil hasil deteksi berdasarkan jenis (makanan atau wajah)
     * 
     * @param detectionType jenis deteksi yang ingin diambil
     * @return LiveData berisi list hasil deteksi sesuai jenis
     */
    @Query("SELECT * FROM detection_results WHERE detectionType = :detectionType ORDER BY timestamp DESC")
    fun getDetectionsByType(detectionType: DetectionViewModel.DetectionMode): LiveData<List<DetectionEntity>>
    
    /**
     * Mengambil hasil deteksi berdasarkan ID
     * 
     * @param id ID dari hasil deteksi yang ingin diambil
     * @return hasil deteksi yang sesuai dengan ID
     */
    @Query("SELECT * FROM detection_results WHERE id = :id")
    suspend fun getDetectionById(id: Long): DetectionEntity?
    
    /**
     * Menghapus hasil deteksi berdasarkan ID
     * 
     * @param id ID dari hasil deteksi yang ingin dihapus
     */
    @Query("DELETE FROM detection_results WHERE id = :id")
    suspend fun deleteDetection(id: Long)
    
    /**
     * Menghapus semua hasil deteksi
     */
    @Query("DELETE FROM detection_results")
    suspend fun deleteAllDetections()
} 