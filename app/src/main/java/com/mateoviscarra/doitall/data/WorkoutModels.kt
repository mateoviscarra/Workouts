package com.mateoviscarra.doitall.data

/**
 * Exercise defined in a category (default template)
 */
data class CategoryExercise(
    val id: String,
    val name: String,
    val defaultSets: Int,
    val defaultReps: String,
    val defaultLoad: String,
    val categories: List<String> = listOf(),
    val isTimed: Boolean = false,
    val defaultTime: String = "60s",
    val isBodyweight: Boolean = false,
    val weightUnit: String = "kg",
    val usesStraps: Boolean = false,
    val defaultNotes: String = ""
)

/**
 * Category containing a library of exercises
 */
data class Category(
    val id: String,
    val name: String,
    val exercises: List<CategoryExercise>
)

/**
 * An exercise assigned to a day with optional alternatives (new format)
 */
data class DayExercise(
    val exerciseId: String,
    val alternatives: List<String> = emptyList(),
    val supersetId: String? = null,
    val isSequential: Boolean = false
)

/**
 * A workout day with flexible exercise assignments (new format)
 */
data class NewWorkoutDay(
    val day: String,
    val muscleGroups: List<String>,
    val isRestDay: Boolean = false,
    val notes: List<String> = emptyList(),
    val exercises: List<DayExercise>
)

/**
 * Complete workout plan with categories and day assignments (new format)
 */
data class NewWorkoutPlan(
    val categories: List<Category>,
    val days: List<NewWorkoutDay>
) {
    fun allExercises(): List<CategoryExercise> = categories.flatMap { it.exercises }
    fun findExercise(id: String): CategoryExercise? = allExercises().find { it.id == id }
    fun categoriesForExercise(exerciseId: String): List<Category> = 
        categories.filter { cat -> cat.exercises.any { it.id == exerciseId } }
}

/**
 * Canonical exercise (derived from category exercise for logging)
 */
data class ExerciseDefinition(
    val id: String,
    val name: String,
    val sets: String,
    val reps: String,
    val load: String,
    val duration: String? = null,
    val intensity: String? = null,
    val isTimed: Boolean = false,
    val defaultTime: String = "60s",
    val isBodyweight: Boolean = false,
    val weightUnit: String = "kg",
    val usesStraps: Boolean = false,
    val defaultNotes: String = ""
)

/**
 * One row in a day: carousel order is [exerciseIds] (main first, then alternatives).
 */
data class WorkoutSlot(
    val exerciseIds: List<String>,
    val supersetId: String? = null,
    val isSequential: Boolean = false
) {
    init {
        require(exerciseIds.isNotEmpty()) { "Slot must have at least one exercise id" }
    }

    val mainExerciseId: String get() = exerciseIds.first()
    fun optionIds(): List<String> = exerciseIds
}

/**
 * Workout day (legacy format for UI compatibility)
 */
data class LegacyWorkoutDay(
    val day: String,
    val muscleGroups: List<String>,
    val isRestDay: Boolean = false,
    val notes: List<String> = emptyList(),
    val slots: List<WorkoutSlot>
)

/**
 * Workout plan (legacy format for UI compatibility)
 */
data class LegacyWorkoutPlan(
    val catalog: Map<String, ExerciseDefinition>,
    val schedule: List<LegacyWorkoutDay>
) {
    fun exerciseLocationsById(): Map<String, List<ExerciseLocation>> {
        val acc = mutableMapOf<String, MutableList<ExerciseLocation>>()
        for (day in schedule) {
            day.slots.forEachIndexed { slotIndex, slot ->
                slot.exerciseIds.distinct().forEach { id ->
                    acc.getOrPut(id) { mutableListOf() }
                        .add(ExerciseLocation(dayKey = day.day, slotIndex = slotIndex))
                }
            }
        }
        return acc.mapValues { (_, list) -> list.distinct() }
    }
}

/**
 * Cleanly formats weight with bodyweight awareness and proper handling for negative weights (assisted exercises).
 * E.g.:
 *  isBodyweight=true, weight="-40", unit="kg" → "Bodyweight - 40 kg" (or "BW - 40 kg" if shortBwPrefix=true)
 *  isBodyweight=true, weight="15", unit="kg"  → "Bodyweight + 15 kg"
 *  isBodyweight=true, weight="", unit="kg"    → "Bodyweight" (or "BW")
 *  isBodyweight=false, weight="-40", unit="kg" → "-40 kg"
 *  isBodyweight=false, weight="100", unit="kg" → "100 kg"
 */
fun formatExerciseWeight(
    weight: String,
    isBodyweight: Boolean,
    unitLabel: String = "",
    shortBwPrefix: Boolean = false
): String {
    val trimmed = weight.trim()
    val bwPrefix = if (shortBwPrefix) "BW" else "Bodyweight"

    if (isBodyweight) {
        if (trimmed.isEmpty() || trimmed.equals("bodyweight", ignoreCase = true)) {
            return bwPrefix
        }

        // Clean out any existing "bodyweight", "bodyweight +", or "bodyweight -" prefix
        val cleanWeight = trimmed
            .replace(Regex("""(?i)^bodyweight\s*([+\-])?\s*"""), "")
            .replace(Regex("""(?i)^bw\s*([+\-])?\s*"""), "")
            .trim()

        if (cleanWeight.isEmpty()) return bwPrefix

        // Check if numeric part is zero (e.g. "0", "0.0", "0 kg", "+0", "-0")
        val unsignedWeight = cleanWeight.removePrefix("+").removePrefix("-").trim()
        val numericOnly = unsignedWeight.replace(Regex("""(?i)(kg|kgs|lbs?|lb)"""), "").trim()
        val isZero = numericOnly == "0" || numericOnly == "0.0" || (numericOnly.toDoubleOrNull() ?: -1.0) == 0.0
        if (isZero) return bwPrefix

        // Determine if it is negative (e.g. "-40", "- 40", or negative numeric value)
        val isNegative = cleanWeight.startsWith("-") ||
                trimmed.contains(Regex("""(?i)bodyweight\s*-\s*""")) ||
                (cleanWeight.toDoubleOrNull() ?: 0.0) < 0

        val absWeight = unsignedWeight
        val hasUnitInWeight = absWeight.contains(Regex("""(?i)(kg|kgs|lbs?|lb)"""))
        val unitSuffix = if (!hasUnitInWeight && unitLabel.isNotBlank()) " $unitLabel" else ""

        return if (isNegative) {
            "$bwPrefix - $absWeight$unitSuffix"
        } else {
            "$bwPrefix + $absWeight$unitSuffix"
        }
    } else {
        if (trimmed.isEmpty()) return ""
        val hasUnitInWeight = trimmed.contains(Regex("""(?i)(kg|kgs|lbs?|lb)"""))
        val unitSuffix = if (!hasUnitInWeight && unitLabel.isNotBlank()) " $unitLabel" else ""
        return "$trimmed$unitSuffix"
    }
}

data class ExerciseLocation(
    val dayKey: String,
    val slotIndex: Int
)

// Type aliases for backward compatibility with existing UI code
typealias WorkoutDay = LegacyWorkoutDay
typealias WorkoutPlan = LegacyWorkoutPlan