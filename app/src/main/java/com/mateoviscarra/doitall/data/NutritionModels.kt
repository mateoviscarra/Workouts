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

data class MealIngredient(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amountDescription: String = "",
    val calories: Int = 0,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f
)

data class SavedMeal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val calories: Int,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val servingDescription: String = "",
    val isPinnedStaple: Boolean = true,
    val defaultMealSlot: MealSlot = MealSlot.BREAKFAST,
    val ingredients: List<MealIngredient> = emptyList()
) {
    val totalCalories: Int
        get() = if (ingredients.isNotEmpty()) ingredients.sumOf { it.calories } else calories

    val totalProtein: Float
        get() = if (ingredients.isNotEmpty()) ingredients.sumOf { it.proteinGrams.toDouble() }.toFloat() else proteinGrams

    val totalCarbs: Float
        get() = if (ingredients.isNotEmpty()) ingredients.sumOf { it.carbsGrams.toDouble() }.toFloat() else carbsGrams

    val totalFat: Float
        get() = if (ingredients.isNotEmpty()) ingredients.sumOf { it.fatGrams.toDouble() }.toFloat() else fatGrams
}

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
    val note: String = "",
    val ingredients: List<MealIngredient> = emptyList()
) {
    val baseCalories: Int
        get() = if (ingredients.isNotEmpty()) ingredients.sumOf { it.calories } else calories

    val baseProtein: Float
        get() = if (ingredients.isNotEmpty()) ingredients.sumOf { it.proteinGrams.toDouble() }.toFloat() else proteinGrams

    val baseCarbs: Float
        get() = if (ingredients.isNotEmpty()) ingredients.sumOf { it.carbsGrams.toDouble() }.toFloat() else carbsGrams

    val baseFat: Float
        get() = if (ingredients.isNotEmpty()) ingredients.sumOf { it.fatGrams.toDouble() }.toFloat() else fatGrams

    val totalCalories: Int get() = (baseCalories * portionMultiplier).toInt()
    val totalProtein: Float get() = baseProtein * portionMultiplier
    val totalCarbs: Float get() = baseCarbs * portionMultiplier
    val totalFat: Float get() = baseFat * portionMultiplier
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
Analyze the image or description provided. Always identify the specific name of the dish or food (mealName) and estimate its portion size or serving description (servingDescription), along with total calories and macronutrients (protein, carbs, fat in grams).
Return STRICT JSON in the following format:
{
  "mealName": "Specific name of the meal or dish (e.g. Silpancho, Overnight Oats con Frutos Rojos, Marraqueta con Queso, etc.)",
  "servingDescription": "Estimated portion or unit (e.g. 1 plato (~400g), 1 taza, 2 unidades, 1 paquete (~60g))",
  "calories": 450,
  "protein": 30.0,
  "carbs": 50.0,
  "fat": 12.0,
  "detectedItems": [
    {"name": "Item name", "portion": "Portion description, e.g. 150g carne"}
  ],
  "notes": "Short helpful context or culinary estimation explanation"
}"""
