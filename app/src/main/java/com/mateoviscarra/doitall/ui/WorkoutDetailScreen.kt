package com.mateoviscarra.doitall.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.items
import com.mateoviscarra.doitall.data.CategoryExercise
import com.mateoviscarra.doitall.data.DayExercise
import com.mateoviscarra.doitall.viewmodel.WorkoutPlanViewModel
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateoviscarra.doitall.calendar.CalendarManager
import com.mateoviscarra.doitall.data.CompletedExerciseRecord
import com.mateoviscarra.doitall.data.ExerciseLocation
import com.mateoviscarra.doitall.data.PersonalRecord
import com.mateoviscarra.doitall.data.WorkoutDay
import com.mateoviscarra.doitall.data.WorkoutPlan
import com.mateoviscarra.doitall.data.WorkoutSessionRecord
import com.mateoviscarra.doitall.data.WorkoutSlot
import com.mateoviscarra.doitall.data.formatExerciseWeight
import com.mateoviscarra.doitall.data.persist.ExerciseLogState
import com.mateoviscarra.doitall.viewmodel.HistoryViewModel
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import com.mateoviscarra.doitall.data.persist.SlotLogState
import com.mateoviscarra.doitall.data.persist.WeightUnit
import com.mateoviscarra.doitall.data.persist.WorkoutStateStore
import com.mateoviscarra.doitall.data.persist.defaultDayLog
import com.mateoviscarra.doitall.data.persist.defaultExerciseLog
import com.mateoviscarra.doitall.data.persist.mergedExerciseLogs
import com.mateoviscarra.doitall.data.persist.PersistRootV2
import com.mateoviscarra.doitall.data.persist.ResolvedDayUi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkoutDetailScreen(
    workoutPlan: WorkoutPlan,
    dayKey: String,
    workoutDay: WorkoutDay,
    onBack: () -> Unit,
    onEditVariation: (slotIndex: Int, pageIndex: Int) -> Unit,
    onEditRoutine: (() -> Unit)? = null,
    calendarManager: CalendarManager,
    planViewModel: WorkoutPlanViewModel? = null,
    viewModel: com.mateoviscarra.doitall.viewmodel.WorkoutDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = dayKey),
    historyViewModel: HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSyncing by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showResetCheckmarksDialog by remember { mutableStateOf(false) }
    var quickEditExercise by remember { mutableStateOf<Triple<String, String, ExerciseLogState>?>(null) }
    var quickEditSlotIndex by remember { mutableStateOf<Int?>(null) }
    var showPairSupersetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(dayKey, workoutDay, workoutPlan) {
        viewModel.loadDay(dayKey, workoutDay, workoutPlan)
    }

    val initialResolved = remember(workoutDay, workoutPlan) {
        ResolvedDayUi(
            dayLog = defaultDayLog(workoutDay),
            exerciseLogs = mergedExerciseLogs(workoutPlan, PersistRootV2(emptyMap(), emptyMap()))
        )
    }

    val resolvedState by viewModel.resolvedDayUi.collectAsStateWithLifecycle()
    val resolved = resolvedState ?: initialResolved

    val locationsById = remember(workoutPlan) { workoutPlan.exerciseLocationsById() }

    val slotGroups = remember(workoutDay.slots) {
        val groups = mutableListOf<WorkoutSlotGroup>()
        var currentSupersetId: String? = null
        var currentIsSequential = false
        val currentSupersetList = mutableListOf<Pair<Int, WorkoutSlot>>()

        workoutDay.slots.forEachIndexed { index, slot ->
            if (slot.supersetId != null) {
                if (currentSupersetId == slot.supersetId) {
                    currentSupersetList.add(index to slot)
                    if (slot.isSequential) currentIsSequential = true
                } else {
                    if (currentSupersetList.isNotEmpty()) {
                        groups.add(WorkoutSlotGroup.Superset(currentSupersetId!!, currentIsSequential, currentSupersetList.toList()))
                        currentSupersetList.clear()
                    }
                    currentSupersetId = slot.supersetId
                    currentIsSequential = slot.isSequential
                    currentSupersetList.add(index to slot)
                }
            } else {
                if (currentSupersetList.isNotEmpty()) {
                    groups.add(WorkoutSlotGroup.Superset(currentSupersetId!!, currentIsSequential, currentSupersetList.toList()))
                    currentSupersetList.clear()
                    currentSupersetId = null
                }
                groups.add(WorkoutSlotGroup.Single(index, slot))
            }
        }
        if (currentSupersetList.isNotEmpty()) {
            groups.add(WorkoutSlotGroup.Superset(currentSupersetId!!, currentIsSequential, currentSupersetList.toList()))
        }
        groups
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = workoutDay.day,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
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
                    if (onEditRoutine != null) {
                        IconButton(onClick = onEditRoutine) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Routine"
                            )
                        }
                    }
                    // Unified Finish Workout button (saves to history + syncs to calendar)
                    if (workoutDay.slots.isNotEmpty()) {
                        IconButton(
                            onClick = { showFinishDialog = true },
                            enabled = !isSyncing
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Finish Workout",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // Reset / Clear Checkmarks button
                    IconButton(
                        onClick = { showResetCheckmarksDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset checkmarks"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (workoutDay.slots.isNotEmpty()) {
                val completedSlots = workoutDay.slots.count { slot ->
                    slot.exerciseIds.any { id -> id in resolved.dayLog.doneExercises }
                }
                val totalSlots = workoutDay.slots.size
                val progress = if (totalSlots > 0) completedSlots.toFloat() / totalSlots else 0f
                val isAllComplete = totalSlots > 0 && completedSlots == totalSlots

                Surface(
                    color = if (isAllComplete) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isAllComplete) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = if (isAllComplete) "Workout Complete!" else "Progress",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAllComplete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "$completedSlots of $totalSlots slots done (${(progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (isAllComplete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    val muscleText = if (workoutDay.muscleGroups.isEmpty()) {
                        "General"
                    } else {
                        workoutDay.muscleGroups.joinToString()
                    }
                    Text(
                        text = "Muscle groups: $muscleText",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (workoutDay.notes.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            workoutDay.notes.forEach { note ->
                                Text(
                                    text = "• $note",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (workoutDay.slots.isEmpty()) {
                    item {
                        Text(
                            text = "No exercises listed for this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(slotGroups) { group ->
                        when (group) {
                            is WorkoutSlotGroup.Single -> {
                                val slot = group.slot
                                val slotIndex = group.slotIndex
                                val slotState = resolved.dayLog.slots[slotIndex.toString()]
                                    ?: com.mateoviscarra.doitall.data.persist.normalizeSlotState(slot, null)
                                ExerciseSlotRow(
                                    workoutPlan = workoutPlan,
                                    dayKey = dayKey,
                                    slot = slot,
                                    slotState = slotState,
                                    exerciseLogs = resolved.exerciseLogs,
                                    locationsById = locationsById,
                                    doneExercises = resolved.dayLog.doneExercises,
                                    supersetLabel = null,
                                    onPageSelected = { page ->
                                        viewModel.updateSelectedPage(slotIndex, page)
                                    },
                                    onEdit = { exerciseId, displayName, log ->
                                        quickEditSlotIndex = slotIndex
                                        quickEditExercise = Triple(exerciseId, displayName, log)
                                    },
                                    onToggleDone = { exerciseId ->
                                        viewModel.toggleExerciseDone(exerciseId)
                                    }
                                )
                            }
                            is WorkoutSlotGroup.Superset -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Bubble Header
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Link,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                                Text(
                                                    text = "SUPERSET",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Text(
                                                    text = "(${group.items.size})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Surface(
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (group.isSequential) "Sequential • A then B" else "Alternating • Set-by-set",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        // Exercises within the bubble
                                        group.items.forEachIndexed { subIndex, (slotIndex, slot) ->
                                            val supersetLabel = if (group.isSequential) {
                                                "PART ${subIndex + 1} • " + (if (subIndex == 0) "ALL SETS FIRST" else "AFTER PART $subIndex")
                                            } else {
                                                "SUPERSET " + ('A' + subIndex)
                                            }
                                            val slotState = resolved.dayLog.slots[slotIndex.toString()]
                                                ?: com.mateoviscarra.doitall.data.persist.normalizeSlotState(slot, null)
                                            ExerciseSlotRow(
                                                workoutPlan = workoutPlan,
                                                dayKey = dayKey,
                                                slot = slot,
                                                slotState = slotState,
                                                exerciseLogs = resolved.exerciseLogs,
                                                locationsById = locationsById,
                                                doneExercises = resolved.dayLog.doneExercises,
                                                supersetLabel = supersetLabel,
                                                onPageSelected = { page ->
                                                    viewModel.updateSelectedPage(slotIndex, page)
                                                },
                                                onEdit = { exerciseId, displayName, log ->
                                                    quickEditSlotIndex = slotIndex
                                                    quickEditExercise = Triple(exerciseId, displayName, log)
                                                },
                                                onToggleDone = { exerciseId ->
                                                    viewModel.toggleExerciseDone(exerciseId)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResetCheckmarksDialog) {
        AlertDialog(
            onDismissRequest = { showResetCheckmarksDialog = false },
            title = { Text("Reset Checkmarks?") },
            text = { Text("Are you sure you want to uncheck all exercises for this workout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.uncheckAllForDay()
                        showResetCheckmarksDialog = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetCheckmarksDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFinishDialog) {
        val completedExercises = remember(resolved, workoutDay) {
            val list = mutableListOf<CompletedExerciseRecord>()
            for (slotIndex in workoutDay.slots.indices) {
                val slot = workoutDay.slots[slotIndex]
                val slotState = resolved.dayLog.slots[slotIndex.toString()]
                    ?: com.mateoviscarra.doitall.data.persist.normalizeSlotState(slot, null)
                val selectedPageIndex = slotState.selectedPage.coerceIn(0, slot.exerciseIds.size - 1)
                val exerciseId = slotState.pageBindings.getOrNull(selectedPageIndex) ?: slot.exerciseIds[selectedPageIndex]

                val isDone = exerciseId in resolved.dayLog.doneExercises
                val hasDoneAny = resolved.dayLog.doneExercises.isNotEmpty()
                if (!hasDoneAny || isDone) {
                    val def = workoutPlan.catalog[exerciseId]
                    val name = def?.name ?: exerciseId
                    val log = resolved.exerciseLogs[exerciseId] ?: (def?.let { defaultExerciseLog(it) } ?: continue)
                    val repsStr = when {
                        log.isTimed -> log.timeTarget.ifEmpty { "60s" }
                        log.usePerSetReps -> log.repsPerSet?.joinToString(", ") ?: "10"
                        else -> "${log.repsSingle ?: 10}"
                    }
                    val numMatch = Regex("""[+-]?[\d.]+""").find(log.weight)
                    val numWeight = numMatch?.value?.toDoubleOrNull() ?: 0.0

                    list.add(
                        CompletedExerciseRecord(
                            exerciseId = exerciseId,
                            exerciseName = name,
                            sets = log.sets,
                            repsSummary = repsStr,
                            weight = log.weight,
                            numericWeight = numWeight,
                            weightUnit = log.weightUnit,
                            isBodyweight = log.isBodyweight,
                            usesStraps = log.usesStraps,
                            comment = log.comment
                        )
                    )
                }
            }
            list
        }

        UnifiedFinishWorkoutDialog(
            workoutTitle = dayKey,
            completedExercises = completedExercises,
            isCalendarConnected = calendarManager.isConnected(),
            onDismiss = { showFinishDialog = false },
            onConfirm = { uncheckAfter, syncToCalendar, durationMinutes, timingOption, customDate, customStartTime ->
                showFinishDialog = false
                val sessionDate = if (timingOption == WorkoutTimingOption.CUSTOM) customDate.toString() else LocalDate.now().toString()
                val session = WorkoutSessionRecord(
                    id = UUID.randomUUID().toString(),
                    dayTitle = dayKey,
                    dateIso = sessionDate,
                    durationMinutes = durationMinutes,
                    exercises = completedExercises
                )
                // 1. Always save to local history first
                historyViewModel.recordSession(session)
                if (uncheckAfter) {
                    viewModel.uncheckAllForDay()
                }

                // 2. Sync to calendar if requested
                if (syncToCalendar) {
                    isSyncing = true
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
                            if (completedExercises.isEmpty()) {
                                append("No exercises logged")
                            } else {
                                completedExercises.forEachIndexed { index, ex ->
                                    append("${ex.exerciseName}: ${ex.sets} sets × ${ex.repsSummary} reps")
                                    val weightStr = formatExerciseWeight(
                                        weight = ex.weight,
                                        isBodyweight = ex.isBodyweight,
                                        unitLabel = ex.weightUnit.name.lowercase()
                                    )
                                    if (weightStr.isNotEmpty()) {
                                        append(" @ $weightStr")
                                    }
                                    if (ex.usesStraps) {
                                        append(" (Straps)")
                                    }
                                    if (index < completedExercises.size - 1) {
                                        append("\n")
                                    }
                                }
                            }
                        }

                        val result = calendarManager.createWorkoutEvent(
                            title = dayKey,
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
                        isSyncing = false
                    }
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Workout saved to History!")
                    }
                }
            }
        )
    }

    val currentSlot = quickEditSlotIndex?.let { workoutDay.slots.getOrNull(it) }
    val currentSupersetId = currentSlot?.supersetId
    val pairedSlot = if (currentSupersetId != null && quickEditSlotIndex != null) {
        workoutDay.slots.filterIndexed { i, s -> i != quickEditSlotIndex && s.supersetId == currentSupersetId }.firstOrNull()
    } else null
    val pairedExerciseName = pairedSlot?.exerciseIds?.firstOrNull()?.let { workoutPlan.catalog[it]?.name ?: it }

    val otherSlotsInDay = workoutDay.slots.mapIndexedNotNull { i, s ->
        if (i != quickEditSlotIndex) {
            val name = s.exerciseIds.firstOrNull()?.let { workoutPlan.catalog[it]?.name ?: it } ?: "Exercise ${i + 1}"
            i to name
        } else null
    }

    quickEditExercise?.let { (exerciseId, displayName, log) ->
        QuickEditExerciseSheet(
            displayName = displayName,
            initialLog = log,
            pairedSlot = pairedSlot,
            pairedExerciseName = pairedExerciseName,
            canSuperset = planViewModel != null,
            onUnlinkSuperset = {
                if (planViewModel != null && currentSupersetId != null) {
                    val currentPlan = planViewModel.plan.value
                    val dayObj = currentPlan.days.find { it.day == dayKey }
                    if (dayObj != null) {
                        val updatedExercises = dayObj.exercises.map { ex ->
                            if (ex.supersetId == currentSupersetId) ex.copy(supersetId = null, isSequential = false) else ex
                        }
                        planViewModel.updateDay(dayKey, dayObj.copy(exercises = updatedExercises))
                    }
                }
            },
            onToggleSupersetMode = { isSeq ->
                if (planViewModel != null && currentSupersetId != null) {
                    val currentPlan = planViewModel.plan.value
                    val dayObj = currentPlan.days.find { it.day == dayKey }
                    if (dayObj != null) {
                        val updatedExercises = dayObj.exercises.map { ex ->
                            if (ex.supersetId == currentSupersetId) ex.copy(isSequential = isSeq) else ex
                        }
                        planViewModel.updateDay(dayKey, dayObj.copy(exercises = updatedExercises))
                    }
                }
            },
            onOpenPairDialog = { showPairSupersetDialog = true },
            onDismiss = {
                quickEditExercise = null
                quickEditSlotIndex = null
            },
            onSave = { updatedLog ->
                viewModel.updateExerciseLog(exerciseId, updatedLog)
                quickEditExercise = null
                quickEditSlotIndex = null
            }
        )
    }

    if (showPairSupersetDialog && quickEditExercise != null) {
        val allCatalogExercises = planViewModel?.plan?.value?.allExercises() ?: emptyList()
        PairSupersetDialog(
            targetExerciseName = quickEditExercise!!.second,
            otherDaySlots = otherSlotsInDay,
            allCatalogExercises = allCatalogExercises,
            onDismiss = { showPairSupersetDialog = false },
            onPairWithExisting = { targetIdx, isSeq ->
                showPairSupersetDialog = false
                if (planViewModel != null && quickEditSlotIndex != null) {
                    val currentPlan = planViewModel.plan.value
                    val dayObj = currentPlan.days.find { it.day == dayKey }
                    if (dayObj != null) {
                        val newSid = "ss_${System.currentTimeMillis() % 100000}"
                        val updatedExercises = dayObj.exercises.mapIndexed { idx, ex ->
                            if (idx == quickEditSlotIndex || idx == targetIdx) {
                                ex.copy(supersetId = newSid, isSequential = isSeq)
                            } else ex
                        }
                        planViewModel.updateDay(dayKey, dayObj.copy(exercises = updatedExercises))
                        quickEditExercise = null
                        quickEditSlotIndex = null
                    }
                }
            },
            onPairWithCatalog = { catExercise, isSeq ->
                showPairSupersetDialog = false
                if (planViewModel != null && quickEditSlotIndex != null) {
                    val currentPlan = planViewModel.plan.value
                    val dayObj = currentPlan.days.find { it.day == dayKey }
                    if (dayObj != null) {
                        val newSid = "ss_${System.currentTimeMillis() % 100000}"
                        val exists = currentPlan.allExercises().any { it.id == catExercise.id }
                        if (!exists) {
                            val catId = currentPlan.categories.firstOrNull { it.id == "pull" }?.id
                                ?: currentPlan.categories.firstOrNull()?.id
                                ?: "pull"
                            planViewModel.addExerciseToCategory(catId, catExercise)
                        }

                        val list = dayObj.exercises.toMutableList()
                        val slotIdx = quickEditSlotIndex!!
                        if (slotIdx in list.indices) {
                            list[slotIdx] = list[slotIdx].copy(supersetId = newSid, isSequential = isSeq)
                            val newDayEx = DayExercise(
                                exerciseId = catExercise.id,
                                alternatives = emptyList(),
                                supersetId = newSid,
                                isSequential = isSeq
                            )
                            if (slotIdx + 1 <= list.size) {
                                list.add(slotIdx + 1, newDayEx)
                            } else {
                                list.add(newDayEx)
                            }
                            planViewModel.updateDay(dayKey, dayObj.copy(exercises = list))
                            quickEditExercise = null
                            quickEditSlotIndex = null
                        }
                    }
                }
            }
        )
    }
}

private sealed class WorkoutSlotGroup {
    data class Single(val slotIndex: Int, val slot: WorkoutSlot) : WorkoutSlotGroup()
    data class Superset(
        val supersetId: String,
        val isSequential: Boolean,
        val items: List<Pair<Int, WorkoutSlot>>
    ) : WorkoutSlotGroup()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseSlotRow(
    workoutPlan: WorkoutPlan,
    dayKey: String,
    slot: WorkoutSlot,
    slotState: SlotLogState,
    exerciseLogs: Map<String, ExerciseLogState>,
    locationsById: Map<String, List<ExerciseLocation>>,
    doneExercises: Set<String>,
    supersetLabel: String? = null,
    onPageSelected: (page: Int) -> Unit,
    onEdit: (exerciseId: String, displayName: String, log: ExerciseLogState) -> Unit,
    onToggleDone: (exerciseId: String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val pageCount = slot.exerciseIds.size
        if (pageCount == 0) return

        val pagerState = rememberPagerState(
            initialPage = slotState.selectedPage.coerceIn(0, pageCount - 1),
            pageCount = { pageCount }
        )

        LaunchedEffect(slotState.selectedPage, pageCount) {
            val target = slotState.selectedPage.coerceIn(0, pageCount - 1)
            if (pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        }

        LaunchedEffect(pagerState, slotState.selectedPage) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (page != slotState.selectedPage) {
                        onPageSelected(page)
                    }
                }
        }

        // Feature 2+3: Equal-height cards + edge peek for adjacent items
        // contentPadding creates the peek gap; height(IntrinsicSize.Max) equalises card heights
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 12.dp
        ) { pageIndex ->
            val exerciseId = slotState.pageBindings.getOrNull(pageIndex) ?: slot.exerciseIds[pageIndex]
            val def = workoutPlan.catalog[exerciseId]
            val displayName = def?.name ?: exerciseId
            val log = exerciseLogs[exerciseId]
                ?: (def?.let { defaultExerciseLog(it) }
                    ?: return@HorizontalPager)
            val isMainVariation = exerciseId == slot.mainExerciseId
            val otherDayNames = locationsById[exerciseId]
                ?.filter { it.dayKey != dayKey }
                ?.map { it.dayKey }
                ?.distinct()
                ?.sorted()
                .orEmpty()
            val isDone = exerciseId in doneExercises

            VariationCard(
                displayName = displayName,
                log = log,
                isMainVariation = isMainVariation,
                linkedOtherDayNames = otherDayNames,
                isDone = isDone,
                supersetLabel = supersetLabel,
                onEditClick = { onEdit(exerciseId, displayName, log) },
                onToggleDone = { onToggleDone(exerciseId) },
                modifier = Modifier.height(200.dp)
            )
        }
    }
}

@Composable
private fun VariationCard(
    displayName: String,
    log: ExerciseLogState,
    isMainVariation: Boolean,
    linkedOtherDayNames: List<String>,
    isDone: Boolean,
    supersetLabel: String? = null,
    onEditClick: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = if (isMainVariation) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        onClick = onEditClick,
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        colors = colors,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isMainVariation) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (supersetLabel != null) {
                        Text(
                            text = supersetLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!isMainVariation) {
                        Text(
                            text = "ALTERNATIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.alpha(if (isDone) 0.6f else 1f)
                    )
                }
                IconButton(onClick = onToggleDone) {
                    Icon(
                        imageVector = if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (isDone) "Mark as not done" else "Mark as done",
                        tint = if (isDone) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
            }

            if (linkedOtherDayNames.isNotEmpty()) {
                Text(
                    text = "Also used on: ${linkedOtherDayNames.joinToString()} — changes apply everywhere.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (log.isCardio) {
                log.duration?.let {
                    Text(text = "Duration: $it", style = MaterialTheme.typography.bodyMedium)
                }
                log.intensity?.let {
                    Text(text = "Intensity: $it", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (log.isTimed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${log.sets} sets × ${log.timeTarget.ifEmpty { "60s" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val weightStr = formatWeight(log)
                if (weightStr != "—") {
                    Text(
                        text = "Weight: $weightStr",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    text = "${log.sets} sets × ${formatReps(log)} reps",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Weight: ${formatWeight(log)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (log.usesStraps) {
                    Text(
                        text = "Straps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (log.comment.isNotBlank()) {
                Text(
                    text = log.comment,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Feature 1: Format weight string with unit and bodyweight support. */
private fun formatWeight(log: ExerciseLogState): String {
    val weightValue = log.weight.trim()
    if (!log.isBodyweight && weightValue.isEmpty()) return "—"
    val num = Regex("""[+-]?[\d.]+""").find(weightValue)?.value?.toDoubleOrNull()
    val unitLabel = if (log.weightUnit == WeightUnit.LBS) {
        if (num != null && (num == 1.0 || num == -1.0)) "lb" else "lbs"
    } else {
        "kg"
    }

    return formatExerciseWeight(
        weight = weightValue,
        isBodyweight = log.isBodyweight,
        unitLabel = unitLabel
    )
}

private fun formatReps(log: ExerciseLogState): String {
    return if (log.usePerSetReps) {
        log.repsPerSet?.joinToString(", ") ?: "—"
    } else {
        log.repsSingle?.toString() ?: "—"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEditExerciseSheet(
    displayName: String,
    initialLog: ExerciseLogState,
    pairedSlot: WorkoutSlot? = null,
    pairedExerciseName: String? = null,
    canSuperset: Boolean = false,
    onUnlinkSuperset: (() -> Unit)? = null,
    onToggleSupersetMode: ((Boolean) -> Unit)? = null,
    onOpenPairDialog: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (ExerciseLogState) -> Unit
) {
    var sets by remember { mutableIntStateOf(initialLog.sets) }
    var isTimed by remember { mutableStateOf(initialLog.isTimed) }
    var timeTarget by remember { mutableStateOf(initialLog.timeTarget.ifEmpty { "60s" }) }
    var usePerSetReps by remember { mutableStateOf(initialLog.usePerSetReps) }
    var repsSingle by remember { mutableStateOf(initialLog.repsSingle?.toString() ?: "") }
    var repsPerSet by remember {
        mutableStateOf(
            initialLog.repsPerSet ?: List(initialLog.sets) { initialLog.repsSingle ?: 10 }
        )
    }
    var weightText by remember { mutableStateOf(initialLog.weight) }
    var weightUnit by remember { mutableStateOf(initialLog.weightUnit) }
    var isBodyweight by remember { mutableStateOf(initialLog.isBodyweight) }
    var usesStraps by remember { mutableStateOf(initialLog.usesStraps) }
    var comment by remember { mutableStateOf(initialLog.comment) }
    var durationText by remember { mutableStateOf(initialLog.duration ?: "") }
    var intensityText by remember { mutableStateOf(initialLog.intensity ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            initialLog.isCardio -> "Edit Cardio Details"
                            isTimed -> "Edit Timed Exercise"
                            else -> "Edit Sets & Load"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (initialLog.isCardio) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("Duration (e.g. 20 min)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = intensityText,
                    onValueChange = { intensityText = it },
                    label = { Text("Intensity (e.g. Zone 2, 7/10)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else if (isTimed) {
                // Sets stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (sets > 1) sets--
                            },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-", style = MaterialTheme.typography.titleMedium)
                        }

                        Text(
                            text = "$sets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = {
                                if (sets < 20) sets++
                            },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                // Target Time section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Target Time",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("30s", "45s", "60s", "90s", "2m").forEach { chipTime ->
                            FilterChip(
                                selected = timeTarget.trim().equals(chipTime, ignoreCase = true),
                                onClick = { timeTarget = chipTime },
                                label = { Text(chipTime) }
                            )
                        }
                    }
                    val timeInteractionSource = remember { MutableInteractionSource() }
                    LaunchedEffect(timeInteractionSource) {
                        timeInteractionSource.interactions.collect {
                            if (it is FocusInteraction.Focus && timeTarget.isNotEmpty()) {
                                timeTarget = ""
                            }
                        }
                    }
                    OutlinedTextField(
                        value = timeTarget,
                        onValueChange = { timeTarget = it },
                        label = { Text("Custom Target Time (e.g. 60s, 45s, 1:15)") },
                        modifier = Modifier.fillMaxWidth(),
                        interactionSource = timeInteractionSource,
                        singleLine = true
                    )
                }

                // Active countdown timer right in the quick edit sheet!
                PlankCountdownTimer(targetTimeStr = timeTarget)

                // Weight / Load
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBodyweight) "Added Weight" else "Weight",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = weightUnit == WeightUnit.LBS,
                                onClick = { weightUnit = WeightUnit.LBS },
                                label = { Text("lbs") }
                            )
                            FilterChip(
                                selected = weightUnit == WeightUnit.KG,
                                onClick = { weightUnit = WeightUnit.KG },
                                label = { Text("kg") }
                            )
                        }
                    }

                    val timedWeightInteractionSource = remember { MutableInteractionSource() }
                    LaunchedEffect(timedWeightInteractionSource) {
                        timedWeightInteractionSource.interactions.collect {
                            if (it is FocusInteraction.Focus && weightText.isNotEmpty()) {
                                weightText = ""
                            }
                        }
                    }
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(if (isBodyweight) "Added weight (optional)" else "Weight (e.g. 10 kg)") },
                        modifier = Modifier.fillMaxWidth(),
                        interactionSource = timedWeightInteractionSource,
                        singleLine = true
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isBodyweight = !isBodyweight }
                    ) {
                        Checkbox(
                            checked = isBodyweight,
                            onCheckedChange = { isBodyweight = it }
                        )
                        Text(
                            text = "Bodyweight",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                // Sets stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (sets > 1) {
                                    sets--
                                    if (repsPerSet.size > sets) {
                                        repsPerSet = repsPerSet.take(sets)
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-", style = MaterialTheme.typography.titleMedium)
                        }

                        Text(
                            text = "$sets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = {
                                if (sets < 20) {
                                    sets++
                                    val lastRep = repsPerSet.lastOrNull() ?: repsSingle.toIntOrNull() ?: 10
                                    repsPerSet = repsPerSet + lastRep
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                // Reps (Single or Per-Set)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (usePerSetReps) "Reps (per set)" else "Reps (target)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                usePerSetReps = !usePerSetReps
                                if (usePerSetReps && repsPerSet.size != sets) {
                                    val s = repsSingle.toIntOrNull() ?: 10
                                    repsPerSet = List(sets) { s }
                                }
                            }
                        ) {
                            Checkbox(
                                checked = usePerSetReps,
                                onCheckedChange = { checked ->
                                    usePerSetReps = checked
                                    if (checked && repsPerSet.size != sets) {
                                        val s = repsSingle.toIntOrNull() ?: 10
                                        repsPerSet = List(sets) { s }
                                    }
                                }
                            )
                            Text(
                                text = "Per set",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (usePerSetReps) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (i in 0 until sets) {
                                val repVal = repsPerSet.getOrElse(i) { 10 }
                                var tempRepText by remember(i, repVal) { mutableStateOf(repVal.toString()) }
                                val repInteractionSource = remember(i) { MutableInteractionSource() }
                                LaunchedEffect(repInteractionSource) {
                                    repInteractionSource.interactions.collect {
                                        if (it is FocusInteraction.Focus && tempRepText.isNotEmpty()) {
                                            tempRepText = ""
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = tempRepText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(3)
                                        tempRepText = digits
                                        val num = digits.toIntOrNull() ?: 0
                                        val list = repsPerSet.toMutableList()
                                        while (list.size <= i) list.add(0)
                                        list[i] = num
                                        repsPerSet = list
                                    },
                                    label = { Text("S${i + 1}") },
                                    modifier = Modifier.width(68.dp),
                                    interactionSource = repInteractionSource,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }
                        }
                    } else {
                        val repsSingleInteractionSource = remember { MutableInteractionSource() }
                        LaunchedEffect(repsSingleInteractionSource) {
                            repsSingleInteractionSource.interactions.collect {
                                if (it is FocusInteraction.Focus && repsSingle.isNotEmpty()) {
                                    repsSingle = ""
                                }
                            }
                        }
                        OutlinedTextField(
                            value = repsSingle,
                            onValueChange = { raw ->
                                repsSingle = raw.filter { it.isDigit() }.take(4)
                            },
                            label = { Text("Reps Target") },
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = repsSingleInteractionSource,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }

                // Weight / Load
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBodyweight) "Added Weight" else "Weight",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = weightUnit == WeightUnit.LBS,
                                onClick = { weightUnit = WeightUnit.LBS },
                                label = { Text("lbs") }
                            )
                            FilterChip(
                                selected = weightUnit == WeightUnit.KG,
                                onClick = { weightUnit = WeightUnit.KG },
                                label = { Text("kg") }
                            )
                        }
                    }

                    val regularWeightInteractionSource = remember { MutableInteractionSource() }
                    LaunchedEffect(regularWeightInteractionSource) {
                        regularWeightInteractionSource.interactions.collect {
                            if (it is FocusInteraction.Focus && weightText.isNotEmpty()) {
                                weightText = ""
                            }
                        }
                    }
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(if (isBodyweight) "Added weight (optional)" else "Weight (e.g. 135)") },
                        modifier = Modifier.fillMaxWidth(),
                        interactionSource = regularWeightInteractionSource,
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isBodyweight = !isBodyweight }
                        ) {
                            Checkbox(
                                checked = isBodyweight,
                                onCheckedChange = { isBodyweight = it }
                            )
                            Text(
                                text = "Bodyweight",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { usesStraps = !usesStraps }
                        ) {
                            Checkbox(
                                checked = usesStraps,
                                onCheckedChange = { usesStraps = it }
                            )
                            Text(
                                text = "Straps",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Comment
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(ExerciseLogState.MAX_COMMENT_LENGTH) },
                label = { Text("Notes / Comment") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            // Superset section
            if (canSuperset) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Superset",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (pairedExerciseName != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = if (pairedSlot?.isSequential == true) "SUPERSET • SEQUENTIAL" else "SUPERSET • ALTERNATING",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = "Paired with $pairedExerciseName",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onUnlinkSuperset?.invoke() },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "Unlink",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                if (pairedSlot != null && onToggleSupersetMode != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = !pairedSlot.isSequential,
                                            onClick = { onToggleSupersetMode(false) },
                                            label = { Text("🔁 Alternating") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = pairedSlot.isSequential,
                                            onClick = { onToggleSupersetMode(true) },
                                            label = { Text("➡️ Sequential") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (onOpenPairDialog != null) {
                        OutlinedButton(
                            onClick = onOpenPairDialog,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🔗 Pair in Superset...", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Save button
            Button(
                onClick = {
                    val finalRepsSingle = if (!usePerSetReps && !isTimed) repsSingle.toIntOrNull() else null
                    val finalRepsPerSet = if (usePerSetReps && !isTimed) repsPerSet else null
                    val updated = initialLog.copy(
                        sets = sets,
                        usePerSetReps = if (isTimed) false else usePerSetReps,
                        repsSingle = finalRepsSingle,
                        repsPerSet = finalRepsPerSet,
                        weight = weightText.trim(),
                        weightUnit = weightUnit,
                        isBodyweight = isBodyweight,
                        usesStraps = usesStraps,
                        comment = comment.trim(),
                        duration = if (initialLog.isCardio) durationText.trim().ifEmpty { null } else initialLog.duration,
                        intensity = if (initialLog.isCardio) intensityText.trim().ifEmpty { null } else initialLog.intensity,
                        isTimed = isTimed,
                        timeTarget = if (isTimed) timeTarget.trim().ifEmpty { "60s" } else initialLog.timeTarget
                    )
                    onSave(updated)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairSupersetDialog(
    targetExerciseName: String,
    otherDaySlots: List<Pair<Int, String>>,
    allCatalogExercises: List<CategoryExercise>,
    onDismiss: () -> Unit,
    onPairWithExisting: (targetSlotIndex: Int, isSequential: Boolean) -> Unit,
    onPairWithCatalog: (CategoryExercise, isSequential: Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (otherDaySlots.isNotEmpty()) 0 else 1) }
    var searchQuery by remember { mutableStateOf("") }
    var isSequential by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pair \"$targetExerciseName\" in Superset",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Superset Type selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Superset Type",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isSequential,
                            onClick = { isSequential = false },
                            label = { Text("🔁 Alternating") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isSequential,
                            onClick = { isSequential = true },
                            label = { Text("➡️ Sequential") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (isSequential)
                            "Sequential: Do all sets of $targetExerciseName first, then all sets of the paired exercise."
                        else
                            "Alternating: Alternate set-by-set (1 set of $targetExerciseName, then 1 set of paired exercise).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("In Routine (${otherDaySlots.size})") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("From Library") }
                    )
                }

                if (selectedTab == 0) {
                    if (otherDaySlots.isEmpty()) {
                        Text(
                            text = "No other exercises in this routine to pair with.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(otherDaySlots) { (slotIdx, name) ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPairWithExisting(slotIdx, isSequential) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search exercise") },
                        placeholder = { Text("e.g. Isometric, Plank") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )

                    val filtered = remember(searchQuery, allCatalogExercises) {
                        val q = searchQuery.trim().lowercase()
                        if (q.isEmpty()) allCatalogExercises
                        else allCatalogExercises.filter { it.name.lowercase().contains(q) }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered) { exercise ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPairWithCatalog(exercise, isSequential) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = exercise.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (exercise.isTimed) "Timed • ${exercise.defaultTime}" else "${exercise.defaultSets} sets • ${exercise.defaultReps} reps",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (searchQuery.isNotBlank() && filtered.none { it.name.equals(searchQuery.trim(), ignoreCase = true) }) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val name = searchQuery.trim()
                                            val isTimedGuess = name.contains("hold", ignoreCase = true) || name.contains("plank", ignoreCase = true)
                                            val newEx = CategoryExercise(
                                                id = name.lowercase().replace(" ", "_").replace("-", "_"),
                                                name = name,
                                                defaultSets = 3,
                                                defaultReps = if (isTimedGuess) "30s" else "10",
                                                defaultLoad = "Bodyweight",
                                                isTimed = isTimedGuess,
                                                defaultTime = if (isTimedGuess) "30s" else "60s"
                                            )
                                            onPairWithCatalog(newEx, isSequential)
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "+ Create \"${searchQuery.trim()}\" & Pair",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun parseSeconds(timeStr: String): Int {
    val trimmed = timeStr.trim().lowercase()
    if (trimmed.endsWith("m") || trimmed.endsWith("min")) {
        val num = Regex("""[\d.]+""").find(trimmed)?.value?.toDoubleOrNull() ?: 1.0
        return (num * 60).toInt()
    }
    if (trimmed.contains(":")) {
        val parts = trimmed.split(":")
        val min = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val sec = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return min * 60 + sec
    }
    val num = Regex("""\d+""").find(trimmed)?.value?.toIntOrNull() ?: 60
    return num
}

@Composable
private fun PlankCountdownTimer(targetTimeStr: String) {
    val context = LocalContext.current
    val totalSeconds = remember(targetTimeStr) { parseSeconds(targetTimeStr).coerceAtLeast(5) }
    var remainingSeconds by remember(totalSeconds) { mutableIntStateOf(totalSeconds) }
    var isRunning by remember { mutableStateOf(false) }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
            if (remainingSeconds == 0) {
                isRunning = false
                vibrator?.let { v ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(600)
                    }
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (remainingSeconds == 0) "Time's Up!" else "${remainingSeconds}s remaining",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingSeconds == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Timer: $targetTimeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (remainingSeconds < totalSeconds) {
                    IconButton(onClick = {
                        isRunning = false
                        remainingSeconds = totalSeconds
                    }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset timer")
                    }
                }
                Button(
                    onClick = {
                        if (remainingSeconds == 0) remainingSeconds = totalSeconds
                        isRunning = !isRunning
                    }
                ) {
                    Text(if (isRunning) "Pause" else if (remainingSeconds == 0) "Restart" else "Start")
                }
            }
        }
    }
}
