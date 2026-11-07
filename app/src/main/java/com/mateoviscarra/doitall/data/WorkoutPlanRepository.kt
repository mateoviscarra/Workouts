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
            val finalPlan = if (!hasIso) {
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
