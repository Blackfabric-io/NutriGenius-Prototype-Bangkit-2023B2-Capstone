package com.nutrigenius.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import android.util.LruCache
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * TextRecognizer - Kelas untuk mendeteksi teks dalam gambar
 *
 * Kelas ini menggunakan ML Kit Text Recognition API untuk
 * mengekstrak teks dari gambar yang dipotret oleh pengguna.
 */
class TextRecognizer(private val context: Context) {
    
    // Gunakan executor khusus untuk proses OCR
    private val executor = Executors.newSingleThreadExecutor()
    
    private val options = TextRecognizerOptions.DEFAULT_OPTIONS
    private val recognizer = TextRecognition.getClient(options)
    private val TAG = "TextRecognizer"
    
    // Cache untuk hasil OCR (20 entri terakhir)
    private val ocrCache = LruCache<String, Pair<String, List<String>>>(20) // Hash -> (Text, Keywords)
    
    /**
     * Mendeteksi teks dalam gambar dengan caching
     *
     * @param bitmap Bitmap gambar yang akan dianalisis
     * @param callback Callback untuk menerima hasil (teks, keywords, atau error)
     */
    fun recognizeTextWithCache(bitmap: Bitmap, callback: (String?, List<String>?, Exception?) -> Unit) {
        // Generate hash dari bitmap
        val imageHash = bitmap.generateHash()
        
        // Cek cache
        ocrCache.get(imageHash)?.let { (text, keywords) ->
            Log.d(TAG, "Using cached OCR result")
            callback(text, keywords, null)
            return
        }
        
        // Pre-process gambar untuk meningkatkan akurasi OCR
        val processedBitmap = preprocessImageForOcr(bitmap)
        
        // Jika tidak ada di cache, lakukan OCR
        recognizeText(processedBitmap) { text, error ->
            // Bersihkan bitmap yang sudah diproses jika berbeda dengan original
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            
            if (error != null) {
                callback(null, null, error)
                return@recognizeText
            }
            
            if (text != null) {
                val keywords = extractKeywords(text)
                // Simpan di cache
                ocrCache.put(imageHash, Pair(text, keywords))
                callback(text, keywords, null)
            } else {
                callback(null, null, Exception("No text detected"))
            }
        }
    }
    
    /**
     * Mendeteksi teks dalam gambar
     *
     * @param bitmap Bitmap gambar yang akan dianalisis
     * @param callback Callback untuk menerima hasil (teks atau error)
     */
    fun recognizeText(bitmap: Bitmap, callback: (String?, Exception?) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Ekstrak teks yang terdeteksi
                val detectedText = visionText.text
                
                // Log untuk debugging
                Log.d(TAG, "Text detected: $detectedText")
                logTextBlocks(visionText)
                
                callback(detectedText, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed: ${e.message}", e)
                callback(null, e)
            }
    }
    
    /**
     * Pre-processing gambar untuk meningkatkan akurasi OCR
     */
    private fun preprocessImageForOcr(bitmap: Bitmap): Bitmap {
        // Konversi ke grayscale untuk meningkatkan kontras teks
        val grayBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(grayBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        
        // Tingkatkan kontras sedikit
        colorMatrix.postConcat(ColorMatrix().apply {
            setScale(1.2f, 1.2f, 1.2f, 1f)
        })
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return grayBitmap
    }
    
    /**
     * Mencatat blok teks yang terdeteksi untuk debugging
     */
    private fun logTextBlocks(visionText: Text) {
        val blocks = visionText.textBlocks
        if (blocks.isEmpty()) {
            Log.d(TAG, "No text blocks detected")
            return
        }
        
        Log.d(TAG, "Text blocks detected: ${blocks.size}")
        blocks.forEachIndexed { index, block ->
            Log.d(TAG, "Block #$index: ${block.text}")
            Log.d(TAG, "Confidence: ${block.confidence}")
            Log.d(TAG, "Language: ${block.recognizedLanguage}")
        }
    }
    
    /**
     * Ekstrak kata kunci dari teks OCR
     * Memfilter dan menormalkan text untuk mendapatkan kata kunci
     */
    fun extractKeywords(text: String): List<String> {
        val preprocessedText = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ") // Hapus karakter non-alfanumerik
            .replace(Regex("\\s+"), " ")        // Normalisasi spasi
            .trim()
        
        // Kata-kata yang biasa ditemukan dalam teks nutrisi
        val nutritionKeywords = setOf(
            "gizi", "nutrisi", "protein", "vitamin", "karbohidrat", "lemak",
            "mineral", "kalori", "energi", "sehat", "makanan", "diet",
            "pertumbuhan", "perkembangan", "stunting", "alergi", "intoleransi",
            "kandungan", "berat", "badan", "tinggi", "persen", "label",
            // Tambahkan lebih banyak keyword relevan
            "anak", "bayi", "balita", "obesitas", "kurus", "gemuk", "seimbang",
            "susu", "sayur", "buah", "daging", "telur", "kacang", "minyak"
        )
        
        // Filter kata-kata untuk mendapatkan kata kunci relevan
        return preprocessedText.split(" ")
            .filter { word -> 
                word.length > 2 && (nutritionKeywords.contains(word) || 
                nutritionKeywords.any { keyword -> word.contains(keyword) })
            }
            .distinct()
            .take(15) // Ambil 15 kata kunci teratas untuk cakupan lebih luas
    }
    
    /**
     * Metode helper untuk menghasilkan hash dari bitmap
     */
    private fun Bitmap.generateHash(): String {
        val byteBuffer = ByteBuffer.allocate(width * height * 4)
        copyPixelsToBuffer(byteBuffer)
        return byteBuffer.array().contentHashCode().toString()
    }
    
    /**
     * Menutup recognizer dan executor saat tidak digunakan
     */
    fun close() {
        recognizer.close()
        executor.shutdown()
    }
} 