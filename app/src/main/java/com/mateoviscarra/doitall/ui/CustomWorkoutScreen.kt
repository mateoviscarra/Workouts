package com.mateoviscarra.doitall.ui

import android.content.Context
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mateoviscarra.doitall.calendar.CalendarManager
import com.mateoviscarra.doitall.data.CompletedExerciseRecord
import com.mateoviscarra.doitall.data.WorkoutSessionRecord
import com.mateoviscarra.doitall.data.formatExerciseWeight
import com.mateoviscarra.doitall.data.persist.WeightUnit
import com.mateoviscarra.doitall.viewmodel.CustomExercise
import com.mateoviscarra.doitall.viewmodel.CustomWorkoutViewModel
import com.mateoviscarra.doitall.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomWorkoutScreen(
    calendarManager: CalendarManager,
    onBack: () -> Unit,
    viewModel: CustomWorkoutViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    historyViewModel: HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Custom Workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (exercises.isNotEmpty()) {
                        IconButton(
                            onClick = { showFinishDialog = true },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Finish Workout",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (exercises.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No exercises yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap the button below to add exercises",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            itemsIndexed(exercises) { index, exercise ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (exercise.isDone) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = {
                                viewModel.toggleDone(index)
                            }) {
                                Icon(
                                    imageVector = if (exercise.isDone) {
                                        Icons.Filled.CheckCircle
                                    } else {
                                        Icons.Outlined.CheckCircle
                                    },
                                    contentDescription = if (exercise.isDone) "Mark as not done" else "Mark as done",
                                    tint = if (exercise.isDone) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = exercise.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (exercise.isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.alpha(if (exercise.isDone) 0.6f else 1f)
                                )
                                val weightStr = formatExerciseWeight(
                                    weight = exercise.weight,
                                    isBodyweight = exercise.isBodyweight,
                                    unitLabel = exercise.weightUnit.name.lowercase()
                                )
                                Text(
                                    text = "${exercise.sets} sets × ${exercise.reps} reps" +
                                            if (weightStr.isNotEmpty()) " @ $weightStr" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.alpha(if (exercise.isDone) 0.6f else 1f)
                                )
                            }
                        }
                        IconButton(onClick = {
                            viewModel.removeExercise(index)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove exercise",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            item {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Add Exercise")
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddExerciseDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { exercise ->
                viewModel.addExercise(exercise)
                showAddDialog = false
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Custom Workout") },
            text = { Text("Are you sure you want to remove all exercises from this custom workout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showFinishDialog) {
        val completedExercises = remember(exercises) {
            val doneList = exercises.filter { it.isDone }
            val toRecord = if (doneList.isNotEmpty()) doneList else exercises
            toRecord.map { ex ->
                val numMatch = Regex("""[+-]?[\d.]+""").find(ex.weight)
                val numWeight = numMatch?.value?.toDoubleOrNull() ?: 0.0
                CompletedExerciseRecord(
                    exerciseId = ex.name.lowercase().trim().replace(Regex("""\s+"""), "_"),
                    exerciseName = ex.name,
                    sets = ex.sets,
                    repsSummary = ex.reps.toString(),
                    weight = ex.weight,
                    numericWeight = numWeight,
                    weightUnit = ex.weightUnit,
                    isBodyweight = ex.isBodyweight,
                    usesStraps = false,
                    comment = ""
                )
            }
        }

        UnifiedFinishWorkoutDialog(
            workoutTitle = "Custom Workout",
            completedExercises = completedExercises,
            isCalendarConnected = calendarManager.isConnected(),
            onDismiss = { showFinishDialog = false },
            onConfirm = { uncheckAfter, syncToCalendar, durationMinutes, timingOption, customDate, customStartTime ->
                showFinishDialog = false
                val sessionDate = if (timingOption == WorkoutTimingOption.CUSTOM) customDate.toString() else LocalDate.now().toString()
                val session = WorkoutSessionRecord(
                    id = UUID.randomUUID().toString(),
                    dayTitle = "Custom Workout",
                    dateIso = sessionDate,
                    durationMinutes = durationMinutes,
                    exercises = completedExercises
                )
                // 1. Always save to local history first
                historyViewModel.recordSession(session)
                if (uncheckAfter) {
                    viewModel.uncheckAll()
                }

                // 2. Sync to calendar if requested
                if (syncToCalendar) {
                    isLoading = true
                    scope.launch {
                        val (date, startHour, startMinute) = when (timingOption) {
                            WorkoutTimingOption.CUSTOM -> Triple(customDate, customStartTime.hour, customStartTime.minute)
                            WorkoutTimingOption.FINISHED_NOW -> {
                                val now = LocalTime.now()
                                val start = now.minusMinutes(durationMinutes.toLong())
                                Triple(LocalDate.now(), start.hour, start.minute)
                            }
                            WorkoutTimingOption.STARTED_NOW -> {
                                val now = LocalTime.now()
                                Triple(LocalDate.now(), now.hour, now.minute)
                            }
                        }

                        val description = buildString {
                            exercises.forEachIndexed { index, exercise ->
                                append("${exercise.name}: ${exercise.sets} sets × ${exercise.reps} reps")
                                val weightStr = formatExerciseWeight(
                                    weight = exercise.weight,
                                    isBodyweight = exercise.isBodyweight,
                                    unitLabel = exercise.weightUnit.name.lowercase()
                                )
                                if (weightStr.isNotEmpty()) {
                                    append(" @ $weightStr")
                                }
                                if (index < exercises.size - 1) append("\n")
                            }
                        }

                        val result = calendarManager.createWorkoutEvent(
                            title = "Custom Workout",
                            date = date,
                            startHour = startHour,
                            startMinute = startMinute,
                            durationMinutes = durationMinutes,
                            description = description
                        )
                        result.fold(
                            onSuccess = {
                                snackbarHostState.showSnackbar("Workout saved to History and added to Google Calendar!")
                            },
                            onFailure = {
                                snackbarHostState.showSnackbar("Workout saved to History, but Google Calendar sync failed: ${it.message}")
                            }
                        )
                        isLoading = false
                    }
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Workout saved to History!")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onAdd: (CustomExercise) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sets by remember { mutableIntStateOf(3) }
    var reps by remember { mutableIntStateOf(10) }
    var weight by remember { mutableStateOf("") }
    var isBodyweight by remember { mutableStateOf(false) }
    var weightUnit by remember { mutableStateOf(WeightUnit.KG) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exercise") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text(
                    text = "Sets: $sets",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = sets.toFloat(),
                    onValueChange = { sets = it.toInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "Reps: $reps",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = reps.toFloat(),
                    onValueChange = { reps = it.toInt() },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isBodyweight,
                        onCheckedChange = { isBodyweight = it }
                    )
                    Text("Bodyweight exercise")
                }
                
                val weightInteractionSource = remember { MutableInteractionSource() }
                androidx.compose.runtime.LaunchedEffect(weightInteractionSource) {
                    weightInteractionSource.interactions.collect {
                        if (it is FocusInteraction.Focus && weight.isNotEmpty()) {
                            weight = ""
                        }
                    }
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { 
                        Text(if (isBodyweight) "Added weight (optional)" else "Weight")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = weightInteractionSource,
                    singleLine = true
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = weightUnit == WeightUnit.KG,
                        onClick = { weightUnit = WeightUnit.KG },
                        label = { Text("kg") }
                    )
                    FilterChip(
                        selected = weightUnit == WeightUnit.LBS,
                        onClick = { weightUnit = WeightUnit.LBS },
                        label = { Text("lbs") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(
                            CustomExercise(
                                name = name.trim(),
                                sets = sets,
                                reps = reps,
                                weight = weight.trim(),
                                isBodyweight = isBodyweight,
                                weightUnit = weightUnit
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
