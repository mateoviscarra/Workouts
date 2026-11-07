package com.mateoviscarra.doitall.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateoviscarra.doitall.data.WorkoutDay
import com.mateoviscarra.doitall.data.WorkoutPlan
import com.mateoviscarra.doitall.data.persist.ExerciseLogState
import com.mateoviscarra.doitall.data.persist.PersistRootV2
import com.mateoviscarra.doitall.data.persist.ResolvedDayUi
import com.mateoviscarra.doitall.data.persist.WorkoutStateStore
import com.mateoviscarra.doitall.data.persist.defaultDayLog
import com.mateoviscarra.doitall.data.persist.mergedExerciseLogs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkoutDetailViewModel @JvmOverloads constructor(
    application: Application,
    private val store: WorkoutStateStore = WorkoutStateStore(application)
) : AndroidViewModel(application) {

    private val _resolvedDayUi = MutableStateFlow<ResolvedDayUi?>(null)
    val resolvedDayUi: StateFlow<ResolvedDayUi?> = _resolvedDayUi.asStateFlow()

    private var currentDayKey: String? = null
    private var currentWorkoutDay: WorkoutDay? = null
    private var currentWorkoutPlan: WorkoutPlan? = null

    fun loadDay(dayKey: String, workoutDay: WorkoutDay, workoutPlan: WorkoutPlan) {
        if (currentDayKey == dayKey && currentWorkoutDay == workoutDay && currentWorkoutPlan == workoutPlan && _resolvedDayUi.value != null) {
            return
        }
        currentDayKey = dayKey
        currentWorkoutDay = workoutDay
        currentWorkoutPlan = workoutPlan

        if (_resolvedDayUi.value == null) {
            _resolvedDayUi.value = ResolvedDayUi(
                dayLog = defaultDayLog(workoutDay),
                exerciseLogs = mergedExerciseLogs(workoutPlan, PersistRootV2(emptyMap(), emptyMap()))
            )
        }

        viewModelScope.launch {
            store.resolvedDayFlow(dayKey, workoutDay, workoutPlan).collectLatest { resolved ->
                _resolvedDayUi.value = resolved
            }
        }
    }

    fun toggleExerciseDone(exerciseId: String) {
        val dayKey = currentDayKey ?: return
        val day = currentWorkoutDay ?: return
        viewModelScope.launch {
            store.toggleExerciseDone(dayKey, day, exerciseId)
        }
    }

    fun uncheckAllForDay() {
        val dayKey = currentDayKey ?: return
        val day = currentWorkoutDay ?: return
        viewModelScope.launch {
            store.uncheckAllForDay(dayKey, day)
        }
    }

    fun updateSelectedPage(slotIndex: Int, page: Int) {
        val dayKey = currentDayKey ?: return
        val day = currentWorkoutDay ?: return
        viewModelScope.launch {
            store.updateSelectedPage(dayKey, day, slotIndex, page)
        }
    }

    fun updateExerciseLog(exerciseId: String, log: ExerciseLogState) {
        viewModelScope.launch {
            store.updateExerciseLog(exerciseId, log)
        }
    }

    fun updateExerciseLogAndBinding(slotIndex: Int, pageIndex: Int, exerciseId: String, log: ExerciseLogState) {
        val dayKey = currentDayKey ?: return
        val day = currentWorkoutDay ?: return
        viewModelScope.launch {
            store.updateExerciseLogAndBinding(dayKey, day, slotIndex, pageIndex, exerciseId, log)
        }
    }
}

