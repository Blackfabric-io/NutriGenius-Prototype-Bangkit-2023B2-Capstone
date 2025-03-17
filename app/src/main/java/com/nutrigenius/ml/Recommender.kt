package com.nutrigenius.ml

import java.util.*

data class NutritionInfo(
    val calories: Int,
    val protein: Float,  // in grams
    val carbs: Float,    // in grams
    val fat: Float,      // in grams
    val vitamins: List<String> = emptyList(),
    val minerals: List<String> = emptyList()
)

data class Recommendation(
    val foodName: String,
    val nutritionInfo: NutritionInfo,
    val healthyAlternatives: List<String> = emptyList(),
    val dietaryWarnings: List<String> = emptyList(),
    val nutritionTips: List<String> = emptyList()
)

class Recommender {
    // Simplified food database for prototype
    private val foodDatabase = mapOf(
        "apple" to NutritionInfo(
            calories = 95,
            protein = 0.5f,
            carbs = 25f,
            fat = 0.3f,
            vitamins = listOf("Vitamin C", "Vitamin A"),
            minerals = listOf("Potassium")
        ),
        "banana" to NutritionInfo(
            calories = 105,
            protein = 1.3f,
            carbs = 27f,
            fat = 0.4f,
            vitamins = listOf("Vitamin B6", "Vitamin C"),
            minerals = listOf("Potassium", "Magnesium")
        ),
        "orange" to NutritionInfo(
            calories = 65,
            protein = 1.2f,
            carbs = 15f,
            fat = 0.2f,
            vitamins = listOf("Vitamin C", "Folate"),
            minerals = listOf("Potassium", "Calcium")
        ),
        "pizza" to NutritionInfo(
            calories = 285,
            protein = 12f,
            carbs = 36f,
            fat = 10f,
            vitamins = listOf("Vitamin A", "Vitamin B12"),
            minerals = listOf("Calcium", "Iron")
        ),
        "sandwich" to NutritionInfo(
            calories = 250,
            protein = 15f,
            carbs = 28f,
            fat = 8f,
            vitamins = listOf("Vitamin B", "Vitamin E"),
            minerals = listOf("Iron", "Zinc")
        ),
        "hot dog" to NutritionInfo(
            calories = 290,
            protein = 10f,
            carbs = 18f,
            fat = 17f,
            vitamins = listOf("Vitamin B6", "Vitamin B12"),
            minerals = listOf("Sodium", "Iron")
        ),
        "carrot" to NutritionInfo(
            calories = 50,
            protein = 1.1f,
            carbs = 12f,
            fat = 0.2f,
            vitamins = listOf("Vitamin A", "Vitamin K"),
            minerals = listOf("Potassium", "Calcium")
        ),
        "broccoli" to NutritionInfo(
            calories = 55,
            protein = 3.7f,
            carbs = 11f,
            fat = 0.6f,
            vitamins = listOf("Vitamin C", "Vitamin K"),
            minerals = listOf("Potassium", "Calcium")
        ),
        "donut" to NutritionInfo(
            calories = 250,
            protein = 3f,
            carbs = 30f,
            fat = 14f,
            vitamins = listOf("Vitamin B"),
            minerals = listOf("Iron")
        ),
        "cake" to NutritionInfo(
            calories = 350,
            protein = 5f,
            carbs = 50f,
            fat = 15f,
            vitamins = listOf("Vitamin A", "Vitamin D"),
            minerals = listOf("Calcium", "Iron")
        ),
        "rice" to NutritionInfo(
            calories = 130,
            protein = 2.7f,
            carbs = 28f,
            fat = 0.3f,
            vitamins = listOf("Vitamin B"),
            minerals = listOf("Magnesium", "Phosphorus")
        ),
        "chicken" to NutritionInfo(
            calories = 165,
            protein = 31f,
            carbs = 0f,
            fat = 3.6f,
            vitamins = listOf("Vitamin B6", "Vitamin B12"),
            minerals = listOf("Phosphorus", "Selenium")
        )
    )
    
    // Health alternatives suggestions
    private val healthyAlternatives = mapOf(
        "pizza" to listOf("whole grain pizza", "vegetable pizza", "cauliflower crust pizza"),
        "burger" to listOf("veggie burger", "turkey burger", "portobello mushroom burger"),
        "fries" to listOf("sweet potato fries", "baked fries", "vegetable sticks"),
        "soda" to listOf("sparkling water", "infused water", "unsweetened tea"),
        "hot dog" to listOf("turkey dog", "veggie dog", "grilled chicken"),
        "donut" to listOf("whole grain muffin", "fruit", "yogurt parfait"),
        "cake" to listOf("angel food cake", "fruit salad", "dark chocolate")
    )
    
    fun getRecommendation(foodName: String): Recommendation? {
        // Convert to lowercase for case-insensitive matching
        val normalizedFoodName = foodName.lowercase(Locale.getDefault())
        
        // Get nutrition info from database
        val nutritionInfo = foodDatabase[normalizedFoodName] ?: return null
        
        // Get healthy alternatives
        val alternatives = healthyAlternatives[normalizedFoodName] ?: emptyList()
        
        // Generate dietary warnings
        val warnings = generateWarnings(nutritionInfo)
        
        // Generate nutrition tips
        val tips = generateNutritionTips(normalizedFoodName, nutritionInfo)
        
        return Recommendation(
            foodName = foodName,
            nutritionInfo = nutritionInfo,
            healthyAlternatives = alternatives,
            dietaryWarnings = warnings,
            nutritionTips = tips
        )
    }
    
    private fun generateWarnings(nutritionInfo: NutritionInfo): List<String> {
        val warnings = mutableListOf<String>()
        
        if (nutritionInfo.calories > 300) {
            warnings.add("High in calories")
        }
        
        if (nutritionInfo.fat > 10) {
            warnings.add("High in fat")
        }
        
        if (nutritionInfo.carbs > 40) {
            warnings.add("High in carbohydrates")
        }
        
        return warnings
    }
    
    private fun generateNutritionTips(foodName: String, nutritionInfo: NutritionInfo): List<String> {
        val tips = mutableListOf<String>()
        
        // Simple tips based on food type and nutrition
        when {
            nutritionInfo.protein > 20 -> tips.add("Good source of protein")
            nutritionInfo.vitamins.contains("Vitamin C") -> tips.add("Contains Vitamin C for immune support")
            nutritionInfo.minerals.contains("Potassium") -> tips.add("Contains potassium which is good for heart health")
            nutritionInfo.calories < 100 -> tips.add("Low calorie option")
        }
        
        // Add more specific tips for certain foods
        when (foodName.lowercase()) {
            "apple", "banana", "orange", "carrot", "broccoli" -> 
                tips.add("Fruits and vegetables are essential for a balanced diet")
            "pizza", "hot dog", "donut", "cake" -> 
                tips.add("Consider healthier alternatives or consume in moderation")
        }
        
        // Add a general tip
        tips.add("Pair with vegetables for a more balanced meal")
        
        return tips
    }
} 