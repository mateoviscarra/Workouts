package com.mateoviscarra.doitall.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mateoviscarra.doitall.data.CompletedExerciseRecord
import com.mateoviscarra.doitall.data.PersonalRecord
import com.mateoviscarra.doitall.data.WorkoutSessionRecord
import com.mateoviscarra.doitall.data.formatExerciseWeight
import com.mateoviscarra.doitall.data.persist.WeightUnit
import com.mateoviscarra.doitall.viewmodel.HistoryViewModel
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: (() -> Unit)? = null,
    viewModel: HistoryViewModel = viewModel(),
    showHeader: Boolean = true
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val prsMap by viewModel.personalRecords.collectAsStateWithLifecycle()

    var sessionToDelete by remember { mutableStateOf<WorkoutSessionRecord?>(null) }
    var prToEdit by remember { mutableStateOf<PersonalRecord?>(null) }
    var showAddPRDialog by remember { mutableStateOf(false) }
    var prToDelete by remember { mutableStateOf<PersonalRecord?>(null) }

    Scaffold(
        topBar = {
            if (showHeader) {
                TopAppBar(
                    title = { Text("History & PRs", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Workouts (${sessions.size})")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Records (${prsMap.size})")
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> SessionsTab(
                    sessions = sessions,
                    onDeleteSession = { sessionToDelete = it }
                )
                1 -> PersonalRecordsTab(
                    prs = prsMap.values.sortedByDescending { it.maxWeight },
                    onAddClick = { showAddPRDialog = true },
                    onEditClick = { prToEdit = it },
                    onDeleteClick = { prToDelete = it }
                )
            }
        }
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session?") },
            text = { Text("Are you sure you want to remove the log for ${session.dayTitle} on ${session.dateIso}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session.id)
                        sessionToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    prToDelete?.let { pr ->
        AlertDialog(
            onDismissRequest = { prToDelete = null },
            title = { Text("Delete Personal Record?") },
            text = { Text("Are you sure you want to remove the record for ${pr.exerciseName}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePR(pr.exerciseId)
                        prToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { prToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddPRDialog) {
        AddEditPRDialog(
            initialPR = null,
            onDismiss = { showAddPRDialog = false },
            onConfirm = { pr ->
                viewModel.addOrUpdatePR(pr)
                showAddPRDialog = false
            }
        )
    }

    prToEdit?.let { existingPR ->
        AddEditPRDialog(
            initialPR = existingPR,
            onDismiss = { prToEdit = null },
            onConfirm = { pr ->
                viewModel.addOrUpdatePR(pr)
                prToEdit = null
            }
        )
    }
}

@Composable
private fun SessionsTab(
    sessions: List<WorkoutSessionRecord>,
    onDeleteSession: (WorkoutSessionRecord) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Text(
                    text = "No workouts logged yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Complete your exercises and tap 'Finish Workout' to save your session to history and calendar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    onDelete = { onDeleteSession(session) }
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: WorkoutSessionRecord,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.dayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = session.dateIso,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete session",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }

                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Show less" else "Show more"
                        )
                    }
                }
            }

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${session.exercises.size} exercises",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                if (session.totalVolume > 0) {
                    Text(
                        text = "Volume: ${String.format(Locale.US, "%,.0f", session.totalVolume)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider()

                    session.exercises.forEach { ex ->
                        ExerciseRowInSession(ex)
                    }

                    if (session.notes.isNotBlank()) {
                        Text(
                            text = "Notes: ${session.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRowInSession(exercise: CompletedExerciseRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.exerciseName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            val weightStr = formatExerciseWeight(
                weight = exercise.weight,
                isBodyweight = exercise.isBodyweight,
                unitLabel = exercise.weightUnit.name.lowercase(),
                shortBwPrefix = true
            )

            val details = buildString {
                append("${exercise.sets} sets × ${exercise.repsSummary} reps")
                if (weightStr.isNotBlank()) append(" @ $weightStr")
                if (exercise.usesStraps) append(" (Straps)")
            }

            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (exercise.comment.isNotBlank()) {
                Text(
                    text = "\"${exercise.comment}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun PersonalRecordsTab(
    prs: List<PersonalRecord>,
    onAddClick: () -> Unit,
    onEditClick: (PersonalRecord) -> Unit,
    onDeleteClick: (PersonalRecord) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredPRs = remember(prs, searchQuery) {
        if (searchQuery.isBlank()) prs else {
            prs.filter { it.exerciseName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search records...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Button(
                onClick = onAddClick
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add PR")
            }
        }

        if (prs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "No Personal Records Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap 'Add PR' to record your 1-rep max or milestone lifts (e.g. Bench Press, Squat, Deadlift).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Personal Record")
                    }
                }
            }
        } else if (filteredPRs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No records matching '$searchQuery'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPRs, key = { it.exerciseId }) { pr ->
                    PRCard(
                        pr = pr,
                        onEdit = { onEditClick(pr) },
                        onDelete = { onDeleteClick(pr) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PRCard(
    pr: PersonalRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pr.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Achieved: ${pr.dateIso}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (pr.note.isNotBlank()) {
                    Text(
                        text = "\"${pr.note}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        val weightFormatted = if (pr.maxWeight % 1.0 == 0.0) {
                            pr.maxWeight.toInt().toString()
                        } else {
                            pr.maxWeight.toString()
                        }

                        val weightText = if (pr.isBodyweight && pr.maxWeight == 0.0) {
                            "Bodyweight"
                        } else if (pr.isBodyweight) {
                            "BW + $weightFormatted ${pr.weightUnit.name.lowercase()}"
                        } else {
                            "$weightFormatted ${pr.weightUnit.name.lowercase()}"
                        }

                        Text(
                            text = weightText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        val repText = if (pr.reps == 1) "1 rep max" else "${pr.reps} reps"
                        Text(
                            text = repText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete record",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditPRDialog(
    initialPR: PersonalRecord?,
    onDismiss: () -> Unit,
    onConfirm: (PersonalRecord) -> Unit
) {
    var name by remember { mutableStateOf(initialPR?.exerciseName ?: "") }
    var weightText by remember { mutableStateOf(initialPR?.let { if (it.maxWeight > 0) it.maxWeight.toString() else "" } ?: "") }
    var repsText by remember { mutableStateOf((initialPR?.reps ?: 1).toString()) }
    var isBodyweight by remember { mutableStateOf(initialPR?.isBodyweight ?: false) }
    var weightUnit by remember { mutableStateOf(initialPR?.weightUnit ?: WeightUnit.KG) }
    var dateIso by remember { mutableStateOf(initialPR?.dateIso ?: LocalDate.now().toString()) }
    var note by remember { mutableStateOf(initialPR?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPR == null) "Add Personal Record" else "Edit Personal Record") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise Name (e.g. Bench Press)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(if (isBodyweight) "Added Weight" else "Weight") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it },
                        label = { Text("Reps (e.g. 1)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Spacer(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isBodyweight,
                            onCheckedChange = { isBodyweight = it }
                        )
                        Text("Bodyweight", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notes (optional, e.g. Competition pause)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val numWeight = weightText.toDoubleOrNull() ?: 0.0
                        val reps = repsText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val id = initialPR?.exerciseId ?: name.lowercase().trim().replace(Regex("""\s+"""), "_")
                        onConfirm(
                            PersonalRecord(
                                exerciseId = id,
                                exerciseName = name.trim(),
                                maxWeight = numWeight,
                                reps = reps,
                                weightUnit = weightUnit,
                                dateIso = dateIso.trim().ifEmpty { LocalDate.now().toString() },
                                isBodyweight = isBodyweight,
                                note = note.trim()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
