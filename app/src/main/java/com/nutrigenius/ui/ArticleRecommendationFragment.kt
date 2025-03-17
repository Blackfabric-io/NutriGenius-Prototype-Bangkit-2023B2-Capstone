package com.nutrigenius.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.nutrigenius.R
import com.nutrigenius.ml.DetectionViewModel
import com.nutrigenius.ml.TextClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * ArticleRecommendationFragment - Fragment untuk rekomendasi artikel
 *
 * Fragment ini memungkinkan pengguna memasukkan teks pertanyaan atau keluhan,
 * atau menerima teks hasil OCR dan keywords, kemudian menggunakan TextClassifier
 * untuk merekomendasikan artikel yang relevan.
 */
class ArticleRecommendationFragment : Fragment() {

    // Text classifier untuk rekomendasi artikel
    private lateinit var textClassifier: TextClassifier
    
    // ViewModel untuk akses data
    private val viewModel: DetectionViewModel by activityViewModels()
    
    // UI components
    private lateinit var queryInput: TextInputEditText
    private lateinit var searchButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultCard: CardView
    private lateinit var emptyResultText: TextView
    private lateinit var categoryText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var articleTitle: TextView
    private lateinit var articleSummary: TextView
    private lateinit var articleContent: TextView
    private lateinit var articleImage: ImageView
    private lateinit var shareButton: Button
    private lateinit var keywordsChipGroup: ChipGroup
    
    // Flag apakah berasal dari text detection
    private var fromTextDetection = false
    private var fromObjectDetection = false
    
    // Data dari text detection
    private var detectedText: String? = null
    private var detectedKeywords: Array<String>? = null
    private var detectionContext: String? = null
    
    // Current article for sharing
    private var currentArticle: TextClassifier.Article? = null
    
    // TAG untuk logging
    private val TAG = "ArticleRecommendation"
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_article_recommendation, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Ambil data dari arguments
        arguments?.let { args ->
            detectedText = args.getString("query")
            detectedKeywords = args.getStringArray("keywords")
            fromTextDetection = args.getBoolean("fromTextDetection", false)
            fromObjectDetection = args.getBoolean("fromObjectDetection", false)
            detectionContext = args.getString("detectionContext")
            
            Log.d(TAG, "From text detection: $fromTextDetection")
            Log.d(TAG, "From object detection: $fromObjectDetection")
            Log.d(TAG, "Detected text: $detectedText")
            Log.d(TAG, "Detected keywords: ${detectedKeywords?.joinToString(", ")}")
            Log.d(TAG, "Detection context: $detectionContext")
        }
        
        // Initialize Text Classifier
        textClassifier = TextClassifier(requireContext())
        
        // Initialize UI components
        initializeViews(view)
        
        // Set click listener for search button
        searchButton.setOnClickListener {
            val query = queryInput.text.toString().trim()
            if (query.isEmpty()) {
                Snackbar.make(
                    view,
                    "Silakan masukkan pertanyaan atau keluhan Anda",
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            
            performSearch(query)
        }
        
        // Set click listener for share button
        shareButton.setOnClickListener {
            shareCurrentArticle()
        }
        
        // Tambahkan navigasi kembali
        view.findViewById<Button>(R.id.backButton)?.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        
        // Tambahkan history button
        view.findViewById<Button>(R.id.historyButton)?.setOnClickListener {
            // Navigate to history fragment
            val historyFragment = HistoryFragment()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, historyFragment)
                .addToBackStack(null)
                .commit()
        }
        
        // Jika ada teks terdeteksi, set ke input dan lakukan pencarian otomatis
        detectedText?.let { text ->
            queryInput.setText(text)
            
            // Jika berasal dari deteksi teks, gunakan klasifikasi OCR
            if (fromTextDetection && detectedKeywords != null) {
                performOcrSearch(text, detectedKeywords!!.toList())
            } 
            // Jika berasal dari deteksi objek, gunakan pencarian biasa
            else if (fromObjectDetection) {
                performSearch(text)
            }
        }
    }
    
    /**
     * Inisialisasi semua view
     */
    private fun initializeViews(view: View) {
        queryInput = view.findViewById(R.id.queryInput)
        searchButton = view.findViewById(R.id.searchButton)
        progressBar = view.findViewById(R.id.progressBar)
        resultCard = view.findViewById(R.id.resultCard)
        emptyResultText = view.findViewById(R.id.emptyResultText)
        categoryText = view.findViewById(R.id.categoryText)
        confidenceText = view.findViewById(R.id.confidenceText)
        articleTitle = view.findViewById(R.id.articleTitle)
        articleSummary = view.findViewById(R.id.articleSummary)
        articleContent = view.findViewById(R.id.articleContent)
        articleImage = view.findViewById(R.id.articleImage)
        shareButton = view.findViewById(R.id.shareButton)
        keywordsChipGroup = view.findViewById(R.id.keywordsChipGroup)
    }
    
