package com.mateoviscarra.doitall.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateoviscarra.doitall.data.CategoryExercise
import com.mateoviscarra.doitall.data.LegacyWorkoutPlan
import com.mateoviscarra.doitall.data.NewWorkoutDay
import com.mateoviscarra.doitall.data.NewWorkoutPlan
import com.mateoviscarra.doitall.data.WorkoutPlanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class WorkoutPlanViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: WorkoutPlanRepository = WorkoutPlanRepository(application)
) : AndroidViewModel(application) {

    val plan: StateFlow<NewWorkoutPlan> = repository.plan
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.plan.value)

    val legacyPlan: StateFlow<LegacyWorkoutPlan> = repository.legacyPlan
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.legacyPlan.value)

    fun addCategory(name: String) = repository.addCategory(name)
    fun deleteCategory(categoryId: String) = repository.deleteCategory(categoryId)
    fun addExerciseToCategory(categoryId: String, exercise: CategoryExercise) =
        repository.addExerciseToCategory(categoryId, exercise)
    fun editExerciseInCategory(categoryId: String, exercise: CategoryExercise) =
        repository.editExerciseInCategory(categoryId, exercise)
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

