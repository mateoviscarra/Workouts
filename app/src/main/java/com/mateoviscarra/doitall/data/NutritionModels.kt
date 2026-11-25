package com.mateoviscarra.doitall.data

import com.mateoviscarra.doitall.data.persist.WeightUnit
import java.time.LocalDate
import java.time.Period
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

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
        get() = if (ingredients.isNotEmpty()) {
            val sum = ingredients.sumOf { it.calories }
            if (sum > 0) sum else calories
        } else calories

    val totalProtein: Float
        get() = if (ingredients.isNotEmpty()) {
            val sum = ingredients.sumOf { it.proteinGrams.toDouble() }.toFloat()
            if (sum > 0f) sum else proteinGrams
        } else proteinGrams

    val totalCarbs: Float
        get() = if (ingredients.isNotEmpty()) {
            val sum = ingredients.sumOf { it.carbsGrams.toDouble() }.toFloat()
            if (sum > 0f) sum else carbsGrams
        } else carbsGrams

    val totalFat: Float
        get() = if (ingredients.isNotEmpty()) {
            val sum = ingredients.sumOf { it.fatGrams.toDouble() }.toFloat()
            if (sum > 0f) sum else fatGrams
        } else fatGrams
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
        get() = if (ingredients.isNotEmpty()) {
            val sum = ingredients.sumOf { it.calories }
            if (sum > 0) sum else calories
        } else calories

    val baseProtein: Float
        get() = if (ingredients.isNotEmpty()) {
            val sum = ingredients.sumOf { it.proteinGrams.toDouble() }.toFloat()
            if (sum > 0f) sum else proteinGrams
        } else proteinGrams

    val baseCarbs: Float
        get() = if (ingredients.isNotEmpty()) {
            val sum = ingredients.sumOf { it.carbsGrams.toDouble() }.toFloat()
            if (sum > 0f) sum else carbsGrams
        } else carbsGrams

    val baseFat: Float
        get() = if (ingredients.isNotEmpty()) {
            val sum = ingredients.sumOf { it.fatGrams.toDouble() }.toFloat()
            if (sum > 0f) sum else fatGrams
        } else fatGrams

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
    val entries: List<FoodLogEntry>,
    val pillLogs: List<PillLogEntry> = emptyList()
)

/**
 * Scheduled supplement / pill with optional macros, micronutrient overview, and reminder schedule.
 */
data class SupplementPill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosageDescription: String = "",
    val calories: Int = 0,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val micronutrientsSummary: String = "",
    val notes: String = "",
    val scheduledDaysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1 = Mon .. 7 = Sun
    val reminderTimes: List<String> = listOf("09:00"),               // "HH:mm"
    val notificationsEnabled: Boolean = false,
    val createdAtDateIso: String = LocalDate.now().toString()
) {
    fun isScheduledForDate(date: LocalDate): Boolean {
        val days = if (scheduledDaysOfWeek.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else scheduledDaysOfWeek
        return date.dayOfWeek.value in days
    }
}

/**
 * Daily record of a supplement pill taken on a given date.
 */
data class PillLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val pillId: String,
    val dateIso: String,
    val takenTime: String = "",
    val pillName: String,
    val dosageDescription: String = "",
    val calories: Int = 0,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val micronutrientsSummary: String = ""
)

const val DEFAULT_NUTRITION_AI_PROMPT = """You are an expert nutritionist and visual food analyst based in Bolivia.
The user is located in Bolivia, so estimate dishes with local Bolivian cuisine and supermarket products in mind (e.g. Bolivian cuts of meat, marraqueta, sarnita, salteñas, silpancho, pique macho, majadito, sopa de maní, llajwa, local protein bars/alfajores, dairy products, grains like quinoa, etc.) as well as international fitness foods.
Analyze the image or description provided.
Estimate the entire meal as a whole for fast, quick logging: total calories, protein, carbs, and fat in grams. Do NOT break it down into separate individual ingredients or sub-items.
Keep 'mealName' very short and concise (1-3 words max, e.g. 'Ceviche', 'Silpancho', 'Oatmeal', 'Majadito', 'Marraqueta con Queso'). Do NOT output long compound names (e.g. use 'Ceviche' instead of 'Ceviche Boliviano de Trucha con Camote'). If the user note mentions extra ingredients or sides (like camote, avocado, extra cheese), incorporate their macros into the total and mention them in 'servingDescription' (e.g. '1 plato con camote') or 'notes', but keep 'mealName' clean and concise.
Return STRICT JSON in the following format:
{
  "mealName": "Short, concise name of dish (e.g. Ceviche, Silpancho, Oatmeal)",
  "servingDescription": "Estimated portion or unit (e.g. 1 plato (~400g), 1 taza, 2 unidades)",
  "calories": 450,
  "protein": 30.0,
  "carbs": 50.0,
  "fat": 12.0,
  "notes": "Short helpful context or culinary estimation explanation"
}"""

