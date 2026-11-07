package com.mateoviscarra.doitall.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object WorkoutRepository {

    fun loadWorkoutPlan(context: Context): NewWorkoutPlan {
        val jsonText = context.assets.open("workout_plan.json")
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(jsonText)
        
        // Parse categories
        val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
        val categories = buildList {
            for (i in 0 until categoriesArray.length()) {
                val catObj = categoriesArray.optJSONObject(i) ?: continue
                val category = parseCategory(catObj)
                add(category)
            }
        }
        
        // Parse days
        val daysArray = root.optJSONArray("days") ?: JSONArray()
        val days = buildList {
            for (i in 0 until daysArray.length()) {
                val dayObj = daysArray.optJSONObject(i) ?: continue
                val day = parseNewDay(dayObj)
                if (day != null) {
                    add(day)
                }
            }
        }
        
        return NewWorkoutPlan(categories = categories, days = days)
    }
    
    private fun parseCategory(obj: JSONObject): Category {
        val id = obj.optString("id", "")
        val name = obj.optString("name", "Unknown")
        val exercisesArray = obj.optJSONArray("exercises") ?: JSONArray()
        
        val exercises = buildList {
            for (i in 0 until exercisesArray.length()) {
                val exObj = exercisesArray.optJSONObject(i) ?: continue
                val exercise = parseCategoryExercise(exObj, id)
                add(exercise)
            }
        }
        
        return Category(id = id, name = name, exercises = exercises)
    }
    
    private fun parseCategoryExercise(obj: JSONObject, categoryId: String): CategoryExercise {
        val repsStr = obj.optString("defaultReps", "10")
        val isTimed = obj.optBoolean("isTimed", false) || repsStr.contains("s", ignoreCase = true) || repsStr.contains("sec", ignoreCase = true)
        val defaultTime = obj.optString("defaultTime", if (isTimed) repsStr else "60s")
        val defaultLoad = obj.optString("defaultLoad", "")
        val isBodyweight = obj.optBoolean("isBodyweight", false) || defaultLoad.contains("bodyweight", ignoreCase = true)
        val weightUnit = obj.optString("weightUnit", if (defaultLoad.contains("lbs", ignoreCase = true)) "lbs" else "kg")
        val usesStraps = obj.optBoolean("usesStraps", false)
        val defaultNotes = obj.optString("defaultNotes", "")
        return CategoryExercise(
            id = obj.optString("id", ""),
            name = obj.optString("name", "Unknown"),
            defaultSets = obj.optInt("defaultSets", 3),
            defaultReps = repsStr,
            defaultLoad = defaultLoad,
            categories = listOf(categoryId),
            isTimed = isTimed,
            defaultTime = defaultTime,
            isBodyweight = isBodyweight,
            weightUnit = weightUnit,
            usesStraps = usesStraps,
            defaultNotes = defaultNotes
        )
    }
    
    private fun parseNewDay(obj: JSONObject): NewWorkoutDay? {
        val day = obj.optString("day", "Unknown Day")
        val isRestDay = obj.optBoolean("is_rest_day", false)
        if (isRestDay || day.equals("Rest", ignoreCase = true)) {
            return null
        }
        val muscleGroups = jsonStringArray(obj.optJSONArray("muscle_groups"))
        val notes = jsonStringArray(obj.optJSONArray("notes"))
        
        val exercisesArray = obj.optJSONArray("exercises") ?: JSONArray()
        val exercises = buildList {
            for (i in 0 until exercisesArray.length()) {
                val exObj = exercisesArray.optJSONObject(i) ?: continue
                val dayExercise = parseDayExercise(exObj)
                add(dayExercise)
            }
        }
        
        return NewWorkoutDay(
            day = day,
            muscleGroups = muscleGroups,
            isRestDay = isRestDay,
            notes = notes,
            exercises = exercises
        )
    }
    
    private fun parseDayExercise(obj: JSONObject): DayExercise {
        val exerciseId = obj.optString("exerciseId", "")
        val alternativesArray = obj.optJSONArray("alternatives")
        val alternatives = if (alternativesArray != null) {
            jsonStringArray(alternativesArray)
        } else {
            emptyList()
        }
        val supersetId = obj.optString("supersetId").takeIf { it.isNotEmpty() }
        val isSequential = obj.optBoolean("isSequential", false)
        
        return DayExercise(
            exerciseId = exerciseId,
            alternatives = alternatives,
            supersetId = supersetId,
            isSequential = isSequential
        )
    }

    private fun jsonStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(array.optString(i))
            }
        }
    }
    
    // Convert new format to legacy format for UI compatibility
    fun convertToLegacyFormat(plan: NewWorkoutPlan): LegacyWorkoutPlan {
        val days = plan.days.map { day ->
            val slots = day.exercises.map { dayEx ->
                val allIds = listOf(dayEx.exerciseId) + dayEx.alternatives
                WorkoutSlot(
                    exerciseIds = allIds,
                    supersetId = dayEx.supersetId,
                    isSequential = dayEx.isSequential
                )
            }
            LegacyWorkoutDay(
                day = day.day,
                muscleGroups = day.muscleGroups,
                isRestDay = day.isRestDay,
                notes = day.notes,
                slots = slots
            )
        }
        
        val catalog = linkedMapOf<String, ExerciseDefinition>()
        for (exercise in plan.allExercises()) {
            catalog[exercise.id] = ExerciseDefinition(
                id = exercise.id,
                name = exercise.name,
                sets = exercise.defaultSets.toString(),
                reps = exercise.defaultReps,
                load = exercise.defaultLoad,
                isTimed = exercise.isTimed,
                defaultTime = exercise.defaultTime,
                isBodyweight = exercise.isBodyweight,
                weightUnit = exercise.weightUnit,
                usesStraps = exercise.usesStraps,
                defaultNotes = exercise.defaultNotes
            )
        }
        
        return LegacyWorkoutPlan(catalog = catalog, schedule = days)
    }
}