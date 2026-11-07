package com.mateoviscarra.doitall.data

import com.mateoviscarra.doitall.data.persist.WeightUnit
import java.util.UUID

enum class MealSlot(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks")
}

enum class EntrySource {
    STAPLE,
    AI_PHOTO,
    AI_LABEL,
    AI_SCAN,
    MANUAL
}

data class SavedMeal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val calories: Int,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val servingDescription: String = "",
    val isPinnedStaple: Boolean = true,
    val defaultMealSlot: MealSlot = MealSlot.BREAKFAST
)

data class FoodLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val dateIso: String,                 // "YYYY-MM-DD"
    val time: String = "",               // e.g. "08:30"
    val mealSlot: MealSlot = MealSlot.BREAKFAST,
    val name: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val portionMultiplier: Float = 1.0f,
    val source: EntrySource = EntrySource.MANUAL,
    val note: String = ""
) {
    val totalCalories: Int get() = (calories * portionMultiplier).toInt()
    val totalProtein: Float get() = proteinGrams * portionMultiplier
    val totalCarbs: Float get() = carbsGrams * portionMultiplier
    val totalFat: Float get() = fatGrams * portionMultiplier
}

data class NutritionGoals(
    val calorieTarget: Int = 2400,
    val proteinTargetGrams: Float = 180f,
    val carbsTargetGrams: Float = 250f,
    val fatTargetGrams: Float = 70f
)

data class WeightLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val dateIso: String,           // "YYYY-MM-DD"
    val weight: Float,             // e.g. 76.4
    val unit: WeightUnit = WeightUnit.KG,
    val note: String = ""
)

data class DayNutritionTotals(
    val dateIso: String,
    val totalCalories: Int,
    val totalProtein: Float,
    val totalCarbs: Float,
    val totalFat: Float,
    val entries: List<FoodLogEntry>
)

const val DEFAULT_NUTRITION_AI_PROMPT = """You are an expert nutritionist and visual food analyst based in Bolivia.
The user is located in Bolivia, so estimate dishes and ingredients with local Bolivian cuisine and supermarket products in mind (e.g. Bolivian cuts of meat, marraqueta, sarnita, salteñas, silpancho, pique macho, majadito, sopa de maní, llajwa, local protein bars/alfajores, dairy products, grains like quinoa, etc.) as well as international fitness foods.
Analyze the image or description provided. Estimate total calories and macronutrients (protein, carbs, fat in grams).
Return STRICT JSON in the following format:
{
  "detectedItems": [
    {"item": "Name of food item", "portion": "Estimated portion description, e.g. 1 bowl (~300g)"}
  ],
  "calories": 450,
  "protein": 30.0,
  "carbs": 50.0,
  "fat": 12.0,
  "notes": "Short helpful context or estimation explanation"
}"""
