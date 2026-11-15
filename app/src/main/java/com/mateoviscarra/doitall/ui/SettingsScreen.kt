package com.mateoviscarra.doitall.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateoviscarra.doitall.calendar.CalendarManager
import com.mateoviscarra.doitall.data.DataBackupManager
import com.mateoviscarra.doitall.data.NutritionGoals
import com.mateoviscarra.doitall.data.NutritionRepository
import com.mateoviscarra.doitall.data.UserProfile
import com.mateoviscarra.doitall.data.WorkoutPlan
import com.mateoviscarra.doitall.ui.theme.ThemeMode
import com.mateoviscarra.doitall.ui.theme.ThemePreferenceStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    workoutPlan: WorkoutPlan,
    calendarManager: CalendarManager,
    themeStore: ThemePreferenceStore,
    nutritionRepo: NutritionRepository? = null,
    onBack: () -> Unit,
    onCalendarSettingsClick: () -> Unit,
    onDataRestored: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { nutritionRepo ?: NutritionRepository(context.applicationContext) }
    val backupManager = remember { DataBackupManager(context.applicationContext) }

    val authState = calendarManager.getAuthState()
    val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()

    val goals by repo.goals.collectAsStateWithLifecycle()
    val savedProfile by repo.userProfile.collectAsStateWithLifecycle()
    val apiKey by repo.geminiApiKey.collectAsStateWithLifecycle()
    val aiPrompt by repo.aiPrompt.collectAsStateWithLifecycle()

    // Local profile states
    var heightInput by remember { mutableStateOf("") }
    var selectedSex by remember { mutableStateOf("") }
    var birthDateInput by remember { mutableStateOf("") }
    var targetWeightInput by remember { mutableStateOf("") }
    var fitnessGoalInput by remember { mutableStateOf("") }
    var showBirthDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(savedProfile) {
        heightInput = if (savedProfile.heightCm > 0f) {
            if (savedProfile.heightCm == savedProfile.heightCm.toInt().toFloat()) {
                savedProfile.heightCm.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", savedProfile.heightCm)
            }
        } else ""
        selectedSex = savedProfile.sex
        birthDateInput = savedProfile.birthDateIso
        targetWeightInput = if (savedProfile.targetWeightKg > 0f) {
            if (savedProfile.targetWeightKg == savedProfile.targetWeightKg.toInt().toFloat()) {
                savedProfile.targetWeightKg.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", savedProfile.targetWeightKg)
            }
        } else ""
        fitnessGoalInput = savedProfile.fitnessGoalSummary
    }

    // Local form states
    var calorieInput by remember(goals.calorieTarget) { mutableStateOf(goals.calorieTarget.toString()) }
    var proteinInput by remember(goals.proteinTargetGrams) { mutableStateOf(goals.proteinTargetGrams.toInt().toString()) }
    var carbsInput by remember(goals.carbsTargetGrams) { mutableStateOf(goals.carbsTargetGrams.toInt().toString()) }
    var fatInput by remember(goals.fatTargetGrams) { mutableStateOf(goals.fatTargetGrams.toInt().toString()) }

    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var promptInput by remember(aiPrompt) { mutableStateOf(aiPrompt) }

    // Dialog state for restore confirmation / result
    var restoreResultDialogMessage by remember { mutableStateOf<String?>(null) }

    val saveProfileToRepo = {
        val cleanHeight = heightInput.trim().replace(',', '.').toFloatOrNull() ?: 0f
        val cleanTargetWeight = targetWeightInput.trim().replace(',', '.').toFloatOrNull() ?: 0f
        repo.saveUserProfile(
            UserProfile(
                heightCm = cleanHeight,
                sex = selectedSex,
                birthDateIso = birthDateInput.trim(),
                targetWeightKg = cleanTargetWeight,
                fitnessGoalSummary = fitnessGoalInput.trim()
            )
        )
    }

    // File picker launcher for ZIP or JSON restore
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val result = context.contentResolver.openInputStream(uri)?.use { stream ->
                        backupManager.restoreFromStream(stream)
                    } ?: Result.failure(IllegalArgumentException("Unable to open selected file."))

                    if (result.isSuccess) {
                        restoreResultDialogMessage = result.getOrNull() ?: "Backup successfully restored!"
                        repo.reloadFromPrefs()
                        onDataRestored()
                    } else {
                        restoreResultDialogMessage = "Restore failed: ${result.exceptionOrNull()?.localizedMessage}"
                    }
                } catch (e: Exception) {
                    restoreResultDialogMessage = "Failed to read file: ${e.localizedMessage}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- PERSONAL PROFILE & FITNESS GOALS ---
            Text(
                text = "Personal Profile & Fitness Goals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val computedAge = remember(birthDateInput) {
                        UserProfile(birthDateIso = birthDateInput.trim()).calculateAge()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Athlete Info & Goals",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (computedAge != null) {
                            Text(
                                text = "Age: $computedAge yrs",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Included automatically in your AI Export bundle so any AI understands your body stats and training goals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sex:",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        listOf("Male", "Female", "Other").forEach { option ->
                            FilterChip(
                                selected = selectedSex.equals(option, ignoreCase = true),
                                onClick = {
                                    selectedSex = if (selectedSex.equals(option, ignoreCase = true)) "" else option
                                },
                                label = { Text(option) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { heightInput = it },
                            label = { Text("Height (cm)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = targetWeightInput,
                            onValueChange = { targetWeightInput = it },
                            label = { Text("Target Weight (kg)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = birthDateInput,
                        onValueChange = { birthDateInput = it },
                        label = { Text("Birth Date (YYYY-MM-DD)") },
                        placeholder = { Text("e.g. 2001-05-14") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showBirthDatePicker = true }) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Pick birth date"
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = fitnessGoalInput,
                        onValueChange = { fitnessGoalInput = it },
                        label = { Text("Fitness Goals & Context") },
                        placeholder = { Text("e.g. Lean bulk/cut, upper body & trap focus, 2-on 1-off split...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )

                    Button(
                        onClick = {
                            saveProfileToRepo()
                            Toast.makeText(context, "Profile & fitness goals saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Profile & Goals")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- APPEARANCE ---
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

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
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose light, dark, or follow device system settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = { themeStore.setThemeMode(ThemeMode.SYSTEM) },
                            label = { Text("System") }
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { themeStore.setThemeMode(ThemeMode.LIGHT) },
                            label = { Text("Light") }
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { themeStore.setThemeMode(ThemeMode.DARK) },
                            label = { Text("Dark") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- NUTRITION GOALS ---
            Text(
                text = "Daily Nutrition Targets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Macro Goals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = calorieInput,
                            onValueChange = { calorieInput = it },
                            label = { Text("Calories") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = proteinInput,
                            onValueChange = { proteinInput = it },
                            label = { Text("Protein (g)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = carbsInput,
                            onValueChange = { carbsInput = it },
                            label = { Text("Carbs (g)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = fatInput,
                            onValueChange = { fatInput = it },
                            label = { Text("Fat (g)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            saveProfileToRepo()
                            val cleanCal = calorieInput.trim().replace(',', '.')
                            val cals = cleanCal.toIntOrNull()
                                ?: cleanCal.toDoubleOrNull()?.let { kotlin.math.round(it).toInt() }
                                ?: goals.calorieTarget
                            val prot = proteinInput.trim().replace(',', '.').toFloatOrNull() ?: goals.proteinTargetGrams
                            val carbs = carbsInput.trim().replace(',', '.').toFloatOrNull() ?: goals.carbsTargetGrams
                            val fat = fatInput.trim().replace(',', '.').toFloatOrNull() ?: goals.fatTargetGrams
                            repo.updateGoals(
                                NutritionGoals(
                                    calorieTarget = cals,
                                    proteinTargetGrams = prot,
                                    carbsTargetGrams = carbs,
                                    fatTargetGrams = fat
                                )
                            )
                            Toast.makeText(context, "Profile & nutrition targets saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Profile & Targets")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- AI NUTRITION SETTINGS ---
            Text(
                text = "AI Vision Scanner (Gemini)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gemini AI Vision Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Take photos of meals or nutrition labels to automatically estimate calories and macros. Enter your free Google AI Studio API key below (aistudio.google.com).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            repo.updateGeminiApiKey(it.trim())
                        },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isApiKeyVisible) "Hide key" else "Show key"
                                )
                            }
                        },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "System Prompt (Bolivia Localization)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "Controls how Gemini estimates dishes, portion sizes, and Bolivian culinary ingredients.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        label = { Text("Custom Prompt") },
                        maxLines = 6
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                repo.resetAiPromptToDefault()
                                promptInput = repo.aiPrompt.value
                                Toast.makeText(context, "Reset prompt to Bolivia default", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }

                        Button(
                            onClick = {
                                repo.updateAiPrompt(promptInput.trim())
                                Toast.makeText(context, "System prompt saved!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Save Prompt")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- DATA BACKUP & EXPORT ---
            Text(
                text = "Data Backup & Portability",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unified Backup & Spreadsheets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Export an AI Coach Bundle (.ZIP) containing your full JSON backup plus AI_README_AND_SCHEMA.md (with your stats, goals, and JSON schema rules), or restore from any .ZIP / .JSON backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            try {
                                saveProfileToRepo()
                                backupManager.shareAiBackupZip()
                            } catch (e: Exception) {
                                Toast.makeText(context, "ZIP export failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export AI Bundle (.ZIP)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    saveProfileToRepo()
                                    val backupJson = backupManager.createUnifiedBackupJson()
                                    val fileName = backupManager.defaultJsonFilename()
                                    backupManager.shareExportFile(fileName, backupJson, "application/json")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export .JSON", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                restoreLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/json",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore", maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val csv = backupManager.exportNutritionLogsCsv()
                                    val fileName = "nutrition_logs_${LocalDate.now()}.csv"
                                    backupManager.shareExportFile(fileName, csv, "text/csv")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Nutrition (CSV)")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val csv = backupManager.exportWorkoutHistoryCsv()
                                    val fileName = "workout_history_${LocalDate.now()}.csv"
                                    backupManager.shareExportFile(fileName, csv, "text/csv")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Workouts (CSV)")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- INTEGRATIONS (GOOGLE CALENDAR) ---
            Text(
                text = "Integrations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                onClick = onCalendarSettingsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Calendar",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when {
                                authState.isConnected -> "Connected"
                                calendarManager.hasAuthError -> "Reconnection required"
                                else -> "Not connected"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                authState.isConnected -> MaterialTheme.colorScheme.primary
                                calendarManager.hasAuthError -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- ABOUT ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "DoItAll Workout & Nutrition Tracker v1.2",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Plan: ${workoutPlan.schedule.size} days, ${workoutPlan.catalog.size} exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Calendar credentials and nutrition data are stored securely on your device and never uploaded to third-party databases.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }

    // Birth Date picker dialog
    if (showBirthDatePicker) {
        val initialEpochMillis = remember(birthDateInput) {
            try {
                LocalDate.parse(birthDateInput.trim())
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                LocalDate.of(2000, 1, 1)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()
            }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEpochMillis)
        DatePickerDialog(
            onDismissRequest = { showBirthDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        birthDateInput = selectedDate.toString()
                    }
                    showBirthDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBirthDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Restore completion alert dialog
    restoreResultDialogMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { restoreResultDialogMessage = null },
            title = { Text("Backup Restore") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { restoreResultDialogMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}