/**
 * User physical profile and fitness context for AI coaching and calorie/macro calculations.
 */
data class UserProfile(
    val heightCm: Float = 0f,
    val sex: String = "",                 // e.g. "Male", "Female", "Other"
    val birthDateIso: String = "",        // YYYY-MM-DD
    val targetWeightKg: Float = 0f,
    val fitnessGoalSummary: String = ""   // Free-form goals, split info, preferences
) {
    fun calculateAge(today: LocalDate = LocalDate.now()): Int? {
        if (birthDateIso.isBlank()) return null
        return try {
            val birthDate = LocalDate.parse(birthDateIso.trim())
            Period.between(birthDate, today).years.takeIf { it in 1..120 }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Optional physical hand dimensions used by Gemini Vision to gauge plate/dish sizes when a hand is in frame.
 */
data class HandScaleReference(
    val enabled: Boolean = false,
    val handLengthCm: Float = 0f,       // Wrist crease to middle fingertip
    val palmLengthCm: Float = 0f,       // Wrist crease to base of middle finger
    val palmWidthCm: Float = 0f,        // Across the 4 knuckles
    val middleFingerCm: Float = 0f,     // Palmar crease to tip
    val handSpanCm: Float = 0f,         // Open hand thumb tip to pinky tip
    val customNotes: String = ""        // Optional extra notes (e.g. fist width, pose)
) {
    fun hasAnyMeasurements(): Boolean =
        handLengthCm > 0f || palmLengthCm > 0f || palmWidthCm > 0f ||
            middleFingerCm > 0f || handSpanCm > 0f || customNotes.isNotBlank()

    fun buildPromptInstruction(): String {
        if (!enabled || !hasAnyMeasurements()) return ""
        return buildString {
            appendLine("OPTIONAL HAND SCALE REFERENCE:")
            appendLine("If (and ONLY if) a human hand is visibly present near the dish, plate, or food in any photo, assume it is the user's hand with these exact measurements and use it as a physical scale reference to estimate plate diameter, food dimensions, and portion weight/volume (accounting for camera perspective):")
            if (handLengthCm > 0f) appendLine("- Total hand length (wrist crease to middle fingertip): ${formatCm(handLengthCm)} cm")
            if (palmLengthCm > 0f) appendLine("- Palm length (wrist crease to base of middle finger): ${formatCm(palmLengthCm)} cm")
            if (palmWidthCm > 0f) appendLine("- Palm width (across knuckles): ${formatCm(palmWidthCm)} cm")
            if (middleFingerCm > 0f) appendLine("- Middle finger length (palm crease to tip): ${formatCm(middleFingerCm)} cm")
            if (handSpanCm > 0f) appendLine("- Open hand span (thumb tip to pinky tip): ${formatCm(handSpanCm)} cm")
            if (customNotes.isNotBlank()) appendLine("- Additional reference notes: ${customNotes.trim()}")
            append("If NO human hand is visibly present in the photo(s), completely ignore this scale reference and estimate portions normally.")
        }
    }

    companion object {
        private fun roundOneDecimal(value: Float): Float = (value * 10f).roundToInt() / 10f

        fun formatCm(value: Float): String {
            val rounded = roundOneDecimal(value)
            return if (rounded == rounded.toInt().toFloat()) {
                rounded.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", rounded)
            }
        }

        fun estimateFromHeight(heightCm: Float, sex: String = "", enabled: Boolean = false): HandScaleReference {
            val effectiveHeight = when {
                heightCm in 120f..230f -> heightCm
                sex.equals("Female", ignoreCase = true) -> 163f
                sex.equals("Male", ignoreCase = true) -> 175f
                else -> 170f
            }
            val handLength = roundOneDecimal(effectiveHeight * 0.105f)
            val palmLength = roundOneDecimal(handLength * 0.57f)
            val palmWidth = roundOneDecimal(handLength * 0.48f)
            val middleFinger = roundOneDecimal(handLength * 0.43f)
            val handSpan = roundOneDecimal(handLength * 1.08f)
            return HandScaleReference(
                enabled = enabled,
                handLengthCm = handLength,
                palmLengthCm = palmLength,
                palmWidthCm = palmWidth,
                middleFingerCm = middleFinger,
                handSpanCm = handSpan,
                customNotes = "Estimated from height (${formatCm(effectiveHeight)} cm) — measure your hand to refine"
            )
        }
    }
}
