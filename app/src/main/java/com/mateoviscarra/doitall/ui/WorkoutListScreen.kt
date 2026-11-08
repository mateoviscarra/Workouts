package com.mateoviscarra.doitall.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mateoviscarra.doitall.data.WorkoutDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    workoutDays: List<WorkoutDay>,
    exerciseNamesById: Map<String, String> = emptyMap(),
    onDaySelected: (index: Int) -> Unit,
    onSettingsClick: () -> Unit,
    onCustomClick: () -> Unit,
    onEditRoutineClick: (dayIndex: Int) -> Unit,
    onNewRoutineClick: () -> Unit,
    showHeader: Boolean = true
) {
    if (showHeader) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Workouts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            WorkoutListContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                workoutDays = workoutDays,
                exerciseNamesById = exerciseNamesById,
                onDaySelected = onDaySelected,
                onCustomClick = onCustomClick,
                onEditRoutineClick = onEditRoutineClick,
                onNewRoutineClick = onNewRoutineClick
            )
        }
    } else {
        WorkoutListContent(
            modifier = Modifier.fillMaxSize(),
            workoutDays = workoutDays,
            exerciseNamesById = exerciseNamesById,
            onDaySelected = onDaySelected,
            onCustomClick = onCustomClick,
            onEditRoutineClick = onEditRoutineClick,
            onNewRoutineClick = onNewRoutineClick
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutListContent(
    modifier: Modifier = Modifier,
    workoutDays: List<WorkoutDay>,
    exerciseNamesById: Map<String, String>,
    onDaySelected: (index: Int) -> Unit,
    onCustomClick: () -> Unit,
    onEditRoutineClick: (dayIndex: Int) -> Unit,
    onNewRoutineClick: () -> Unit
) {
    val context = LocalContext.current
    val cardioStore = remember { CardioPreferencesStore(context.applicationContext) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick action buttons: Custom & New Routine
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCustomClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Button(
                    onClick = onNewRoutineClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = "New Routine",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }

        // Categorize days dynamically
        val cardioDay = workoutDays.firstOrNull {
            it.day.equals("Cardio", ignoreCase = true) || it.day.contains("cardio", ignoreCase = true)
        }
        val nonCardioDays = workoutDays.filter { it != cardioDay }

        val pushDays = nonCardioDays.filter { it.day.contains("push", ignoreCase = true) }
        val pullDays = nonCardioDays.filter { it.day.contains("pull", ignoreCase = true) }
        val legDays = nonCardioDays.filter { it.day.contains("leg", ignoreCase = true) }
        val isStandardSplit = pushDays.isNotEmpty() || pullDays.isNotEmpty() || legDays.isNotEmpty()
        val bottomSplitDays = if (isStandardSplit) {
            nonCardioDays.filter { it !in pushDays && it !in pullDays && it !in legDays }
        } else {
            emptyList()
        }

        if (isStandardSplit) {
            // Main Split: 3-column PPL top layer + dynamic bottom layer (Abs & any added split days)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Main Split",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pushDays.forEach { day ->
                                val index = workoutDays.indexOf(day)
                                WorkoutButton(
                                    day = day,
                                    onClick = { onDaySelected(index) },
                                    onEdit = { onEditRoutineClick(index) }
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pullDays.forEach { day ->
                                val index = workoutDays.indexOf(day)
                                WorkoutButton(
                                    day = day,
                                    onClick = { onDaySelected(index) },
                                    onEdit = { onEditRoutineClick(index) }
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            legDays.forEach { day ->
                                val index = workoutDays.indexOf(day)
                                WorkoutButton(
                                    day = day,
                                    onClick = { onDaySelected(index) },
                                    onEdit = { onEditRoutineClick(index) }
                                )
                            }
                        }
                    }

                    // Dynamic bottom layer of Main Split (1 item = full-width bar, 2 = 2 columns, 3+ = 3-column grid)
                    if (bottomSplitDays.isNotEmpty()) {
                        bottomSplitDays.chunked(3).forEach { rowDays ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowDays.forEach { day ->
                                    val index = workoutDays.indexOf(day)
                                    Box(modifier = Modifier.weight(1f)) {
                                        WorkoutButton(
                                            day = day,
                                            onClick = { onDaySelected(index) },
                                            onEdit = { onEditRoutineClick(index) }
                                        )
                                    }
                                }
                                // When there are >3 items and the second row is partial, align to the 3-column grid
                                if (bottomSplitDays.size > 3 && rowDays.size < 3) {
                                    repeat(3 - rowDays.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (nonCardioDays.isNotEmpty()) {
            // Non-PPL layout: dynamically chunk into 2 columns
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Routines",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    nonCardioDays.chunked(2).forEach { rowDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowDays.forEach { day ->
                                val index = workoutDays.indexOf(day)
                                Column(modifier = Modifier.weight(1f)) {
                                    WorkoutButton(
                                        day = day,
                                        onClick = { onDaySelected(index) },
                                        onEdit = { onEditRoutineClick(index) }
                                    )
                                }
                            }
                            repeat(2 - rowDays.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Cardio Block
        if (cardioDay != null) {
            val cardioIndex = workoutDays.indexOf(cardioDay)
            val cardioIds = cardioDay.slots.flatMap { it.exerciseIds }.distinct()
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Cardio",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Card(
                        onClick = { onDaySelected(cardioIndex) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = cardioDay.day,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Pick one activity • Set duration & speed/level",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onEditRoutineClick(cardioIndex) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Cardio Options",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            if (cardioIds.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    cardioIds.forEach { exId ->
                                        val label = exerciseNamesById[exId]
                                            ?: exId.replace("_", " ").trim().replaceFirstChar { it.uppercase() }
                                        AssistChip(
                                            onClick = {
                                                cardioStore.setSelectedActivityId(exId)
                                                onDaySelected(cardioIndex)
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
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

@Composable
private fun WorkoutButton(
    day: WorkoutDay,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    isAccent: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAccent) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 10.dp, start = 10.dp, end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = day.day,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Routine",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            val countText = if (day.slots.isNotEmpty()) "${day.slots.size} exercises" else "Empty"
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
