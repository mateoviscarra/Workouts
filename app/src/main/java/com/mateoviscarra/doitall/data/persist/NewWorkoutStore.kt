package com.mateoviscarra.doitall.data.persist

import android.content.Context
import android.content.SharedPreferences
import com.mateoviscarra.doitall.data.Category
import com.mateoviscarra.doitall.data.CategoryExercise
import com.mateoviscarra.doitall.data.DayExercise
import com.mateoviscarra.doitall.data.KNOWN_CARDIO_IDS
import com.mateoviscarra.doitall.data.NewWorkoutDay
import com.mateoviscarra.doitall.data.NewWorkoutPlan
import org.json.JSONArray
import org.json.JSONObject

class NewWorkoutStore(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_DAYS = "days"
    }
    
    val planFlow: kotlinx.coroutines.flow.Flow<NewWorkoutPlan?>
        get() = kotlinx.coroutines.flow.flow {
            emit(loadPlan())
        }
    
    fun loadPlanOnce(): NewWorkoutPlan? = loadPlan()
    
    private fun loadPlan(): NewWorkoutPlan? {
        val categoriesJson = prefs.getString(KEY_CATEGORIES, null)
        val daysJson = prefs.getString(KEY_DAYS, null)
        
        if (categoriesJson != null && daysJson != null) {
            return try {
                parsePlan(categoriesJson, daysJson)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
    
    suspend fun savePlan(plan: NewWorkoutPlan) {
        prefs.edit()
            .putString(KEY_CATEGORIES, categoriesToJson(plan.categories))
            .putString(KEY_DAYS, daysToJson(plan.days))
            .apply()
    }

    fun exportPlanJson(): JSONObject {
        val root = JSONObject()
        val categoriesJson = prefs.getString(KEY_CATEGORIES, null)
        val daysJson = prefs.getString(KEY_DAYS, null)
        if (categoriesJson != null) root.put("categories", JSONArray(categoriesJson))
        if (daysJson != null) root.put("days", JSONArray(daysJson))
        return root
    }

    fun importPlanJson(obj: JSONObject): Boolean {
        if (obj.has("categories") && obj.has("days")) {
            val categoriesArr = JSONArray(obj.getJSONArray("categories").toString())
            val daysArr = JSONArray(obj.getJSONArray("days").toString())

            // Collect all cardio IDs (known defaults + anything inside standalone "Cardio" day)
            val cardioIds = KNOWN_CARDIO_IDS.toMutableSet()
            var hasStandaloneCardio = false
            for (i in 0 until daysArr.length()) {
                val dayObj = daysArr.optJSONObject(i) ?: continue
                if (dayObj.optString("day", "").trim().equals("Cardio", ignoreCase = true)) {
                    hasStandaloneCardio = true
                    val exArr = dayObj.optJSONArray("exercises") ?: continue
                    for (j in 0 until exArr.length()) {
                        val exObj = exArr.optJSONObject(j) ?: continue
                        exObj.optString("exerciseId").takeIf { it.isNotBlank() }?.let { cardioIds.add(it) }
                    }
                }
            }

            ensureCardioExercisesInCategories(categoriesArr)
            if (!hasStandaloneCardio) {
                daysArr.put(defaultStandaloneCardioDayJson())
            }

            // Strip cardio exercise slots from non-Cardio days and add "cardio" to their muscleGroups
            for (i in 0 until daysArr.length()) {
                val dayObj = daysArr.optJSONObject(i) ?: continue
                val dayName = dayObj.optString("day", "").trim()
                if (dayName.equals("Cardio", ignoreCase = true)) continue

                val exArr = dayObj.optJSONArray("exercises") ?: JSONArray()
                val cleanedExArr = JSONArray()
                var hadCardio = false
                for (j in 0 until exArr.length()) {
                    val exObj = exArr.optJSONObject(j) ?: continue
                    val primary = exObj.optString("exerciseId", "")
                    val altsArr = exObj.optJSONArray("alternatives")
                    val allIds = buildList {
                        if (primary.isNotBlank()) add(primary)
                        if (altsArr != null) {
                            for (k in 0 until altsArr.length()) {
                                altsArr.optString(k).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                    if (allIds.any { it in cardioIds }) {
                        hadCardio = true
                    }
                    val remaining = allIds.filter { it !in cardioIds }
                    if (remaining.isNotEmpty()) {
                        val copyObj = JSONObject(exObj.toString())
                        copyObj.put("exerciseId", remaining.first())
                        if (remaining.size > 1) {
                            copyObj.put("alternatives", JSONArray(remaining.drop(1)))
                        } else {
                            copyObj.remove("alternatives")
                        }
                        cleanedExArr.put(copyObj)
                    }
                }
                dayObj.put("exercises", cleanedExArr)

                val isRecovery = dayName.contains("Recovery", ignoreCase = true)
                if (hadCardio || isRecovery) {
                    val mgKey = if (dayObj.has("muscle_groups") && !dayObj.has("muscleGroups")) "muscle_groups" else "muscleGroups"
                    val mgArr = dayObj.optJSONArray(mgKey) ?: JSONArray().also { dayObj.put(mgKey, it) }
                    val hasCardioTag = (0 until mgArr.length()).any { mgArr.optString(it).equals("cardio", ignoreCase = true) }
                    if (!hasCardioTag) {
                        mgArr.put("cardio")
                    }
                }
            }

            prefs.edit()
                .putString(KEY_CATEGORIES, categoriesArr.toString())
                .putString(KEY_DAYS, daysArr.toString())
                .apply()
            return true
        }
        return false
    }

    private fun ensureCardioExercisesInCategories(categoriesArr: JSONArray) {
        val existingIds = mutableSetOf<String>()
        var cardioCatObj: JSONObject? = null
        for (i in 0 until categoriesArr.length()) {
            val catObj = categoriesArr.optJSONObject(i) ?: continue
            if (catObj.optString("id", "").equals("cardio", ignoreCase = true) ||
                catObj.optString("name", "").equals("Cardio", ignoreCase = true)
            ) {
                cardioCatObj = catObj
            }
            val exArr = catObj.optJSONArray("exercises") ?: continue
            for (j in 0 until exArr.length()) {
                val id = exArr.optJSONObject(j)?.optString("id", "")
                if (!id.isNullOrBlank()) existingIds.add(id)
            }
        }
        if (cardioCatObj == null) {
            cardioCatObj = JSONObject().apply {
                put("id", "cardio")
                put("name", "Cardio")
                put("exercises", JSONArray())
            }
            categoriesArr.put(cardioCatObj)
        }
        val cardioExercises = cardioCatObj.optJSONArray("exercises") ?: JSONArray().also {
            cardioCatObj.put("exercises", it)
        }
        val defaults = listOf(
            Triple("running", "Running", "20min"),
            Triple("cycling", "Cycling", "30min"),
            Triple("incline_walking", "Incline walking", "45min"),
            Triple("walking", "Walking", "30min"),
            Triple("stair_master_", "Stair master", "10 mins")
        )
        for ((id, name, reps) in defaults) {
            if (id !in existingIds) {
                cardioExercises.put(JSONObject().apply {
                    put("id", id)
                    put("name", name)
                    put("defaultSets", 1)
                    put("defaultReps", reps)
                    put("defaultLoad", "")
                    put("isTimed", true)
                    put("defaultTime", reps)
                    put("isBodyweight", false)
                    put("weightUnit", "kg")
                    put("usesStraps", false)
                    put("defaultNotes", if (id == "stair_master_") "Level 5" else "")
                })
            }
        }
    }

    private fun defaultStandaloneCardioDayJson(): JSONObject {
        val exArr = JSONArray()
        listOf("incline_walking", "stair_master_", "running", "cycling", "walking").forEach { id ->
            exArr.put(JSONObject().put("exerciseId", id))
        }
        return JSONObject().apply {
            put("day", "Cardio")
            put("muscleGroups", JSONArray())
            put("isRestDay", false)
            put("notes", JSONArray())
            put("exercises", exArr)
        }
    }
    
    private fun categoriesToJson(categories: List<Category>): String {
        val arr = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            val exArr = JSONArray()
            cat.exercises.forEach { ex ->
                val exObj = JSONObject()
                exObj.put("id", ex.id)
                exObj.put("name", ex.name)
                exObj.put("defaultSets", ex.defaultSets)
                exObj.put("defaultReps", ex.defaultReps)
                exObj.put("defaultLoad", ex.defaultLoad)
                exObj.put("isTimed", ex.isTimed)
                exObj.put("defaultTime", ex.defaultTime)
                exObj.put("isBodyweight", ex.isBodyweight)
                exObj.put("weightUnit", ex.weightUnit)
                exObj.put("usesStraps", ex.usesStraps)
                exObj.put("defaultNotes", ex.defaultNotes)
                exArr.put(exObj)
            }
            obj.put("exercises", exArr)
            arr.put(obj)
        }
        return arr.toString()
    }
    
    private fun daysToJson(days: List<NewWorkoutDay>): String {
        val arr = JSONArray()
        days.forEach { day ->
            val obj = JSONObject()
            obj.put("day", day.day)
            obj.put("muscleGroups", JSONArray(day.muscleGroups))
            obj.put("isRestDay", day.isRestDay)
            obj.put("notes", JSONArray(day.notes))
            val exArr = JSONArray()
            day.exercises.forEach { ex ->
                val exObj = JSONObject()
                exObj.put("exerciseId", ex.exerciseId)
                if (ex.alternatives.isNotEmpty()) {
                    exObj.put("alternatives", JSONArray(ex.alternatives))
                }
                if (ex.supersetId != null) {
                    exObj.put("supersetId", ex.supersetId)
                    exObj.put("isSequential", ex.isSequential)
                }
                exArr.put(exObj)
            }
            obj.put("exercises", exArr)
            arr.put(obj)
        }
        return arr.toString()
    }
    
    private fun parsePlan(categoriesJson: String, daysJson: String): NewWorkoutPlan {
        val categories = parseCategories(JSONArray(categoriesJson))
        val days = parseDays(JSONArray(daysJson))
        return NewWorkoutPlan(categories, days)
    }
    
    private fun parseCategories(arr: JSONArray): List<Category> {
        val list = mutableListOf<Category>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val exArr = obj.getJSONArray("exercises")
            val exercises = parseExercises(exArr)
            list.add(Category(
                id = obj.getString("id"),
                name = obj.getString("name"),
                exercises = exercises
            ))
        }
        return list
    }
    
    private fun parseExercises(arr: JSONArray): List<CategoryExercise> {
        val list = mutableListOf<CategoryExercise>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val repsStr = obj.optString("defaultReps", "10")
            val isTimed = obj.optBoolean("isTimed", false) || repsStr.contains("s", ignoreCase = true) || repsStr.contains("sec", ignoreCase = true)
            val defaultLoad = obj.optString("defaultLoad", "")
            val isBodyweight = obj.optBoolean("isBodyweight", false) || defaultLoad.contains("bodyweight", ignoreCase = true)
            val weightUnit = obj.optString("weightUnit", if (defaultLoad.contains("lbs", ignoreCase = true)) "lbs" else "kg")
            val usesStraps = obj.optBoolean("usesStraps", false)
            val defaultNotes = obj.optString("defaultNotes", "")
            list.add(CategoryExercise(
                id = obj.getString("id"),
                name = obj.getString("name"),
                defaultSets = obj.optInt("defaultSets", 3),
                defaultReps = repsStr,
                defaultLoad = defaultLoad,
                isTimed = isTimed,
                defaultTime = obj.optString("defaultTime", if (isTimed) repsStr else "60s"),
                isBodyweight = isBodyweight,
                weightUnit = weightUnit,
                usesStraps = usesStraps,
                defaultNotes = defaultNotes
            ))
        }
        return list
    }
    
    private fun parseDays(arr: JSONArray): List<NewWorkoutDay> {
        val list = mutableListOf<NewWorkoutDay>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val dayName = obj.getString("day")
            val isRestDay = obj.optBoolean("isRestDay", false)
            if (isRestDay || dayName.equals("Rest", ignoreCase = true)) {
                continue
            }
            val mgArr = obj.optJSONArray("muscleGroups") ?: obj.optJSONArray("muscle_groups")
            val muscleGroups = if (mgArr != null) (0 until mgArr.length()).map { mgArr.getString(it) } else emptyList()
            val notesArr = obj.optJSONArray("notes")
            val notes = if (notesArr != null) (0 until notesArr.length()).map { notesArr.getString(it) } else emptyList()
            val exArr = obj.optJSONArray("exercises") ?: JSONArray()
            val exercises = parseDayExercises(exArr)
            list.add(NewWorkoutDay(
                day = dayName,
                muscleGroups = muscleGroups,
                isRestDay = false,
                notes = notes,
                exercises = exercises
            ))
        }
        return list
    }
    
    private fun parseDayExercises(arr: JSONArray): List<DayExercise> {
        val list = mutableListOf<DayExercise>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val alternatives = if (obj.has("alternatives")) {
                val altArr = obj.getJSONArray("alternatives")
                (0 until altArr.length()).map { altArr.getString(it) }
            } else {
                emptyList()
            }
            val supersetId = obj.optString("supersetId").takeIf { it.isNotEmpty() }
            val isSequential = obj.optBoolean("isSequential", false)
            list.add(DayExercise(
                exerciseId = obj.getString("exerciseId"),
                alternatives = alternatives,
                supersetId = supersetId,
                isSequential = isSequential
            ))
        }
        return list
    }
}