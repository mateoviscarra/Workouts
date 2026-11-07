package com.mateoviscarra.doitall.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateoviscarra.doitall.data.DayNutritionTotals
import com.mateoviscarra.doitall.data.EntrySource
import com.mateoviscarra.doitall.data.FoodLogEntry
import com.mateoviscarra.doitall.data.MealSlot
import com.mateoviscarra.doitall.data.NutritionGoals
import com.mateoviscarra.doitall.data.NutritionRepository
import com.mateoviscarra.doitall.data.SavedMeal
import com.mateoviscarra.doitall.data.WeightLogEntry
import com.mateoviscarra.doitall.data.persist.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class NutritionViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: NutritionRepository = NutritionRepository(application)
) : AndroidViewModel(application) {

    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val goals: StateFlow<NutritionGoals> = repository.goals
    val savedMeals: StateFlow<List<SavedMeal>> = repository.savedMeals
    val allFoodLogs: StateFlow<List<FoodLogEntry>> = repository.foodLogs
    val allWeightLogs: StateFlow<List<WeightLogEntry>> = repository.weightLogs
    val aiPrompt: StateFlow<String> = repository.aiPrompt
    val geminiApiKey: StateFlow<String> = repository.geminiApiKey

    // Weight for currently selected date
    val currentDayWeight: StateFlow<WeightLogEntry?> = combine(
        allWeightLogs,
        selectedDate
    ) { logs, dateIso ->
        logs.firstOrNull { it.dateIso == dateIso }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Latest weight overall
    val latestWeight: StateFlow<WeightLogEntry?> = allWeightLogs.map { logs ->
        logs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 7-day moving average weight
    val sevenDayAverageWeight: StateFlow<Float?> = allWeightLogs.map { logs ->
        if (logs.isEmpty()) null
        else {
            val recent = logs.take(7)
            recent.map { it.weight }.average().toFloat()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Weekly delta: difference between latest 7-day average and previous 7 days (or previous logged weight)
    val weeklyWeightDelta: StateFlow<Float?> = allWeightLogs.map { logs ->
        if (logs.size < 2) null
        else {
            val first7 = logs.take(7)
            val next7 = logs.drop(7).take(7)
            if (next7.isNotEmpty()) {
                val avg1 = first7.map { it.weight }.average().toFloat()
                val avg2 = next7.map { it.weight }.average().toFloat()
                avg1 - avg2
            } else {
                // If fewer than 14 entries, compare latest vs oldest available
                logs.first().weight - logs.last().weight
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Filtered logs for currently selected date
    val currentDayLogs: StateFlow<List<FoodLogEntry>> = combine(
        allFoodLogs,
        selectedDate
    ) { logs, dateIso ->
        logs.filter { it.dateIso == dateIso }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Daily totals for currently selected date
    val currentDayTotals: StateFlow<DayNutritionTotals> = combine(
        currentDayLogs,
        selectedDate
    ) { logs, dateIso ->
        DayNutritionTotals(
            dateIso = dateIso,
            totalCalories = logs.sumOf { it.totalCalories },
            totalProtein = logs.sumOf { it.totalProtein.toDouble() }.toFloat(),
            totalCarbs = logs.sumOf { it.totalCarbs.toDouble() }.toFloat(),
            totalFat = logs.sumOf { it.totalFat.toDouble() }.toFloat(),
            entries = logs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DayNutritionTotals(LocalDate.now().toString(), 0, 0f, 0f, 0f, emptyList())
    )

    // --- Date Navigation ---
    fun selectDate(dateIso: String) {
        _selectedDate.value = dateIso
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now().toString()
    }

    fun previousDay() {
        val current = runCatching { LocalDate.parse(_selectedDate.value) }.getOrDefault(LocalDate.now())
        _selectedDate.value = current.minusDays(1).toString()
    }

    fun nextDay() {
        val current = runCatching { LocalDate.parse(_selectedDate.value) }.getOrDefault(LocalDate.now())
        _selectedDate.value = current.plusDays(1).toString()
    }

    // --- Food Logging ---
    fun logStaple(staple: SavedMeal, mealSlot: MealSlot = staple.defaultMealSlot) {
        val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val entry = FoodLogEntry(
            dateIso = _selectedDate.value,
            time = nowTime,
            mealSlot = mealSlot,
            name = staple.name,
            calories = staple.calories,
            proteinGrams = staple.proteinGrams,
            carbsGrams = staple.carbsGrams,
            fatGrams = staple.fatGrams,
            portionMultiplier = 1.0f,
            source = EntrySource.STAPLE,
            note = staple.servingDescription
        )
        repository.addFoodLogEntry(entry)
    }

    fun addManualEntry(
        name: String,
        calories: Int,
        proteinGrams: Float,
        carbsGrams: Float,
        fatGrams: Float,
        mealSlot: MealSlot,
        note: String = "",
        source: EntrySource = EntrySource.MANUAL
    ) {
        val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val entry = FoodLogEntry(
            dateIso = _selectedDate.value,
            time = nowTime,
            mealSlot = mealSlot,
            name = name,
            calories = calories,
            proteinGrams = proteinGrams,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams,
            portionMultiplier = 1.0f,
            source = source,
            note = note
        )
        repository.addFoodLogEntry(entry)
    }

    fun updateEntry(entry: FoodLogEntry) {
        repository.updateFoodLogEntry(entry)
    }

    fun deleteEntry(entryId: String) {
        repository.deleteFoodLogEntry(entryId)
    }

    // --- Saved Meals / Staples ---
    fun saveMeal(meal: SavedMeal) {
        repository.saveMeal(meal)
    }

    fun deleteSavedMeal(mealId: String) {
        repository.deleteSavedMeal(mealId)
    }

    fun togglePinnedStaple(mealId: String) {
        repository.togglePinnedStaple(mealId)
    }

    // --- Weight Logging ---
    fun logWeight(weight: Float, unit: WeightUnit = WeightUnit.KG, dateIso: String = _selectedDate.value, note: String = "") {
        repository.logWeight(dateIso = dateIso, weight = weight, unit = unit, note = note)
    }

    fun deleteWeight(id: String) {
        repository.deleteWeightLog(id)
    }

    // --- Goals & Settings ---
    fun updateGoals(goals: NutritionGoals) {
        repository.updateGoals(goals)
    }

    fun updateAiPrompt(prompt: String) {
        repository.updateAiPrompt(prompt)
    }

    fun resetAiPromptToDefault() {
        repository.resetAiPromptToDefault()
    }

    fun updateGeminiApiKey(key: String) {
        repository.updateGeminiApiKey(key)
    }
}
