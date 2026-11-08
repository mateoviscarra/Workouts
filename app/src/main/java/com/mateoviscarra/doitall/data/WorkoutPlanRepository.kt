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
            val finalPlan = migrateAbsAndPlankIfNeeded(withIsoPlan)

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

    private fun updateAndPersist(newPlan: NewWorkoutPlan) {
        _plan.value = newPlan
        _legacyPlan.value = WorkoutRepository.convertToLegacyFormat(newPlan)
        scope.launch {
            try {
                store.savePlan(newPlan)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist workout plan", e)
            }
        }
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
