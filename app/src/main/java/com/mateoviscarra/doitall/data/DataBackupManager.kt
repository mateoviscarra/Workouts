package com.mateoviscarra.doitall.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mateoviscarra.doitall.data.persist.NewWorkoutStore
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class DataBackupManager(private val context: Context) {
    private val workoutStore = NewWorkoutStore(context)
    private val historyRepo = WorkoutHistoryRepository(context)
    private val nutritionRepo = NutritionRepository(context)

    fun createUnifiedBackupJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportDate", LocalDate.now().toString())
        root.put("workoutPlan", workoutStore.exportPlanJson())
        root.put("workoutHistory", historyRepo.exportHistoryJson())
        root.put("nutrition", nutritionRepo.exportNutritionJson())
        return root.toString(2)
    }

    fun restoreUnifiedBackup(jsonString: String): Result<String> {
        return runCatching {
            val root = JSONObject(jsonString)
            val partsRestored = mutableListOf<String>()

            if (root.has("workoutPlan")) {
                val planObj = root.getJSONObject("workoutPlan")
                if (workoutStore.importPlanJson(planObj)) {
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
