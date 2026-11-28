package com.mateoviscarra.doitall.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.mateoviscarra.doitall.BuildConfig
import com.mateoviscarra.doitall.calendar.CalendarManager
import com.mateoviscarra.doitall.data.DataBackupManager
import com.mateoviscarra.doitall.data.DriveSyncManager
import com.mateoviscarra.doitall.data.HandScaleReference
import com.mateoviscarra.doitall.data.NutritionGoals
import com.mateoviscarra.doitall.data.NutritionRepository
import com.mateoviscarra.doitall.data.RecoverableDriveAuthException
import com.mateoviscarra.doitall.data.UserProfile
import com.mateoviscarra.doitall.data.WorkoutPlan
import com.mateoviscarra.doitall.ui.theme.ThemeMode
import com.mateoviscarra.doitall.ui.theme.ThemePreferenceStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class SettingsSection(val title: String) {
    MENU("Settings"),
    PROFILE("Athlete Profile"),
    NUTRITION_TARGETS("Nutrition Targets"),
    GEMINI_AI("Gemini AI & Prompt"),
    HAND_SCALE("Hand Scale Reference"),
    DRIVE_SYNC("Drive & NotebookLM"),
    BACKUP_EXPORT("Backup & Export"),
    APPEARANCE("Appearance"),
    ABOUT("About & Privacy")
}

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

    var activeSection by rememberSaveable { mutableStateOf(SettingsSection.MENU) }

    BackHandler(enabled = activeSection != SettingsSection.MENU) {
        activeSection = SettingsSection.MENU
    }

    val authState = calendarManager.getAuthState()
    val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()
    val driveSyncState by DriveSyncManager.stateFlow(context).collectAsStateWithLifecycle()
    var driveAccountEmail by remember { mutableStateOf(DriveSyncManager.getSignedInAccount(context)?.email) }
    var hasDriveScope by remember { mutableStateOf(DriveSyncManager.hasDriveScope(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                driveAccountEmail = DriveSyncManager.getSignedInAccount(context)?.email
                hasDriveScope = DriveSyncManager.hasDriveScope(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val goals by repo.goals.collectAsStateWithLifecycle()
    val savedProfile by repo.userProfile.collectAsStateWithLifecycle()
    val apiKey by repo.geminiApiKey.collectAsStateWithLifecycle()
    val aiPrompt by repo.aiPrompt.collectAsStateWithLifecycle()
    val savedHandRef by repo.handScaleRef.collectAsStateWithLifecycle()

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

    // Hand scale reference states
    var handRefEnabled by remember { mutableStateOf(false) }
    var handLengthInput by remember { mutableStateOf("") }
    var palmLengthInput by remember { mutableStateOf("") }
    var palmWidthInput by remember { mutableStateOf("") }
    var middleFingerInput by remember { mutableStateOf("") }
    var handSpanInput by remember { mutableStateOf("") }
    var handNotesInput by remember { mutableStateOf("") }
    var showHandPromptPreview by remember { mutableStateOf(false) }

    fun populateHandInputs(ref: HandScaleReference) {
        handRefEnabled = ref.enabled
        handLengthInput = if (ref.handLengthCm > 0f) HandScaleReference.formatCm(ref.handLengthCm) else ""
        palmLengthInput = if (ref.palmLengthCm > 0f) HandScaleReference.formatCm(ref.palmLengthCm) else ""
        palmWidthInput = if (ref.palmWidthCm > 0f) HandScaleReference.formatCm(ref.palmWidthCm) else ""
        middleFingerInput = if (ref.middleFingerCm > 0f) HandScaleReference.formatCm(ref.middleFingerCm) else ""
        handSpanInput = if (ref.handSpanCm > 0f) HandScaleReference.formatCm(ref.handSpanCm) else ""
        handNotesInput = ref.customNotes
    }

    LaunchedEffect(savedHandRef) {
        populateHandInputs(savedHandRef)
    }

    fun parseCm(raw: String): Float =
        raw.trim().replace(',', '.').toFloatOrNull()?.coerceAtLeast(0f) ?: 0f

    fun buildHandRefFromInputs(overrideEnabled: Boolean = handRefEnabled): HandScaleReference =
        HandScaleReference(
            enabled = overrideEnabled,
            handLengthCm = parseCm(handLengthInput),
            palmLengthCm = parseCm(palmLengthInput),
            palmWidthCm = parseCm(palmWidthInput),
            middleFingerCm = parseCm(middleFingerInput),
            handSpanCm = parseCm(handSpanInput),
            customNotes = handNotesInput.trim()
        )

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

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            coroutineScope.launch {
                val res = DriveSyncManager.syncNow(context)
                res.onSuccess {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Drive sync failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val driveSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = result.data?.let { GoogleSignIn.getSignedInAccountFromIntent(it) }
        if (task != null) {
            task.addOnSuccessListener { account ->
                calendarManager.setAuthSuccess()
                driveAccountEmail = account.email
                hasDriveScope = DriveSyncManager.hasDriveScope(context)
                if (hasDriveScope) {
                    coroutineScope.launch {
                        val res = DriveSyncManager.syncNow(context)
                        res.onSuccess {
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            val rec = (err as? RecoverableDriveAuthException)?.recoveryIntent
                            if (rec != null) {
                                driveConsentLauncher.launch(rec)
                            } else {
                                Toast.makeText(context, "Sync error: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }.addOnFailureListener { err ->
                val code = (err as? ApiException)?.statusCode
                val msg = if (code == CommonStatusCodes.DEVELOPER_ERROR || code == 10) {
                    "Google sign-in configuration mismatch (Code 10). Check SHA-1 in Calendar Settings."
                } else {
                    "Google Drive sign-in failed: ${err.localizedMessage ?: "Code $code"}"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        } else {
            driveAccountEmail = DriveSyncManager.getSignedInAccount(context)?.email
            hasDriveScope = DriveSyncManager.hasDriveScope(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activeSection.title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeSection == SettingsSection.MENU) onBack()
                            else activeSection = SettingsSection.MENU
                        }
                    ) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (activeSection) {
                SettingsSection.MENU -> {
                    val profileSummary = buildList {
                        if (savedProfile.sex.isNotBlank()) add(savedProfile.sex)
                        if (savedProfile.heightCm > 0f) add("${savedProfile.heightCm.toInt()} cm")
                        if (savedProfile.targetWeightKg > 0f) add("Goal ${savedProfile.targetWeightKg} kg")
                    }.joinToString(" • ").ifBlank { "Set height, age, target weight & goals" }

                    val targetsSummary = "${goals.calorieTarget} kcal • ${goals.proteinTargetGrams.toInt()}g P • ${goals.carbsTargetGrams.toInt()}g C • ${goals.fatTargetGrams.toInt()}g F"
                    val geminiSummary = if (apiKey.isNotBlank()) "API key configured" else "API key not set"
                    val handSummary = if (savedHandRef.enabled) {
                        if (savedHandRef.handLengthCm > 0f) "Active • ${HandScaleReference.formatCm(savedHandRef.handLengthCm)} cm"
                        else "Active"
                    } else {
                        "Off"
                    }
                    val calendarSummary = when {
                        authState.isConnected -> "Connected"
                        calendarManager.hasAuthError -> "Reconnection required"
                        else -> "Not connected"
                    }
                    val driveSummary = when {
                        hasDriveScope && !driveAccountEmail.isNullOrBlank() -> "Connected • $driveAccountEmail"
                        hasDriveScope -> "Connected"
                        !driveAccountEmail.isNullOrBlank() -> "Drive access needed"
                        else -> "Not connected"
                    }
                    val themeSummary = when (themeMode) {
                        ThemeMode.SYSTEM -> "Follow system"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }

                    SettingsCategoryGroup(title = "Profile & Goals") {
                        SettingsMenuRow(
                            icon = Icons.Outlined.Person,
                            title = "Athlete Profile",
                            subtitle = profileSummary,
                            onClick = { activeSection = SettingsSection.PROFILE }
                        )
                        SettingsRowDivider()
                        SettingsMenuRow(
                            icon = Icons.Outlined.Restaurant,
                            title = "Nutrition Targets",
                            subtitle = targetsSummary,
                            onClick = { activeSection = SettingsSection.NUTRITION_TARGETS }
                        )
                    }

                    SettingsCategoryGroup(title = "AI & Scanning") {
                        SettingsMenuRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = "Gemini AI & Prompt",
                            subtitle = geminiSummary,
                            onClick = { activeSection = SettingsSection.GEMINI_AI }
                        )
                        SettingsRowDivider()
                        SettingsMenuRow(
                            icon = Icons.Outlined.Straighten,
                            title = "Hand Scale Reference",
                            subtitle = handSummary,
                            onClick = { activeSection = SettingsSection.HAND_SCALE }
                        )
                    }

                    SettingsCategoryGroup(title = "Sync & Data") {
                        SettingsMenuRow(
                            icon = Icons.Outlined.CalendarMonth,
                            title = "Google Calendar",
                            subtitle = calendarSummary,
                            onClick = onCalendarSettingsClick
                        )
                        SettingsRowDivider()
                        SettingsMenuRow(
                            icon = Icons.Outlined.CloudUpload,
                            title = "Drive & NotebookLM",
                            subtitle = driveSummary,
                            onClick = { activeSection = SettingsSection.DRIVE_SYNC }
                        )
                        SettingsRowDivider()
                        SettingsMenuRow(
                            icon = Icons.Outlined.Storage,
                            title = "Backup & Export",
                            subtitle = "ZIP bundle, JSON restore & CSV exports",
                            onClick = { activeSection = SettingsSection.BACKUP_EXPORT }
                        )
                    }

                    SettingsCategoryGroup(title = "App") {
                        SettingsMenuRow(
                            icon = Icons.Outlined.Palette,
                            title = "Appearance",
                            subtitle = themeSummary,
                            onClick = { activeSection = SettingsSection.APPEARANCE }
                        )
                        SettingsRowDivider()
                        SettingsMenuRow(
                            icon = Icons.Outlined.Info,
                            title = "About & Privacy",
                            subtitle = "v1.2 • ${workoutPlan.schedule.size} days, ${workoutPlan.catalog.size} exercises",
                            onClick = { activeSection = SettingsSection.ABOUT }
                        )
                    }
                }

                SettingsSection.PROFILE -> {
                    SettingsInfoBanner(
                        icon = Icons.Outlined.Person,
                        title = "Why set your Athlete Profile?",
                        description = "Your height, age, target weight, and training context are included automatically in AI Coach exports, Menu Coach advice, and your NotebookLM context doc so recommendations stay tailored to you."
                    )

                    SettingsSectionCard {
                        val computedAge = remember(birthDateInput) {
                            UserProfile(birthDateIso = birthDateInput.trim()).calculateAge()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Athlete Info",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (computedAge != null) {
                                Text(
                                    text = "Age: $computedAge yrs",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

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
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = "Pick birth date"
                                    )
                                }
                            }
                        )

                        OutlinedTextField(
                            value = fitnessGoalInput,
                            onValueChange = { fitnessGoalInput = it },
                            label = { Text("Fitness Goals & Context") },
                            placeholder = { Text("e.g. Cut / body recomposition, upper body & trap focus, 2-on 1-off split...") },
                            supportingText = { Text("Calorie & macro targets are configured separately in Nutrition Targets.") },
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
                            Text("Save Profile")
                        }
                    }
                }

                SettingsSection.NUTRITION_TARGETS -> {
                    SettingsInfoBanner(
                        icon = Icons.Outlined.Restaurant,
                        title = "Daily Nutrition Targets",
                        description = "Sets your daily calorie and macro budgets. These targets power the Nutrition progress bars, Menu Coach meal recommendations, and exported coach summaries."
                    )

                    SettingsSectionCard {
                        Text(
                            text = "Macro Goals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

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
                                Toast.makeText(context, "Nutrition targets saved!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save Targets")
                        }
                    }
                }

                SettingsSection.GEMINI_AI -> {
                    val isPersonal = BuildConfig.SEED_PERSONAL_DATA
                    SettingsInfoBanner(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Gemini AI Nutrition Scanner",
                        description = "Analyzes meal photos, nutrition labels, and restaurant menus to estimate calories, macros, and ingredients automatically.",
                        howToConnect = "1. Visit aistudio.google.com and sign in with Google.\n2. Click \"Create API key\" (free tier works great).\n3. Copy and paste the key below — it saves automatically on your device."
                    )

                    SettingsSectionCard {
                        Text(
                            text = "API Key",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
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

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = if (isPersonal) "System Prompt (Bolivia Localization)" else "System Prompt",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Controls how Gemini estimates portion sizes, ingredients, and regional dishes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            label = { Text("Custom Prompt") },
                            maxLines = 8
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
                                    val msg = if (isPersonal) "Reset prompt to Bolivia default" else "Reset prompt to default"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
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

                SettingsSection.HAND_SCALE -> {
                    SettingsInfoBanner(
                        icon = Icons.Outlined.Straighten,
                        title = "Hand Scale Reference",
                        description = "Gives Gemini your exact hand measurements as a physical ruler when your hand appears next to a plate or bowl. If no hand is visible in the photo, Gemini ignores it automatically."
                    )

                    SettingsSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Hand Scale Reference",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (handRefEnabled) "Included in AI food photo scans" else "Disabled for AI scans",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = handRefEnabled,
                                onCheckedChange = { checked ->
                                    handRefEnabled = checked
                                    repo.updateHandScaleReference(buildHandRefFromInputs(overrideEnabled = checked))
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = handLengthInput,
                                onValueChange = { handLengthInput = it },
                                label = { Text("Hand Length (cm)") },
                                placeholder = { Text("e.g. 17.5") },
                                supportingText = { Text("Wrist to middle tip") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = palmWidthInput,
                                onValueChange = { palmWidthInput = it },
                                label = { Text("Palm Width (cm)") },
                                placeholder = { Text("e.g. 8.5") },
                                supportingText = { Text("Across knuckles") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = palmLengthInput,
                                onValueChange = { palmLengthInput = it },
                                label = { Text("Palm Length (cm)") },
                                placeholder = { Text("e.g. 10.0") },
                                supportingText = { Text("Wrist to finger base") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = middleFingerInput,
                                onValueChange = { middleFingerInput = it },
                                label = { Text("Middle Finger (cm)") },
                                placeholder = { Text("e.g. 7.5") },
                                supportingText = { Text("Palm crease to tip") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = handSpanInput,
                            onValueChange = { handSpanInput = it },
                            label = { Text("Open Hand Span (cm)") },
                            placeholder = { Text("e.g. 16.2") },
                            supportingText = { Text("Thumb tip to pinky tip when open") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = handNotesInput,
                            onValueChange = { handNotesInput = it },
                            label = { Text("Extra Notes (optional)") },
                            placeholder = { Text("e.g. Middle finger width ~1.7 cm; relaxed open pose") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 3
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cleanH = heightInput.trim().replace(',', '.').toFloatOrNull() ?: 0f
                                    val est = HandScaleReference.estimateFromHeight(cleanH, selectedSex, enabled = handRefEnabled)
                                    populateHandInputs(est)
                                    Toast.makeText(context, "Estimated hand sizes from height — adjust if needed", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Estimate from Height")
                            }

                            Button(
                                onClick = {
                                    repo.updateHandScaleReference(buildHandRefFromInputs())
                                    Toast.makeText(context, "Hand scale reference saved!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Save Hand Ref")
                            }
                        }

                        TextButton(
                            onClick = { showHandPromptPreview = !showHandPromptPreview },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(if (showHandPromptPreview) "Hide Prompt Preview" else "Preview Prompt Snippet")
                        }

                        if (showHandPromptPreview) {
                            val previewText = buildHandRefFromInputs(overrideEnabled = true).buildPromptInstruction()
                                .ifBlank { "Add at least one measurement above to activate the prompt snippet." }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                SettingsSection.DRIVE_SYNC -> {
                    SettingsInfoBanner(
                        icon = Icons.Outlined.CloudUpload,
                        title = "NotebookLM & Google Drive Sync",
                        description = "Keeps \"DoItAll — Live Coach Context & Data\" (Google Doc) and \"workouts_backup_latest.json\" updated in-place inside your Google Drive folder \"DoItAll Fitness\".",
                        howToConnect = "1. Tap \"Connect Google Drive\" below and grant Drive access.\n2. Tap \"Sync to Google Drive Now\" to create the folder & files.\n3. In NotebookLM, add the Google Doc once as a source — future syncs update it in place."
                    )

                    SettingsSectionCard {
                        Text(
                            text = when {
                                hasDriveScope && !driveAccountEmail.isNullOrBlank() -> "Connected as $driveAccountEmail"
                                hasDriveScope -> "Google Drive connected"
                                !driveAccountEmail.isNullOrBlank() -> "Signed in ($driveAccountEmail) — Drive access needed"
                                else -> "Not connected to Google Drive"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasDriveScope) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val lastSyncedLabel = remember(driveSyncState.lastSyncTimeMs) {
                            if (driveSyncState.lastSyncTimeMs > 0L) {
                                val formatted = Instant.ofEpochMilli(driveSyncState.lastSyncTimeMs)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm", Locale.US))
                                "Last synced: $formatted"
                            } else {
                                "Last synced: Never"
                            }
                        }
                        Text(
                            text = lastSyncedLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        driveSyncState.lastSyncError?.takeIf { it.isNotBlank() }?.let { errText ->
                            Text(
                                text = "Last sync error: $errText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Sync on App Updates",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Automatically updates Drive ~12s after workouts, meals, weight, or routine changes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = driveSyncState.autoSyncEnabled,
                                onCheckedChange = { enabled ->
                                    DriveSyncManager.setAutoSyncEnabled(context, enabled)
                                }
                            )
                        }

                        if (!hasDriveScope) {
                            Button(
                                onClick = {
                                    val signInIntent = DriveSyncManager.getDriveSignInClient(context).signInIntent
                                    driveSignInLauncher.launch(signInIntent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (driveAccountEmail != null) "Grant Google Drive Access" else "Connect Google Drive")
                            }
                        } else {
                            Button(
                                onClick = {
                                    saveProfileToRepo()
                                    coroutineScope.launch {
                                        val res = DriveSyncManager.syncNow(context)
                                        res.onSuccess { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            val recoveryIntent = (err as? RecoverableDriveAuthException)?.recoveryIntent
                                            if (recoveryIntent != null) {
                                                driveConsentLauncher.launch(recoveryIntent)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Sync failed: ${err.localizedMessage ?: "Unknown error"}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !driveSyncState.isSyncing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (driveSyncState.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Syncing to Google Drive...")
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync to Google Drive Now")
                                }
                            }
                        }
                    }
                }

                SettingsSection.BACKUP_EXPORT -> {
                    SettingsInfoBanner(
                        icon = Icons.Outlined.Storage,
                        title = "Backup & Portability",
                        description = "Export a full AI Coach Bundle (.ZIP) with your logs and schema guide, create/restore .JSON backups when switching phones, or export spreadsheets (.CSV) of your nutrition and workouts."
                    )

                    SettingsSectionCard {
                        Text(
                            text = "Full Backups",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
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
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
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
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore", maxLines = 1)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        Text(
                            text = "Spreadsheets",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

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

                SettingsSection.APPEARANCE -> {
                    SettingsInfoBanner(
                        icon = Icons.Outlined.Palette,
                        title = "App Theme",
                        description = "Switch between Light, Dark, or follow your Android system theme across the app and home screen widget."
                    )

                    SettingsSectionCard {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
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

                SettingsSection.ABOUT -> {
                    SettingsInfoBanner(
                        icon = Icons.Outlined.Info,
                        title = "About DoItAll",
                        description = "Local-first workout, cardio, nutrition, and supplement tracker built for fast daily logging and optional Google Calendar, Drive, and Gemini AI integrations."
                    )

                    SettingsSectionCard {
                        Text(
                            text = "DoItAll Workout & Nutrition Tracker v1.2",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Edition: ${if (BuildConfig.SEED_PERSONAL_DATA) "Personal" else "Starter"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Plan: ${workoutPlan.schedule.size} days, ${workoutPlan.catalog.size} exercises",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
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
                                text = "Calendar credentials, API keys, and nutrition/workout logs are stored locally on your device and never uploaded to third-party servers except the Google/Gemini services you explicitly connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

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

@Composable
private fun SettingsCategoryGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
private fun SettingsInfoBanner(
    icon: ImageVector,
    title: String,
    description: String,
    howToConnect: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (!howToConnect.isNullOrBlank()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f)
                )
                Text(
                    text = "How to connect",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = howToConnect,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
