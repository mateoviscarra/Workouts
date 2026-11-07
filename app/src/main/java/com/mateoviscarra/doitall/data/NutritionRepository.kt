package com.mateoviscarra.doitall.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mateoviscarra.doitall.data.persist.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val TAG = "NutritionRepository"
private const val PREFS_NAME = "nutrition_prefs"
private const val KEY_SAVED_MEALS = "saved_meals_json"
private const val KEY_FOOD_LOGS = "food_logs_json"
private const val KEY_WEIGHT_LOGS = "weight_logs_json"
private const val KEY_NUTRITION_GOALS = "nutrition_goals_json"
private const val KEY_AI_PROMPT = "custom_ai_prompt"
private const val KEY_GEMINI_API_KEY = "gemini_api_key"

class NutritionRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _goals = MutableStateFlow(NutritionGoals())
    val goals: StateFlow<NutritionGoals> = _goals.asStateFlow()

    private val _savedMeals = MutableStateFlow<List<SavedMeal>>(emptyList())
    val savedMeals: StateFlow<List<SavedMeal>> = _savedMeals.asStateFlow()

    private val _foodLogs = MutableStateFlow<List<FoodLogEntry>>(emptyList())
    val foodLogs: StateFlow<List<FoodLogEntry>> = _foodLogs.asStateFlow()

    private val _weightLogs = MutableStateFlow<List<WeightLogEntry>>(emptyList())
    val weightLogs: StateFlow<List<WeightLogEntry>> = _weightLogs.asStateFlow()

    private val _aiPrompt = MutableStateFlow(DEFAULT_NUTRITION_AI_PROMPT)
    val aiPrompt: StateFlow<String> = _aiPrompt.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        // Goals
        val goalsJson = prefs.getString(KEY_NUTRITION_GOALS, null)
        if (!goalsJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(goalsJson)
                _goals.value = NutritionGoals(
                    calorieTarget = obj.optInt("calorieTarget", 2400),
                    proteinTargetGrams = obj.optDouble("proteinTargetGrams", 180.0).toFloat(),
                    carbsTargetGrams = obj.optDouble("carbsTargetGrams", 250.0).toFloat(),
                    fatTargetGrams = obj.optDouble("fatTargetGrams", 70.0).toFloat()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading nutrition goals", e)
            }
        }

        // Saved Meals / Staples
        val mealsJson = prefs.getString(KEY_SAVED_MEALS, null)
        if (!mealsJson.isNullOrBlank()) {
            try {
                _savedMeals.value = parseSavedMeals(mealsJson)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved meals", e)
            }
        } else {
            // Seed initial staples placeholder for user
            val initial = listOf(
                SavedMeal(
                    id = "staple_overnight_oats",
                    name = "Overnight Oats",
                    calories = 450,
                    proteinGrams = 30f,
                    carbsGrams = 60f,
                    fatGrams = 10f,
                    servingDescription = "1 standard jar",
                    isPinnedStaple = true,
                    defaultMealSlot = MealSlot.BREAKFAST
                ),
                SavedMeal(
                    id = "staple_protein_alfajor",
                    name = "Protein Alfajor",
                    calories = 220,
                    proteinGrams = 18f,
                    carbsGrams = 22f,
                    fatGrams = 7f,
                    servingDescription = "1 unit",
                    isPinnedStaple = true,
                    defaultMealSlot = MealSlot.SNACKS
                )
            )
            _savedMeals.value = initial
            saveSavedMealsToDisk(initial)
        }

        // Food Logs
        val logsJson = prefs.getString(KEY_FOOD_LOGS, null)
        if (!logsJson.isNullOrBlank()) {
            try {
                _foodLogs.value = parseFoodLogs(logsJson)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading food logs", e)
            }
        }

        // Weight Logs
        val weightsJson = prefs.getString(KEY_WEIGHT_LOGS, null)
        if (!weightsJson.isNullOrBlank()) {
            try {
                _weightLogs.value = parseWeightLogs(weightsJson)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading weight logs", e)
            }
        }

        // AI Prompt & API Key
        val savedPrompt = prefs.getString(KEY_AI_PROMPT, null)
        _aiPrompt.value = if (savedPrompt.isNullOrBlank() || !savedPrompt.contains("mealName")) {
            prefs.edit().putString(KEY_AI_PROMPT, DEFAULT_NUTRITION_AI_PROMPT).apply()
            DEFAULT_NUTRITION_AI_PROMPT
        } else {
            savedPrompt
        }
        _geminiApiKey.value = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    // --- Goals ---
    fun updateGoals(newGoals: NutritionGoals) {
        _goals.value = newGoals
        scope.launch {
            val obj = JSONObject().apply {
                put("calorieTarget", newGoals.calorieTarget)
                put("proteinTargetGrams", newGoals.proteinTargetGrams.toDouble())
                put("carbsTargetGrams", newGoals.carbsTargetGrams.toDouble())
                put("fatTargetGrams", newGoals.fatTargetGrams.toDouble())
            }
            prefs.edit().putString(KEY_NUTRITION_GOALS, obj.toString()).apply()
        }
    }

    // --- Saved Meals / Staples ---
    fun saveMeal(meal: SavedMeal) {
        val current = _savedMeals.value.toMutableList()
        val index = current.indexOfFirst { it.id == meal.id }
        if (index >= 0) {
            current[index] = meal
        } else {
            current.add(0, meal)
        }
        _savedMeals.value = current
        saveSavedMealsToDisk(current)
    }

    fun deleteSavedMeal(mealId: String) {
        val updated = _savedMeals.value.filter { it.id != mealId }
        _savedMeals.value = updated
        saveSavedMealsToDisk(updated)
    }

    fun togglePinnedStaple(mealId: String) {
        val current = _savedMeals.value.toMutableList()
        val index = current.indexOfFirst { it.id == mealId }
        if (index >= 0) {
            val m = current[index]
            current[index] = m.copy(isPinnedStaple = !m.isPinnedStaple)
            _savedMeals.value = current
            saveSavedMealsToDisk(current)
        }
    }

    private fun saveSavedMealsToDisk(list: List<SavedMeal>) {
        scope.launch {
            val arr = JSONArray()
            list.forEach { m ->
                val obj = JSONObject().apply {
                    put("id", m.id)
                    put("name", m.name)
                    put("calories", m.calories)
                    put("proteinGrams", m.proteinGrams.toDouble())
                    put("carbsGrams", m.carbsGrams.toDouble())
                    put("fatGrams", m.fatGrams.toDouble())
                    put("servingDescription", m.servingDescription)
                    put("isPinnedStaple", m.isPinnedStaple)
                    put("defaultMealSlot", m.defaultMealSlot.name)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_SAVED_MEALS, arr.toString()).apply()
        }
    }

    private fun parseSavedMeals(jsonStr: String): List<SavedMeal> {
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SavedMeal>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val slotStr = obj.optString("defaultMealSlot", MealSlot.BREAKFAST.name)
            val slot = try { MealSlot.valueOf(slotStr) } catch (_: Exception) { MealSlot.BREAKFAST }
            list.add(
                SavedMeal(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.getString("name"),
                    calories = obj.getInt("calories"),
                    proteinGrams = obj.optDouble("proteinGrams", 0.0).toFloat(),
                    carbsGrams = obj.optDouble("carbsGrams", 0.0).toFloat(),
                    fatGrams = obj.optDouble("fatGrams", 0.0).toFloat(),
                    servingDescription = obj.optString("servingDescription", ""),
                    isPinnedStaple = obj.optBoolean("isPinnedStaple", true),
                    defaultMealSlot = slot
                )
            )
        }
        return list
    }

    // --- Food Logs ---
    fun addFoodLogEntry(entry: FoodLogEntry): FoodLogEntry {
        val current = _foodLogs.value.toMutableList()
        current.add(0, entry)
        _foodLogs.value = current
        saveFoodLogsToDisk(current)
        return entry
    }

    fun updateFoodLogEntry(entry: FoodLogEntry) {
        val current = _foodLogs.value.toMutableList()
        val index = current.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            current[index] = entry
            _foodLogs.value = current
            saveFoodLogsToDisk(current)
        }
    }

    fun deleteFoodLogEntry(entryId: String) {
        val updated = _foodLogs.value.filter { it.id != entryId }
        _foodLogs.value = updated
        saveFoodLogsToDisk(updated)
    }

    fun getLogsForDate(dateIso: String): List<FoodLogEntry> {
        return _foodLogs.value.filter { it.dateIso == dateIso }
    }

    private fun saveFoodLogsToDisk(list: List<FoodLogEntry>) {
        scope.launch {
            val arr = JSONArray()
            list.forEach { e ->
                val obj = JSONObject().apply {
                    put("id", e.id)
                    put("dateIso", e.dateIso)
                    put("time", e.time)
                    put("mealSlot", e.mealSlot.name)
                    put("name", e.name)
                    put("calories", e.calories)
                    put("proteinGrams", e.proteinGrams.toDouble())
                    put("carbsGrams", e.carbsGrams.toDouble())
                    put("fatGrams", e.fatGrams.toDouble())
                    put("portionMultiplier", e.portionMultiplier.toDouble())
                    put("source", e.source.name)
                    put("note", e.note)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_FOOD_LOGS, arr.toString()).apply()
        }
    }

    private fun parseFoodLogs(jsonStr: String): List<FoodLogEntry> {
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<FoodLogEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val slotStr = obj.optString("mealSlot", MealSlot.BREAKFAST.name)
            val slot = try { MealSlot.valueOf(slotStr) } catch (_: Exception) { MealSlot.BREAKFAST }
            val sourceStr = obj.optString("source", EntrySource.MANUAL.name)
            val source = try { EntrySource.valueOf(sourceStr) } catch (_: Exception) { EntrySource.MANUAL }
            list.add(
                FoodLogEntry(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    dateIso = obj.getString("dateIso"),
                    time = obj.optString("time", ""),
                    mealSlot = slot,
                    name = obj.getString("name"),
                    calories = obj.getInt("calories"),
                    proteinGrams = obj.optDouble("proteinGrams", 0.0).toFloat(),
                    carbsGrams = obj.optDouble("carbsGrams", 0.0).toFloat(),
                    fatGrams = obj.optDouble("fatGrams", 0.0).toFloat(),
                    portionMultiplier = obj.optDouble("portionMultiplier", 1.0).toFloat(),
                    source = source,
                    note = obj.optString("note", "")
                )
            )
        }
        return list
    }

    // --- AI Configuration ---
    fun updateAiPrompt(prompt: String) {
        _aiPrompt.value = prompt
        prefs.edit().putString(KEY_AI_PROMPT, prompt).apply()
    }

    fun resetAiPromptToDefault() {
        updateAiPrompt(DEFAULT_NUTRITION_AI_PROMPT)
    }

    fun updateGeminiApiKey(key: String) {
        val trimmed = key.trim()
        _geminiApiKey.value = trimmed
        prefs.edit().putString(KEY_GEMINI_API_KEY, trimmed).apply()
    }

    // Direct raw serialization for export/backup
    fun exportNutritionJson(): JSONObject {
        val root = JSONObject()
        
        // Goals
        val g = _goals.value
        root.put("goals", JSONObject().apply {
            put("calorieTarget", g.calorieTarget)
            put("proteinTargetGrams", g.proteinTargetGrams.toDouble())
            put("carbsTargetGrams", g.carbsTargetGrams.toDouble())
            put("fatTargetGrams", g.fatTargetGrams.toDouble())
        })

        // Saved meals
        val mealsArr = JSONArray()
        _savedMeals.value.forEach { m ->
            mealsArr.put(JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("calories", m.calories)
                put("proteinGrams", m.proteinGrams.toDouble())
                put("carbsGrams", m.carbsGrams.toDouble())
                put("fatGrams", m.fatGrams.toDouble())
                put("servingDescription", m.servingDescription)
                put("isPinnedStaple", m.isPinnedStaple)
                put("defaultMealSlot", m.defaultMealSlot.name)
            })
        }
        root.put("savedMeals", mealsArr)

        // Food logs
        val logsArr = JSONArray()
        _foodLogs.value.forEach { e ->
            logsArr.put(JSONObject().apply {
                put("id", e.id)
                put("dateIso", e.dateIso)
                put("time", e.time)
                put("mealSlot", e.mealSlot.name)
                put("name", e.name)
                put("calories", e.calories)
                put("proteinGrams", e.proteinGrams.toDouble())
                put("carbsGrams", e.carbsGrams.toDouble())
                put("fatGrams", e.fatGrams.toDouble())
                put("portionMultiplier", e.portionMultiplier.toDouble())
                put("source", e.source.name)
                put("note", e.note)
            })
        }
        root.put("foodLogs", logsArr)

        // Weight logs
        val weightsArr = JSONArray()
        _weightLogs.value.forEach { w ->
            weightsArr.put(JSONObject().apply {
                put("id", w.id)
                put("dateIso", w.dateIso)
                put("weight", w.weight.toDouble())
                put("unit", w.unit.name)
                put("note", w.note)
            })
        }
        root.put("weightLogs", weightsArr)

        return root
    }

    fun importNutritionJson(obj: JSONObject) {
        if (obj.has("goals")) {
            val gObj = obj.getJSONObject("goals")
            updateGoals(
                NutritionGoals(
                    calorieTarget = gObj.optInt("calorieTarget", 2400),
                    proteinTargetGrams = gObj.optDouble("proteinTargetGrams", 180.0).toFloat(),
                    carbsTargetGrams = gObj.optDouble("carbsTargetGrams", 250.0).toFloat(),
                    fatTargetGrams = gObj.optDouble("fatTargetGrams", 70.0).toFloat()
                )
            )
        }
        if (obj.has("savedMeals")) {
            val meals = parseSavedMeals(obj.getJSONArray("savedMeals").toString())
            _savedMeals.value = meals
            saveSavedMealsToDisk(meals)
        }
        if (obj.has("foodLogs")) {
            val logs = parseFoodLogs(obj.getJSONArray("foodLogs").toString())
            _foodLogs.value = logs
            saveFoodLogsToDisk(logs)
        }
        if (obj.has("weightLogs")) {
            val weights = parseWeightLogs(obj.getJSONArray("weightLogs").toString())
            _weightLogs.value = weights
            saveWeightLogsToDisk(weights)
        }
    }

    // --- Weight Logging ---
    fun logWeight(dateIso: String, weight: Float, unit: WeightUnit = WeightUnit.KG, note: String = ""): WeightLogEntry {
        val current = _weightLogs.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.dateIso == dateIso }
        val entry = if (existingIndex >= 0) {
            val existing = current[existingIndex]
            val updated = existing.copy(weight = weight, unit = unit, note = note)
            current[existingIndex] = updated
            updated
        } else {
            val newEntry = WeightLogEntry(
                id = UUID.randomUUID().toString(),
                dateIso = dateIso,
                weight = weight,
                unit = unit,
                note = note
            )
            current.add(newEntry)
            newEntry
        }
        current.sortByDescending { it.dateIso }
        _weightLogs.value = current
        saveWeightLogsToDisk(current)
        return entry
    }

    fun deleteWeightLog(id: String) {
        val updated = _weightLogs.value.filter { it.id != id }
        _weightLogs.value = updated
        saveWeightLogsToDisk(updated)
    }

    fun getWeightForDate(dateIso: String): WeightLogEntry? {
        return _weightLogs.value.firstOrNull { it.dateIso == dateIso }
    }

    private fun saveWeightLogsToDisk(list: List<WeightLogEntry>) {
        scope.launch {
            val arr = JSONArray()
            list.forEach { w ->
                val obj = JSONObject().apply {
                    put("id", w.id)
                    put("dateIso", w.dateIso)
                    put("weight", w.weight.toDouble())
                    put("unit", w.unit.name)
                    put("note", w.note)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_WEIGHT_LOGS, arr.toString()).apply()
        }
    }

    private fun parseWeightLogs(jsonStr: String): List<WeightLogEntry> {
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<WeightLogEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val unitStr = obj.optString("unit", WeightUnit.KG.name)
            val unit = try { WeightUnit.valueOf(unitStr) } catch (_: Exception) { WeightUnit.KG }
            list.add(
                WeightLogEntry(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    dateIso = obj.getString("dateIso"),
                    weight = obj.getDouble("weight").toFloat(),
                    unit = unit,
                    note = obj.optString("note", "")
                )
            )
        }
        return list.sortedByDescending { it.dateIso }
    }
}
