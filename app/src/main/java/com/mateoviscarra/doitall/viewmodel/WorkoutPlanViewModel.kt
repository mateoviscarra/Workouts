package com.mateoviscarra.doitall.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateoviscarra.doitall.data.CategoryExercise
import com.mateoviscarra.doitall.data.LegacyWorkoutPlan
import com.mateoviscarra.doitall.data.NewWorkoutDay
import com.mateoviscarra.doitall.data.NewWorkoutPlan
import com.mateoviscarra.doitall.data.WorkoutPlanRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class WorkoutPlanViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: WorkoutPlanRepository = WorkoutPlanRepository(application)
) : AndroidViewModel(application) {

    private val stateStore = com.mateoviscarra.doitall.data.persist.WorkoutStateStore(application)

    val plan: StateFlow<NewWorkoutPlan> = repository.plan
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.plan.value)

    val legacyPlan: StateFlow<LegacyWorkoutPlan> = repository.legacyPlan
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.legacyPlan.value)

    fun addCategory(name: String) = repository.addCategory(name)
    fun deleteCategory(categoryId: String) = repository.deleteCategory(categoryId)
    fun addExerciseToCategory(categoryId: String, exercise: CategoryExercise) =
        repository.addExerciseToCategory(categoryId, exercise)
    fun editExerciseInCategory(categoryId: String, exercise: CategoryExercise) {
        repository.editExerciseInCategory(categoryId, exercise)
        viewModelScope.launch {
            val root = stateStore.loadPersistRoot()
            val existing = root.logs[exercise.id]
            if (existing != null) {
                val updated = existing.copy(
                    isTimed = exercise.isTimed,
                    timeTarget = if (exercise.isTimed && (existing.timeTarget.isBlank() || existing.timeTarget == "60s")) {
                        exercise.defaultTime.ifEmpty { "60s" }
                    } else existing.timeTarget,
                    weight = if (exercise.isTimed && (existing.weight.equals("sec", ignoreCase = true) || existing.weight.equals("bodyweight", ignoreCase = true))) "" else existing.weight,
                    isBodyweight = exercise.isBodyweight || existing.isBodyweight
                )
                stateStore.updateExerciseLog(exercise.id, updated)
            }
        }
    }
    fun deleteExerciseFromCategory(categoryId: String, exerciseId: String) =
        repository.deleteExerciseFromCategory(categoryId, exerciseId)

    fun addDay(name: String) = repository.addDay(name)
    fun addDay(day: NewWorkoutDay) = repository.addDay(day)
    fun deleteDay(dayName: String) = repository.deleteDay(dayName)
    fun updateDay(day: NewWorkoutDay) = repository.updateDay(day)
    fun updateDay(originalName: String, day: NewWorkoutDay) = repository.updateDay(originalName, day)
    fun savePlan(newPlan: NewWorkoutPlan) = repository.savePlan(newPlan)
    fun reloadPlan() = repository.reloadPlan()
}

