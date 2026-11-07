package com.mateoviscarra.doitall.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.mateoviscarra.doitall.data.persist.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class CustomExercise(
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: String,
    val isBodyweight: Boolean,
    val weightUnit: WeightUnit,
    val isDone: Boolean = false
)

class CustomWorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("custom_workout_prefs", Context.MODE_PRIVATE)

    private val _exercises = MutableStateFlow<List<CustomExercise>>(loadExercises())
    val exercises: StateFlow<List<CustomExercise>> = _exercises.asStateFlow()

    private fun loadExercises(): List<CustomExercise> {
        val json = prefs.getString("draft_exercises", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                val unitStr = obj.optString("weightUnit", "KG")
                val unit = if (unitStr.equals("LBS", ignoreCase = true)) WeightUnit.LBS else WeightUnit.KG
                CustomExercise(
                    name = obj.getString("name"),
                    sets = obj.optInt("sets", 3),
                    reps = obj.optInt("reps", 10),
                    weight = obj.optString("weight", ""),
                    isBodyweight = obj.optBoolean("isBodyweight", false),
                    weightUnit = unit,
                    isDone = obj.optBoolean("isDone", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<CustomExercise>) {
        _exercises.value = list
        val array = JSONArray()
        list.forEach { ex ->
            val obj = JSONObject()
                .put("name", ex.name)
                .put("sets", ex.sets)
                .put("reps", ex.reps)
                .put("weight", ex.weight)
                .put("isBodyweight", ex.isBodyweight)
                .put("weightUnit", ex.weightUnit.name)
                .put("isDone", ex.isDone)
            array.put(obj)
        }
        prefs.edit().putString("draft_exercises", array.toString()).apply()
    }

    fun addExercise(exercise: CustomExercise) {
        persist(_exercises.value + exercise)
    }

    fun removeExercise(index: Int) {
        if (index in _exercises.value.indices) {
            val list = _exercises.value.toMutableList()
            list.removeAt(index)
            persist(list)
        }
    }

    fun toggleDone(index: Int) {
        if (index in _exercises.value.indices) {
            val list = _exercises.value.mapIndexed { i, ex ->
                if (i == index) ex.copy(isDone = !ex.isDone) else ex
            }
            persist(list)
        }
    }

    fun uncheckAll() {
        val list = _exercises.value.map { it.copy(isDone = false) }
        persist(list)
    }

    fun clearAll() {
        persist(emptyList())
    }
}

