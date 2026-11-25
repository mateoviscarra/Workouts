package com.mateoviscarra.doitall.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mateoviscarra.doitall.data.PillLogEntry
import com.mateoviscarra.doitall.data.SupplementPill
import com.mateoviscarra.doitall.network.GeminiVisionClient
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
fun PillsDailyLogCard(
    selectedDateIso: String,
    supplements: List<SupplementPill>,
    dayPillLogs: List<PillLogEntry>,
    onToggleTaken: (SupplementPill) -> Unit,
    onPillClick: (SupplementPill) -> Unit,
    onManagePillsClick: () -> Unit
) {
    val selectedDate = remember(selectedDateIso) {
        runCatching { LocalDate.parse(selectedDateIso) }.getOrDefault(LocalDate.now())
    }
    val today = LocalDate.now()
    val nowTime = LocalTime.now()
    val isPastDate = selectedDate.isBefore(today)
    val isToday = selectedDate == today

    val logsByPillId = remember(dayPillLogs) { dayPillLogs.associateBy { it.pillId } }
    val displayPills = remember(supplements, selectedDate, logsByPillId) {
        supplements.filter { pill ->
            pill.isScheduledForDate(selectedDate) || pill.id in logsByPillId
        }
    }

    val takenCount = displayPills.count { it.id in logsByPillId }
    val missedCount = if (isPastDate) {
        displayPills.count { pill ->
            val createdDate = runCatching { LocalDate.parse(pill.createdAtDateIso) }.getOrDefault(today)
            pill.id !in logsByPillId && !selectedDate.isBefore(createdDate)
        }
    } else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Pills & Supplements",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val subtitle = buildString {
                            append("$takenCount/${displayPills.size} taken")
                            if (missedCount > 0) append(" • $missedCount missed")
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (missedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onManagePillsClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Manage", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (displayPills.isEmpty()) {
                Text(
                    text = "No pills scheduled for this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayPills.forEach { pill ->
                    val takenLog = logsByPillId[pill.id]
                    val isTaken = takenLog != null
                    val createdDate = runCatching { LocalDate.parse(pill.createdAtDateIso) }.getOrDefault(today)
                    val isMissed = !isTaken && isPastDate && !selectedDate.isBefore(createdDate)
                    val isDueToday = !isTaken && isToday && pill.reminderTimes.any { timeStr ->
                        runCatching { LocalTime.parse(timeStr) }.getOrNull()?.isBefore(nowTime) == true
                    }

                    Surface(
                        onClick = { onPillClick(pill) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onToggleTaken(pill) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTaken) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                    contentDescription = if (isTaken) "Mark not taken" else "Mark taken",
                                    tint = when {
                                        isTaken -> MaterialTheme.colorScheme.primary
                                        isMissed -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = pill.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = if (isTaken) TextDecoration.LineThrough else TextDecoration.None,
                                        modifier = Modifier.alpha(if (isTaken) 0.65f else 1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (pill.dosageDescription.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = pill.dosageDescription,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                val metaParts = buildList {
                                    if (pill.calories > 0 || pill.fatGrams > 0f) {
                                        val fatStr = if (pill.fatGrams > 0f) " (${pill.fatGrams}g F)" else ""
                                        add("${pill.calories} kcal$fatStr")
                                    }
                                    if (pill.micronutrientsSummary.isNotBlank()) {
                                        add(pill.micronutrientsSummary)
                                    } else if (pill.notes.isNotBlank()) {
                                        add(pill.notes)
                                    }
                                }
                                if (metaParts.isNotEmpty()) {
                                    Text(
                                        text = metaParts.joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            val statusBadge = when {
                                isTaken -> Triple(
                                    if (takenLog!!.takenTime.isNotBlank()) "Taken ${takenLog.takenTime}" else "Taken",
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                isMissed -> Triple(
                                    "Missed",
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.colorScheme.onErrorContainer
                                )
                                isDueToday -> Triple(
                                    "Due ${pill.reminderTimes.firstOrNull() ?: ""}",
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                else -> Triple(
                                    pill.reminderTimes.joinToString(", ").ifBlank { "Scheduled" },
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = statusBadge.second
                            ) {
                                Text(
                                    text = statusBadge.first,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = statusBadge.third,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PillsManagementView(
    supplements: List<SupplementPill>,
    getMissedDates: (SupplementPill) -> List<LocalDate>,
    onNewPillClick: () -> Unit,
    onEditPill: (SupplementPill) -> Unit,
    onDeletePill: (SupplementPill) -> Unit,
    onToggleNotification: (SupplementPill, Boolean) -> Unit
) {
    val context = LocalContext.current
    var pendingNotificationPill by remember { mutableStateOf<SupplementPill?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingNotificationPill
        pendingNotificationPill = null
        if (granted && target != null) {
            onToggleNotification(target, true)
        } else if (!granted) {
            Toast.makeText(context, "Notification permission denied. Enable in system settings for reminders.", Toast.LENGTH_LONG).show()
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pills & Supplements (${supplements.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onNewPillClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Pill")
            }
        }

        if (supplements.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "No pills configured yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Track your daily supplements, reminder schedules, calories, and micronutrient overview.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(supplements, key = { it.id }) { pill ->
                    val missedDates = remember(pill, supplements) { getMissedDates(pill) }
                    Card(
                        onClick = { onEditPill(pill) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = pill.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (pill.dosageDescription.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = pill.dosageDescription,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    val daysText = if (pill.scheduledDaysOfWeek.size >= 7) {
                                        "Every day"
                                    } else {
                                        pill.scheduledDaysOfWeek.sorted().joinToString(", ") { dayNum ->
                                            DayOfWeek.of(dayNum).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                        }
                                    }
                                    val timesText = pill.reminderTimes.joinToString(", ").ifBlank { "No time set" }
                                    Text(
                                        text = "$daysText @ $timesText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (!pill.notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (!granted) {
                                                    pendingNotificationPill = pill
                                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                    return@IconButton
                                                }
                                            }
                                            onToggleNotification(pill, !pill.notificationsEnabled)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (pill.notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                            contentDescription = "Toggle reminder",
                                            tint = if (pill.notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(onClick = { onEditPill(pill) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit pill")
                                    }

                                    IconButton(onClick = { onDeletePill(pill) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete pill",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            if (pill.calories > 0 || pill.fatGrams > 0f || pill.proteinGrams > 0f || pill.carbsGrams > 0f) {
                                Text(
                                    text = "Macros per dose: ${pill.calories} kcal • P ${pill.proteinGrams}g • C ${pill.carbsGrams}g • F ${pill.fatGrams}g",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (pill.micronutrientsSummary.isNotBlank()) {
                                Text(
                                    text = "Actives / Micronutrients: ${pill.micronutrientsSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (missedDates.isNotEmpty()) {
                                val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
                                val datesPreview = missedDates.take(5).joinToString(", ") { it.format(fmt) }
                                val extra = if (missedDates.size > 5) " +${missedDates.size - 5} more" else ""
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Missed (last 14d): $datesPreview$extra",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PillEditorDialog(
    initialPill: SupplementPill?,
    apiKey: String,
    visionClient: GeminiVisionClient,
    onDismiss: () -> Unit,
    onSave: (SupplementPill) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialPill?.name ?: "") }
    var dosage by remember { mutableStateOf(initialPill?.dosageDescription ?: "") }
    var caloriesStr by remember { mutableStateOf(if ((initialPill?.calories ?: 0) > 0) initialPill!!.calories.toString() else "") }
    var fatStr by remember { mutableStateOf(if ((initialPill?.fatGrams ?: 0f) > 0f) initialPill!!.fatGrams.toString() else "") }
    var proteinStr by remember { mutableStateOf(if ((initialPill?.proteinGrams ?: 0f) > 0f) initialPill!!.proteinGrams.toString() else "") }
    var carbsStr by remember { mutableStateOf(if ((initialPill?.carbsGrams ?: 0f) > 0f) initialPill!!.carbsGrams.toString() else "") }
    var micronutrients by remember { mutableStateOf(initialPill?.micronutrientsSummary ?: "") }
    var notes by remember { mutableStateOf(initialPill?.notes ?: "") }
    var selectedDays by remember { mutableStateOf(initialPill?.scheduledDaysOfWeek ?: setOf(1, 2, 3, 4, 5, 6, 7)) }
    var reminderTimes by remember { mutableStateOf(initialPill?.reminderTimes ?: listOf("09:00")) }
    var notificationsEnabled by remember { mutableStateOf(initialPill?.notificationsEnabled ?: false) }

    var showTimePicker by remember { mutableStateOf(false) }
    var isScanningLabel by remember { mutableStateOf(false) }
    var scanTempUri by remember { mutableStateOf<Uri?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationsEnabled = true
        } else {
            Toast.makeText(context, "Notification permission denied. Reminders won't appear until enabled.", Toast.LENGTH_LONG).show()
        }
    }

    fun runSupplementScan(uri: Uri) {
        if (apiKey.isBlank()) {
            Toast.makeText(context, "Set your Gemini API key in Settings first.", Toast.LENGTH_LONG).show()
            return
        }
        isScanningLabel = true
        scope.launch {
            val result = visionClient.analyzeSupplementLabel(
                imageUris = listOf(uri),
                apiKey = apiKey,
                userNote = name
            )
            isScanningLabel = false
            result.onSuccess { res ->
                name = res.name
                dosage = res.dosageDescription
                caloriesStr = if (res.calories > 0) res.calories.toString() else ""
                fatStr = if (res.fatGrams > 0f) res.fatGrams.toString() else ""
                proteinStr = if (res.proteinGrams > 0f) res.proteinGrams.toString() else ""
                carbsStr = if (res.carbsGrams > 0f) res.carbsGrams.toString() else ""
                micronutrients = res.micronutrientsSummary
                if (res.notes.isNotBlank()) notes = res.notes
                Toast.makeText(context, "Extracted supplement details!", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Label scan failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = scanTempUri
        if (success && uri != null) {
            runSupplementScan(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runSupplementScan(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPill == null) "New Pill / Supplement" else "Edit ${initialPill.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // AI Supplement Label Scanner row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scan Bottle / Supplement Facts with AI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (isScanningLabel) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Reading supplement label...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val cacheDir = File(context.cacheDir, "camera").apply { mkdirs() }
                                        val tempFile = File(cacheDir, "supp_${System.currentTimeMillis()}.jpg")
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                        scanTempUri = uri
                                        cameraLauncher.launch(uri)
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Camera", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = {
                                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gallery", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Fish Oil, Creatine)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text("Dose (e.g. 240mg)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = caloriesStr,
                        onValueChange = { caloriesStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Calories (opt)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = proteinStr,
                        onValueChange = { proteinStr = it },
                        label = { Text("Prot (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsStr,
                        onValueChange = { carbsStr = it },
                        label = { Text("Carb (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatStr,
                        onValueChange = { fatStr = it },
                        label = { Text("Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = micronutrients,
                    onValueChange = { micronutrients = it },
                    label = { Text("Micronutrients / Active Ingredients") },
                    placeholder = { Text("e.g. EPA 720mg, DHA 480mg, Vitamin E 10mg") },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Timing Cue (optional)") },
                    placeholder = { Text("e.g. Take right after lunch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(
                    text = "Schedule Days",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val dayLabels = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
                    dayLabels.forEach { (dayNum, label) ->
                        val selected = dayNum in selectedDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) selectedDays - dayNum else selectedDays + dayNum
                                if (next.isNotEmpty()) selectedDays = next
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reminder Times",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { showTimePicker = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Add Time")
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reminderTimes.forEach { t ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(t) },
                            trailingIcon = if (reminderTimes.size > 1) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove time",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                reminderTimes = reminderTimes - t
                                            }
                                    )
                                }
                            } else null
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Reminders",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Send push alert with 'Mark Taken' button",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@Switch
                                }
                            }
                            notificationsEnabled = checked
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isNotEmpty()) {
                        onSave(
                            SupplementPill(
                                id = initialPill?.id ?: UUID.randomUUID().toString(),
                                name = cleanName,
                                dosageDescription = dosage.trim(),
                                calories = caloriesStr.toIntOrNull() ?: 0,
                                proteinGrams = proteinStr.replace(',', '.').toFloatOrNull() ?: 0f,
                                carbsGrams = carbsStr.replace(',', '.').toFloatOrNull() ?: 0f,
                                fatGrams = fatStr.replace(',', '.').toFloatOrNull() ?: 0f,
                                micronutrientsSummary = micronutrients.trim(),
                                notes = notes.trim(),
                                scheduledDaysOfWeek = selectedDays,
                                reminderTimes = reminderTimes.distinct().sorted(),
                                notificationsEnabled = notificationsEnabled,
                                createdAtDateIso = initialPill?.createdAtDateIso ?: LocalDate.now().toString()
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

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Add Reminder Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formatted = String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                        if (formatted !in reminderTimes) {
                            reminderTimes = (reminderTimes + formatted).sorted()
                        }
                        showTimePicker = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
