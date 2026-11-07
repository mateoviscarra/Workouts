package com.mateoviscarra.doitall.data.persist

import com.mateoviscarra.doitall.data.ExerciseDefinition
import com.mateoviscarra.doitall.data.WorkoutDay
import com.mateoviscarra.doitall.data.WorkoutPlan
import com.mateoviscarra.doitall.data.WorkoutSlot
import com.mateoviscarra.doitall.data.defaultRepsInt
import com.mateoviscarra.doitall.data.defaultSetsInt

/**
 * Parses the JSON load string to extract bodyweight flag and numeric weight.
 * E.g. "Bodyweight + 15 kg" → isBodyweight=true, weight="15"
 * E.g. "Bodyweight"         → isBodyweight=true, weight=""
 * E.g. "32 kg each"         → isBodyweight=false, weight="32 kg each"
 */
private fun parseLoadString(load: String): Pair<Boolean, String> {
    val trimmed = load.trim()
    if (trimmed.equals("bodyweight", ignoreCase = true)) {
        return true to ""
    }
    val bwRegex = Regex("""(?i)bodyweight\s*([+\-])\s*(.+)""")
    val match = bwRegex.find(trimmed)
    if (match != null) {
        val sign = match.groupValues[1].trim()
        val rest = match.groupValues[2].trim()
        val numMatch = Regex("""[+-]?[\d.]+""").find(rest)
        val numStr = numMatch?.value ?: rest
        val finalNum = if (sign == "-" && !numStr.startsWith("-")) "-$numStr" else numStr
        return true to finalNum
    }
    if (trimmed.startsWith("bodyweight", ignoreCase = true)) {
        val rest = trimmed.substring("bodyweight".length).trim()
        val numMatch = Regex("""[+-]?[\d.]+""").find(rest)
        if (numMatch != null) {
            return true to numMatch.value
        }
        return true to ""
    }
    return false to trimmed
}

fun defaultExerciseLog(def: ExerciseDefinition): ExerciseLogState {
    val cardio = def.duration != null
    val isTimed = def.isTimed || def.reps.contains("s", ignoreCase = true) || def.reps.contains("sec", ignoreCase = true)
    val sets = if (cardio) 1 else def.defaultSetsInt().coerceAtLeast(1)
    val repsSingle = if (cardio || isTimed) null else def.defaultRepsInt()
    val (parsedIsBodyweight, parsedWeight) = if (cardio) false to "" else parseLoadString(def.load)
    val isBodyweight = def.isBodyweight || parsedIsBodyweight
    val timeTarget = if (isTimed) {
        if (def.defaultTime.isNotBlank()) def.defaultTime else if (def.reps.isNotBlank()) def.reps else "60s"
    } else "60s"
    val unit = if (def.weightUnit.equals("lbs", ignoreCase = true)) WeightUnit.LBS else WeightUnit.KG

    return ExerciseLogState(
        sets = sets,
        weight = if (cardio) "" else parsedWeight,
        usePerSetReps = false,
        repsSingle = repsSingle,
        repsPerSet = null,
        comment = def.defaultNotes,
        isCardio = cardio,
        duration = def.duration,
        intensity = def.intensity,
        weightUnit = unit,
        isBodyweight = isBodyweight,
        usesStraps = def.usesStraps,
        isTimed = isTimed,
        timeTarget = timeTarget
    )
}

fun defaultDayLog(day: WorkoutDay): DayLogState {
    val slots = day.slots.mapIndexed { index, slot ->
        index.toString() to SlotLogState(
            selectedPage = 0,
            pageBindings = slot.exerciseIds.toList()
        )
    }.toMap()
    return DayLogState(slots = slots)
}

fun normalizeSlotState(slot: WorkoutSlot, persisted: SlotLogState?): SlotLogState {
    val size = slot.exerciseIds.size
    val defaultBindings = slot.exerciseIds.toList()
    if (persisted == null) {
        return SlotLogState(selectedPage = 0, pageBindings = defaultBindings)
    }
    val allowed = slot.exerciseIds.toSet()
    val bindings = List(size) { i ->
        val p = persisted.pageBindings.getOrNull(i)
        if (p != null && p in allowed) p else defaultBindings[i]
    }
    return SlotLogState(
        selectedPage = persisted.selectedPage.coerceIn(0, (size - 1).coerceAtLeast(0)),
        pageBindings = bindings
    )
}

fun dayLogFromPersist(root: PersistRootV2, dayKey: String, day: WorkoutDay): DayLogState {
    val persistedDay = root.days[dayKey]
    return DayLogState(
        slots = day.slots.mapIndexed { index, slot ->
            val key = index.toString()
            val slotPersisted = persistedDay?.slots?.get(key)
            key to normalizeSlotState(slot, slotPersisted)
        }.toMap(),
        doneExercises = persistedDay?.doneExercises ?: emptySet()
    )
}

fun mergedExerciseLogs(plan: WorkoutPlan, root: PersistRootV2): Map<String, ExerciseLogState> {
    val out = linkedMapOf<String, ExerciseLogState>()
    for (def in plan.catalog.values) {
        out[def.id] = root.logs[def.id] ?: defaultExerciseLog(def)
    }
    return out
}
