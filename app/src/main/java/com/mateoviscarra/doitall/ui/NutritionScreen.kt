package com.mateoviscarra.doitall.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mateoviscarra.doitall.data.EntrySource
import com.mateoviscarra.doitall.data.FoodLogEntry
import com.mateoviscarra.doitall.data.MealSlot
import com.mateoviscarra.doitall.data.SavedMeal
import com.mateoviscarra.doitall.network.FoodScanResult
import com.mateoviscarra.doitall.network.GeminiVisionClient
import com.mateoviscarra.doitall.viewmodel.NutritionViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel = viewModel(),
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visionClient = remember { GeminiVisionClient(context.applicationContext) }

    val selectedDateIso by viewModel.selectedDate.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val dayTotals by viewModel.currentDayTotals.collectAsStateWithLifecycle()
    val dayLogs by viewModel.currentDayLogs.collectAsStateWithLifecycle()
    val savedMeals by viewModel.savedMeals.collectAsStateWithLifecycle()
    val apiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val aiPrompt by viewModel.aiPrompt.collectAsStateWithLifecycle()

    // Dialog & Sheet States
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<FoodLogEntry?>(null) }
    var initialSlotForAdd by remember { mutableStateOf(MealSlot.LUNCH) }

    var showStapleEditorDialog by remember { mutableStateOf(false) }
    var stapleToEdit by remember { mutableStateOf<SavedMeal?>(null) }

    var showScanSourceSheet by remember { mutableStateOf(false) }
    var scanUserNote by remember { mutableStateOf("") }
    var isAnalyzingPhoto by remember { mutableStateOf(false) }
    var scannedResult by remember { mutableStateOf<FoodScanResult?>(null) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }

    // Camera temp file URI
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraTempUri != null) {
            val uri = cameraTempUri!!
            isAnalyzingPhoto = true
            scope.launch {
                val result = visionClient.analyzeFoodImage(uri, apiKey, aiPrompt, scanUserNote)
                isAnalyzingPhoto = false
                if (result.isSuccess) {
                    scannedResult = result.getOrNull()
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                    scanErrorMessage = msg
                    Toast.makeText(context, "Scan error: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isAnalyzingPhoto = true
            scope.launch {
                val result = visionClient.analyzeFoodImage(uri, apiKey, aiPrompt, scanUserNote)
                isAnalyzingPhoto = false
                if (result.isSuccess) {
                    scannedResult = result.getOrNull()
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                    scanErrorMessage = msg
                    Toast.makeText(context, "Scan error: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun startCameraCapture() {
        val cacheDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val tempFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        cameraTempUri = uri
        cameraLauncher.launch(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrition") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- DATE STRIP ---
            DateNavigationStrip(
                selectedDateIso = selectedDateIso,
                onPrevious = { viewModel.previousDay() },
                onNext = { viewModel.nextDay() },
                onToday = { viewModel.goToToday() }
            )

            // --- MACRO OVERVIEW CARD ---
            MacroOverviewCard(
                goals = goals,
                totals = dayTotals
            )

            // --- ACTION BUTTONS (SCAN, QUICK ADD, NEW STAPLE) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (apiKey.isBlank()) {
                            Toast.makeText(context, "Please set your Gemini API key in Settings first", Toast.LENGTH_LONG).show()
                            onNavigateToSettings()
                        } else {
                            showScanSourceSheet = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Scan", maxLines = 1, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        initialSlotForAdd = MealSlot.LUNCH
                        showQuickAddDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Quick Add", maxLines = 1, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        stapleToEdit = null
                        showStapleEditorDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Staple", maxLines = 1, fontSize = 13.sp)
                }
            }

            // --- PINNED STAPLES CAROUSEL ---
            val pinnedStaples = savedMeals.filter { it.isPinnedStaple }
            if (pinnedStaples.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Staples & Favorites",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "1-tap add",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pinnedStaples, key = { it.id }) { staple ->
                            StapleCard(
                                staple = staple,
                                onLogClick = {
                                    viewModel.logStaple(staple)
                                    Toast.makeText(context, "Added ${staple.name} to log", Toast.LENGTH_SHORT).show()
                                },
                                onEditClick = {
                                    stapleToEdit = staple
                                    showStapleEditorDialog = true
                                },
                                onDeleteClick = {
                                    viewModel.deleteSavedMeal(staple.id)
                                    Toast.makeText(context, "Deleted staple", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // --- MEALS LOG (BY SLOT) ---
            Text(
                text = "Today's Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            MealSlot.entries.forEach { slot ->
                val entriesForSlot = dayLogs.filter { it.mealSlot == slot }
                MealSlotCard(
                    slot = slot,
                    entries = entriesForSlot,
                    onAddClick = {
                        initialSlotForAdd = slot
                        showQuickAddDialog = true
                    },
                    onEntryClick = { entry ->
                        entryToEdit = entry
                    },
                    onDeleteEntry = { entry ->
                        viewModel.deleteEntry(entry.id)
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- QUICK ADD / EDIT ENTRY DIALOG ---
    if (showQuickAddDialog || entryToEdit != null) {
        val isEditing = entryToEdit != null
        QuickAddFoodDialog(
            initialEntry = entryToEdit,
            initialMealSlot = entryToEdit?.mealSlot ?: initialSlotForAdd,
            onDismiss = {
                showQuickAddDialog = false
                entryToEdit = null
            },
            onSave = { name, cals, prot, carbs, fat, slot, note, saveAsStaple ->
                if (isEditing && entryToEdit != null) {
                    viewModel.updateEntry(
                        entryToEdit!!.copy(
                            name = name,
                            calories = cals,
                            proteinGrams = prot,
                            carbsGrams = carbs,
                            fatGrams = fat,
                            mealSlot = slot,
                            note = note
                        )
                    )
                } else {
                    viewModel.addManualEntry(
                        name = name,
                        calories = cals,
                        proteinGrams = prot,
                        carbsGrams = carbs,
                        fatGrams = fat,
                        mealSlot = slot,
                        note = note
                    )
                }

                if (saveAsStaple) {
                    viewModel.saveMeal(
                        SavedMeal(
                            name = name,
                            calories = cals,
                            proteinGrams = prot,
                            carbsGrams = carbs,
                            fatGrams = fat,
                            servingDescription = note,
                            isPinnedStaple = true,
                            defaultMealSlot = slot
                        )
                    )
                }

                showQuickAddDialog = false
                entryToEdit = null
            }
        )
    }

    // --- STAPLE EDITOR DIALOG ---
    if (showStapleEditorDialog) {
        StapleEditorDialog(
            initialStaple = stapleToEdit,
            onDismiss = { showStapleEditorDialog = false },
            onSave = { saved ->
                viewModel.saveMeal(saved)
                showStapleEditorDialog = false
            }
        )
    }

    // --- PHOTO SCAN SOURCE SHEET ---
    if (showScanSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showScanSourceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "AI Nutrition Scanner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Snap a photo of your meal or nutrition label. Gemini will detect items, estimate portions, and calculate macros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = scanUserNote,
                    onValueChange = { scanUserNote = it },
                    label = { Text("Extra details (optional)") },
                    placeholder = { Text("e.g. 2 pieces, Bolivian marraqueta with cheese") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            showScanSourceSheet = false
                            startCameraCapture()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Camera")
                    }

                    OutlinedButton(
                        onClick = {
                            showScanSourceSheet = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gallery")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // --- SCAN PROGRESS DIALOG ---
    if (isAnalyzingPhoto) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Analyzing Photo...") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text("Gemini is estimating calories, macros, and Bolivian culinary ingredients...")
                }
            }
        )
    }

    // --- SCAN RESULT REVIEW SHEET ---
    scannedResult?.let { result ->
        ScanResultReviewDialog(
            result = result,
            onDismiss = { scannedResult = null },
            onConfirm = { name, cals, prot, carbs, fat, slot, note, portionMult, saveAsStaple ->
                viewModel.addManualEntry(
                    name = name,
                    calories = cals,
                    proteinGrams = prot,
                    carbsGrams = carbs,
                    fatGrams = fat,
                    mealSlot = slot,
                    note = note,
                    source = EntrySource.AI_SCAN
                )

                if (saveAsStaple) {
                    viewModel.saveMeal(
                        SavedMeal(
                            name = name,
                            calories = (cals / portionMult).toInt(),
                            proteinGrams = prot / portionMult,
                            carbsGrams = carbs / portionMult,
                            fatGrams = fat / portionMult,
                            servingDescription = note,
                            isPinnedStaple = true,
                            defaultMealSlot = slot
                        )
                    )
                }

                scannedResult = null
                Toast.makeText(context, "Logged $name!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- SCAN ERROR DIALOG ---
    scanErrorMessage?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { scanErrorMessage = null },
            title = { Text("AI Scan Notice") },
            text = { Text(errorMsg) },
            confirmButton = {
                Button(onClick = { scanErrorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

// -------------------------------------------------------------------------
// DATE NAVIGATION STRIP
// -------------------------------------------------------------------------
@Composable
private fun DateNavigationStrip(
    selectedDateIso: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val selectedDate = remember(selectedDateIso) {
        runCatching { LocalDate.parse(selectedDateIso) }.getOrDefault(LocalDate.now())
    }
    val today = remember { LocalDate.now() }

    val displayText = remember(selectedDate) {
        when (selectedDate) {
            today -> "Today, ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            today.minusDays(1) -> "Yesterday, ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            today.plusDays(1) -> "Tomorrow, ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
            }

            Surface(
                onClick = onToday,
                shape = RoundedCornerShape(16.dp),
                color = if (selectedDate == today) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
            }
        }
    }
}

// -------------------------------------------------------------------------
// MACRO OVERVIEW CARD
// -------------------------------------------------------------------------
@Composable
private fun MacroOverviewCard(
    goals: com.mateoviscarra.doitall.data.NutritionGoals,
    totals: com.mateoviscarra.doitall.data.DayNutritionTotals
) {
    val calRemaining = (goals.calorieTarget - totals.totalCalories).coerceAtLeast(0)
    val calProgress = (totals.totalCalories.toFloat() / goals.calorieTarget.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Calories Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Calories",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${totals.totalCalories}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$calRemaining kcal remaining",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Goal: ${goals.calorieTarget} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { calProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            // 3 Macro Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MacroColumnItem(
                    label = "Protein",
                    current = totals.totalProtein,
                    target = goals.proteinTargetGrams,
                    unit = "g",
                    color = Color(0xFFEF5350),
                    modifier = Modifier.weight(1f)
                )
                MacroColumnItem(
                    label = "Carbs",
                    current = totals.totalCarbs,
                    target = goals.carbsTargetGrams,
                    unit = "g",
                    color = Color(0xFF42A5F5),
                    modifier = Modifier.weight(1f)
                )
                MacroColumnItem(
                    label = "Fat",
                    current = totals.totalFat,
                    target = goals.fatTargetGrams,
                    unit = "g",
                    color = Color(0xFFFFA726),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MacroColumnItem(
    label: String,
    current: Float,
    target: Float,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = (current / target.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${current.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "/${target.toInt()}$unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

// -------------------------------------------------------------------------
// STAPLE CARD
// -------------------------------------------------------------------------
@Composable
private fun StapleCard(
    staple: SavedMeal,
    onLogClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.width(180.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = staple.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            Text(
                text = "${staple.calories} kcal • ${staple.proteinGrams.toInt()}g P",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            if (staple.servingDescription.isNotBlank()) {
                Text(
                    text = staple.servingDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Button(
                onClick = onLogClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Log", fontSize = 12.sp)
            }
        }
    }
}

// -------------------------------------------------------------------------
// MEAL SLOT CARD
// -------------------------------------------------------------------------
@Composable
private fun MealSlotCard(
    slot: MealSlot,
    entries: List<FoodLogEntry>,
    onAddClick: () -> Unit,
    onEntryClick: (FoodLogEntry) -> Unit,
    onDeleteEntry: (FoodLogEntry) -> Unit
) {
    val totalCals = entries.sumOf { it.totalCalories }
    val totalProt = entries.sumOf { it.totalProtein.toDouble() }.toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = slot.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (entries.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• $totalCals kcal | ${totalProt}g P",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add to ${slot.displayName}")
                }
            }

            if (entries.isEmpty()) {
                Text(
                    text = "No meals logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                entries.forEach { entry ->
                    FoodEntryRow(
                        entry = entry,
                        onClick = { onEntryClick(entry) },
                        onDelete = { onDeleteEntry(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodEntryRow(
    entry: FoodLogEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (entry.portionMultiplier != 1.0f) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${entry.portionMultiplier}x)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (entry.source == EntrySource.AI_SCAN) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "${entry.totalCalories} kcal • ${entry.totalProtein.toInt()}g P • ${entry.totalCarbs.toInt()}g C • ${entry.totalFat.toInt()}g F",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------------------
// QUICK ADD / EDIT FOOD DIALOG
// -------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddFoodDialog(
    initialEntry: FoodLogEntry?,
    initialMealSlot: MealSlot,
    onDismiss: () -> Unit,
    onSave: (name: String, calories: Int, prot: Float, carbs: Float, fat: Float, slot: MealSlot, note: String, saveAsStaple: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialEntry?.name ?: "") }
    var calories by remember { mutableStateOf(initialEntry?.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(initialEntry?.proteinGrams?.toInt()?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initialEntry?.carbsGrams?.toInt()?.toString() ?: "") }
    var fat by remember { mutableStateOf(initialEntry?.fatGrams?.toInt()?.toString() ?: "") }
    var selectedSlot by remember { mutableStateOf(initialEntry?.mealSlot ?: initialMealSlot) }
    var note by remember { mutableStateOf(initialEntry?.note ?: "") }
    var saveAsStaple by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEntry == null) "Log Food" else "Edit Food Entry") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meal / Food Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Meal Slot Selector Chips
                Text("Meal Slot", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MealSlot.entries.forEach { slot ->
                        FilterChip(
                            selected = selectedSlot == slot,
                            onClick = { selectedSlot = slot },
                            label = { Text(slot.displayName) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("Calories") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text("Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Serving description or note") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (initialEntry == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { saveAsStaple = !saveAsStaple }
                    ) {
                        Checkbox(
                            checked = saveAsStaple,
                            onCheckedChange = { saveAsStaple = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save as Pinned Staple", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name.trim(),
                            calories.toIntOrNull() ?: 0,
                            protein.toFloatOrNull() ?: 0f,
                            carbs.toFloatOrNull() ?: 0f,
                            fat.toFloatOrNull() ?: 0f,
                            selectedSlot,
                            note.trim(),
                            saveAsStaple
                        )
                    }
                }
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

// -------------------------------------------------------------------------
// STAPLE EDITOR DIALOG
// -------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StapleEditorDialog(
    initialStaple: SavedMeal?,
    onDismiss: () -> Unit,
    onSave: (SavedMeal) -> Unit
) {
    var name by remember { mutableStateOf(initialStaple?.name ?: "") }
    var calories by remember { mutableStateOf(initialStaple?.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(initialStaple?.proteinGrams?.toInt()?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initialStaple?.carbsGrams?.toInt()?.toString() ?: "") }
    var fat by remember { mutableStateOf(initialStaple?.fatGrams?.toInt()?.toString() ?: "") }
    var servingDesc by remember { mutableStateOf(initialStaple?.servingDescription ?: "") }
    var selectedSlot by remember { mutableStateOf(initialStaple?.defaultMealSlot ?: MealSlot.BREAKFAST) }
    var isPinned by remember { mutableStateOf(initialStaple?.isPinnedStaple ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialStaple == null) "New Staple Meal" else "Edit Staple") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Staple Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("Calories") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text("Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = servingDesc,
                    onValueChange = { servingDesc = it },
                    label = { Text("Serving description (e.g. 1 jar, 1 unit)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Default Meal Slot", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MealSlot.entries.forEach { slot ->
                        FilterChip(
                            selected = selectedSlot == slot,
                            onClick = { selectedSlot = slot },
                            label = { Text(slot.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val meal = (initialStaple ?: SavedMeal(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            calories = 0
                        )).copy(
                            name = name.trim(),
                            calories = calories.toIntOrNull() ?: 0,
                            proteinGrams = protein.toFloatOrNull() ?: 0f,
                            carbsGrams = carbs.toFloatOrNull() ?: 0f,
                            fatGrams = fat.toFloatOrNull() ?: 0f,
                            servingDescription = servingDesc.trim(),
                            defaultMealSlot = selectedSlot,
                            isPinnedStaple = isPinned
                        )
                        onSave(meal)
                    }
                }
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

// -------------------------------------------------------------------------
// SCAN RESULT REVIEW DIALOG
// -------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanResultReviewDialog(
    result: FoodScanResult,
    onDismiss: () -> Unit,
    onConfirm: (name: String, calories: Int, prot: Float, carbs: Float, fat: Float, slot: MealSlot, note: String, portionMultiplier: Float, saveAsStaple: Boolean) -> Unit
) {
    var multiplier by remember { mutableFloatStateOf(1.0f) }
    var name by remember(result.mealName) { mutableStateOf(result.mealName) }
    var baseCalories by remember(result.calories) { mutableIntStateOf(result.calories) }
    var baseProtein by remember(result.proteinGrams) { mutableFloatStateOf(result.proteinGrams) }
    var baseCarbs by remember(result.carbsGrams) { mutableFloatStateOf(result.carbsGrams) }
    var baseFat by remember(result.fatGrams) { mutableFloatStateOf(result.fatGrams) }
    var selectedSlot by remember { mutableStateOf(MealSlot.LUNCH) }
    var saveAsStaple by remember { mutableStateOf(false) }

    val currentCals = (baseCalories * multiplier).toInt()
    val currentProt = baseProtein * multiplier
    val currentCarbs = baseCarbs * multiplier
    val currentFat = baseFat * multiplier

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gemini AI Estimate")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meal Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (result.servingDescription.isNotBlank()) {
                    Text(
                        text = "Portion: ${result.servingDescription}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Multiplier Chips
                Text("Portion Multiplier", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { m ->
                        FilterChip(
                            selected = multiplier == m,
                            onClick = { multiplier = m },
                            label = { Text("${m}x") }
                        )
                    }
                }

                // Detected breakdown card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$currentCals kcal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${currentProt.toInt()}g Protein  •  ${currentCarbs.toInt()}g Carbs  •  ${currentFat.toInt()}g Fat",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Sub-items list if available
                if (result.items.isNotEmpty()) {
                    Text("Detected Items:", style = MaterialTheme.typography.labelMedium)
                    result.items.forEach { item ->
                        Text(
                            text = "• ${item.name} (${item.portion}): ${item.calories} kcal, ${item.proteinGrams.toInt()}g P",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (result.notes.isNotBlank()) {
                    Text(
                        text = "Note: ${result.notes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Text("Meal Slot", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MealSlot.entries.forEach { slot ->
                        FilterChip(
                            selected = selectedSlot == slot,
                            onClick = { selectedSlot = slot },
                            label = { Text(slot.displayName) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { saveAsStaple = !saveAsStaple }
                ) {
                    Checkbox(
                        checked = saveAsStaple,
                        onCheckedChange = { saveAsStaple = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save to Staples", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.trim(),
                        currentCals,
                        currentProt,
                        currentCarbs,
                        currentFat,
                        selectedSlot,
                        result.servingDescription,
                        multiplier,
                        saveAsStaple
                    )
                }
            ) {
                Text("Confirm & Log")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
