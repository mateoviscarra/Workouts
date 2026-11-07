package com.mateoviscarra.doitall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateoviscarra.doitall.data.CompletedExerciseRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class WorkoutTimingOption {
    FINISHED_NOW,
    STARTED_NOW,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedFinishWorkoutDialog(
    workoutTitle: String,
    completedExercises: List<CompletedExerciseRecord>,
    isCalendarConnected: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        uncheckAfter: Boolean,
        syncToCalendar: Boolean,
        durationMinutes: Int,
        timingOption: WorkoutTimingOption,
        customDate: LocalDate,
        customStartTime: LocalTime
    ) -> Unit
) {
    var uncheckAfter by remember { mutableStateOf(true) }
    var syncToCalendar by remember { mutableStateOf(isCalendarConnected) }
    var durationMinutes by remember { mutableIntStateOf(60) }
    var timingOption by remember { mutableStateOf(WorkoutTimingOption.FINISHED_NOW) }

    var customDate by remember { mutableStateOf(LocalDate.now()) }
    var customStartTime by remember { mutableStateOf(LocalTime.now().minusHours(1)) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Finish Workout")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Workout overview card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$workoutTitle • ${completedExercises.size} exercises",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val totalVol = completedExercises.sumOf { ex ->
                            val repsParts = ex.repsSummary.split(",")
                            val repsCount = repsParts.mapNotNull { it.trim().toDoubleOrNull() }
                            val repsAvg = if (repsCount.isNotEmpty()) repsCount.average() else 10.0
                            val effWeight = if (ex.numericWeight > 0) ex.numericWeight else 0.0
                            ex.sets * repsAvg * effWeight
                        }
                        if (totalVol > 0) {
                            val unitStr = completedExercises.firstOrNull { it.numericWeight > 0 }?.weightUnit?.name?.lowercase() ?: "kg"
                            Text(
                                text = "Total Volume: ${String.format(Locale.US, "%,.0f", totalVol)} $unitStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Checkbox: reset checkmarks
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = uncheckAfter,
                        onCheckedChange = { uncheckAfter = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset exercise checkmarks for next time",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDivider()

                // Google Calendar Sync option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = syncToCalendar,
                        onCheckedChange = { syncToCalendar = it },
                        enabled = isCalendarConnected
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp),
                                tint = if (syncToCalendar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Add event to Google Calendar",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (!isCalendarConnected) {
                            Text(
                                text = "Not connected in Settings (saved to history only)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (syncToCalendar && isCalendarConnected) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Duration: $durationMinutes minutes",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = durationMinutes.toFloat(),
                            onValueChange = { durationMinutes = it.toInt() },
                            valueRange = 15f..180f,
                            steps = 10,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "When was this workout?",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Column(Modifier.selectableGroup()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = timingOption == WorkoutTimingOption.FINISHED_NOW,
                                        onClick = { timingOption = WorkoutTimingOption.FINISHED_NOW },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = timingOption == WorkoutTimingOption.FINISHED_NOW,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Finished just now", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = timingOption == WorkoutTimingOption.STARTED_NOW,
                                        onClick = { timingOption = WorkoutTimingOption.STARTED_NOW },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = timingOption == WorkoutTimingOption.STARTED_NOW,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Starting now", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = timingOption == WorkoutTimingOption.CUSTOM,
                                        onClick = { timingOption = WorkoutTimingOption.CUSTOM },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = timingOption == WorkoutTimingOption.CUSTOM,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Set custom time/date", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        if (timingOption == WorkoutTimingOption.CUSTOM) {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Date: ${customDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        TextButton(onClick = { showDatePicker = true }) {
                                            Text("Change")
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Start: ${customStartTime.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        TextButton(onClick = { showTimePicker = true }) {
                                            Text("Change")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        uncheckAfter,
                        syncToCalendar && isCalendarConnected,
                        durationMinutes,
                        timingOption,
                        customDate,
                        customStartTime
                    )
                }
            ) {
                Text("Finish Workout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            customDate = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = customStartTime.hour,
            initialMinute = customStartTime.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Start Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        customStartTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
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