    /**
     * Menambahkan chip untuk setiap keyword yang terdeteksi
     */
    private fun displayKeywords(keywords: List<String>) {
        keywordsChipGroup.removeAllViews()
        
        if (keywords.isEmpty()) {
            keywordsChipGroup.visibility = View.GONE
            return
        }
        
        keywordsChipGroup.visibility = View.VISIBLE
        
        for (keyword in keywords) {
            val chip = Chip(requireContext())
            chip.text = keyword
            chip.isClickable = true
            chip.isCheckable = false
            chip.setOnClickListener {
                // Search using this keyword
                queryInput.setText(keyword)
                performSearch(keyword)
            }
            keywordsChipGroup.addView(chip)
        }
    }
    
    /**
     * Melakukan pencarian berdasarkan hasil OCR
     */
    private fun performOcrSearch(text: String, keywords: List<String>) {
        // Tampilkan loading
        setLoading(true)
        
        // Display keywords as chips
        displayKeywords(keywords)
        
        // Proses di background thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Klasifikasi teks OCR untuk mendapatkan rekomendasi
                val recommendation = textClassifier.classifyOcrText(text, keywords, detectionContext)
                
                // Load image jika ada
                val bitmap = if (!recommendation.article.imageUrl.isNullOrEmpty()) {
                    try {
                        // Ini hanya untuk simulasi, dalam produksi gunakan Glide atau Picasso
                        val url = URL(recommendation.article.imageUrl)
                        BitmapFactory.decodeStream(url.openStream())
                    } catch (e: Exception) {
                        null
                    }
                } else null
                
                // Update UI di main thread
                withContext(Dispatchers.Main) {
                    displayResult(recommendation, bitmap)
                    setLoading(false)
                    
                    // Save current article for sharing
                    currentArticle = recommendation.article
                }
            } catch (e: Exception) {
                // Tampilkan error di main thread
                withContext(Dispatchers.Main) {
                    Snackbar.make(
                        requireView(),
                        "Error: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                    setLoading(false)
                }
            }
        }
    }
    
    /**
     * Melakukan pencarian dan klasifikasi teks biasa
     */
    private fun performSearch(query: String) {
        // Tampilkan loading
        setLoading(true)
        
        // Proses di background thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Klasifikasi teks untuk mendapatkan rekomendasi
                val recommendation = textClassifier.classifyText(query, detectionContext)
                
                // Load image jika ada
                val bitmap = if (!recommendation.article.imageUrl.isNullOrEmpty()) {
                    try {
                        // Ini hanya untuk simulasi, dalam produksi gunakan Glide atau Picasso
                        val url = URL(recommendation.article.imageUrl)
                        BitmapFactory.decodeStream(url.openStream())
                    } catch (e: Exception) {
                        null
                    }
                } else null
                
                // Update UI di main thread
                withContext(Dispatchers.Main) {
                    displayResult(recommendation, bitmap)
                    setLoading(false)
                    
                    // Save current article for sharing
                    currentArticle = recommendation.article
                    
                    // Save search to history
                    viewModel.saveDetectionResult(
                        objectClass = "Search: $query",
                        confidence = recommendation.confidence,
                        detectionType = DetectionViewModel.DetectionMode.TEXT,
                        imageData = null,
                        nutritionInfoJson = "{\"category\":\"${recommendation.category}\",\"source\":\"search\"}"
                    )
                }
            } catch (e: Exception) {
                // Tampilkan error di main thread
                withContext(Dispatchers.Main) {
                    Snackbar.make(
                        requireView(),
                        "Error: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                    setLoading(false)
                }
            }
        }
    }
    
    /**
     * Menampilkan hasil rekomendasi artikel
     */
    private fun displayResult(
        recommendation: TextClassifier.ArticleRecommendation,
        bitmap: android.graphics.Bitmap?
    ) {
        // Tampilkan kategori dan confidence
        categoryText.text = recommendation.category
        confidenceText.text = "Relevansi: ${String.format("%.0f", recommendation.confidence * 100)}%"
        
        // Tampilkan detail artikel
        articleTitle.text = recommendation.article.title
        articleSummary.text = recommendation.article.summary
        articleContent.text = recommendation.article.content
        
        // Tampilkan gambar jika ada
        if (bitmap != null) {
            articleImage.setImageBitmap(bitmap)
            articleImage.visibility = View.VISIBLE
        } else {
            articleImage.visibility = View.GONE
        }
        
        // Tampilkan card hasil dan sembunyikan teks kosong
        resultCard.visibility = View.VISIBLE
        emptyResultText.visibility = View.GONE
    }
    
    /**
     * Membagikan artikel saat ini
     */
    private fun shareCurrentArticle() {
        // Pastikan ada artikel yang ditampilkan
        if (resultCard.visibility != View.VISIBLE || currentArticle == null) return
        
        val shareText = """
            ${currentArticle?.title}
            
            ${currentArticle?.summary}
            
            ${currentArticle?.content?.toString()?.take(200)}...
            
            Dibagikan dari aplikasi NutriGenius
        """.trimIndent()
        
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        
        val shareIntent = Intent.createChooser(sendIntent, "Bagikan artikel via")
        startActivity(shareIntent)
    }
    
    /**
     * Mengatur status loading
     */
    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            searchButton.isEnabled = false
        } else {
            progressBar.visibility = View.GONE
            searchButton.isEnabled = true
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        textClassifier.close()
    }
} 