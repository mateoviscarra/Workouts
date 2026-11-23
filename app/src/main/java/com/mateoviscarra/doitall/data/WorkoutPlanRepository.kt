package com.mateoviscarra.doitall.data

import android.content.Context
import android.util.Log
import com.mateoviscarra.doitall.data.persist.NewWorkoutStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "WorkoutPlanRepository"

val KNOWN_CARDIO_IDS = setOf(
    "incline_walking",
    "stair_master_",
    "stair_master",
    "running",
    "cycling",
    "walking",
    "elliptical",
    "rowing",
    "stationary_bike",
    "treadmill"
)

class WorkoutPlanRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val store = NewWorkoutStore(context.applicationContext)

    private val _plan = MutableStateFlow(loadInitialPlan())
    val plan: StateFlow<NewWorkoutPlan> = _plan.asStateFlow()

    private val _legacyPlan = MutableStateFlow(WorkoutRepository.convertToLegacyFormat(_plan.value))
    val legacyPlan: StateFlow<LegacyWorkoutPlan> = _legacyPlan.asStateFlow()

    private fun loadInitialPlan(): NewWorkoutPlan {
        return try {
            val rawPlan = store.loadPlanOnce() ?: WorkoutRepository.loadWorkoutPlan(context)
            val filteredDays = rawPlan.days.filter { !it.isRestDay && !it.day.equals("Rest", ignoreCase = true) }
            val cleanPlan = rawPlan.copy(days = filteredDays)

            val hasIso = cleanPlan.allExercises().any { it.id == "isometric_pull_ups" || it.id == "isometric_pull_up_holds" }
            val withIsoPlan = if (!hasIso) {
                val isoExercise = CategoryExercise(
                    id = "isometric_pull_up_holds",
                    name = "Isometric Pull-Up Holds",
                    defaultSets = 3,
                    defaultReps = "30s",
                    defaultLoad = "Bodyweight",
                    isTimed = true,
                    defaultTime = "30s"
                )
                cleanPlan.copy(
                    categories = cleanPlan.categories.map { cat ->
                        if (cat.id == "pull") cat.copy(exercises = cat.exercises + isoExercise) else cat
                    }
                )
            } else {
                cleanPlan
            }

            // Organize Abs routine into 4 movement slots with alternatives if it still has flat unorganized slots
            val absMigratedPlan = migrateAbsAndPlankIfNeeded(withIsoPlan)
            // Separate cardio into its own category and strip cardio exercise slots out of recovery/non-cardio days
            val finalPlan = migrateCardioAndRecoveryIfNeeded(absMigratedPlan)

            if (finalPlan != rawPlan) {
                scope.launch {
                    try {
                        store.savePlan(finalPlan)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist cleaned plan", e)
                    }
                }
            }
            finalPlan
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load saved plan, using default seed", e)
            WorkoutRepository.loadWorkoutPlan(context)
        }
    }

    private fun migrateAbsAndPlankIfNeeded(plan: NewWorkoutPlan): NewWorkoutPlan {
        // 1. Clean up plank defaultLoad if it was set to "sec"
        val updatedCategories = plan.categories.map { cat ->
            cat.copy(
                exercises = cat.exercises.map { ex ->
                    if (ex.id == "plank" && ex.defaultLoad.equals("sec", ignoreCase = true)) {
                        ex.copy(defaultLoad = "Bodyweight", isBodyweight = true)
                    } else {
                        ex
                    }
                }
            )
        }

        // 2. Organize Abs day into 4 structured movement slots with alternatives if still flat
        val allCatalogIds = updatedCategories.flatMap { it.exercises }.map { it.id }.toSet()
        val updatedDays = plan.days.map { day ->
            if (day.day.equals("Abs", ignoreCase = true) &&
                day.exercises.size > 4 &&
                day.exercises.all { it.alternatives.isEmpty() }
            ) {
                val existingIdsInAbs = day.exercises.map { it.exerciseId }.toSet()
                val desiredSlots = listOf(
                    "ab_machine" to listOf("cable_crunch", "crunches"),
                    "hanging_leg_raise" to listOf("reverse_crunches", "dragon_flags"),
                    "torso_rotation_machine" to listOf("bicycle_crunches"),
                    "plank" to listOf("ab_wheel")
                )

                val usedIds = mutableSetOf<String>()
                val organizedSlots = desiredSlots.mapNotNull { (primary, alts) ->
                    val availableGroup = (listOf(primary) + alts).filter { it in existingIdsInAbs || it in allCatalogIds }
                    if (availableGroup.isNotEmpty()) {
                        usedIds.addAll(availableGroup)
                        DayExercise(
                            exerciseId = availableGroup.first(),
                            alternatives = availableGroup.drop(1)
                        )
                    } else null
                }.toMutableList()

                // Preserve any extra custom exercises the user might have added to Abs
                for (ex in day.exercises) {
                    if (ex.exerciseId !in usedIds) {
                        organizedSlots.add(ex)
                    }
                }

                day.copy(exercises = organizedSlots)
            } else {
                day
            }
        }

        return plan.copy(categories = updatedCategories, days = updatedDays)
    }

    private fun migrateCardioAndRecoveryIfNeeded(plan: NewWorkoutPlan): NewWorkoutPlan {
        val cardioDay = plan.days.find { it.day.trim().equals("Cardio", ignoreCase = true) }
        val existingCardioCategory = plan.categories.find {
            it.id.equals("cardio", ignoreCase = true) || it.name.equals("Cardio", ignoreCase = true)
        }

        val cardioIds = buildSet {
            addAll(KNOWN_CARDIO_IDS)
            cardioDay?.exercises?.forEach { ex ->
                add(ex.exerciseId)
                addAll(ex.alternatives)
            }
            existingCardioCategory?.exercises?.forEach { ex ->
                add(ex.id)
            }
        }

        // Gather all cardio exercises from any category, clean up bodyweight/min fields, and move to "cardio" category
        val collectedCardioExercises = linkedMapOf<String, CategoryExercise>()
        existingCardioCategory?.exercises?.forEach { ex ->
            collectedCardioExercises[ex.id] = ex.copy(
                isBodyweight = false,
                defaultLoad = "",
                categories = listOf("cardio")
            )
        }
        plan.categories.forEach { cat ->
            cat.exercises.forEach { ex ->
                if (ex.id in cardioIds) {
                    collectedCardioExercises.putIfAbsent(
                        ex.id,
                        ex.copy(
                            isBodyweight = false,
                            defaultLoad = "",
                            categories = listOf("cardio")
                        )
                    )
                }
            }
        }

        val cleanedCategories = plan.categories.mapNotNull { cat ->
            if (cat.id.equals("cardio", ignoreCase = true) || cat.name.equals("Cardio", ignoreCase = true)) {
                null // Replaced below with consolidated Cardio category
            } else {
                cat.copy(exercises = cat.exercises.filter { it.id !in cardioIds })
            }
        } + Category(
            id = "cardio",
            name = "Cardio",
            exercises = collectedCardioExercises.values.toList()
        )

        // Ensure non-Cardio days don't contain cardio exercise slots (they use the embedded Cardio widget instead)
        var hasStandaloneCardioDay = false
        val cleanedDays = plan.days.map { day ->
            if (day.day.trim().equals("Cardio", ignoreCase = true)) {
                hasStandaloneCardioDay = true
                val existingIds = day.exercises.map { it.exerciseId }.toSet()
                val extraCardio = collectedCardioExercises.keys
                    .filter { it !in existingIds }
                    .map { DayExercise(exerciseId = it) }
                if (extraCardio.isNotEmpty()) {
                    day.copy(exercises = day.exercises + extraCardio)
                } else {
                    day
                }
            } else {
                var hadCardioSlot = false
                val filteredExercises = day.exercises.mapNotNull { ex ->
                    val allIds = listOf(ex.exerciseId) + ex.alternatives
                    if (allIds.any { it in cardioIds }) {
                        hadCardioSlot = true
                    }
                    val remaining = allIds.filter { it !in cardioIds }
                    if (remaining.isEmpty()) {
                        null
                    } else {
                        ex.copy(
                            exerciseId = remaining.first(),
                            alternatives = remaining.drop(1)
                        )
                    }
                }
                val updatedMuscles = if (hadCardioSlot &&
                    day.muscleGroups.none { it.equals("cardio", ignoreCase = true) }
                ) {
                    day.muscleGroups + "cardio"
                } else {
                    day.muscleGroups
                }
                if (filteredExercises != day.exercises || updatedMuscles != day.muscleGroups) {
                    day.copy(muscleGroups = updatedMuscles, exercises = filteredExercises)
                } else {
                    day
                }
            }
        }

        val finalDays = if (!hasStandaloneCardioDay) {
            cleanedDays + NewWorkoutDay(
                day = "Cardio",
                muscleGroups = emptyList(),
                isRestDay = false,
                notes = emptyList(),
                exercises = collectedCardioExercises.keys.map { DayExercise(exerciseId = it) }
            )
        } else {
            cleanedDays
        }

        return plan.copy(categories = cleanedCategories, days = finalDays)
    }

    private fun updateAndPersist(newPlan: NewWorkoutPlan) {
        _plan.value = newPlan
        _legacyPlan.value = WorkoutRepository.convertToLegacyFormat(newPlan)
        scope.launch {
            try {
                store.savePlan(newPlan)
                DriveSyncManager.scheduleAutoSync(context, "Workout plan updated")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist workout plan", e)
            }
        }
    }

    fun moveDay(fromIndex: Int, toIndex: Int) {
        val currentDays = _plan.value.days.toMutableList()
        if (fromIndex !in currentDays.indices || toIndex !in currentDays.indices || fromIndex == toIndex) return
        val moved = currentDays.removeAt(fromIndex)
        currentDays.add(toIndex, moved)
        updateAndPersist(_plan.value.copy(days = currentDays))
    }

    fun savePlan(newPlan: NewWorkoutPlan) {
        updateAndPersist(newPlan)
    }

    fun addCategory(name: String) {
        val newCategory = Category(
            id = name.lowercase().replace(" ", "_"),
            name = name,
            exercises = emptyList()
        )
        val updated = _plan.value.copy(
            categories = _plan.value.categories + newCategory
        )
        updateAndPersist(updated)
    }

    fun deleteCategory(categoryId: String) {
        val updated = _plan.value.copy(
            categories = _plan.value.categories.filter { it.id != categoryId }
        )
        updateAndPersist(updated)
    }

    fun addExerciseToCategory(categoryId: String, exercise: CategoryExercise) {
        val updated = _plan.value.copy(
            categories = _plan.value.categories.map { cat ->
                if (cat.id == categoryId) {
                    cat.copy(exercises = cat.exercises + exercise)
                } else {
                    cat
                }
            }
        )
        updateAndPersist(updated)
    }

    fun editExerciseInCategory(categoryId: String, exercise: CategoryExercise) {
        val updated = _plan.value.copy(
            categories = _plan.value.categories.map { cat ->
                if (cat.id == categoryId) {
                    cat.copy(exercises = cat.exercises.map { if (it.id == exercise.id) exercise else it })
                } else {
                    cat
                }
            }
        )
        updateAndPersist(updated)
    }

    fun deleteExerciseFromCategory(categoryId: String, exerciseId: String) {
        val updated = _plan.value.copy(
            categories = _plan.value.categories.map { cat ->
                if (cat.id == categoryId) {
                    cat.copy(exercises = cat.exercises.filter { it.id != exerciseId })
                } else {
                    cat
                }
            },
            days = _plan.value.days.map { day ->
                day.copy(
                    exercises = day.exercises.filter { it.exerciseId != exerciseId }
                        .map { ex ->
                            if (exerciseId in ex.alternatives) {
                                ex.copy(alternatives = ex.alternatives.filter { it != exerciseId })
                            } else {
                                ex
                            }
                        }
                )
            }
        )
        updateAndPersist(updated)
    }

    fun addDay(day: NewWorkoutDay) {
        val updated = _plan.value.copy(
            days = _plan.value.days + day
        )
        updateAndPersist(updated)
    }

    fun addDay(name: String) {
        val newDay = NewWorkoutDay(
            day = name,
            muscleGroups = listOf(name),
            isRestDay = false,
            notes = emptyList(),
            exercises = emptyList()
        )
        addDay(newDay)
    }

    fun deleteDay(dayName: String) {
        val updated = _plan.value.copy(
            days = _plan.value.days.filter { it.day != dayName }
        )
        updateAndPersist(updated)
    }

    fun updateDay(originalName: String, updatedDay: NewWorkoutDay) {
        val updated = _plan.value.copy(
            days = _plan.value.days.map { day ->
                if (day.day == originalName) updatedDay else day
            }
        )
        updateAndPersist(updated)
    }

    fun updateDay(updatedDay: NewWorkoutDay) {
        updateDay(updatedDay.day, updatedDay)
    }

    fun reloadPlan() {
        val loaded = loadInitialPlan()
        _plan.value = loaded
        _legacyPlan.value = WorkoutRepository.convertToLegacyFormat(loaded)
    }
}
