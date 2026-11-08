package com.mateoviscarra.doitall.data

import com.mateoviscarra.doitall.data.persist.WeightUnit

data class CompletedExerciseRecord(
    val exerciseId: String,
    val exerciseName: String,
    val sets: Int,
    val repsSummary: String,
    val weight: String,
    val numericWeight: Double = 0.0,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val isBodyweight: Boolean = false,
    val usesStraps: Boolean = false,
    val comment: String = "",
    val isPR: Boolean = false,
    val isCardio: Boolean = false
)

data class WorkoutSessionRecord(
    val id: String,
    val dayTitle: String,
    val dateIso: String, // e.g. "2026-10-28"
    val timestamp: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 60,
    val exercises: List<CompletedExerciseRecord>,
    val notes: String = ""
) {
    val totalVolume: Double get() = exercises.sumOf { ex ->
        if (!ex.isCardio && ex.numericWeight > 0) {
            val repsParts = ex.repsSummary.split(",")
            val repsCount = repsParts.mapNotNull { it.trim().toDoubleOrNull() }
            val repsAvg = if (repsCount.isNotEmpty()) repsCount.average() else 10.0
            ex.sets * repsAvg * ex.numericWeight
        } else 0.0
    }
}

data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val maxWeight: Double,
    val reps: Int,
    val weightUnit: WeightUnit,
    val dateIso: String,
    val isBodyweight: Boolean = false,
    val note: String = ""
)

