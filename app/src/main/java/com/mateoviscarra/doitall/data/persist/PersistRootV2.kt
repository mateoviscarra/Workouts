package com.mateoviscarra.doitall.data.persist

import org.json.JSONArray
import org.json.JSONObject

private const val VERSION = 2

data class PersistRootV2(
    val logs: Map<String, ExerciseLogState>,
    val days: Map<String, DayLogState>
) {
    fun toJsonString(): String {
        val logsObj = JSONObject()
        logs.forEach { (id, log) -> logsObj.put(id, log.toJson()) }

        val daysObj = JSONObject()
        days.forEach { (dayKey, day) -> daysObj.put(dayKey, day.toDayJson()) }

        return JSONObject()
            .put("v", VERSION)
            .put("logs", logsObj)
            .put("days", daysObj)
            .toString()
    }
}

fun parsePersistRootV2(json: String?): PersistRootV2 {
    if (json.isNullOrBlank()) return PersistRootV2(emptyMap(), emptyMap())
    val root = JSONObject(json)
    if (root.optInt("v", 0) < VERSION) {
        return PersistRootV2(emptyMap(), emptyMap())
    }
    val logsObj = root.optJSONObject("logs") ?: JSONObject()
    val logs = buildMap {
        logsObj.keys().forEach { id ->
            val o = logsObj.optJSONObject(id) ?: return@forEach
            put(id, parseExerciseLog(o))
        }
    }
    val daysObj = root.optJSONObject("days") ?: JSONObject()
    val days = buildMap {
        daysObj.keys().forEach { dayKey ->
            val o = daysObj.optJSONObject(dayKey) ?: return@forEach
            put(dayKey, parseDayLogState(o))
        }
    }
    return PersistRootV2(logs = logs, days = days)
}

private fun ExerciseLogState.toJson(): JSONObject {
    val o = JSONObject()
        .put("sets", sets)
        .put("weight", weight)
        .put("usePerSetReps", usePerSetReps)
        .put("comment", comment.take(ExerciseLogState.MAX_COMMENT_LENGTH))
        .put("isCardio", isCardio)
        .put("weightUnit", weightUnit.name.lowercase())
        .put("isBodyweight", isBodyweight)
        .put("usesStraps", usesStraps)
        .put("isTimed", isTimed)
        .put("timeTarget", timeTarget)
    if (repsSingle != null) o.put("repsSingle", repsSingle)
    if (repsPerSet != null) {
        val a = JSONArray()
        repsPerSet.forEach { a.put(it) }
        o.put("repsPerSet", a)
    }
    if (duration != null) o.put("duration", duration)
    if (intensity != null) o.put("intensity", intensity)
    return o
}

private fun parseExerciseLog(json: JSONObject): ExerciseLogState {
    val repsArr = json.optJSONArray("repsPerSet")
    val repsPerSet = if (repsArr == null) null else List(repsArr.length()) { repsArr.optInt(it) }
    val unitStr = json.optString("weightUnit", "kg")
    val weightUnit = if (unitStr.equals("lbs", ignoreCase = true)) WeightUnit.LBS else WeightUnit.KG
    return ExerciseLogState(
        sets = json.optInt("sets", 1).coerceAtLeast(1),
        weight = json.optString("weight", ""),
        usePerSetReps = json.optBoolean("usePerSetReps", false),
        repsSingle = if (json.has("repsSingle")) json.optInt("repsSingle") else null,
        repsPerSet = repsPerSet,
        comment = json.optString("comment", "").take(ExerciseLogState.MAX_COMMENT_LENGTH),
        isCardio = json.optBoolean("isCardio", false),
        duration = if (json.has("duration")) json.optString("duration").ifBlank { null } else null,
        intensity = if (json.has("intensity")) json.optString("intensity").ifBlank { null } else null,
        weightUnit = weightUnit,
        isBodyweight = json.optBoolean("isBodyweight", false),
        usesStraps = json.optBoolean("usesStraps", false),
        isTimed = json.optBoolean("isTimed", false),
        timeTarget = json.optString("timeTarget", "60s")
    )
}

private fun DayLogState.toDayJson(): JSONObject {
    val slotsObj = JSONObject()
    slots.forEach { (k, v) -> slotsObj.put(k, v.toSlotJson()) }
    val doneArr = JSONArray()
    doneExercises.forEach { doneArr.put(it) }
    return JSONObject()
        .put("slots", slotsObj)
        .put("done", doneArr)
}

private fun SlotLogState.toSlotJson(): JSONObject {
    val arr = JSONArray()
    pageBindings.forEach { arr.put(it) }
    return JSONObject()
        .put("selectedPage", selectedPage)
        .put("bindings", arr)
}

private fun parseDayLogState(json: JSONObject): DayLogState {
    val slotsObj = json.optJSONObject("slots") ?: JSONObject()
    val slots = buildMap {
        slotsObj.keys().forEach { key ->
            val o = slotsObj.optJSONObject(key) ?: return@forEach
            put(key, parseSlotLogState(o))
        }
    }
    val doneArr = json.optJSONArray("done") ?: JSONArray()
    val doneExercises = buildSet<String> {
        for (i in 0 until doneArr.length()) {
            val s = doneArr.optString(i)
            if (s.isNotEmpty()) add(s)
        }
    }
    return DayLogState(slots = slots, doneExercises = doneExercises)
}

private fun parseSlotLogState(json: JSONObject): SlotLogState {
    val selectedPage = json.optInt("selectedPage", 0)
    val arr = json.optJSONArray("bindings") ?: JSONArray()
    val bindings = List(arr.length()) { arr.optString(it) }
    return SlotLogState(
        selectedPage = selectedPage,
        pageBindings = bindings
    )
}
