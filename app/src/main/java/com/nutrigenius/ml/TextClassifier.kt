package com.nutrigenius.ml

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * TextClassifier - Model untuk klasifikasi teks dan rekomendasi artikel
 *
 * Kelas ini mengimplementasikan model TFLite untuk mengklasifikasikan teks pertanyaan atau
 * keluhan pengguna ke dalam beberapa kategori artikel yang relevan.
 */
class TextClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val labels = listOf(
        "Gizi Seimbang",
        "Perkembangan Anak",
        "Alergi Makanan",
        "Pencegahan Stunting",
        "Pola Makan Sehat"
    )
    
    // Vocabulary sederhana untuk tokenizer
    private val vocabulary = mutableMapOf<String, Int>()
    private val maxTokens = 20 // Maximum panjang teks yang akan diproses
    private val TAG = "TextClassifier"
    
    // Cache untuk hasil klasifikasi
    private val classificationCache = mutableMapOf<String, ArticleRecommendation>()
    
    init {
        try {
            // Di versi prototipe, kita menggunakan model dummy yang selalu mengembalikan
            // distribusi probabilitas yang sama
            // interpreter = loadRealModelWhenAvailable()
            
            // Inisialisasi vocabulary sederhana
            initVocabulary()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing text classifier: ${e.message}")
        }
    }
    
    /**
     * Mengklasifikasikan teks input dan memberikan rekomendasi artikel
     *
     * @param text Teks input dari pengguna (pertanyaan atau keluhan)
     * @return ArticleRecommendation yang berisi kategori dan artikel rekomendasi
     */
    fun classifyText(text: String, detectionContext: String? = null): ArticleRecommendation {
        // Cek cache untuk input yang sama
        val cacheKey = text.lowercase().trim() + (detectionContext ?: "")
        classificationCache[cacheKey]?.let {
            Log.d(TAG, "Using cached classification result for: $cacheKey")
            return it
        }
        
        // Untuk prototipe, kita menggunakan klasifikasi berdasarkan keyword matching sederhana
        // daripada menggunakan model ML yang sebenarnya
        val lowercaseText = text.lowercase()
        
        // Hitung skor untuk setiap kategori berdasarkan keyword matching
        val scores = mutableMapOf<String, Float>()
        labels.forEach { label ->
            val baseScore = calculateCategoryScore(lowercaseText, label)
            val contextBonus = matchWithDetectionContext(label, detectionContext)
            scores[label] = baseScore + contextBonus
        }
        
        // Kategori dengan skor tertinggi
        val topCategory = scores.maxByOrNull { it.value }?.key ?: labels[0]
        val confidence = scores[topCategory] ?: 0.7f // Default confidence
        
        // Dapatkan artikel untuk kategori
        val article = getArticleForCategory(topCategory)
        
        val result = ArticleRecommendation(topCategory, confidence, article)
        // Simpan di cache
        classificationCache[cacheKey] = result
        return result
    }
    
    /**
     * Mengklasifikasikan teks hasil OCR
     * Menggunakan pendekatan khusus untuk teks dari label makanan atau dokumen
     *
     * @param ocrText Teks hasil OCR
     * @param keywords Keywords yang diekstrak dari teks OCR
     * @return ArticleRecommendation yang berisi kategori dan artikel rekomendasi
     */
    fun classifyOcrText(ocrText: String, keywords: List<String>, detectionContext: String? = null): ArticleRecommendation {
        Log.d(TAG, "Classifying OCR text with keywords: $keywords")
        
        // Cek cache untuk input yang sama
        val cacheKey = "ocr:" + ocrText.lowercase().trim() + keywords.joinToString(",") + (detectionContext ?: "")
        classificationCache[cacheKey]?.let {
            Log.d(TAG, "Using cached OCR classification result")
            return it
        }
        
        // Gunakan gabungan teks asli dan keywords untuk scoring
        val preprocessedText = preprocessOcrText(ocrText)
        val combinedText = "$preprocessedText ${keywords.joinToString(" ")}"
        
        // Hitung skor dengan bobot tambahan untuk keywords
        val scores = mutableMapOf<String, Float>()
        labels.forEach { label ->
            val baseScore = calculateCategoryScore(combinedText, label)
            val keywordScore = calculateKeywordCategoryScore(keywords, label)
            val contextBonus = matchWithDetectionContext(label, detectionContext)
            
            // Gabungkan skor dengan bobot lebih pada keyword
            scores[label] = (baseScore * 0.3f) + (keywordScore * 0.5f) + contextBonus
        }
        
        // Log skor untuk debugging
        scores.forEach { (category, score) ->
            Log.d(TAG, "Category: $category, Score: $score")
        }
        
        // Kategori dengan skor tertinggi
        val topCategory = scores.maxByOrNull { it.value }?.key ?: labels[0]
        val confidence = scores[topCategory] ?: 0.7f // Default confidence
        
        // Dapatkan artikel untuk kategori
        val article = getArticleForCategory(topCategory)
        
        val result = ArticleRecommendation(topCategory, confidence, article)
        // Simpan di cache
        classificationCache[cacheKey] = result
        return result
    }
    
    /**
     * Matching dengan konteks deteksi makanan sebelumnya
     * Memberikan bobot tambahan jika kategori berhubungan dengan konteks
     */
    private fun matchWithDetectionContext(category: String, detectionContext: String?): Float {
        if (detectionContext.isNullOrEmpty()) return 0f
        
        val context = detectionContext.lowercase()
        
        // Pemetaan konteks deteksi makanan ke kategori artikel yang relevan
        val contextCategoryMapping = mapOf(
            // Buah-buahan -> Gizi Seimbang & Pola Makan Sehat
            "apple" to setOf("Gizi Seimbang", "Pola Makan Sehat"),
            "banana" to setOf("Gizi Seimbang", "Pola Makan Sehat"),
            "orange" to setOf("Gizi Seimbang", "Pola Makan Sehat"),
            "fruit" to setOf("Gizi Seimbang", "Pola Makan Sehat"),
            
            // Susu & produk susu -> Alergi Makanan & Pencegahan Stunting
            "milk" to setOf("Alergi Makanan", "Pencegahan Stunting", "Gizi Seimbang"),
            "yogurt" to setOf("Gizi Seimbang", "Pencegahan Stunting"),
            "cheese" to setOf("Alergi Makanan", "Gizi Seimbang"),
            
            // Kategori lain
            "vegetable" to setOf("Gizi Seimbang", "Pola Makan Sehat"),
            "meat" to setOf("Gizi Seimbang", "Perkembangan Anak"),
            "fish" to setOf("Gizi Seimbang", "Perkembangan Anak", "Pencegahan Stunting"),
            "egg" to setOf("Alergi Makanan", "Gizi Seimbang")
        )
        
        // Cek jika konteks yang terdeteksi cocok dengan mapping
        for ((key, categories) in contextCategoryMapping) {
            if (context.contains(key)) {
                if (categories.contains(category)) {
                    return 0.2f // Berikan bobot tambahan
                }
            }
        }
        
        return 0f
    }
    
    /**
     * Pra-pemrosesan teks hasil OCR
     */
    private fun preprocessOcrText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ") // Hapus karakter non-alfanumerik
            .replace(Regex("\\s+"), " ")        // Normalisasi spasi
            .trim()
    }
    
    /**
     * Menghitung skor kategori berdasarkan keywords hasil ekstraksi
     */
    private fun calculateKeywordCategoryScore(keywords: List<String>, category: String): Float {
        val keywordSets = mapOf(
            "Gizi Seimbang" to setOf("gizi", "nutrisi", "seimbang", "makanan", "protein", "vitamin", "sehat", "kalori", "energi", "karbohidrat", "mineral"),
            "Perkembangan Anak" to setOf("tumbuh", "kembang", "perkembangan", "milestone", "pertumbuhan", "kognitif", "motorik", "usia", "tahap", "bayi", "balita", "anak"),
            "Alergi Makanan" to setOf("alergi", "reaksi", "sensitif", "gatal", "telur", "susu", "kacang", "gluten", "intoleransi", "ruam", "muntah"),
            "Pencegahan Stunting" to setOf("stunting", "pendek", "tinggi", "berat", "malnutrisi", "pencegahan", "gizi buruk", "pertumbuhan", "asi", "mpasi"),
            "Pola Makan Sehat" to setOf("pola", "makan", "diet", "porsi", "jadwal", "rutin", "sayur", "buah", "sehat", "teratur", "kebiasaan")
        )
        
        val categoryKeywords = keywordSets[category] ?: emptySet()
        
        // Hitung berapa keyword yang match dengan kategori
        var matches = 0
        var totalWeight = 0f
        
        for (keyword in keywords) {
            for (categoryKeyword in categoryKeywords) {
                if (keyword.contains(categoryKeyword) || categoryKeyword.contains(keyword)) {
                    // Berikan bobot lebih untuk match yang lebih dekat
                    val similarity = calculateSimilarity(keyword, categoryKeyword)
                    totalWeight += similarity
                    matches++
                    break
                }
            }
        }
        
        // Normalisasi skor (0.5 - 0.95)
        return if (categoryKeywords.isEmpty() || keywords.isEmpty()) 0.5f 
        else 0.5f + min(totalWeight / keywords.size, 1f) * 0.45f
    }
    
    /**
     * Menghitung similarity sederhana antara dua string
     */
    private fun calculateSimilarity(str1: String, str2: String): Float {
        // Implementasi sederhana: persentase karakter yang sama
        val minLength = min(str1.length, str2.length)
        if (minLength == 0) return 0f
        
        var matchCount = 0
        for (i in 0 until minLength) {
            if (str1[i] == str2[i]) matchCount++
        }
        
        return matchCount.toFloat() / minLength
    }
    
    /**
     * Menghitung skor relevansi teks dengan kategori berdasarkan keyword
     */
    private fun calculateCategoryScore(text: String, category: String): Float {
        val keywordSets = mapOf(
            "Gizi Seimbang" to setOf("gizi", "nutrisi", "seimbang", "makanan", "protein", "vitamin", "sehat", "karbohidrat", "mineral", "kalori", "energi"),
            "Perkembangan Anak" to setOf("tumbuh", "kembang", "perkembangan", "milestone", "pertumbuhan", "kognitif", "motorik", "tahap", "bayi", "balita", "anak"),
            "Alergi Makanan" to setOf("alergi", "reaksi", "sensitif", "gatal", "telur", "susu", "kacang", "gluten", "intoleransi", "ruam", "muntah"),
            "Pencegahan Stunting" to setOf("stunting", "pendek", "tinggi", "berat", "malnutrisi", "pencegahan", "gizi buruk", "pertumbuhan", "asi", "mpasi"),
            "Pola Makan Sehat" to setOf("pola", "makan", "diet", "porsi", "jadwal", "rutin", "sayur", "buah", "sehat", "teratur", "kebiasaan")
        )
        
        val keywords = keywordSets[category] ?: emptySet()
        
        // Hitung berapa keyword yang muncul dalam teks
        var matches = 0
        for (keyword in keywords) {
            if (text.contains(keyword)) {
                matches++
            }
        }
        
        // Normalisasi skor (0.5 - 0.95)
        return if (keywords.isEmpty()) 0.5f else 0.5f + (matches.toFloat() / keywords.size) * 0.45f
    }
    
    /**
     * Mendapatkan artikel untuk kategori tertentu dari data dummy
     */
    private fun getArticleForCategory(category: String): Article {
        try {
            // Baca file JSON dari assets
            val jsonString = context.assets.open("articles_data.json").bufferedReader().use { it.readText() }
            val articlesData = JSONObject(jsonString)
            
            if (articlesData.has(category)) {
                val categoryArticles = articlesData.getJSONArray(category)
                
                // Ambil artikel random dari kategori
                val randomIndex = (0 until categoryArticles.length()).random()
                val articleObj = categoryArticles.getJSONObject(randomIndex)
                
                return Article(
                    title = articleObj.getString("title"),
                    summary = articleObj.getString("summary"),
                    content = articleObj.getString("content"),
                    imageUrl = articleObj.optString("imageUrl"),
                    category = category
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading articles data: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing article data: ${e.message}")
        }
        
        // Return default article if category not found or error
        return createDefaultArticle(category)
    }
    
    /**
     * Membuat artikel default jika data tidak ditemukan
     */
    private fun createDefaultArticle(category: String): Article {
        return Article(
            title = "Artikel tentang $category",
            summary = "Informasi penting tentang $category untuk tumbuh kembang anak yang optimal",
            content = "Ini adalah artikel placeholder untuk kategori $category. " +
                    "Dalam aplikasi sebenarnya, artikel ini akan berisi informasi lengkap tentang " +
                    "topik yang relevan dengan kategori yang dipilih berdasarkan pertanyaan pengguna.",
            imageUrl = null,
            category = category
        )
    }
    
    /**
     * Inisialisasi vocabulary sederhana untuk tokenisasi
     */
    private fun initVocabulary() {
        // Kata-kata umum dalam bahasa Indonesia tentang nutrisi dan kesehatan
        val commonWords = listOf(
            "anak", "makan", "gizi", "nutrisi", "pertumbuhan", "perkembangan", 
            "sehat", "sakit", "alergi", "vitamin", "mineral", "protein", 
            "karbohidrat", "lemak", "sayur", "buah", "daging", "telur", 
            "susu", "stunting", "obesitas", "kurus", "tinggi", "berat",
            "diet", "seimbang", "porsi", "asi", "mpasi", "rutin", "teratur"
        )
        
        // Buat vocabulary map dari kata-kata umum
        commonWords.forEachIndexed { index, word ->
            vocabulary[word] = index + 1  // Indeks 0 untuk unknown/padding
        }
    }
    
    /**
     * Tokenisasi dan padding teks input (untuk model ML)
     */
    private fun tokenizeAndPad(text: String): Array<FloatArray> {
        val words = text.split("\\s+".toRegex())
        val tokens = IntArray(maxTokens) { 0 } // 0 adalah padding
        
        // Tokenisasi kata-kata
        for (i in words.indices) {
            if (i >= maxTokens) break
            val word = words[i]
            tokens[i] = vocabulary[word] ?: 0  // 0 jika kata tidak ada di vocabulary
        }
        
        // Convert ke format input yang diharapkan model
        val inputBuffer = Array(1) { FloatArray(maxTokens) }
        for (i in tokens.indices) {
            inputBuffer[0][i] = tokens[i].toFloat()
        }
        
        return inputBuffer
    }
    
    /**
     * Objek yang berisi informasi artikel rekomendasi
     */
    data class ArticleRecommendation(
        val category: String,
        val confidence: Float,
        val article: Article
    )
    
    /**
     * Model data untuk artikel
     */
    data class Article(
        val title: String,
        val summary: String,
        val content: String,
        val imageUrl: String? = null,
        val category: String
    )
    
    /**
     * Membersihkan cache
     */
    fun clearCache() {
        classificationCache.clear()
    }
    
    /**
     * Menutup interpreter saat tidak digunakan
     */
    fun close() {
        interpreter?.close()
    }
} 