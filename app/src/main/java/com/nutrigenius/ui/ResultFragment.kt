package com.nutrigenius.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import com.nutrigenius.R
import com.nutrigenius.ml.DetectionViewModel
import com.nutrigenius.ml.NutritionInfo
import com.nutrigenius.ml.Recommendation

/**
 * ResultFragment - Fragment untuk menampilkan hasil deteksi
 * 
 * Fragment ini bertanggung jawab untuk menampilkan hasil deteksi makanan
 * atau wajah yang diperoleh dari ScannerFragment melalui ViewModel bersama.
 */
class ResultFragment : Fragment() {
    
    // ViewModel untuk mengakses hasil deteksi
    private val viewModel: DetectionViewModel by activityViewModels()
    
    // UI components
    private lateinit var titleTextView: TextView
    private lateinit var foodImageView: ImageView
    private lateinit var foodNameTextView: TextView
    private lateinit var confidenceTextView: TextView
    private lateinit var nutritionCardView: CardView
    private lateinit var recommendationsCardView: CardView
    private lateinit var growthCardView: CardView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_result, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize UI components
        titleTextView = view.findViewById(R.id.resultTitleTextView)
        foodImageView = view.findViewById(R.id.foodImageView)
        foodNameTextView = view.findViewById(R.id.foodNameTextView)
        confidenceTextView = view.findViewById(R.id.confidenceTextView)
        nutritionCardView = view.findViewById(R.id.nutritionCardView)
        recommendationsCardView = view.findViewById(R.id.recommendationsCardView)
        growthCardView = view.findViewById(R.id.growthCardView)
        
        // Observe detection mode
        viewModel.detectionMode.observe(viewLifecycleOwner) { mode ->
            // Update UI based on detection mode
            when (mode) {
                DetectionViewModel.DetectionMode.OBJECT -> {
                    // Show food detection UI
                    displayFoodUI()
                }
                DetectionViewModel.DetectionMode.FACE -> {
                    // Show face detection UI
                    displayFaceUI()
                }
                null -> {
                    // Default to food detection if mode is not set
                    displayFoodUI()
                }
            }
        }
        
        // Observe food detection results
        viewModel.foodDetectionResult.observe(viewLifecycleOwner) { result ->
            if (result != null && viewModel.detectionMode.value == DetectionViewModel.DetectionMode.OBJECT) {
                val (detections, recommendation) = result
                
                if (detections.isNotEmpty()) {
                    val detection = detections[0]
                    foodNameTextView.text = detection.objectClass.capitalize()
                    confidenceTextView.text = 
                        "Confidence: ${String.format("%.1f", detection.confidence * 100)}%"
                    
                    displayNutritionInfo(recommendation)
                } else {
                    foodNameTextView.text = "No food detected"
                    confidenceTextView.text = ""
                    nutritionCardView.visibility = View.GONE
                    recommendationsCardView.visibility = View.GONE
                }
            }
        }
        
        // Observe face detection results
        viewModel.faceDetectionResult.observe(viewLifecycleOwner) { faceResult ->
            if (faceResult != null && viewModel.detectionMode.value == DetectionViewModel.DetectionMode.FACE) {
                foodNameTextView.text = "Face Detected"
                confidenceTextView.text = 
                    "Confidence: ${String.format("%.1f", faceResult.confidence * 100)}%"
                
                // Set growth statistics
                view.findViewById<TextView>(R.id.ageTextView).text = "Estimated Age: ${faceResult.age} years"
                view.findViewById<TextView>(R.id.genderTextView).text = "Gender: ${faceResult.gender}"
                
                // Generate dummy growth percentile based on age
                val heightPercentile = (1..99).random()
                val weightPercentile = (1..99).random()
                
                view.findViewById<TextView>(R.id.heightPercentileTextView).text = 
                    "Height Percentile: $heightPercentile%"
                view.findViewById<TextView>(R.id.weightPercentileTextView).text = 
                    "Weight Percentile: $weightPercentile%"
                
                // Growth status based on percentiles
                val growthStatus = when {
                    heightPercentile < 5 || weightPercentile < 5 -> "Below normal range"
                    heightPercentile > 95 || weightPercentile > 95 -> "Above normal range"
                    else -> "Within normal range"
                }
                view.findViewById<TextView>(R.id.growthStatusTextView).text = "Growth Status: $growthStatus"
            }
        }
        
