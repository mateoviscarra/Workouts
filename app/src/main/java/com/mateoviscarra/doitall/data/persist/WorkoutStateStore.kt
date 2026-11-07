package com.mateoviscarra.doitall.data.persist

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mateoviscarra.doitall.data.WorkoutDay
import com.mateoviscarra.doitall.data.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.doItAllWorkoutDataStore by preferencesDataStore(name = "workout_log")

private val JSON_KEY_V2 = stringPreferencesKey("workout_log_json_v2")

/** Persisted slice + merged logs for one screen. */
data class ResolvedDayUi(
    val dayLog: DayLogState,
    val exerciseLogs: Map<String, ExerciseLogState>
)

class WorkoutStateStore(private val context: Context) {

    private val dataStore get() = context.applicationContext.doItAllWorkoutDataStore

    fun resolvedDayFlow(dayKey: String, day: WorkoutDay, plan: WorkoutPlan): Flow<ResolvedDayUi> {
        return dataStore.data.map { prefs ->
            val root = parsePersistRootV2(prefs[JSON_KEY_V2])
            ResolvedDayUi(
                dayLog = dayLogFromPersist(root, dayKey, day),
                exerciseLogs = mergedExerciseLogs(plan, root)
            )
        }
    }

    suspend fun loadResolvedDay(dayKey: String, day: WorkoutDay, plan: WorkoutPlan): ResolvedDayUi {
        val prefs = dataStore.data.first()
        val root = parsePersistRootV2(prefs[JSON_KEY_V2])
        return ResolvedDayUi(
            dayLog = dayLogFromPersist(root, dayKey, day),
            exerciseLogs = mergedExerciseLogs(plan, root)
        )
    }

    suspend fun loadPersistRoot(): PersistRootV2 {
        val prefs = dataStore.data.first()
        return parsePersistRootV2(prefs[JSON_KEY_V2])
    }

    suspend fun updateSelectedPage(dayKey: String, day: WorkoutDay, slotIndex: Int, page: Int) {
        dataStore.edit { prefs ->
            val root = parsePersistRootV2(prefs[JSON_KEY_V2])
            val current = dayLogFromPersist(root, dayKey, day)
            val slot = day.slots.getOrNull(slotIndex) ?: return@edit
            val max = (slot.exerciseIds.size - 1).coerceAtLeast(0)
            val nextDay = current.updateSlot(slotIndex) {
                it.copy(selectedPage = page.coerceIn(0, max))
            }
            prefs[JSON_KEY_V2] = root.withDay(dayKey, nextDay).toJsonString()
        }
    }

    suspend fun updateExerciseLog(exerciseId: String, log: ExerciseLogState) {
        dataStore.edit { prefs ->
            val root = parsePersistRootV2(prefs[JSON_KEY_V2])
            val logs = root.logs.toMutableMap()
            logs[exerciseId] = sanitizeExerciseLog(log)
            prefs[JSON_KEY_V2] = root.copy(logs = logs).toJsonString()
        }
    }

    suspend fun updateExerciseLogAndBinding(
        dayKey: String,
        day: WorkoutDay,
        slotIndex: Int,
        pageIndex: Int,
        exerciseId: String,
        log: ExerciseLogState
    ) {
        dataStore.edit { prefs ->
            val root = parsePersistRootV2(prefs[JSON_KEY_V2])
            val logs = root.logs.toMutableMap()
            logs[exerciseId] = sanitizeExerciseLog(log)

            val currentDay = dayLogFromPersist(root, dayKey, day)
            val nextDay = currentDay.updateSlot(slotIndex) { slot ->
                if (pageIndex !in slot.pageBindings.indices) return@updateSlot slot
                val list = slot.pageBindings.toMutableList()
                list[pageIndex] = exerciseId
                slot.copy(pageBindings = list)
            }
            prefs[JSON_KEY_V2] = PersistRootV2(
                logs = logs,
                days = root.days + (dayKey to nextDay)
            ).toJsonString()
        }
    }

    /** Toggles the done state for [exerciseId] on [dayKey]. */
    suspend fun toggleExerciseDone(dayKey: String, day: WorkoutDay, exerciseId: String) {
        dataStore.edit { prefs ->
            val root = parsePersistRootV2(prefs[JSON_KEY_V2])
            val current = dayLogFromPersist(root, dayKey, day)
            val done = current.doneExercises.toMutableSet()
            if (exerciseId in done) done.remove(exerciseId) else done.add(exerciseId)
            val nextDay = current.copy(doneExercises = done)
            prefs[JSON_KEY_V2] = root.withDay(dayKey, nextDay).toJsonString()
        }
    }

    /** Clears all done entries for [dayKey]. */
    suspend fun uncheckAllForDay(dayKey: String, day: WorkoutDay) {
        dataStore.edit { prefs ->
            val root = parsePersistRootV2(prefs[JSON_KEY_V2])
            val current = dayLogFromPersist(root, dayKey, day)
            val nextDay = current.copy(doneExercises = emptySet())
            prefs[JSON_KEY_V2] = root.withDay(dayKey, nextDay).toJsonString()
        }
    }
}

private fun sanitizeExerciseLog(log: ExerciseLogState): ExerciseLogState =
    log.copy(comment = log.comment.take(ExerciseLogState.MAX_COMMENT_LENGTH))

private fun PersistRootV2.withDay(dayKey: String, day: DayLogState): PersistRootV2 =
    copy(days = days + (dayKey to day))
