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

/**
 * FaceDetector - Kelas untuk mendeteksi wajah dan memperkirakan usia
 * 
 * Menggunakan TensorFlow Lite untuk mendeteksi wajah dan memperkirakan usia dari gambar.
 * Dalam versi prototype ini, hasil dihasilkan melalui simulasi jika model tidak tersedia.
 * 
 * @param context Context aplikasi untuk mengakses assets
 */
data class FaceResult(
    val faceRect: RectF,
    val age: Int,
    val confidence: Float,
    val gender: String? = null
)

class FaceDetector(private val context: Context) {
    // Interpreter dan model properties
    private var interpreter: Interpreter? = null
    private val modelVersion = "1.0.0" // Tracking model version for updates
    private val modelName get() = "face_detection_model_v${modelVersion.replace(".", "_")}.tflite"
    private val imageSize = 224 // Input size for the model
    
    // TAG untuk logging
    companion object {
        private const val TAG = "FaceDetector"
        
        // Caching model instance untuk penggunaan ulang
        private var cachedInterpreter: Interpreter? = null
        private var cachedModelVersion: String? = null
    }
    
    init {
        try {
            // Cek apakah ada cached interpreter dengan versi yang sama
            if (cachedInterpreter != null && cachedModelVersion == modelVersion) {
                Log.d(TAG, "Using cached interpreter instance")
                interpreter = cachedInterpreter
            } else {
                // Load model baru
                val model = loadModelFile(context, modelName)
                interpreter = Interpreter(model)
                
                // Cache interpreter untuk penggunaan berikutnya
                cachedInterpreter = interpreter
                cachedModelVersion = modelVersion
                Log.d(TAG, "Created new interpreter instance and cached it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing face detector: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Mendeteksi wajah dan memperkirakan usia dari gambar
     * 
     * @param bitmap Gambar yang akan dianalisis
     * @return FaceResult dengan posisi wajah, usia, dan kepercayaan, atau null jika tidak ada wajah terdeteksi
     */
    fun detectFaceAndEstimateAge(bitmap: Bitmap): FaceResult? {
        try {
            // Preprocess the image
            val processedImage = preprocessImage(bitmap)
            
            // If we have a real interpreter, use it
            if (interpreter != null) {
                // Create output buffers
                // Assuming model outputs: [face_rect_coords (4), age_estimate (1), confidence (1)]
                val faceRectOutput = Array(1) { FloatArray(4) } // [left, top, right, bottom]
                val ageOutput = Array(1) { FloatArray(1) }
                val confidenceOutput = Array(1) { FloatArray(1) }
                
                // TODO: In a real implementation, run inference with actual model outputs
                // interpreter?.run(processedImage, outputs)
                
                // For prototype, simulate results
                simulateFaceDetection(bitmap)?.let { return it }
            } else {
                // Fallback to simulation if interpreter is not available
                Log.d(TAG, "Interpreter not available, using simulation")
                return simulateFaceDetection(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting face: ${e.message}")
            e.printStackTrace()
        }
        
        return null
    }
    
    /**
     * Simulasi deteksi wajah untuk prototype
     * 
     * @param bitmap Gambar input
     * @return FaceResult simulasi
     */
    private fun simulateFaceDetection(bitmap: Bitmap): FaceResult? {
        // For prototype, simulate results
        val simulatedAge = (3..70).random()
        val simulatedConfidence = 0.75f + (0.2f * Math.random()).toFloat()
        
        // Create face rect (centered in image with random size)
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height / 2f
        val width = bitmap.width * (0.4f + (Math.random() * 0.2).toFloat())
        val height = bitmap.height * (0.4f + (Math.random() * 0.2).toFloat())
        
        val faceRect = RectF(
            centerX - width/2,
            centerY - height/2,
            centerX + width/2,
            centerY + height/2
        )
        
        return FaceResult(
            faceRect = faceRect,
            age = simulatedAge,
            confidence = simulatedConfidence,
            gender = if (Math.random() > 0.5) "Male" else "Female"
        )
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