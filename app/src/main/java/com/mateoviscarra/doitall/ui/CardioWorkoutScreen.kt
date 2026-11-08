package com.mateoviscarra.doitall.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mateoviscarra.doitall.calendar.CalendarManager
import com.mateoviscarra.doitall.data.CategoryExercise
import com.mateoviscarra.doitall.data.CompletedExerciseRecord
import com.mateoviscarra.doitall.data.DayExercise
import com.mateoviscarra.doitall.data.ExerciseDefinition
import com.mateoviscarra.doitall.data.WorkoutDay
import com.mateoviscarra.doitall.data.WorkoutPlan
import com.mateoviscarra.doitall.data.WorkoutSessionRecord
import com.mateoviscarra.doitall.viewmodel.HistoryViewModel
import com.mateoviscarra.doitall.viewmodel.WorkoutPlanViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

enum class CardioIntensityType {
    SPEED,
    LEVEL
}

data class CardioActivityConfig(
    val exerciseId: String,
    val durationMinutes: Int = 30,
    val intensityType: CardioIntensityType = CardioIntensityType.SPEED,
    val speedValue: String = "",
    val speedUnit: String = "km/h",
    val levelValue: Int = 5,
    val notes: String = ""
) {
    fun formattedIntensity(): String {
        return when (intensityType) {
            CardioIntensityType.SPEED -> {
                val clean = speedValue.trim()
                if (clean.isNotEmpty()) "$clean $speedUnit" else ""
            }
            CardioIntensityType.LEVEL -> {
                if (levelValue > 0) "Level $levelValue" else ""
            }
        }
    }

    fun shortSummary(): String {
        val intensity = formattedIntensity()
        return buildString {
            append("$durationMinutes min")
            if (intensity.isNotBlank()) append(" • $intensity")
        }
    }
}

class CardioPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("cardio_prefs", Context.MODE_PRIVATE)

    fun getSelectedActivityId(defaultId: String): String {
        return prefs.getString("selected_activity_id", null) ?: defaultId
    }

    fun setSelectedActivityId(exerciseId: String) {
        prefs.edit().putString("selected_activity_id", exerciseId).apply()
    }

    fun getConfig(def: ExerciseDefinition): CardioActivityConfig {
        val raw = prefs.getString("config_${def.id}", null)
        if (raw != null) {
            try {
                val obj = JSONObject(raw)
                val typeStr = obj.optString("intensityType", "SPEED")
                val type = if (typeStr == "LEVEL") CardioIntensityType.LEVEL else CardioIntensityType.SPEED
                return CardioActivityConfig(
                    exerciseId = def.id,
                    durationMinutes = obj.optInt("durationMinutes", 30).coerceIn(1, 300),
                    intensityType = type,
                    speedValue = obj.optString("speedValue", ""),
                    speedUnit = obj.optString("speedUnit", "km/h"),
                    levelValue = obj.optInt("levelValue", 5).coerceIn(1, 30),
                    notes = obj.optString("notes", "")
                )
            } catch (_: Exception) {
            }
        }
        return seedDefaultConfig(def)
    }

    fun saveConfig(config: CardioActivityConfig) {
        val obj = JSONObject()
            .put("durationMinutes", config.durationMinutes)
            .put("intensityType", config.intensityType.name)
            .put("speedValue", config.speedValue)
            .put("speedUnit", config.speedUnit)
            .put("levelValue", config.levelValue)
            .put("notes", config.notes)
        prefs.edit().putString("config_${config.exerciseId}", obj.toString()).apply()
    }

    private fun seedDefaultConfig(def: ExerciseDefinition): CardioActivityConfig {
        val idLower = def.id.lowercase()
        val nameLower = def.name.lowercase()
        val notesLower = def.defaultNotes.lowercase()

        // Special defaults matching user's phone history & setup
        if (idLower == "incline_walking") {
            return CardioActivityConfig(
                exerciseId = def.id,
                durationMinutes = 45,
                intensityType = CardioIntensityType.SPEED,
                speedValue = "3.5",
                speedUnit = "km/h",
                levelValue = 5,
                notes = "15% incline"
            )
        }
        if (idLower.contains("stair") || nameLower.contains("stair")) {
            val lvlMatch = Regex("""(?i)level\s*(\d+)""").find(def.defaultNotes)
            val lvl = lvlMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            val mins = Regex("""\d+""").find(def.reps.ifBlank { def.defaultTime })?.value?.toIntOrNull() ?: 10
            return CardioActivityConfig(
                exerciseId = def.id,
                durationMinutes = mins,
                intensityType = CardioIntensityType.LEVEL,
                speedValue = "",
                speedUnit = "km/h",
                levelValue = lvl,
                notes = ""
            )
        }

        val parsedMins = Regex("""\d+""").find(def.reps.ifBlank { def.defaultTime })?.value?.toIntOrNull()
        val duration = when {
            parsedMins != null && parsedMins in 5..240 -> parsedMins
            idLower.contains("run") -> 20
            else -> 30
        }
        val isLevel = notesLower.contains("level")
        return CardioActivityConfig(
            exerciseId = def.id,
            durationMinutes = duration,
            intensityType = if (isLevel) CardioIntensityType.LEVEL else CardioIntensityType.SPEED,
            speedValue = "",
            speedUnit = "km/h",
            levelValue = 5,
            notes = if (isLevel) "" else def.defaultNotes
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardioWorkoutScreen(
    workoutPlan: WorkoutPlan,
    workoutDay: WorkoutDay,
    onBack: () -> Unit,
    onEditRoutine: () -> Unit,
    calendarManager: CalendarManager,
    planViewModel: WorkoutPlanViewModel? = null,
    historyViewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val cardioStore = remember { CardioPreferencesStore(context.applicationContext) }

    val activities = remember(workoutDay, workoutPlan) {
        workoutDay.slots
            .flatMap { it.exerciseIds }
            .distinct()
            .mapNotNull { id -> workoutPlan.catalog[id] }
    }

    var selectedId by remember(activities) {
        val defaultId = activities.firstOrNull()?.id ?: ""
        val saved = cardioStore.getSelectedActivityId(defaultId)
        val validId = if (activities.any { it.id == saved }) saved else defaultId
        mutableStateOf(validId)
    }

    val selectedDef = activities.find { it.id == selectedId } ?: activities.firstOrNull()

    var currentConfig by remember(selectedDef) {
        mutableStateOf(
            selectedDef?.let { cardioStore.getConfig(it) }
                ?: CardioActivityConfig(exerciseId = selectedId)
        )
    }

    fun updateConfig(transform: (CardioActivityConfig) -> CardioActivityConfig) {
        val updated = transform(currentConfig)
        currentConfig = updated
        cardioStore.saveConfig(updated)
    }

    var showFinishDialog by remember { mutableStateOf(false) }
    var showAddActivityDialog by remember { mutableStateOf(false) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cardio",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = onEditRoutine) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Cardio Options"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectedDef != null) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showFinishDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Log ${selectedDef.name} (${currentConfig.durationMinutes} min)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Single-Activity Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select Activity",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { showAddActivityDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Option", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    activities.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { def ->
                                val isSelected = def.id == selectedDef?.id
                                val savedCfg = if (isSelected) currentConfig else cardioStore.getConfig(def)
                                Card(
                                    onClick = {
                                        selectedId = def.id
                                        cardioStore.setSelectedActivityId(def.id)
                                        currentConfig = cardioStore.getConfig(def)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    border = if (isSelected) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = def.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = savedCfg.shortSummary(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                            }
                                        )
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 2. Duration Section
            if (selectedDef != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Duration",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Syncs to History & Calendar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            // Stepper Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        updateConfig {
                                            it.copy(durationMinutes = (it.durationMinutes - 5).coerceAtLeast(1))
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("-5m", fontWeight = FontWeight.SemiBold)
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        updateConfig {
                                            it.copy(durationMinutes = (it.durationMinutes - 1).coerceAtLeast(1))
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease 1 min")
                                }

                                Surface(
                                    onClick = { showCustomDurationDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "${currentConfig.durationMinutes} min",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        updateConfig {
                                            it.copy(durationMinutes = (it.durationMinutes + 1).coerceAtMost(300))
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase 1 min")
                                }

                                OutlinedButton(
                                    onClick = {
                                        updateConfig {
                                            it.copy(durationMinutes = (it.durationMinutes + 5).coerceAtMost(300))
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("+5m", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Quick Duration Presets
                            val durationPresets = listOf(10, 15, 20, 30, 45, 60)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                durationPresets.forEach { mins ->
                                    FilterChip(
                                        selected = currentConfig.durationMinutes == mins,
                                        onClick = { updateConfig { it.copy(durationMinutes = mins) } },
                                        label = { Text("${mins}m") }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Intensity Section (Speed vs Level)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (currentConfig.intensityType == CardioIntensityType.LEVEL) {
                                            Icons.Default.Stairs
                                        } else {
                                            Icons.Default.Speed
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Intensity",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = currentConfig.intensityType == CardioIntensityType.SPEED,
                                        onClick = {
                                            updateConfig { it.copy(intensityType = CardioIntensityType.SPEED) }
                                        },
                                        label = { Text("Speed") }
                                    )
                                    FilterChip(
                                        selected = currentConfig.intensityType == CardioIntensityType.LEVEL,
                                        onClick = {
                                            updateConfig { it.copy(intensityType = CardioIntensityType.LEVEL) }
                                        },
                                        label = { Text("Level") }
                                    )
                                }
                            }

                            if (currentConfig.intensityType == CardioIntensityType.SPEED) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = currentConfig.speedValue,
                                        onValueChange = { newVal ->
                                            updateConfig { it.copy(speedValue = newVal) }
                                        },
                                        label = { Text("Speed (e.g. 3.5, 8.0)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )

                                    FilterChip(
                                        selected = currentConfig.speedUnit == "km/h",
                                        onClick = { updateConfig { it.copy(speedUnit = "km/h") } },
                                        label = { Text("km/h") }
                                    )
                                    FilterChip(
                                        selected = currentConfig.speedUnit == "mph",
                                        onClick = { updateConfig { it.copy(speedUnit = "mph") } },
                                        label = { Text("mph") }
                                    )
                                }
                            } else {
                                // Level stepper for Stair master / Stationary bike / Elliptical
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            updateConfig {
                                                it.copy(levelValue = (it.levelValue - 1).coerceAtLeast(1))
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease Level")
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    ) {
                                        Text(
                                            text = "Level ${currentConfig.levelValue}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                                        )
                                    }

                                    FilledTonalIconButton(
                                        onClick = {
                                            updateConfig {
                                                it.copy(levelValue = (it.levelValue + 1).coerceAtMost(30))
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase Level")
                                    }
                                }

                                val levelPresets = listOf(3, 5, 6, 8, 10, 12)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    levelPresets.forEach { lvl ->
                                        FilterChip(
                                            selected = currentConfig.levelValue == lvl,
                                            onClick = { updateConfig { it.copy(levelValue = lvl) } },
                                            label = { Text("Lvl $lvl") }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = currentConfig.notes,
                                onValueChange = { newNotes ->
                                    updateConfig { it.copy(notes = newNotes) }
                                },
                                label = {
                                    Text(
                                        if (currentConfig.intensityType == CardioIntensityType.SPEED) {
                                            "Incline / Notes (optional, e.g. 15% incline)"
                                        } else {
                                            "Notes (optional)"
                                        }
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomDurationDialog) {
        var textVal by remember(currentConfig.durationMinutes) {
            mutableStateOf(currentConfig.durationMinutes.toString())
        }
        AlertDialog(
            onDismissRequest = { showCustomDurationDialog = false },
            title = { Text("Set Cardio Duration") },
            text = {
                OutlinedTextField(
                    value = textVal,
                    onValueChange = { textVal = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = textVal.toIntOrNull()?.coerceIn(1, 300)
                        if (mins != null) {
                            updateConfig { it.copy(durationMinutes = mins) }
                        }
                        showCustomDurationDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDurationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddActivityDialog) {
        var newName by remember { mutableStateOf("") }
        var defaultMins by remember { mutableStateOf("20") }
        var defaultMode by remember { mutableStateOf(CardioIntensityType.SPEED) }

        AlertDialog(
            onDismissRequest = { showAddActivityDialog = false },
            title = { Text("Add Cardio Option") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Activity Name (e.g. Rowing, Elliptical)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = defaultMins,
                        onValueChange = { defaultMins = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Default Duration (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Intensity:", style = MaterialTheme.typography.bodyMedium)
                        FilterChip(
                            selected = defaultMode == CardioIntensityType.SPEED,
                            onClick = { defaultMode = CardioIntensityType.SPEED },
                            label = { Text("Speed") }
                        )
                        FilterChip(
                            selected = defaultMode == CardioIntensityType.LEVEL,
                            onClick = { defaultMode = CardioIntensityType.LEVEL },
                            label = { Text("Level") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = newName.trim()
                        if (cleanName.isNotEmpty() && planViewModel != null) {
                            val id = cleanName.lowercase().replace(Regex("""[^a-z0-9]+"""), "_")
                            val mins = defaultMins.toIntOrNull()?.coerceIn(1, 300) ?: 20
                            val newEx = CategoryExercise(
                                id = id,
                                name = cleanName,
                                defaultSets = 1,
                                defaultReps = "${mins}min",
                                defaultLoad = "",
                                isTimed = true,
                                defaultTime = "${mins}min"
                            )
                            planViewModel.addExerciseToCategory("accessory", newEx)
                            val currentPlan = planViewModel.plan.value
                            val cardioDay = currentPlan.days.find { it.day.equals(workoutDay.day, ignoreCase = true) }
                            if (cardioDay != null) {
                                val updatedDay = cardioDay.copy(
                                    exercises = cardioDay.exercises + DayExercise(exerciseId = id)
                                )
                                planViewModel.updateDay(cardioDay.day, updatedDay)
                            }
                            cardioStore.saveConfig(
                                CardioActivityConfig(
                                    exerciseId = id,
                                    durationMinutes = mins,
                                    intensityType = defaultMode
                                )
                            )
                            selectedId = id
                            cardioStore.setSelectedActivityId(id)
                        }
                        showAddActivityDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddActivityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFinishDialog && selectedDef != null) {
        val intensityStr = currentConfig.formattedIntensity()
        val previewRecord = CompletedExerciseRecord(
            exerciseId = selectedDef.id,
            exerciseName = selectedDef.name,
            sets = 1,
            repsSummary = "${currentConfig.durationMinutes} min",
            weight = intensityStr,
            numericWeight = 0.0,
            isBodyweight = false,
            usesStraps = false,
            comment = currentConfig.notes.trim(),
            isPR = false,
            isCardio = true
        )

        UnifiedFinishWorkoutDialog(
            workoutTitle = "Cardio",
            completedExercises = listOf(previewRecord),
            isCalendarConnected = calendarManager.isConnected(),
            initialDurationMinutes = currentConfig.durationMinutes,
            isCardioSession = true,
            onDismiss = { showFinishDialog = false },
            onConfirm = { _, syncToCalendar, finalDurationMinutes, timingOption, customDate, customStartTime ->
                showFinishDialog = false
                // Ensure config & record match the exact confirmed duration
                if (finalDurationMinutes != currentConfig.durationMinutes) {
                    updateConfig { it.copy(durationMinutes = finalDurationMinutes) }
                }
                val finalRecord = previewRecord.copy(
                    repsSummary = "$finalDurationMinutes min"
                )
                val sessionDate = if (timingOption == WorkoutTimingOption.CUSTOM) {
                    customDate.toString()
                } else {
                    LocalDate.now().toString()
                }

                val session = WorkoutSessionRecord(
                    id = UUID.randomUUID().toString(),
                    dayTitle = "Cardio",
                    dateIso = sessionDate,
                    durationMinutes = finalDurationMinutes,
                    exercises = listOf(finalRecord)
                )
                historyViewModel.recordSession(session)

                if (syncToCalendar) {
                    isSyncing = true
                    scope.launch {
                        val (date, startHour, startMinute) = when (timingOption) {
                            WorkoutTimingOption.CUSTOM -> Triple(customDate, customStartTime.hour, customStartTime.minute)
                            WorkoutTimingOption.FINISHED_NOW -> {
                                val now = LocalDateTime.now()
                                val start = now.minusMinutes(finalDurationMinutes.toLong())
                                Triple(start.toLocalDate(), start.hour, start.minute)
                            }
                            WorkoutTimingOption.STARTED_NOW -> {
                                val now = LocalTime.now()
                                Triple(LocalDate.now(), now.hour, now.minute)
                            }
                        }

                        val description = buildString {
                            append("${selectedDef.name}: $finalDurationMinutes min")
                            if (intensityStr.isNotBlank()) {
                                append(" • $intensityStr")
                            }
                            if (currentConfig.notes.isNotBlank()) {
                                append(" (${currentConfig.notes.trim()})")
                            }
                        }

                        val result = calendarManager.createWorkoutEvent(
                            title = "Cardio (${selectedDef.name})",
                            date = date,
                            startHour = startHour,
                            startMinute = startMinute,
                            durationMinutes = finalDurationMinutes,
                            description = description
                        )
                        result.fold(
                            onSuccess = {
                                snackbarHostState.showSnackbar("Cardio logged to History and Google Calendar!")
                            },
                            onFailure = {
                                snackbarHostState.showSnackbar("Cardio logged to History, but Calendar sync failed: ${it.message}")
                            }
                        )
                        isSyncing = false
                    }
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Cardio logged to History!")
                    }
                }
            }
        )
    }
}
