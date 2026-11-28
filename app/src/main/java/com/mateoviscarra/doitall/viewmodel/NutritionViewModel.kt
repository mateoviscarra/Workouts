package com.mateoviscarra.doitall.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateoviscarra.doitall.data.DayNutritionTotals
import com.mateoviscarra.doitall.data.EntrySource
import com.mateoviscarra.doitall.data.FoodLogEntry
import com.mateoviscarra.doitall.data.HandScaleReference
import com.mateoviscarra.doitall.data.MealSlot
import com.mateoviscarra.doitall.data.NutritionGoals
import com.mateoviscarra.doitall.data.NutritionRepository
import com.mateoviscarra.doitall.data.PillLogEntry
import com.mateoviscarra.doitall.data.SavedMeal
import com.mateoviscarra.doitall.data.SupplementPill
import com.mateoviscarra.doitall.data.WeightLogEntry
import com.mateoviscarra.doitall.data.persist.WeightUnit
import com.mateoviscarra.doitall.network.FoodScanResult
import com.mateoviscarra.doitall.network.GeminiVisionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class AiScanUiState(
    val showSheet: Boolean = false,
    val selectedUris: List<Uri> = emptyList(),
    val userNote: String = "",
    val useHandScale: Boolean = true,
    val isAnalyzing: Boolean = false,
    val scannedResult: FoodScanResult? = null,
    val errorMessage: String? = null,
    val cameraTempUri: Uri? = null
)

data class MenuCoachUiState(
    val showSheet: Boolean = false,
    val selectedUris: List<Uri> = emptyList(),
    val userText: String = "",
    val isAnalyzing: Boolean = false,
    val adviceResult: String? = null,
    val errorMessage: String? = null,
    val cameraTempUri: Uri? = null
)

class NutritionViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: NutritionRepository = NutritionRepository(application)
) : AndroidViewModel(application) {

    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val goals: StateFlow<NutritionGoals> = repository.goals
    val userProfile: StateFlow<com.mateoviscarra.doitall.data.UserProfile> = repository.userProfile
    val savedMeals: StateFlow<List<SavedMeal>> = repository.savedMeals
    val allFoodLogs: StateFlow<List<FoodLogEntry>> = repository.foodLogs
    val allWeightLogs: StateFlow<List<WeightLogEntry>> = repository.weightLogs
    val supplements: StateFlow<List<SupplementPill>> = repository.supplements
    val allPillLogs: StateFlow<List<PillLogEntry>> = repository.pillLogs
    val aiPrompt: StateFlow<String> = repository.aiPrompt
    val handScaleRef: StateFlow<HandScaleReference> = repository.handScaleRef
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

    val currentDayPillLogs: StateFlow<List<PillLogEntry>> = combine(
        allPillLogs,
        selectedDate
    ) { logs, dateIso ->
        logs.filter { it.dateIso == dateIso }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Daily totals for currently selected date (food + taken pills with calories/macros)
    val currentDayTotals: StateFlow<DayNutritionTotals> = combine(
        currentDayLogs,
        currentDayPillLogs,
        selectedDate
    ) { logs, pLogs, dateIso ->
        DayNutritionTotals(
            dateIso = dateIso,
            totalCalories = logs.sumOf { it.totalCalories } + pLogs.sumOf { it.calories },
            totalProtein = (logs.sumOf { it.totalProtein.toDouble() } + pLogs.sumOf { it.proteinGrams.toDouble() }).toFloat(),
            totalCarbs = (logs.sumOf { it.totalCarbs.toDouble() } + pLogs.sumOf { it.carbsGrams.toDouble() }).toFloat(),
            totalFat = (logs.sumOf { it.totalFat.toDouble() } + pLogs.sumOf { it.fatGrams.toDouble() }).toFloat(),
            entries = logs,
            pillLogs = pLogs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DayNutritionTotals(LocalDate.now().toString(), 0, 0f, 0f, 0f, emptyList(), emptyList())
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
            calories = staple.totalCalories,
            proteinGrams = staple.totalProtein,
            carbsGrams = staple.totalCarbs,
            fatGrams = staple.totalFat,
            portionMultiplier = 1.0f,
            source = EntrySource.STAPLE,
            note = staple.servingDescription,
            ingredients = staple.ingredients
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
        source: EntrySource = EntrySource.MANUAL,
        ingredients: List<com.mateoviscarra.doitall.data.MealIngredient> = emptyList()
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
            note = note,
            ingredients = ingredients
        )
        repository.addFoodLogEntry(entry)
    }

    fun updateEntry(entry: FoodLogEntry) {
        repository.updateFoodLogEntry(entry)
    }

    fun moveEntrySlot(entryId: String, newSlot: MealSlot) {
        val entry = allFoodLogs.value.firstOrNull { it.id == entryId } ?: return
        if (entry.mealSlot != newSlot) {
            repository.updateFoodLogEntry(entry.copy(mealSlot = newSlot))
        }
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

    // --- Pills & Supplements ---
    fun saveSupplement(pill: SupplementPill) {
        repository.saveSupplement(pill)
    }

    fun deleteSupplement(pillId: String) {
        repository.deleteSupplement(pillId)
    }

    fun togglePillTaken(pill: SupplementPill, dateIso: String = _selectedDate.value) {
        repository.togglePillTaken(pill, dateIso)
    }

    fun getMissedDatesForPill(pill: SupplementPill, lookbackDays: Int = 14): List<LocalDate> {
        val today = LocalDate.now()
        val createdDate = runCatching { LocalDate.parse(pill.createdAtDateIso) }.getOrDefault(today)
        val earliest = today.minusDays(lookbackDays.toLong())
        val startDate = if (createdDate.isAfter(earliest)) createdDate else earliest
        if (!startDate.isBefore(today)) return emptyList()

        val takenDates = allPillLogs.value
            .asSequence()
            .filter { it.pillId == pill.id }
            .map { it.dateIso }
            .toSet()

        val missed = mutableListOf<LocalDate>()
        var d = startDate
        while (d.isBefore(today)) {
            if (pill.isScheduledForDate(d) && d.toString() !in takenDates) {
                missed.add(d)
            }
            d = d.plusDays(1)
        }
        return missed.sortedDescending()
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

    fun updateHandScaleReference(ref: HandScaleReference) {
        repository.updateHandScaleReference(ref)
    }

    fun updateGeminiApiKey(key: String) {
        repository.updateGeminiApiKey(key)
    }

    // --- AI Nutrition Scanner & Menu Coach (retained across rotation) ---
    private val visionClient by lazy { GeminiVisionClient(getApplication()) }

    private val _aiScanState = MutableStateFlow(AiScanUiState())
    val aiScanState: StateFlow<AiScanUiState> = _aiScanState.asStateFlow()

    private val _menuCoachState = MutableStateFlow(MenuCoachUiState())
    val menuCoachState: StateFlow<MenuCoachUiState> = _menuCoachState.asStateFlow()

    fun openScanSheet() {
        _aiScanState.update {
            it.copy(showSheet = true, useHandScale = handScaleRef.value.enabled)
        }
    }

    fun dismissScanSheet() {
        _aiScanState.update { it.copy(showSheet = false) }
    }

    fun setScanCameraTempUri(uri: Uri?) {
        _aiScanState.update { it.copy(cameraTempUri = uri) }
    }

    fun onScanCameraResult(success: Boolean) {
        val uri = _aiScanState.value.cameraTempUri
        if (success && uri != null) {
            _aiScanState.update {
                it.copy(
                    selectedUris = it.selectedUris + uri,
                    showSheet = true,
                    cameraTempUri = null
                )
            }
        }
    }

    fun onScanGalleryResult(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            _aiScanState.update {
                it.copy(
                    selectedUris = (it.selectedUris + uris).distinct(),
                    showSheet = true
                )
            }
        }
    }

    fun removeScanUri(uri: Uri) {
        _aiScanState.update { it.copy(selectedUris = it.selectedUris.filterNot { item -> item == uri }) }
    }

    fun clearScanUris() {
        _aiScanState.update { it.copy(selectedUris = emptyList()) }
    }

    fun updateScanUserNote(note: String) {
        _aiScanState.update { it.copy(userNote = note) }
    }

    fun toggleScanHandScale() {
        _aiScanState.update { it.copy(useHandScale = !it.useHandScale) }
    }

    fun dismissScannedResult() {
        _aiScanState.update { it.copy(scannedResult = null) }
    }

    fun dismissScanError() {
        _aiScanState.update {
            val shouldReopen = it.selectedUris.isNotEmpty() || it.userNote.isNotBlank()
            it.copy(errorMessage = null, showSheet = if (shouldReopen) true else it.showSheet)
        }
    }

    fun runAiAnalysis() {
        val state = _aiScanState.value
        if (state.isAnalyzing || (state.selectedUris.isEmpty() && state.userNote.isBlank())) return
        _aiScanState.update { it.copy(showSheet = false, isAnalyzing = true, errorMessage = null) }

        val currentHandRef = handScaleRef.value
        val handInstruction = if (state.useHandScale && state.selectedUris.isNotEmpty() && currentHandRef.hasAnyMeasurements()) {
            currentHandRef.copy(enabled = true).buildPromptInstruction()
        } else {
            ""
        }

        viewModelScope.launch {
            val result = visionClient.analyzeFoodImages(
                imageUris = state.selectedUris,
                apiKey = geminiApiKey.value,
                systemPrompt = aiPrompt.value,
                userNote = state.userNote,
                handScaleInstruction = handInstruction
            )
            if (result.isSuccess) {
                _aiScanState.update {
                    it.copy(
                        isAnalyzing = false,
                        scannedResult = result.getOrNull(),
                        selectedUris = emptyList(),
                        userNote = ""
                    )
                }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                _aiScanState.update { it.copy(isAnalyzing = false, errorMessage = msg) }
            }
        }
    }

    fun openMenuCoachSheet() {
        _menuCoachState.update { it.copy(showSheet = true) }
    }

    fun dismissMenuCoachSheet() {
        _menuCoachState.update { it.copy(showSheet = false) }
    }

    fun setMenuCameraTempUri(uri: Uri?) {
        _menuCoachState.update { it.copy(cameraTempUri = uri) }
    }

    fun onMenuCameraResult(success: Boolean) {
        val uri = _menuCoachState.value.cameraTempUri
        if (success && uri != null) {
            _menuCoachState.update {
                it.copy(
                    selectedUris = it.selectedUris + uri,
                    showSheet = true,
                    cameraTempUri = null
                )
            }
        }
    }

    fun onMenuGalleryResult(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            _menuCoachState.update {
                it.copy(
                    selectedUris = (it.selectedUris + uris).distinct(),
                    showSheet = true
                )
            }
        }
    }

    fun removeMenuUri(uri: Uri) {
        _menuCoachState.update { it.copy(selectedUris = it.selectedUris.filterNot { item -> item == uri }) }
    }

    fun clearMenuUris() {
        _menuCoachState.update { it.copy(selectedUris = emptyList()) }
    }

    fun updateMenuUserText(text: String) {
        _menuCoachState.update { it.copy(userText = text) }
    }

    fun dismissMenuAdviceResult() {
        _menuCoachState.update { it.copy(adviceResult = null) }
    }

    fun dismissMenuError() {
        _menuCoachState.update {
            val shouldReopen = it.selectedUris.isNotEmpty() || it.userText.isNotBlank()
            it.copy(errorMessage = null, showSheet = if (shouldReopen) true else it.showSheet)
        }
    }

    fun runMenuAdviceAnalysis() {
        val state = _menuCoachState.value
        if (state.isAnalyzing || (state.selectedUris.isEmpty() && state.userText.isBlank())) return
        _menuCoachState.update { it.copy(showSheet = false, isAnalyzing = true, errorMessage = null) }

        val remainingCal = (goals.value.calorieTarget - currentDayTotals.value.totalCalories).coerceAtLeast(0)
        val remainingProt = (goals.value.proteinTargetGrams - currentDayTotals.value.totalProtein).coerceAtLeast(0f)

        viewModelScope.launch {
            val result = visionClient.askMenuAdvice(
                imageUris = state.selectedUris,
                menuText = state.userText,
                userProfile = userProfile.value,
                goals = goals.value,
                remainingCalories = remainingCal,
                remainingProtein = remainingProt,
                apiKey = geminiApiKey.value
            )
            if (result.isSuccess) {
                _menuCoachState.update {
                    it.copy(
                        isAnalyzing = false,
                        adviceResult = result.getOrNull(),
                        selectedUris = emptyList(),
                        userText = ""
                    )
                }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                _menuCoachState.update { it.copy(isAnalyzing = false, errorMessage = msg) }
            }
        }
    }
}
