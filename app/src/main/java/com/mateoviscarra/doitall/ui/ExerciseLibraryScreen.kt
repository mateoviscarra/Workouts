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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mateoviscarra.doitall.data.Category
import com.mateoviscarra.doitall.data.CategoryExercise
import com.mateoviscarra.doitall.data.formatExerciseWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    categories: List<Category>,
    onAddExercise: (categoryId: String, CategoryExercise) -> Unit,
    onEditExercise: (categoryId: String, CategoryExercise) -> Unit,
    onDeleteExercise: (categoryId: String, exerciseId: String) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    showHeader: Boolean = true
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) } // null = All
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<Pair<String, CategoryExercise>?>(null) }
    var exerciseToDelete by remember { mutableStateOf<Pair<String, CategoryExercise>?>(null) }

    // Flatten exercises with their parent category info
    val allCategoryExercises = remember(categories) {
        categories.flatMap { cat ->
            cat.exercises.map { ex -> cat to ex }
        }
    }

    val filteredList = remember(allCategoryExercises, searchQuery, selectedCategoryId) {
        allCategoryExercises.filter { (cat, ex) ->
            val matchesCategory = selectedCategoryId == null || cat.id == selectedCategoryId
            val matchesSearch = searchQuery.isBlank() ||
                    ex.name.contains(searchQuery, ignoreCase = true) ||
                    cat.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }.sortedBy { it.second.name }
    }

    Scaffold(
        topBar = {
            if (showHeader) {
                TopAppBar(
                    title = { Text("Exercise Library", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showAddCategoryDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "Add Category"
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddExerciseDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Exercise")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search exercises or muscle groups...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Category Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryId == null,
                    onClick = { selectedCategoryId = null },
                    label = { Text("All (${allCategoryExercises.size})") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = {
                            selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id
                        },
                        label = { Text("${cat.name} (${cat.exercises.size})") }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { showAddCategoryDialog = true },
                    label = { Text("+ Category") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Exercise List
            if (filteredList.isEmpty()) {
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
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No exercises match '$searchQuery'" else "No exercises in this category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(onClick = { showAddExerciseDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Exercise")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.second.id }) { (category, exercise) ->
                        ExerciseLibraryCard(
                            exercise = exercise,
                            categoryName = category.name,
                            onEdit = { exerciseToEdit = category.id to exercise },
                            onDelete = { exerciseToDelete = category.id to exercise }
                        )
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add Category") },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("Category Name (e.g. Forearms, Mobility)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCatName.isNotBlank()) {
                            onAddCategory(newCatName.trim())
                            showAddCategoryDialog = false
                        }
                    },
                    enabled = newCatName.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddExerciseDialog) {
        AddEditExerciseDialog(
            categories = categories,
            initialCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.firstOrNull(),
            initialExercise = null,
            onDismiss = { showAddExerciseDialog = false },
            onConfirm = { catId, ex ->
                onAddExercise(catId, ex)
                showAddExerciseDialog = false
            }
        )
    }

    exerciseToEdit?.let { (catId, exercise) ->
        AddEditExerciseDialog(
            categories = categories,
            initialCategory = categories.firstOrNull { it.id == catId },
            initialExercise = exercise,
            onDismiss = { exerciseToEdit = null },
            onConfirm = { newCatId, updatedEx ->
                if (newCatId != catId) {
                    onDeleteExercise(catId, exercise.id)
                    onAddExercise(newCatId, updatedEx)
                } else {
                    onEditExercise(catId, updatedEx)
                }
                exerciseToEdit = null
            }
        )
    }

    exerciseToDelete?.let { (catId, exercise) ->
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            title = { Text("Delete Exercise?") },
            text = { Text("Are you sure you want to remove '${exercise.name}' from your exercise library?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteExercise(catId, exercise.id)
                        exerciseToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ExerciseLibraryCard(
    exercise: CategoryExercise,
    categoryName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (exercise.isTimed) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Timed",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        if (exercise.isBodyweight || exercise.defaultLoad.contains("bodyweight", ignoreCase = true)) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "BW",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (exercise.usesStraps) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Straps",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    val details = buildString {
                        if (exercise.isTimed) {
                            append("${exercise.defaultSets} sets × ${exercise.defaultTime}")
                        } else {
                            append("${exercise.defaultSets} sets × ${exercise.defaultReps} reps")
                        }
                        if (exercise.defaultLoad.isNotBlank()) {
                            append(" @ ${exercise.defaultLoad}")
                        }
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (exercise.defaultNotes.isNotBlank()) {
                        Text(
                            text = "Note: ${exercise.defaultNotes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditExerciseDialog(
    categories: List<Category>,
    initialCategory: Category?,
    initialExercise: CategoryExercise?,
    onDismiss: () -> Unit,
    onConfirm: (categoryId: String, CategoryExercise) -> Unit
) {
    var name by remember { mutableStateOf(initialExercise?.name ?: "") }
    var selectedCategory by remember { mutableStateOf(initialCategory ?: categories.firstOrNull()) }
    var isTimed by remember { mutableStateOf(initialExercise?.isTimed ?: false) }
    var timeText by remember { mutableStateOf(initialExercise?.defaultTime ?: "60s") }
    var setsText by remember { mutableStateOf((initialExercise?.defaultSets ?: 3).toString()) }
    var repsText by remember { mutableStateOf(initialExercise?.defaultReps ?: "10") }
    var isBodyweight by remember {
        mutableStateOf(
            initialExercise?.isBodyweight ?: (initialExercise?.defaultLoad?.contains("bodyweight", ignoreCase = true) == true)
        )
    }
    var weightUnit by remember {
        mutableStateOf(
            initialExercise?.weightUnit?.lowercase()?.ifEmpty { "kg" }
                ?: if (initialExercise?.defaultLoad?.contains("lbs", ignoreCase = true) == true) "lbs" else "kg"
        )
    }
    var weightText by remember {
        mutableStateOf(
            if (initialExercise != null) {
                val numMatch = Regex("""[+-]?[\d.]+""").find(initialExercise.defaultLoad)
                numMatch?.value ?: ""
            } else ""
        )
    }
    var usesStraps by remember { mutableStateOf(initialExercise?.usesStraps ?: false) }
    var notesText by remember { mutableStateOf(initialExercise?.defaultNotes ?: "") }

    var expandedCategoryMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialExercise == null) "New Exercise" else "Edit Exercise") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selector
                if (categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expandedCategoryMenu,
                        onExpandedChange = { expandedCategoryMenu = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "Select Category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Muscle / Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCategoryMenu,
                            onDismissRequest = { expandedCategoryMenu = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategory = cat
                                        expandedCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTimed = !isTimed },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isTimed,
                        onCheckedChange = { isTimed = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Timed Exercise (e.g. Plank, Isometric Hold)")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = setsText,
                        onValueChange = { setsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Default Sets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (isTimed) {
                        OutlinedTextField(
                            value = timeText,
                            onValueChange = { timeText = it },
                            label = { Text("Target Time (e.g. 60s)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        OutlinedTextField(
                            value = repsText,
                            onValueChange = { repsText = it },
                            label = { Text("Default Reps") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
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
                            text = if (isBodyweight) "Added Weight" else "Default Weight",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = weightUnit == "lbs",
                                onClick = { weightUnit = "lbs" },
                                label = { Text("lbs") }
                            )
                            FilterChip(
                                selected = weightUnit == "kg",
                                onClick = { weightUnit = "kg" },
                                label = { Text("kg") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(if (isBodyweight) "Added weight (optional)" else "Weight (e.g. 20)") },
                        modifier = Modifier.fillMaxWidth(),
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

                // Notes / Comment
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes / Comment") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cat = selectedCategory ?: return@Button
                    if (name.isNotBlank()) {
                        val sets = setsText.toIntOrNull()?.coerceAtLeast(1) ?: 3
                        val id = initialExercise?.id ?: name.lowercase().trim().replace(Regex("""\s+"""), "_")
                        val computedLoad = when {
                            isBodyweight && weightText.isNotBlank() -> {
                                formatExerciseWeight(weightText, isBodyweight = true, unitLabel = weightUnit)
                            }
                            isBodyweight -> "Bodyweight"
                            weightText.isNotBlank() -> "${weightText.trim()} $weightUnit"
                            else -> ""
                        }
                        onConfirm(
                            cat.id,
                            CategoryExercise(
                                id = id,
                                name = name.trim(),
                                defaultSets = sets,
                                defaultReps = if (isTimed) timeText.trim().ifEmpty { "60s" } else repsText.trim().ifEmpty { "10" },
                                defaultLoad = computedLoad,
                                categories = listOf(cat.id),
                                isTimed = isTimed,
                                defaultTime = timeText.trim().ifEmpty { "60s" },
                                isBodyweight = isBodyweight,
                                weightUnit = weightUnit,
                                usesStraps = usesStraps,
                                defaultNotes = notesText.trim()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && selectedCategory != null
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

