package com.mateoviscarra.doitall.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mateoviscarra.doitall.data.persist.WeightUnit
import com.mateoviscarra.doitall.data.persist.WorkoutStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private const val TAG = "WorkoutHistoryRepo"
private const val PREFS_NAME = "workout_history_prefs"
private const val KEY_SESSIONS = "workout_sessions_json"
private const val KEY_PRS = "personal_records_json"

class WorkoutHistoryRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _sessions = MutableStateFlow<List<WorkoutSessionRecord>>(emptyList())
    val sessions: StateFlow<List<WorkoutSessionRecord>> = _sessions.asStateFlow()

    private val _personalRecords = MutableStateFlow<Map<String, PersonalRecord>>(emptyMap())
    val personalRecords: StateFlow<Map<String, PersonalRecord>> = _personalRecords.asStateFlow()

    init {
        // Ensure auto-seeded PRs are cleared so Records starts empty for user-entered PRs
        if (!prefs.getBoolean("manual_prs_migrated_v2", false)) {
            prefs.edit().remove(KEY_PRS).putBoolean("manual_prs_migrated_v2", true).apply()
        }
        loadData()
    }

    private fun loadData() {
        // Load sessions
        val sessionsJson = prefs.getString(KEY_SESSIONS, null)
        val loadedSessions = if (!sessionsJson.isNullOrBlank()) {
            try {
                parseSessionsJson(sessionsJson)
            } catch (e: Exception) {
                Log.e(TAG, "Failed parsing sessions json", e)
                emptyList()
            }
        } else {
            emptyList()
        }
        _sessions.value = loadedSessions

        // Load PRs
        val prsJson = prefs.getString(KEY_PRS, null)
        val loadedPrs = if (!prsJson.isNullOrBlank()) {
            try {
                parsePRsJson(prsJson)
            } catch (e: Exception) {
                Log.e(TAG, "Failed parsing PRs json", e)
                emptyMap()
            }
        } else {
            emptyMap()
        }
        _personalRecords.value = loadedPrs
    }

    fun getPR(exerciseId: String): PersonalRecord? = _personalRecords.value[exerciseId]

    fun addOrUpdatePR(pr: PersonalRecord) {
        val currentPrs = _personalRecords.value.toMutableMap()
        currentPrs[pr.exerciseId] = pr
        _personalRecords.value = currentPrs
        scope.launch {
            persistPRs(currentPrs)
        }
    }

    fun deletePR(exerciseId: String) {
        val currentPrs = _personalRecords.value.filterKeys { it != exerciseId }
        _personalRecords.value = currentPrs
        scope.launch {
            persistPRs(currentPrs)
        }
    }

    fun recordSession(session: WorkoutSessionRecord): WorkoutSessionRecord {
        val updatedSessions = listOf(session) + _sessions.value
        _sessions.value = updatedSessions
        scope.launch {
            persistSessions(updatedSessions)
        }
        return session
    }

    fun deleteSession(sessionId: String) {
        val updated = _sessions.value.filter { it.id != sessionId }
        _sessions.value = updated
        scope.launch {
            persistSessions(updated)
        }
    }

    private fun getEstimatedReps(repsSummary: String): Int {
        val parts = repsSummary.split(",")
        return parts.mapNotNull { it.trim().toIntOrNull() }.maxOrNull() ?: 10
    }

    private fun persistSessions(sessions: List<WorkoutSessionRecord>) {
        val array = JSONArray()
        for (session in sessions) {
            val obj = JSONObject()
                .put("id", session.id)
                .put("dayTitle", session.dayTitle)
                .put("dateIso", session.dateIso)
                .put("timestamp", session.timestamp)
                .put("durationMinutes", session.durationMinutes)
                .put("notes", session.notes)

            val exArray = JSONArray()
            for (ex in session.exercises) {
                val exObj = JSONObject()
                    .put("exerciseId", ex.exerciseId)
                    .put("exerciseName", ex.exerciseName)
                    .put("sets", ex.sets)
                    .put("repsSummary", ex.repsSummary)
                    .put("weight", ex.weight)
                    .put("numericWeight", ex.numericWeight)
                    .put("weightUnit", ex.weightUnit.name)
                    .put("isBodyweight", ex.isBodyweight)
                    .put("usesStraps", ex.usesStraps)
                    .put("comment", ex.comment)
                    .put("isPR", ex.isPR)
                exArray.put(exObj)
            }
            obj.put("exercises", exArray)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    private fun parseSessionsJson(json: String): List<WorkoutSessionRecord> {
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val exArray = obj.getJSONArray("exercises")
            val exercises = List(exArray.length()) { j ->
                val exObj = exArray.getJSONObject(j)
                val unitStr = exObj.optString("weightUnit", "KG")
                val unit = if (unitStr.equals("LBS", ignoreCase = true)) WeightUnit.LBS else WeightUnit.KG
                CompletedExerciseRecord(
                    exerciseId = exObj.getString("exerciseId"),
                    exerciseName = exObj.getString("exerciseName"),
                    sets = exObj.optInt("sets", 1),
                    repsSummary = exObj.optString("repsSummary", ""),
                    weight = exObj.optString("weight", ""),
                    numericWeight = exObj.optDouble("numericWeight", 0.0),
                    weightUnit = unit,
                    isBodyweight = exObj.optBoolean("isBodyweight", false),
                    usesStraps = exObj.optBoolean("usesStraps", false),
                    comment = exObj.optString("comment", ""),
                    isPR = exObj.optBoolean("isPR", false)
                )
            }
            WorkoutSessionRecord(
                id = obj.getString("id"),
                dayTitle = obj.getString("dayTitle"),
                dateIso = obj.getString("dateIso"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                durationMinutes = obj.optInt("durationMinutes", 60),
                exercises = exercises,
                notes = obj.optString("notes", "")
            )
        }
    }

    fun exportHistoryJson(): JSONObject {
        val root = JSONObject()
        val sessionsStr = prefs.getString(KEY_SESSIONS, null)
        if (!sessionsStr.isNullOrBlank()) {
            root.put("sessions", JSONArray(sessionsStr))
        } else {
            root.put("sessions", JSONArray())
        }
        val prsStr = prefs.getString(KEY_PRS, null)
        if (!prsStr.isNullOrBlank()) {
            root.put("personalRecords", JSONObject(prsStr))
        } else {
            root.put("personalRecords", JSONObject())
        }
        return root
    }

    fun importHistoryJson(obj: JSONObject) {
        if (obj.has("sessions")) {
            val arr = obj.getJSONArray("sessions")
            prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
            _sessions.value = parseSessionsJson(arr.toString())
        }
        if (obj.has("personalRecords")) {
            val prObj = obj.getJSONObject("personalRecords")
            prefs.edit().putString(KEY_PRS, prObj.toString()).apply()
            _personalRecords.value = parsePRsJson(prObj.toString())
        }
    }

    private fun persistPRs(prs: Map<String, PersonalRecord>) {
        val obj = JSONObject()
        for ((id, pr) in prs) {
            val prObj = JSONObject()
                .put("exerciseId", pr.exerciseId)
                .put("exerciseName", pr.exerciseName)
                .put("maxWeight", pr.maxWeight)
                .put("reps", pr.reps)
                .put("weightUnit", pr.weightUnit.name)
                .put("dateIso", pr.dateIso)
                .put("isBodyweight", pr.isBodyweight)
                .put("note", pr.note)
            obj.put(id, prObj)
        }
        prefs.edit().putString(KEY_PRS, obj.toString()).apply()
    }

    private fun parsePRsJson(json: String): Map<String, PersonalRecord> {
        val obj = JSONObject(json)
        val map = mutableMapOf<String, PersonalRecord>()
        for (key in obj.keys()) {
            val prObj = obj.getJSONObject(key)
            val unitStr = prObj.optString("weightUnit", "KG")
            val unit = if (unitStr.equals("LBS", ignoreCase = true)) WeightUnit.LBS else WeightUnit.KG
            map[key] = PersonalRecord(
                exerciseId = prObj.getString("exerciseId"),
                exerciseName = prObj.getString("exerciseName"),
                maxWeight = prObj.optDouble("maxWeight", 0.0),
                reps = prObj.optInt("reps", 1),
                weightUnit = unit,
                dateIso = prObj.optString("dateIso", ""),
                isBodyweight = prObj.optBoolean("isBodyweight", false),
                note = prObj.optString("note", "")
            )
        }
        return map
    }
}
