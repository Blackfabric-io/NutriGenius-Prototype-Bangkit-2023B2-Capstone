package com.nutrigenius.data

import androidx.lifecycle.LiveData
import com.nutrigenius.ml.DetectionViewModel
import java.util.Date

/**
 * DetectionRepository - Repository untuk mengelola data deteksi
 *
 * Class ini menyediakan abstraksi untuk akses data dari database.
 */
class DetectionRepository(private val detectionDao: DetectionDao) {
    
    /**
     * Mengambil semua hasil deteksi dari database
     */
    val allDetections: LiveData<List<DetectionEntity>> = detectionDao.getAllDetections()
    
    /**
     * Mengambil hasil deteksi makanan
     */
    val foodDetections: LiveData<List<DetectionEntity>> = 
        detectionDao.getDetectionsByType(DetectionViewModel.DetectionMode.OBJECT)
    
    /**
     * Mengambil hasil deteksi wajah
     */
    val faceDetections: LiveData<List<DetectionEntity>> = 
        detectionDao.getDetectionsByType(DetectionViewModel.DetectionMode.FACE)
    
    /**
     * Menyimpan hasil deteksi baru ke database
     * 
     * @param objectClass Nama objek yang terdeteksi
     * @param confidence Tingkat kepercayaan deteksi
     * @param detectionType Jenis deteksi (makanan atau wajah)
     * @param imageData Data gambar dalam bentuk byte array
     * @param nutritionInfoJson Informasi nutrisi dalam format JSON (opsional)
     * @return ID dari record yang disimpan
     */
    suspend fun saveDetection(
        objectClass: String,
        confidence: Float,
        detectionType: DetectionViewModel.DetectionMode,
        imageData: ByteArray?,
        nutritionInfoJson: String? = null
    ): Long {
        val detection = DetectionEntity(
            timestamp = Date(),
            objectClass = objectClass,
            confidence = confidence,
            detectionType = detectionType,
            imageData = imageData,
            nutritionInfoJson = nutritionInfoJson
        )
        
        return detectionDao.insertDetection(detection)
    }
    
    /**
     * Mengambil detail deteksi berdasarkan ID
     * 
     * @param id ID dari hasil deteksi
     * @return Detail hasil deteksi
     */
    suspend fun getDetectionById(id: Long): DetectionEntity? {
        return detectionDao.getDetectionById(id)
    }
    
    /**
     * Menghapus hasil deteksi berdasarkan ID
     * 
     * @param id ID dari hasil deteksi yang akan dihapus
     */
    suspend fun deleteDetection(id: Long) {
        detectionDao.deleteDetection(id)
    }
    
    /**
     * Menghapus semua hasil deteksi
     */
    suspend fun deleteAllDetections() {
        detectionDao.deleteAllDetections()
    }
} 