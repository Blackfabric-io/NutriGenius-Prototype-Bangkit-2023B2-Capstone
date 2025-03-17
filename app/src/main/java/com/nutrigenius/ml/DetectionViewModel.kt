package com.nutrigenius.ml

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nutrigenius.data.AppDatabase
import com.nutrigenius.data.DetectionEntity
import com.nutrigenius.data.DetectionRepository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * DetectionViewModel - ViewModel untuk mengelola data deteksi dan UI state
 *
 * Class ini menyimpan hasil deteksi terbaru, mengelola mode deteksi,
 * dan mengakses repository untuk operasi database.
 */
class DetectionViewModel(application: Application) : AndroidViewModel(application) {
    
    // Enum untuk mode deteksi
    enum class DetectionMode {
        OBJECT, // Deteksi makanan
        FACE,   // Deteksi wajah
        TEXT    // Deteksi teks (mode baru)
    }
    
    // Repository untuk akses database
    private val repository: DetectionRepository
    
    // LiveData untuk daftar deteksi dari database
    val allDetections: LiveData<List<DetectionEntity>>
    val foodDetections: LiveData<List<DetectionEntity>>
    val faceDetections: LiveData<List<DetectionEntity>>
    val textDetections: LiveData<List<DetectionEntity>> // Deteksi teks
    
    // LiveData untuk mode deteksi saat ini
    private val _detectionMode = MutableLiveData<DetectionMode>()
    val detectionMode: LiveData<DetectionMode> = _detectionMode
    
    // LiveData untuk hasil deteksi makanan saat ini
    private val _foodDetections = MutableLiveData<List<DetectionResult>>()
    val foodDetectionResults: LiveData<List<DetectionResult>> = _foodDetections
    
    // LiveData untuk rekomendasi nutrisi saat ini
    private val _recommendation = MutableLiveData<Recommendation?>()
    val recommendation: LiveData<Recommendation?> = _recommendation
    
    // LiveData untuk hasil deteksi wajah saat ini
    private val _faceResult = MutableLiveData<FaceResult?>()
    val faceResult: LiveData<FaceResult?> = _faceResult
    
    // LiveData untuk hasil deteksi teks saat ini
    private val _textDetectionResult = MutableLiveData<TextDetectionResult?>()
    val textDetectionResult: LiveData<TextDetectionResult?> = _textDetectionResult
    
    // LiveData untuk menampung history deteksi teks
    private val _textDetectionHistory = MutableLiveData<List<TextDetectionResult>>()
    val textDetectionHistory: LiveData<List<TextDetectionResult>> = _textDetectionHistory
    
    // LiveData untuk gambar yang diambil
    private val _capturedImage = MutableLiveData<ByteArray?>()
    val capturedImage: LiveData<ByteArray?> = _capturedImage
    
    init {
        // Inisialisasi repository dan database
        val detectionDao = AppDatabase.getDatabase(application).detectionDao()
        repository = DetectionRepository(detectionDao)
        
        // Inisialisasi LiveData dari repository
        allDetections = repository.allDetections
        foodDetections = repository.foodDetections
        faceDetections = repository.faceDetections
        textDetections = repository.textDetections // Tambahkan query khusus di Repository
        
        // Set nilai default
        _detectionMode.value = DetectionMode.OBJECT
        _textDetectionHistory.value = emptyList()
    }
    
    /**
     * Mengubah mode deteksi saat ini
     */
    fun setDetectionMode(mode: DetectionMode) {
        _detectionMode.value = mode
    }
    
    /**
     * Menyimpan hasil deteksi makanan
     */
    fun setFoodResult(detections: List<DetectionResult>, recommendation: Recommendation?) {
        _foodDetections.value = detections
        _recommendation.value = recommendation
    }
    
    /**
     * Menyimpan hasil deteksi wajah
     */
    fun setFaceResult(result: FaceResult) {
        _faceResult.value = result
    }
    
    /**
     * Menyimpan hasil deteksi teks
     */
    fun setTextDetectionResult(detectedText: String, keywords: List<String>) {
        val result = TextDetectionResult(detectedText, keywords)
        _textDetectionResult.value = result
        
        // Tambahkan ke history dengan batas 10 deteksi terakhir
        val currentHistory = _textDetectionHistory.value ?: emptyList()
        _textDetectionHistory.value = (listOf(result) + currentHistory).take(10)
    }
    
    /**
     * Menyimpan gambar yang diambil
     */
    fun setCapturedImage(imageData: ByteArray?) {
        _capturedImage.value = imageData
    }
    
    /**
     * Mendapatkan Bitmap dari data gambar
     */
    fun getBitmapFromImageData(): Bitmap? {
        val imageData = _capturedImage.value ?: return null
        return BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
    }
    
    /**
     * Menyimpan hasil deteksi ke database
     */
    fun saveDetectionResult(
        objectClass: String,
        confidence: Float, 
        detectionType: DetectionMode,
        imageData: ByteArray?, 
        nutritionInfoJson: String? = null
    ) {
        viewModelScope.launch {
            repository.saveDetection(
                objectClass = objectClass,
                confidence = confidence,
                detectionType = detectionType,
                imageData = imageData,
                nutritionInfoJson = nutritionInfoJson
            )
        }
    }
    
    /**
     * Mencari deteksi teks berdasarkan keyword
     */
    fun searchTextDetections(keyword: String): LiveData<List<DetectionEntity>> {
        return repository.searchTextDetections(keyword)
    }
    
    /**
     * Menghapus hasil deteksi dari database
     */
    fun deleteDetection(id: Long) {
        viewModelScope.launch {
            repository.deleteDetection(id)
        }
    }
    
    /**
     * Menghapus semua hasil deteksi
     */
    fun deleteAllDetections() {
        viewModelScope.launch {
            repository.deleteAllDetections()
        }
    }
    
    /**
     * Mengambil detail deteksi berdasarkan ID
     */
    suspend fun getDetectionById(id: Long): DetectionEntity? {
        return repository.getDetectionById(id)
    }
    
    /**
     * Factory untuk membuat ViewModel dengan parameter
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DetectionViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DetectionViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * Hasil deteksi objek
 */
data class DetectionResult(
    val objectClass: String,
    val confidence: Float,
    val rect: RectF
)

/**
 * Hasil deteksi wajah
 */
data class FaceResult(
    val age: Int,
    val gender: String,
    val confidence: Float,
    val faceRect: RectF
)

/**
 * Hasil deteksi teks
 */
data class TextDetectionResult(
    val detectedText: String,
    val keywords: List<String>
)

/**
 * Informasi nutrisi untuk makanan
 */
data class NutritionInfo(
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float
)

/**
 * Rekomendasi nutrisi berdasarkan makanan yang terdeteksi
 */
data class Recommendation(
    val nutritionInfo: NutritionInfo,
    val recommendations: List<String>
) 