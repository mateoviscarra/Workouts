package com.mateoviscarra.doitall.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mateoviscarra.doitall.data.persist.NewWorkoutStore
import com.mateoviscarra.doitall.data.persist.PersistRootV2
import com.mateoviscarra.doitall.data.persist.WorkoutStateStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DataBackupManager(private val context: Context) {
    private val workoutStore = NewWorkoutStore(context)
    private val historyRepo = WorkoutHistoryRepository(context)
    private val nutritionRepo = NutritionRepository(context)
    private val stateStore = WorkoutStateStore(context)

    fun defaultZipFilename(date: LocalDate = LocalDate.now()): String =
        "workouts_ai_export_$date.zip"

    fun defaultJsonFilename(date: LocalDate = LocalDate.now()): String =
        "workouts_backup_$date.json"

    private fun buildSyncedPlanJson(): JSONObject {
        val rawPlanObj = workoutStore.exportPlanJson()
        val planObj = if (rawPlanObj.has("categories") && rawPlanObj.has("days")) {
            JSONObject(rawPlanObj.toString())
        } else {
            val loaded = WorkoutRepository.loadWorkoutPlan(context)
            runBlocking { workoutStore.savePlan(loaded) }
            workoutStore.exportPlanJson()
        }

        val persistRoot: PersistRootV2 = runBlocking { stateStore.loadPersistRoot() }
        val categoriesArr = planObj.optJSONArray("categories") ?: return planObj

        for (i in 0 until categoriesArr.length()) {
            val catObj = categoriesArr.optJSONObject(i) ?: continue
            val exArr = catObj.optJSONArray("exercises") ?: continue
            for (j in 0 until exArr.length()) {
                val exObj = exArr.optJSONObject(j) ?: continue
                val exId = exObj.optString("id", "")
                val liveLog = persistRoot.logs[exId] ?: continue

                exObj.put("defaultSets", liveLog.sets)
                if (liveLog.isTimed) {
                    val timeStr = liveLog.timeTarget.ifBlank { exObj.optString("defaultTime", "60s") }
                    exObj.put("defaultReps", timeStr)
                    exObj.put("defaultTime", timeStr)
                } else if (!liveLog.isCardio) {
                    val repsVal = if (liveLog.usePerSetReps && !liveLog.repsPerSet.isNullOrEmpty()) {
                        liveLog.repsPerSet.firstOrNull()?.toString() ?: exObj.optString("defaultReps", "10")
                    } else {
                        liveLog.repsSingle?.toString() ?: exObj.optString("defaultReps", "10")
                    }
                    exObj.put("defaultReps", repsVal)
                }

                if (!liveLog.isCardio) {
                    val formattedLoad = formatExerciseWeight(
                        weight = liveLog.weight,
                        isBodyweight = liveLog.isBodyweight,
                        unitLabel = liveLog.weightUnit.name.lowercase(Locale.ROOT),
                        shortBwPrefix = false
                    )
                    if (formattedLoad.isNotBlank() || liveLog.isBodyweight) {
                        exObj.put("defaultLoad", formattedLoad)
                    } else {
                        exObj.put("defaultLoad", "")
                    }
                }
                exObj.put("isTimed", liveLog.isTimed)
                exObj.put("isBodyweight", liveLog.isBodyweight)
                exObj.put("weightUnit", liveLog.weightUnit.name.lowercase(Locale.ROOT))
                exObj.put("usesStraps", liveLog.usesStraps)
                exObj.put("defaultNotes", liveLog.comment)
            }
        }
        return planObj
    }

    private fun buildUserProfileJson(profile: UserProfile = nutritionRepo.userProfile.value): JSONObject {
        return JSONObject().apply {
            put("heightCm", profile.heightCm.toDouble())
            put("sex", profile.sex)
            put("birthDateIso", profile.birthDateIso)
            profile.calculateAge()?.let { put("ageYears", it) }
            put("targetWeightKg", profile.targetWeightKg.toDouble())
            put("fitnessGoalSummary", profile.fitnessGoalSummary)
        }
    }

    fun createUnifiedBackupJson(): String {
        historyRepo.reloadFromPrefs()
        nutritionRepo.reloadFromPrefs()
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportDate", LocalDate.now().toString())
        root.put("userProfile", buildUserProfileJson())
        root.put("workoutPlan", buildSyncedPlanJson())
        root.put("workoutHistory", historyRepo.exportHistoryJson())
        root.put("nutrition", nutritionRepo.exportNutritionJson())
        return root.toString(2)
    }

    fun createAiGuideMarkdown(backupRoot: JSONObject? = null): String {
        val profile = nutritionRepo.userProfile.value
        val age = profile.calculateAge()
        val goals = nutritionRepo.goals.value
        val weightLogs = nutritionRepo.weightLogs.value
        val latestWeight = weightLogs.maxByOrNull { it.dateIso }
        val sessions = historyRepo.sessions.value
        val foodLogs = nutritionRepo.foodLogs.value
        val planDays = backupRoot?.optJSONObject("workoutPlan")?.optJSONArray("days")?.length()
            ?: workoutStore.loadPlanOnce()?.days?.size ?: 0
        val backupFilename = defaultJsonFilename()

        val sb = StringBuilder()
        sb.appendLine("# DoItAll — Athlete Profile, Training Context & JSON Import Specification")
        sb.appendLine()
        sb.appendLine("This bundle was exported on **${LocalDate.now()}** from the user's **DoItAll Workout & Nutrition Tracker** Android app.")
        sb.appendLine("It contains two files:")
        sb.appendLine("1. **`$backupFilename`**: Full backup JSON with workout routines, exercise catalog, training logs, personal records, nutrition logs, staple meals, weight logs, and user profile.")
        sb.appendLine("2. **`AI_README_AND_SCHEMA.md`** *(this document)*: User context, app mechanics, and strict schema rules so you can analyze performance accurately and generate new `.json` plans that import cleanly back into the app.")
        sb.appendLine()

        sb.appendLine("## 1. Athlete Profile & Goals")
        sb.appendLine()
        sb.appendLine("- **Sex:** ${profile.sex.ifBlank { "Not specified (ask user or prompt them to set in Settings)" }}")
        val birthStr = when {
            profile.birthDateIso.isNotBlank() && age != null -> "${profile.birthDateIso} (**Age: $age**)"
            profile.birthDateIso.isNotBlank() -> profile.birthDateIso
            else -> "Not specified"
        }
        sb.appendLine("- **Birth Date / Age:** $birthStr")
        sb.appendLine("- **Height:** ${if (profile.heightCm > 0f) "${String.format(Locale.US, "%.1f", profile.heightCm).removeSuffix(".0")} cm" else "Not specified"}")
        val currentWeightStr = if (latestWeight != null) {
            "${latestWeight.weight} ${latestWeight.unit.name.lowercase(Locale.ROOT)} (logged ${latestWeight.dateIso})"
        } else {
            "No weight logs recorded yet"
        }
        sb.appendLine("- **Current Weight:** $currentWeightStr")
        sb.appendLine("- **Target Weight:** ${if (profile.targetWeightKg > 0f) "${String.format(Locale.US, "%.1f", profile.targetWeightKg).removeSuffix(".0")} kg" else "Not specified"}")
        sb.appendLine("- **Daily Nutrition Targets:** ${goals.calorieTarget} kcal | Protein: ${goals.proteinTargetGrams.toInt()}g | Carbs: ${goals.carbsTargetGrams.toInt()}g | Fat: ${goals.fatTargetGrams.toInt()}g")
        sb.appendLine("- **Current Data Snapshot:** $planDays routine days configured, ${sessions.size} workout sessions logged, ${foodLogs.size} food entries, ${weightLogs.size} weight entries.")
        sb.appendLine()
        sb.appendLine("### Fitness Goals & Training Notes")
        if (profile.fitnessGoalSummary.isNotBlank()) {
            sb.appendLine(profile.fitnessGoalSummary.trim())
        } else {
            sb.appendLine("_No written goal notes set yet in Settings._")
        }
        sb.appendLine()

        sb.appendLine("## 2. How the DoItAll App Works (Domain Model)")
        sb.appendLine()
        sb.appendLine("- **Exercise Catalog (`workoutPlan.categories`)**: Exercises are organized into categories (`\"push\"`, `\"pull\"`, `\"legs\"`, `\"accessory\"`, `\"cardio\"`, plus any user-created categories). Every routine day references exercises strictly by `id` from this catalog.")
        sb.appendLine("- **Routine Days & Carousels (`workoutPlan.days`)**: Each day has a `day` title, `muscleGroups[]`, `notes[]`, and an ordered `exercises[]` list. Every entry in `day.exercises[]` is a **carousel slot** with a primary `exerciseId` (index 0, shown by default) and optional swipeable `alternatives[]` in case equipment is occupied.")
        sb.appendLine("- **Supersets (`supersetId` & `isSequential`)**: Consecutive slots sharing the same non-null `supersetId` string (e.g. `\"ss_pull_1\"`) are grouped into a single superset card:")
        sb.appendLine("  - `\"isSequential\": false` -> **Alternating superset** (alternate back and forth set by set: A1 -> B1 -> A2 -> B2).")
        sb.appendLine("  - `\"isSequential\": true` -> **Sequential combo** (finish all sets of exercise A, then perform exercise B, e.g. Pull-Ups followed by Isometric Pull-Up Holds).")
        sb.appendLine("- **Dedicated Standalone `\"Cardio\"` Category & Recovery/Hybrid Days**:")
        sb.appendLine("  - Cardio activities live in their own `\"cardio\"` category and standalone `\"Cardio\"` day (which powers the bottom **Cardio** card and dedicated Cardio logger).")
        sb.appendLine("  - For recovery or hybrid days that combine abs/strength with cardio (e.g. `\"3 - Recovery\"`, `\"6 - Recovery\"`), **do NOT put cardio exercises inside `day.exercises[]`** (that would treat them as isometric timer sets). Instead, **include `\"cardio\"` in `muscleGroups[]`** (e.g. `[\"abs\", \"cardio\"]`) so the screen renders the embedded Cardio logger card directly alongside the strength/abs slots, while **always keeping the standalone `\"Cardio\"` day** in `workoutPlan.days`.")
        sb.appendLine("- **Exercise Load, Timed Holds & Flag Formats**:")
        sb.appendLine("  - **Standard Weighted**: `\"isTimed\": false`, `\"isBodyweight\": false`, `\"defaultLoad\": \"85 kg\"` (or `\"130 lbs\"`), `\"weightUnit\": \"kg\"` or `\"lbs\"`. Assisted negative loads like `\"-25 kg\"` are valid.")
        sb.appendLine("  - **Bodyweight / Weighted Bodyweight / Assisted**: `\"isBodyweight\": true`. `\"defaultLoad\"` can be `\"Bodyweight\"`, `\"Bodyweight + 15 kg\"`, or `\"Bodyweight - 40 kg\"`.")
        sb.appendLine("  - **Timed / Isometric Holds (e.g. Plank, Dead Hang, Incline Walk in a routine)**: `\"isTimed\": true`, `\"defaultReps\": \"60s\"` (or `\"40min\"`), `\"defaultTime\": \"60s\"`. Timed exercises include a built-in countdown timer in the workout UI.")
        sb.appendLine("  - **Straps & Notes**: `\"usesStraps\": true` highlights a Straps badge; `\"defaultNotes\"` displays persistent coaching cues.")
        sb.appendLine()

        sb.appendLine("## 3. JSON Schema & Strict Rules for Generating New JSONs")
        sb.appendLine()
        sb.appendLine("When the user asks you to update their program, adjust macros/goals, add new exercises, or analyze their data and build a replacement backup, output a **complete, valid JSON** (the app can import `.json` or `.zip` directly in **Settings -> Restore**).")
        sb.appendLine()
        sb.appendLine("### Strict Compatibility Rules")
        sb.appendLine("1. **Referential Integrity**: Every `exerciseId` and every item in `alternatives[]` inside `workoutPlan.days[].exercises[]` **MUST** match an `id` defined in `workoutPlan.categories[].exercises[]`. If you prescribe a new exercise, add its full definition object inside the appropriate category first.")
        sb.appendLine("2. **Updating Working Numbers**: Restoring a backup overwrites active UI exercise targets from `workoutPlan.categories[].exercises[]` (`defaultSets`, `defaultReps`, `defaultLoad`, `isTimed`, `defaultTime`, `isBodyweight`, `weightUnit`, `usesStraps`, `defaultNotes`). Always update those fields in `categories` when changing weights/reps/sets/notes.")
        sb.appendLine("3. **Preserve User History & Nutrition Logs**: Unless the user explicitly asks to wipe history, **copy forward** `workoutHistory`, `nutrition.savedMeals`, `nutrition.foodLogs`, `nutrition.weightLogs`, and `userProfile` unchanged from the attached JSON so the user keeps all past logs after importing.")
        sb.appendLine("4. **Required Array Fields**: Keep `muscleGroups` (array of strings) and `notes` (array of strings) present on each day, even if empty (`[]`). Omit rest days or set `\"isRestDay\": true` (rest days are skipped on import).")
        sb.appendLine()
        sb.appendLine("### Top-Level JSON Schema Template")
        sb.appendLine("```json")
        sb.appendLine("""{
  "version": 1,
  "exportedAt": 1794699844277,
  "exportDate": "YYYY-MM-DD",
  "userProfile": {
    "heightCm": 175.0,
    "sex": "Male",
    "birthDateIso": "YYYY-MM-DD",
    "targetWeightKg": 75.0,
    "fitnessGoalSummary": "..."
  },
  "workoutPlan": {
    "categories": [
      {
        "id": "push",
        "name": "Push",
        "exercises": [
          {
            "id": "barbell_bench_press",
            "name": "Barbell Bench Press",
            "defaultSets": 3,
            "defaultReps": "7",
            "defaultLoad": "85 kg",
            "isTimed": false,
            "defaultTime": "60s",
            "isBodyweight": false,
            "weightUnit": "kg",
            "usesStraps": false,
            "defaultNotes": ""
          }
        ]
      }
    ],
    "days": [
      {
        "day": "Day 1 - Push",
        "muscleGroups": ["chest", "shoulders", "triceps"],
        "isRestDay": false,
        "notes": ["Coaching note for the day"],
        "exercises": [
          {
            "exerciseId": "barbell_bench_press",
            "alternatives": ["dumbbell_bench_press", "machine_chest_press"]
          },
          {
            "exerciseId": "pull_ups",
            "alternatives": ["lat_pulldowns"],
            "supersetId": "ss_pull_1",
            "isSequential": true
          }
        ]
      },
      {
        "day": "Cardio",
        "muscleGroups": [],
        "isRestDay": false,
        "notes": [],
        "exercises": [
          { "exerciseId": "incline_walking" },
          { "exerciseId": "stair_master_" },
          { "exerciseId": "running" },
          { "exerciseId": "cycling" },
          { "exerciseId": "walking" }
        ]
      }
    ]
  },
  "workoutHistory": {
    "sessions": [],
    "personalRecords": {}
  },
  "nutrition": {
    "goals": {
      "calorieTarget": 2100,
      "proteinTargetGrams": 160,
      "carbsTargetGrams": 245,
      "fatTargetGrams": 70
    },
    "savedMeals": [],
    "foodLogs": [],
    "weightLogs": []
  }
}""")
        sb.appendLine("```")
        return sb.toString()
    }

    /**
     * Generates a single comprehensive document optimized for Google Docs + NotebookLM indexing:
     * combines athlete profile & goals, daily nutrition targets, live training & nutrition summaries,
     * app mechanics/JSON guide, and the complete raw backup JSON.
     */
    fun createNotebookLmDocMarkdown(): String {
        val backupJson = createUnifiedBackupJson()
        val backupRoot = runCatching { JSONObject(backupJson) }.getOrNull()
        val profile = nutritionRepo.userProfile.value
        val age = profile.calculateAge()
        val goals = nutritionRepo.goals.value
        val plan = workoutStore.loadPlanOnce() ?: WorkoutRepository.loadWorkoutPlan(context)
        val persistRoot = runBlocking { stateStore.loadPersistRoot() }
        val sessions = historyRepo.sessions.value.sortedByDescending { it.dateIso }
        val prs = historyRepo.personalRecords.value.values.sortedByDescending { it.dateIso }
        val foodLogs = nutritionRepo.foodLogs.value
        val savedMeals = nutritionRepo.savedMeals.value
        val weightLogs = nutritionRepo.weightLogs.value.sortedByDescending { it.dateIso }
        val supplements = nutritionRepo.supplements.value
        val pillLogs = nutritionRepo.pillLogs.value
        val syncedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))

        val sb = StringBuilder()
        sb.appendLine("# DoItAll — Live Coach Context & Data")
        sb.appendLine()
        sb.appendLine("Last synced from DoItAll Android App: $syncedAt")
        sb.appendLine("Use this document as the authoritative source of truth for the athlete's current stats, active workout split, training weights, workout history, body weight trend, nutrition intake, and strict DoItAll JSON schema.")
        sb.appendLine()

        // Section 1: Profile & Targets
        sb.appendLine("## 1. Athlete Profile, Nutrition Targets & Training Goals")
        sb.appendLine("- Sex: ${profile.sex.ifBlank { "Not specified" }}")
        val birthStr = when {
            profile.birthDateIso.isNotBlank() && age != null -> "${profile.birthDateIso} (Age: $age)"
            profile.birthDateIso.isNotBlank() -> profile.birthDateIso
            else -> "Not specified"
        }
        sb.appendLine("- Birth Date / Age: $birthStr")
        sb.appendLine("- Height: ${if (profile.heightCm > 0f) "${String.format(Locale.US, "%.1f", profile.heightCm).removeSuffix(".0")} cm" else "Not specified"}")
        val latestWeight = weightLogs.firstOrNull()
        val weightStr = if (latestWeight != null) {
            "${latestWeight.weight} ${latestWeight.unit.name.lowercase(Locale.ROOT)} (logged ${latestWeight.dateIso})"
        } else {
            "No weight logs recorded"
        }
        sb.appendLine("- Latest Bodyweight: $weightStr")
        sb.appendLine("- Target Bodyweight: ${if (profile.targetWeightKg > 0f) "${String.format(Locale.US, "%.1f", profile.targetWeightKg).removeSuffix(".0")} kg" else "Not specified"}")
        sb.appendLine("- Daily Nutrition Targets: ${goals.calorieTarget} kcal | Protein: ${goals.proteinTargetGrams.toInt()} g | Carbs: ${goals.carbsTargetGrams.toInt()} g | Fat: ${goals.fatTargetGrams.toInt()} g")
        sb.appendLine("- Fitness Goals & Context: ${profile.fitnessGoalSummary.ifBlank { "Not specified" }}")
        sb.appendLine()

        // Section 2: Active Workout Split
        sb.appendLine("## 2. Active Workout Split & Current Working Weights")
        val exerciseCatalogMap = plan.allExercises().associateBy { it.id }
        plan.days.forEachIndexed { idx, day ->
            sb.appendLine("### ${idx + 1}. ${day.day}")
            if (day.muscleGroups.isNotEmpty()) {
                sb.appendLine("Muscle Groups: ${day.muscleGroups.joinToString(", ")}")
            }
            if (day.notes.isNotEmpty()) {
                sb.appendLine("Notes: ${day.notes.joinToString(" | ")}")
            }
            day.exercises.forEachIndexed { slotIdx, slot ->
                val exDef = exerciseCatalogMap[slot.exerciseId]
                val liveLog = persistRoot.logs[slot.exerciseId]
                val name = exDef?.name ?: slot.exerciseId
                val supersetTag = if (!slot.supersetId.isNullOrBlank()) {
                    if (slot.isSequential) " [Sequential Combo: ${slot.supersetId}]" else " [Superset: ${slot.supersetId}]"
                } else ""
                val metrics = if (liveLog != null) {
                    if (liveLog.isCardio) {
                        val dur = liveLog.timeTarget.ifBlank { "${liveLog.sets} min" }
                        val intensity = liveLog.weight.takeIf { it.isNotBlank() }?.let { " | Intensity: $it" } ?: ""
                        "$dur$intensity"
                    } else if (liveLog.isTimed) {
                        val t = liveLog.timeTarget.ifBlank { exDef?.defaultTime ?: "60s" }
                        "${liveLog.sets} sets x $t"
                    } else {
                        val repsText = if (liveLog.usePerSetReps && !liveLog.repsPerSet.isNullOrEmpty()) {
                            liveLog.repsPerSet.joinToString(",")
                        } else {
                            (liveLog.repsSingle ?: exDef?.defaultReps ?: "10").toString()
                        }
                        val loadText = formatExerciseWeight(
                            weight = liveLog.weight,
                            isBodyweight = liveLog.isBodyweight,
                            unitLabel = liveLog.weightUnit.name.lowercase(Locale.ROOT),
                            shortBwPrefix = false
                        ).ifBlank { "Bodyweight" }
                        val strapsText = if (liveLog.usesStraps) " [Straps]" else ""
                        "${liveLog.sets} sets x $repsText reps @ $loadText$strapsText"
                    }
                } else {
                    "${exDef?.defaultSets ?: 3} sets x ${exDef?.defaultReps ?: "10"} @ ${exDef?.defaultLoad ?: "Bodyweight"}"
                }
                val altsText = if (slot.alternatives.isNotEmpty()) {
                    val altNames = slot.alternatives.map { id -> exerciseCatalogMap[id]?.name ?: id }
                    " (Alts: ${altNames.joinToString(", ")})"
                } else ""
                val commentText = liveLog?.comment?.takeIf { it.isNotBlank() }?.let { " — Note: $it" } ?: ""
                sb.appendLine("- Slot ${slotIdx + 1}: $name$supersetTag — $metrics$altsText$commentText")
            }
            sb.appendLine()
        }

        // Section 3: Recent Workout History & PRs
        sb.appendLine("## 3. Workout History & Personal Records")
        sb.appendLine("Total Logged Sessions: ${sessions.size}")
        if (prs.isNotEmpty()) {
            sb.appendLine("### Personal Records (PRs)")
            prs.forEach { pr ->
                val weightLabel = if (pr.isBodyweight) {
                    if (pr.maxWeight > 0.0) "BW + ${pr.maxWeight} ${pr.weightUnit.name.lowercase(Locale.ROOT)}" else "Bodyweight"
                } else {
                    "${pr.maxWeight} ${pr.weightUnit.name.lowercase(Locale.ROOT)}"
                }
                val note = pr.note.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                sb.appendLine("- ${pr.exerciseName}: $weightLabel x ${pr.reps} reps on ${pr.dateIso}$note")
            }
            sb.appendLine()
        }

        val recentSessions = sessions.take(20)
        if (recentSessions.isNotEmpty()) {
            sb.appendLine("### Recent Sessions (Last ${recentSessions.size})")
            recentSessions.forEach { session ->
                sb.appendLine("#### ${session.dateIso} — ${session.dayTitle} (${session.durationMinutes} min)")
                if (session.notes.isNotBlank()) {
                    sb.appendLine("Session Notes: ${session.notes}")
                }
                session.exercises.forEach { ex ->
                    val line = if (ex.isCardio) {
                        val intensity = ex.weight.takeIf { it.isNotBlank() }?.let { " @ $it" } ?: ""
                        "- ${ex.exerciseName}: ${ex.repsSummary}$intensity"
                    } else {
                        val loadStr = if (ex.isBodyweight) {
                            if (ex.weight.isNotBlank()) "BW + ${ex.weight}" else "BW"
                        } else {
                            if (ex.weight.isNotBlank()) "${ex.weight} ${ex.weightUnit.name.lowercase(Locale.ROOT)}" else ""
                        }
                        val atLoad = if (loadStr.isNotBlank()) " @ $loadStr" else ""
                        val prFlag = if (ex.isPR) " [PR]" else ""
                        val note = ex.comment.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
                        "- ${ex.exerciseName}: ${ex.sets} sets x ${ex.repsSummary}$atLoad$prFlag$note"
                    }
                    sb.appendLine(line)
                }
                sb.appendLine()
            }
        } else {
            sb.appendLine("No workout sessions logged yet.")
            sb.appendLine()
        }

        // Section 4: Nutrition Summary (Last 14 Logged Days) & Weight Trend
        sb.appendLine("## 4. Nutrition Intake Summary & Bodyweight Trend")
        if (weightLogs.isNotEmpty()) {
            sb.appendLine("### Bodyweight Trend (Last ${weightLogs.take(20).size} Entries)")
            weightLogs.take(20).forEach { w ->
                val note = w.note.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
                sb.appendLine("- ${w.dateIso}: ${w.weight} ${w.unit.name.lowercase(Locale.ROOT)}$note")
            }
            sb.appendLine()
        }

        val logsByDate = foodLogs.groupBy { it.dateIso }.toSortedMap(compareByDescending { it })
        val recentDates = logsByDate.keys.take(14)
        if (recentDates.isNotEmpty()) {
            sb.appendLine("### Daily Nutrition Logs (Last ${recentDates.size} Logged Days vs Target ${goals.calorieTarget} kcal)")
            recentDates.forEach { date ->
                val dayEntries = logsByDate[date].orEmpty()
                val totalCals = dayEntries.sumOf { it.totalCalories }
                val totalProt = dayEntries.sumOf { it.totalProtein.toDouble() }.toInt()
                val totalCarbs = dayEntries.sumOf { it.totalCarbs.toDouble() }.toInt()
                val totalFat = dayEntries.sumOf { it.totalFat.toDouble() }.toInt()
                sb.appendLine("#### $date: $totalCals / ${goals.calorieTarget} kcal | P: ${totalProt}g / ${goals.proteinTargetGrams.toInt()}g | C: ${totalCarbs}g / ${goals.carbsTargetGrams.toInt()}g | F: ${totalFat}g / ${goals.fatTargetGrams.toInt()}g")
                dayEntries.forEach { entry ->
                    val mult = if (entry.portionMultiplier != 1.0f) " (${entry.portionMultiplier}x)" else ""
                    sb.appendLine("- [${entry.mealSlot.displayName}] ${entry.name}$mult: ${entry.totalCalories} kcal (P ${entry.totalProtein.toInt()}g, C ${entry.totalCarbs.toInt()}g, F ${entry.totalFat.toInt()}g)")
                }
                sb.appendLine()
            }
        } else {
            sb.appendLine("No food logs recorded yet.")
            sb.appendLine()
        }

        if (savedMeals.isNotEmpty()) {
            sb.appendLine("### Saved Staple Meals (${savedMeals.size})")
            savedMeals.forEach { meal ->
                val pin = if (meal.isPinnedStaple) "[Staple]" else "[Saved]"
                val serv = meal.servingDescription.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                sb.appendLine("- $pin ${meal.name}$serv: ${meal.totalCalories} kcal (P ${meal.totalProtein.toInt()}g, C ${meal.totalCarbs.toInt()}g, F ${meal.totalFat.toInt()}g)")
            }
            sb.appendLine()
        }

        if (supplements.isNotEmpty()) {
            val today = LocalDate.now()
            sb.appendLine("### Pills & Supplements Protocol (${supplements.size})")
            supplements.forEach { pill ->
                val dose = pill.dosageDescription.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                val macros = if (pill.calories > 0 || pill.fatGrams > 0f) {
                    " — ${pill.calories} kcal (F ${pill.fatGrams}g)"
                } else ""
                val micro = pill.micronutrientsSummary.takeIf { it.isNotBlank() }?.let { " | Actives: $it" } ?: ""
                val times = pill.reminderTimes.joinToString(", ").ifBlank { "No reminder time" }
                val takenDates = pillLogs.asSequence().filter { it.pillId == pill.id }.map { it.dateIso }.toSet()
                val created = runCatching { LocalDate.parse(pill.createdAtDateIso) }.getOrDefault(today)
                val earliest = today.minusDays(14)
                val start = if (created.isAfter(earliest)) created else earliest
                var scheduledCount = 0
                var missedCount = 0
                var d = start
                while (d.isBefore(today)) {
                    if (pill.isScheduledForDate(d)) {
                        scheduledCount++
                        if (d.toString() !in takenDates) missedCount++
                    }
                    d = d.plusDays(1)
                }
                val adherenceText = if (scheduledCount > 0) " | Last 14d: ${scheduledCount - missedCount}/$scheduledCount taken ($missedCount missed)" else ""
                sb.appendLine("- ${pill.name}$dose @ $times$macros$micro$adherenceText")
            }
            sb.appendLine()
        }

        // Section 5: AI Schema & Guide
        sb.appendLine("## 5. DoItAll App Mechanics & Strict JSON Import Guide")
        sb.appendLine()
        sb.appendLine(createAiGuideMarkdown(backupRoot))
        sb.appendLine()

        // Section 6: Raw Backup JSON
        sb.appendLine("## 6. Complete Raw Backup JSON (workouts_backup_latest.json)")
        sb.appendLine("```json")
        sb.appendLine(backupJson)
        sb.appendLine("```")
        return sb.toString()
    }

    fun exportAiBackupZipToFile(filename: String = defaultZipFilename()): File {
        val jsonStr = createUnifiedBackupJson()
        val rootObj = runCatching { JSONObject(jsonStr) }.getOrNull()
        val guideMd = createAiGuideMarkdown(rootObj)
        val jsonEntryName = defaultJsonFilename()

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(exportDir, filename)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry(jsonEntryName))
            zos.write(jsonStr.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("AI_README_AND_SCHEMA.md"))
            zos.write(guideMd.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return zipFile
    }

    fun shareAiBackupZip(filename: String = defaultZipFilename()) {
        val zipFile = exportAiBackupZipToFile(filename)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Export AI Coach Bundle (ZIP)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun restoreFromStream(inputStream: InputStream): Result<String> {
        return runCatching {
            val bytes = inputStream.readBytes()
            if (bytes.isEmpty()) {
                throw IllegalArgumentException("Selected file is empty.")
            }

            val isZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())

            val jsonString = if (isZip) {
                var extractedJson: String? = null
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory &&
                            !name.startsWith("__MACOSX") &&
                            name.lowercase(Locale.ROOT).endsWith(".json")
                        ) {
                            extractedJson = zis.readBytes().toString(Charsets.UTF_8)
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                extractedJson
                    ?: throw IllegalArgumentException("No .json backup file found inside the selected ZIP archive.")
            } else {
                String(bytes, Charsets.UTF_8)
            }

            restoreUnifiedBackup(jsonString).getOrThrow()
        }
    }

    fun restoreUnifiedBackup(jsonString: String): Result<String> {
        return runCatching {
            val root = JSONObject(jsonString)
            val partsRestored = mutableListOf<String>()

            if (root.has("workoutPlan")) {
                val planObj = root.getJSONObject("workoutPlan")
                if (workoutStore.importPlanJson(planObj)) {
                    val loadedPlan = workoutStore.loadPlanOnce()
                    if (loadedPlan != null) {
                        val legacyPlan = WorkoutRepository.convertToLegacyFormat(loadedPlan)
                        runBlocking {
                            stateStore.syncLogsFromPlan(legacyPlan)
                        }
                    }
                    partsRestored.add("Workout Plan")
                }
            }

            if (root.has("workoutHistory")) {
                val historyObj = root.getJSONObject("workoutHistory")
                historyRepo.importHistoryJson(historyObj)
                partsRestored.add("Workout History")
            }

            if (root.has("nutrition")) {
                val nutritionObj = root.getJSONObject("nutrition")
                nutritionRepo.importNutritionJson(nutritionObj)
                partsRestored.add("Nutrition & Meals")
            }

            root.optJSONObject("userProfile")?.let { pObj ->
                val current = nutritionRepo.userProfile.value
                nutritionRepo.saveUserProfile(
                    UserProfile(
                        heightCm = pObj.optDouble("heightCm", current.heightCm.toDouble()).toFloat(),
                        sex = pObj.optString("sex", current.sex),
                        birthDateIso = pObj.optString("birthDateIso", current.birthDateIso),
                        targetWeightKg = pObj.optDouble("targetWeightKg", current.targetWeightKg.toDouble()).toFloat(),
                        fitnessGoalSummary = pObj.optString("fitnessGoalSummary", current.fitnessGoalSummary)
                    )
                )
                if ("User Profile" !in partsRestored) partsRestored.add("User Profile")
            }

            if (partsRestored.isEmpty()) {
                throw IllegalArgumentException("No recognized backup data found in file.")
            }

            "Successfully restored: ${partsRestored.joinToString(", ")}"
        }
    }

    fun exportNutritionLogsCsv(): String {
        val logs = nutritionRepo.foodLogs.value
        val sb = StringBuilder()
        sb.append("Date,Time,Meal,Name,Calories,Protein(g),Carbs(g),Fat(g),PortionMultiplier,Source,Note\n")
        logs.forEach { log ->
            sb.append("\"${log.dateIso}\",")
            sb.append("\"${log.time}\",")
            sb.append("\"${log.mealSlot.displayName}\",")
            sb.append("\"${log.name.replace("\"", "\"\"")}\",")
            sb.append("${log.totalCalories},")
            sb.append("${String.format("%.1f", log.totalProtein)},")
            sb.append("${String.format("%.1f", log.totalCarbs)},")
            sb.append("${String.format("%.1f", log.totalFat)},")
            sb.append("${log.portionMultiplier},")
            sb.append("\"${log.source.name}\",")
            sb.append("\"${log.note.replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    fun exportWeightHistoryCsv(): String {
        val weights = nutritionRepo.weightLogs.value
        val sb = StringBuilder()
        sb.append("Date,Weight,Unit,Note\n")
        weights.forEach { w ->
            sb.append("\"${w.dateIso}\",")
            sb.append("${w.weight},")
            sb.append("\"${w.unit.name}\",")
            sb.append("\"${w.note.replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    fun exportWorkoutHistoryCsv(): String {
        val sessions = historyRepo.sessions.value
        val sb = StringBuilder()
        sb.append("Date,DayTitle,DurationMinutes,ExerciseName,Sets,Reps,Weight,Unit,IsBodyweight,UsesStraps,IsPR,Notes\n")
        sessions.forEach { s ->
            s.exercises.forEach { ex ->
                sb.append("\"${s.dateIso}\",")
                sb.append("\"${s.dayTitle.replace("\"", "\"\"")}\",")
                sb.append("${s.durationMinutes},")
                sb.append("\"${ex.exerciseName.replace("\"", "\"\"")}\",")
                sb.append("${ex.sets},")
                sb.append("\"${ex.repsSummary}\",")
                sb.append("\"${ex.weight}\",")
                sb.append("\"${ex.weightUnit.name}\",")
                sb.append("${ex.isBodyweight},")
                sb.append("${ex.usesStraps},")
                sb.append("${ex.isPR},")
                sb.append("\"${ex.comment.replace("\"", "\"\"")}\"\n")
            }
        }
        return sb.toString()
    }

    fun shareExportFile(filename: String, content: String, mimeType: String = "application/json") {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, filename)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Export Data").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

class BackupImportReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.mateoviscarra.doitall.ACTION_IMPORT_BACKUP") return
        val pendingResult = goAsync()
        Thread {
            try {
                val fileName = File(intent.getStringExtra("file_name") ?: "import_backup.json").name
                val file = File(context.cacheDir, fileName)
                if (file.exists()) {
                    val result = file.inputStream().use {
                        DataBackupManager(context.applicationContext).restoreFromStream(it)
                    }
                    val message = result.getOrElse { "ERROR: ${it.localizedMessage}" }
                    android.util.Log.i("BackupImportReceiver", "Import result: $message")
                    pendingResult.resultCode = if (result.isSuccess) android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED
                    pendingResult.resultData = message
                } else {
                    val msg = "File not found: ${file.absolutePath}"
                    android.util.Log.e("BackupImportReceiver", msg)
                    pendingResult.resultCode = android.app.Activity.RESULT_CANCELED
                    pendingResult.resultData = msg
                }
            } catch (e: Exception) {
                android.util.Log.e("BackupImportReceiver", "Import failed", e)
                pendingResult.resultCode = android.app.Activity.RESULT_CANCELED
                pendingResult.resultData = "ERROR: ${e.localizedMessage}"
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
