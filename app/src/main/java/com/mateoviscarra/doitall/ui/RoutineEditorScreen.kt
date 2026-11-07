package com.mateoviscarra.doitall.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateoviscarra.doitall.data.Category
import com.mateoviscarra.doitall.data.CategoryExercise
import com.mateoviscarra.doitall.data.DayExercise
import com.mateoviscarra.doitall.data.NewWorkoutDay

private val COMMON_MUSCLE_GROUPS = listOf(
    "chest", "back", "quads", "hamstrings", "glutes", "calves",
    "shoulders", "biceps", "triceps", "abs", "cardio"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    initialDay: NewWorkoutDay?,
    categories: List<Category>,
    allExercises: List<CategoryExercise>,
    onBack: () -> Unit,
    onSave: (NewWorkoutDay) -> Unit,
    onDelete: (() -> Unit)? = null,
    onAddExerciseToCatalog: (categoryId: String, CategoryExercise) -> Unit
) {
    var dayName by remember { mutableStateOf(initialDay?.day ?: "") }
    var selectedMuscles by remember { mutableStateOf(initialDay?.muscleGroups?.toSet() ?: emptySet()) }
    var exercises by remember { mutableStateOf(initialDay?.exercises ?: emptyList()) }
    var notesText by remember { mutableStateOf(initialDay?.notes?.joinToString("\n") ?: "") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    var showExercisePickerForSlotIndex by remember { mutableStateOf<Int?>(null) } // null = adding new main slot, -1 = closed, >=0 = adding alt to slot
    var isPickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialDay == null) "New Routine" else "Edit: ${initialDay.day}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (initialDay != null && onDelete != null) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Routine",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (dayName.isNotBlank()) {
                                val notes = notesText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                onSave(
                                    NewWorkoutDay(
                                        day = dayName.trim(),
                                        muscleGroups = selectedMuscles.toList(),
                                        isRestDay = false,
                                        notes = notes,
                                        exercises = exercises
                                    )
                                )
                            }
                        },
                        enabled = dayName.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Routine Name Input
            item {
                OutlinedTextField(
                    value = dayName,
                    onValueChange = { dayName = it },
                    label = { Text("Routine Name (e.g. Push A, Legs, Arms)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Muscle Groups Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Muscle Groups Trained",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        COMMON_MUSCLE_GROUPS.forEach { muscle ->
                            val isSelected = muscle in selectedMuscles
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedMuscles = if (isSelected) {
                                        selectedMuscles - muscle
                                    } else {
                                        selectedMuscles + muscle
                                    }
                                },
                                label = { Text(muscle.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            // Routine Notes Input
            item {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Routine Notes (optional, e.g. Use straps, Warm up 5m)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exercise Slots (${exercises.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Swipeable variations inside each slot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Exercise Slots List
            if (exercises.isEmpty()) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No exercises in this routine yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = {
                                showExercisePickerForSlotIndex = null // null = new main slot
                                isPickerOpen = true
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add First Exercise")
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(exercises) { index, dayEx ->
                    val hasSuperset = dayEx.supersetId != null
                    RoutineSlotCard(
                        slotIndex = index,
                        totalSlots = exercises.size,
                        dayExercise = dayEx,
                        allExercises = allExercises,
                        onMoveUp = {
                            if (index > 0) {
                                val list = exercises.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                exercises = list
                            }
                        },
                        onMoveDown = {
                            if (index < exercises.size - 1) {
                                val list = exercises.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                exercises = list
                            }
                        },
                        onDeleteSlot = {
                            val list = exercises.toMutableList()
                            val removed = list.removeAt(index)
                            // If removed was part of a superset, check if remaining with that superset has <= 1
                            if (removed.supersetId != null) {
                                val remaining = list.filter { it.supersetId == removed.supersetId }
                                if (remaining.size <= 1) {
                                    val rIdx = list.indexOfFirst { it.supersetId == removed.supersetId }
                                    if (rIdx >= 0) {
                                        list[rIdx] = list[rIdx].copy(supersetId = null)
                                    }
                                }
                            }
                            exercises = list
                        },
                        onLinkSupersetWithNext = if (!hasSuperset && index < exercises.size - 1) {
                            {
                                val list = exercises.toMutableList()
                                val id = "ss_${System.currentTimeMillis() % 100000}"
                                list[index] = list[index].copy(supersetId = id)
                                list[index + 1] = list[index + 1].copy(supersetId = id)
                                exercises = list
                            }
                        } else null,
                        onUnlinkSuperset = if (hasSuperset) {
                            {
                                val list = exercises.toMutableList()
                                val oldId = dayEx.supersetId
                                list[index] = dayEx.copy(supersetId = null, isSequential = false)
                                val remaining = list.filter { it.supersetId == oldId }
                                if (remaining.size <= 1) {
                                    val rIdx = list.indexOfFirst { it.supersetId == oldId }
                                    if (rIdx >= 0) {
                                        list[rIdx] = list[rIdx].copy(supersetId = null, isSequential = false)
                                    }
                                }
                                exercises = list
                            }
                        } else null,
                        onToggleSupersetMode = if (hasSuperset) {
                            { isSeq ->
                                val list = exercises.toMutableList()
                                val sid = dayEx.supersetId
                                list.indices.forEach { i ->
                                    if (list[i].supersetId == sid) {
                                        list[i] = list[i].copy(isSequential = isSeq)
                                    }
                                }
                                exercises = list
                            }
                        } else null,
                        onAddAlternative = {
                            showExercisePickerForSlotIndex = index
                            isPickerOpen = true
                        },
                        onRemoveAlternative = { altId ->
                            val list = exercises.toMutableList()
                            list[index] = dayEx.copy(alternatives = dayEx.alternatives - altId)
                            exercises = list
                        }
                    )
                }

                item {
                    Button(
                        onClick = {
                            showExercisePickerForSlotIndex = null
                            isPickerOpen = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Exercise to Routine")
                    }
                }
            }
        }
    }

    if (isPickerOpen) {
        val targetSlotIndex = showExercisePickerForSlotIndex
        val excludedIds = if (targetSlotIndex != null && targetSlotIndex in exercises.indices) {
            val currentSlot = exercises[targetSlotIndex]
            listOf(currentSlot.exerciseId) + currentSlot.alternatives
        } else {
            emptyList()
        }

        SearchableExercisePickerDialog(
            categories = categories,
            allExercises = allExercises.filter { it.id !in excludedIds },
            onDismiss = { isPickerOpen = false },
            onSelectExercise = { exerciseId ->
                if (targetSlotIndex == null) {
                    // Adding new main slot
                    exercises = exercises + DayExercise(exerciseId = exerciseId)
                } else if (targetSlotIndex in exercises.indices) {
                    // Adding alternative to existing slot
                    val list = exercises.toMutableList()
                    val target = list[targetSlotIndex]
                    list[targetSlotIndex] = target.copy(alternatives = target.alternatives + exerciseId)
                    exercises = list
                }
                isPickerOpen = false
            },
            onCreateNewExercise = { catId, newEx ->
                onAddExerciseToCatalog(catId, newEx)
                if (targetSlotIndex == null) {
                    exercises = exercises + DayExercise(exerciseId = newEx.id)
                } else if (targetSlotIndex in exercises.indices) {
                    val list = exercises.toMutableList()
                    val target = list[targetSlotIndex]
                    list[targetSlotIndex] = target.copy(alternatives = target.alternatives + newEx.id)
                    exercises = list
                }
                isPickerOpen = false
            }
        )
    }

    if (showDeleteConfirmation && onDelete != null && initialDay != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Routine?") },
            text = { Text("Are you sure you want to delete '${initialDay.day}'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RoutineSlotCard(
    slotIndex: Int,
    totalSlots: Int,
    dayExercise: DayExercise,
    allExercises: List<CategoryExercise>,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDeleteSlot: () -> Unit,
    onLinkSupersetWithNext: (() -> Unit)? = null,
    onUnlinkSuperset: (() -> Unit)? = null,
    onToggleSupersetMode: ((Boolean) -> Unit)? = null,
    onAddAlternative: () -> Unit,
    onRemoveAlternative: (String) -> Unit
) {
    val mainEx = allExercises.find { it.id == dayExercise.exerciseId }
    val mainName = mainEx?.name ?: dayExercise.exerciseId

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (dayExercise.supersetId != null) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main Exercise Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${slotIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (dayExercise.supersetId != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (dayExercise.isSequential) "SUPERSET (SEQ)" else "SUPERSET (ALT)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mainName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = slotIndex > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = slotIndex < totalSlots - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteSlot,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Slot",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Superset Action Row
            if (onUnlinkSuperset != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !dayExercise.isSequential,
                            onClick = { onToggleSupersetMode?.invoke(false) },
                            label = { Text("🔁 Alternating", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = dayExercise.isSequential,
                            onClick = { onToggleSupersetMode?.invoke(true) },
                            label = { Text("➡️ Sequential", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    OutlinedButton(
                        onClick = onUnlinkSuperset,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Unlink", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (onLinkSupersetWithNext != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onLinkSupersetWithNext,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("🔗 Superset with next", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Alternatives Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Carousel Alternatives (${dayExercise.alternatives.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    TextButton(
                        onClick = onAddAlternative,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Add Alt", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (dayExercise.alternatives.isEmpty()) {
                    Text(
                        text = "No alternative variations added for this slot yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                } else {
                    dayExercise.alternatives.forEach { altId ->
                        val altEx = allExercises.find { it.id == altId }
                        val altName = altEx?.name ?: altId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = altName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onRemoveAlternative(altId) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove variation",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableExercisePickerDialog(
    categories: List<Category>,
    allExercises: List<CategoryExercise>,
    onDismiss: () -> Unit,
    onSelectExercise: (String) -> Unit,
    onCreateNewExercise: (categoryId: String, CategoryExercise) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filtered = remember(allExercises, searchQuery, selectedCategoryFilter) {
        allExercises.filter { ex ->
            val matchesCategory = selectedCategoryFilter == null || ex.categories.contains(selectedCategoryFilter)
            val matchesSearch = searchQuery.isBlank() || ex.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }.sortedBy { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search exercises...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                        label = { Text("All") }
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat.id,
                            onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat.id) null else cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }

                // List of filtered exercises
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No existing exercise found." else "No exercises in this category.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (searchQuery.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { showCreateDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Create '${searchQuery.trim()}'")
                                    }
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { ex ->
                            Surface(
                                onClick = { onSelectExercise(ex.id) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ex.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val catName = categories.find { it.id in ex.categories }?.name
                                    if (catName != null) {
                                        Text(
                                            text = catName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (searchQuery.isNotBlank() && filtered.none { it.name.equals(searchQuery.trim(), ignoreCase = true) }) {
                TextButton(onClick = { showCreateDialog = true }) {
                    Text("+ Create '${searchQuery.trim()}'")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showCreateDialog) {
        val targetCat = categories.find { it.id == selectedCategoryFilter } ?: categories.firstOrNull()
        if (targetCat != null) {
            val trimmedName = searchQuery.trim()
            val newId = trimmedName.lowercase().replace(Regex("""\s+"""), "_")
            val newEx = CategoryExercise(
                id = newId,
                name = trimmedName,
                defaultSets = 3,
                defaultReps = "10",
                defaultLoad = "",
                categories = listOf(targetCat.id),
                isTimed = false,
                defaultTime = "60s"
            )
            onCreateNewExercise(targetCat.id, newEx)
            showCreateDialog = false
        }
    }
}