        // Observe captured image
        viewModel.capturedImage.observe(viewLifecycleOwner) { imageBytes ->
            if (imageBytes != null) {
                try {
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    foodImageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error displaying image: ${e.message}", e)
                    
                    // Fallback to placeholder
                    if (viewModel.detectionMode.value == DetectionViewModel.DetectionMode.OBJECT) {
                        foodImageView.setImageResource(R.drawable.placeholder_food)
                    } else {
                        foodImageView.setImageResource(R.drawable.placeholder_face)
                    }
                }
            } else {
                // Use placeholders if no image available
                if (viewModel.detectionMode.value == DetectionViewModel.DetectionMode.OBJECT) {
                    foodImageView.setImageResource(R.drawable.placeholder_food)
                } else {
                    foodImageView.setImageResource(R.drawable.placeholder_face)
                }
            }
        }
        
        // Set up button to go back to scanner
        view.findViewById<Button>(R.id.backToScannerButton).setOnClickListener {
            // Navigate back to scanner
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ScannerFragment())
                .commit()
        }
        
        // If no data in ViewModel, use dummy data
        if (viewModel.foodDetectionResult.value == null && 
            viewModel.faceDetectionResult.value == null) {
            createDummyData()
        }
    }
    
    /**
     * Menampilkan UI untuk deteksi makanan
     */
    private fun displayFoodUI() {
        titleTextView.text = "Food Analysis"
        
        // Show nutrition cards, hide growth card
        nutritionCardView.visibility = View.VISIBLE
        recommendationsCardView.visibility = View.VISIBLE
        growthCardView.visibility = View.GONE
        
        // Set placeholder image if not set by ViewModel
        if (viewModel.capturedImage.value == null) {
            foodImageView.setImageResource(R.drawable.placeholder_food)
        }
    }
    
    /**
     * Menampilkan UI untuk deteksi wajah
     */
    private fun displayFaceUI() {
        titleTextView.text = "Growth Monitoring"
        
        // Hide nutrition cards, show growth card
        nutritionCardView.visibility = View.GONE
        recommendationsCardView.visibility = View.GONE
        growthCardView.visibility = View.VISIBLE
        
        // Set placeholder image if not set by ViewModel
        if (viewModel.capturedImage.value == null) {
            foodImageView.setImageResource(R.drawable.placeholder_face)
        }
    }
    
    /**
     * Menampilkan informasi nutrisi dari rekomendasi
     */
    private fun displayNutritionInfo(recommendation: Recommendation?) {
        recommendation?.let { rec ->
            // Show nutrition card
            nutritionCardView.visibility = View.VISIBLE
            
            // Set nutrition information
            view?.findViewById<TextView>(R.id.caloriesTextView)?.text = 
                "Calories: ${rec.nutritionInfo.calories} kcal"
            view?.findViewById<TextView>(R.id.proteinTextView)?.text = 
                "Protein: ${rec.nutritionInfo.protein}g"
            view?.findViewById<TextView>(R.id.carbsTextView)?.text = 
                "Carbs: ${rec.nutritionInfo.carbs}g"
            view?.findViewById<TextView>(R.id.fatTextView)?.text = 
                "Fat: ${rec.nutritionInfo.fat}g"
            
            // Display vitamins and minerals
            val vitaminsText = "Vitamins: ${rec.nutritionInfo.vitamins.joinToString(", ")}"
            view?.findViewById<TextView>(R.id.vitaminsTextView)?.text = vitaminsText
            
            val mineralsText = "Minerals: ${rec.nutritionInfo.minerals.joinToString(", ")}"
            view?.findViewById<TextView>(R.id.mineralsTextView)?.text = mineralsText
            
            // Show recommendations card
            recommendationsCardView.visibility = View.VISIBLE
            
            // Display dietary warnings
            if (rec.dietaryWarnings.isNotEmpty()) {
                val warningsText = "Warnings: ${rec.dietaryWarnings.joinToString(", ")}"
                view?.findViewById<TextView>(R.id.warningsTextView)?.apply {
                    text = warningsText
                    visibility = View.VISIBLE
                }
            } else {
                view?.findViewById<TextView>(R.id.warningsTextView)?.visibility = View.GONE
            }
            
            // Display alternatives
            if (rec.healthyAlternatives.isNotEmpty()) {
                val alternativesText = "Healthy Alternatives: ${rec.healthyAlternatives.joinToString(", ")}"
                view?.findViewById<TextView>(R.id.alternativesTextView)?.apply {
                    text = alternativesText
                    visibility = View.VISIBLE
                }
            } else {
                view?.findViewById<TextView>(R.id.alternativesTextView)?.visibility = View.GONE
            }
            
            // Display nutrition tips
            if (rec.nutritionTips.isNotEmpty()) {
                val tipsText = "Tips:\n• ${rec.nutritionTips.joinToString("\n• ")}"
                view?.findViewById<TextView>(R.id.tipsTextView)?.apply {
                    text = tipsText
                    visibility = View.VISIBLE
                }
            } else {
                view?.findViewById<TextView>(R.id.tipsTextView)?.visibility = View.GONE
            }
        } ?: run {
            // If no recommendation data
            nutritionCardView.visibility = View.GONE
            recommendationsCardView.visibility = View.GONE
        }
    }
    
    /**
     * Menciptakan data dummy jika tidak ada data di ViewModel
     */
    private fun createDummyData() {
        // For demonstration only
        try {
            // Randomly choose between food and face detection
            if (Math.random() > 0.5) {
                // Food detection dummy data
                val recommender = com.nutrigenius.ml.Recommender()
                val foods = listOf("apple", "banana", "pizza", "hot dog", "carrot")
                val foodName = foods.random()
                val confidence = 0.7f + (0.25f * Math.random()).toFloat()
                val recommendation = recommender.getRecommendation(foodName)
                
                // Update UI
                viewModel.setDetectionMode(DetectionViewModel.DetectionMode.OBJECT)
                foodNameTextView.text = foodName.capitalize()
                confidenceTextView.text = "Confidence: ${String.format("%.1f", confidence * 100)}%"
                
                displayNutritionInfo(recommendation)
            } else {
                // Face detection dummy data
                viewModel.setDetectionMode(DetectionViewModel.DetectionMode.FACE)
                
                val age = (3..70).random()
                val gender = if (Math.random() > 0.5) "Male" else "Female"
                val confidence = 0.75f + (0.2f * Math.random()).toFloat()
                
                foodNameTextView.text = "Face Detected"
                confidenceTextView.text = "Confidence: ${String.format("%.1f", confidence * 100)}%"
                
                // Set growth statistics
                view?.findViewById<TextView>(R.id.ageTextView)?.text = "Estimated Age: $age years"
                view?.findViewById<TextView>(R.id.genderTextView)?.text = "Gender: $gender"
                
                // Generate dummy growth percentile
                val heightPercentile = (1..99).random()
                val weightPercentile = (1..99).random()
                
                view?.findViewById<TextView>(R.id.heightPercentileTextView)?.text = 
                    "Height Percentile: $heightPercentile%"
                view?.findViewById<TextView>(R.id.weightPercentileTextView)?.text = 
                    "Weight Percentile: $weightPercentile%"
                
                // Growth status
                val growthStatus = when {
                    heightPercentile < 5 || weightPercentile < 5 -> "Below normal range"
                    heightPercentile > 95 || weightPercentile > 95 -> "Above normal range"
                    else -> "Within normal range"
                }
                view?.findViewById<TextView>(R.id.growthStatusTextView)?.text = "Growth Status: $growthStatus"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating dummy data: ${e.message}", e)
            showError("Could not load preview data")
        }
    }
    
    /**
     * Menampilkan pesan error
     */
    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG)
                .setAction("OK") { }
                .show()
        }
    }
    
    companion object {
        private const val TAG = "ResultFragment"
    }
    
    /**
     * Extension function to capitalize first letter of string
     */
    private fun String.capitalize(): String {
        return if (this.isNotEmpty()) {
            this[0].uppercase() + this.substring(1)
        } else {
            this
        }
    }
} 