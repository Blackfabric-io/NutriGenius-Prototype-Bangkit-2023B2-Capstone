package com.nutrigenius.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

/**
 * ObjectDetector - Kelas untuk mendeteksi objek (terutama makanan) dari gambar
 * 
 * Menggunakan TensorFlow Lite untuk mendeteksi objek dari gambar.
 * Dalam versi prototype ini, hasil dihasilkan melalui simulasi jika model tidak tersedia.
 * 
 * @param context Context aplikasi untuk mengakses assets
 */
data class DetectionResult(
    val objectClass: String,
    val rect: RectF,
    val confidence: Float
)

class ObjectDetector(private val context: Context) {
    // Interpreter dan model properties
    private var interpreter: Interpreter? = null
    private var labelList: List<String> = emptyList()
    private val modelVersion = "1.0.0" // Tracking model version for updates
    private val modelName get() = "object_detection_model_v${modelVersion.replace(".", "_")}.tflite"
    private val labelName get() = "object_labels_v${modelVersion.replace(".", "_")}.txt"
    private val imageSize = 300 // Common input size for SSD models
    
    // TAG untuk logging
    companion object {
        private const val TAG = "ObjectDetector"
        
        // Caching model instance dan labels untuk penggunaan ulang
        private var cachedInterpreter: Interpreter? = null
        private var cachedLabelList: List<String>? = null
        private var cachedModelVersion: String? = null
    }
    
    // Sample object categories (based on COCO dataset common food items)
    private val sampleCategories = listOf(
        "apple", "banana", "orange", "pizza", "sandwich", "hot dog", 
        "carrot", "broccoli", "donut", "cake"
    )
    
    init {
        try {
            // Cek apakah ada cached interpreter dengan versi yang sama
            if (cachedInterpreter != null && cachedLabelList != null && cachedModelVersion == modelVersion) {
                Log.d(TAG, "Using cached interpreter and labels")
                interpreter = cachedInterpreter
                labelList = cachedLabelList!!
            } else {
                // Load model baru
                Log.d(TAG, "Loading model and labels from assets")
                val model = loadModelFile(context, modelName)
                interpreter = Interpreter(model)
                
                // Try to load labels
                try {
                    labelList = FileUtil.loadLabels(context, labelName)
                    
                    // Cache model dan labels
                    cachedInterpreter = interpreter
                    cachedLabelList = labelList
                    cachedModelVersion = modelVersion
                    Log.d(TAG, "Created new interpreter instance and cached it with ${labelList.size} labels")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load labels: ${e.message}, using sample categories")
                    labelList = sampleCategories
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing object detector: ${e.message}")
            e.printStackTrace()
            // If model doesn't exist, use sample categories
            labelList = sampleCategories
        }
    }
    
    /**
     * Mendeteksi objek dari gambar
     * 
     * @param bitmap Gambar yang akan dianalisis
     * @param maxResults Jumlah maksimum hasil yang akan dikembalikan
     * @return List hasil deteksi diurutkan berdasarkan confidence
     */
    fun detectObjects(bitmap: Bitmap, maxResults: Int = 5): List<DetectionResult> {
        try {
            // If we have a real interpreter, use it
            if (interpreter != null) {
                // Preprocess the image
                val processedImage = preprocessImage(bitmap)
                
                // TODO: In a real implementation, run inference with actual model
                // For prototype, simulate results
                return simulateObjectDetection(bitmap, maxResults)
            } else {
                // Fallback to simulation if interpreter is not available
                Log.d(TAG, "Interpreter not available, using simulation")
                return simulateObjectDetection(bitmap, maxResults)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting objects: ${e.message}")
            e.printStackTrace()
        }
        
        return emptyList()
    }
    
    /**
     * Simulasi deteksi objek untuk prototype
     * 
     * @param bitmap Gambar input
     * @param maxResults Jumlah maksimum hasil
     * @return List hasil deteksi simulasi
     */
    private fun simulateObjectDetection(bitmap: Bitmap, maxResults: Int): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        val numObjects = (1..3).random() // Simulate 1-3 objects detected
        
        // Create random detections
        for (i in 0 until numObjects) {
            // Pick a random category
            val categoryIndex = (0 until labelList.size).random()
            val category = labelList[categoryIndex]
            
            // Create random box (ensure it's within image bounds)
            val left = (bitmap.width * Math.random() * 0.5).toFloat()
            val top = (bitmap.height * Math.random() * 0.5).toFloat()
            val width = (bitmap.width * (0.2 + Math.random() * 0.3)).toFloat()
            val height = (bitmap.height * (0.2 + Math.random() * 0.3)).toFloat()
            
            val rect = RectF(
                left,
                top,
                left + width,
                top + height
            )
            
            // Random confidence between 0.6 and 0.95
            val confidence = 0.6f + (Math.random() * 0.35).toFloat()
            
            results.add(DetectionResult(category, rect, confidence))
        }
        
        // Sort by confidence and limit to maxResults
        return results.sortedByDescending { it.confidence }.take(maxResults)
    }
    
    /**
     * Untuk kompatibilitas mundur dengan FoodDetector
     * 
     * @param bitmap Gambar input
     * @return Pair (nama makanan, confidence)
     */
    fun detectFood(bitmap: Bitmap): Pair<String, Float> {
        // For backward compatibility, detect objects and return the top one
        val detections = detectObjects(bitmap, 1)
        return if (detections.isNotEmpty()) {
            Pair(detections[0].objectClass, detections[0].confidence)
        } else {
            Pair("unknown", 0f)
        }
    }
    
    /**
     * Preproses gambar untuk input model
     * 
     * @param bitmap Gambar input
     * @return ByteBuffer input untuk model
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(imageSize, imageSize, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)
        
        return processedImage.buffer
    }
    
    /**
     * Load model dari assets
     * 
     * @param context Context untuk akses assets
     * @param modelName Nama file model
     * @return MappedByteBuffer model
     */
    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        try {
            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            // If model doesn't exist, create a dummy ByteBuffer for prototype
            Log.w(TAG, "Model file not found: $modelName. Using dummy buffer for prototype.")
            e.printStackTrace()
            val buffer = ByteBuffer.allocateDirect(1024)
            buffer.order(java.nio.ByteOrder.nativeOrder())
            return buffer.asReadOnlyBuffer() as MappedByteBuffer
        }
    }
    
    /**
     * Tutup interpreter dan bebaskan resources
     */
    fun close() {
        // Don't close cached interpreter
        if (interpreter != cachedInterpreter) {
            interpreter?.close()
        }
    }
} 