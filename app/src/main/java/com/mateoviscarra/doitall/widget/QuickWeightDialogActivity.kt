package com.mateoviscarra.doitall.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateoviscarra.doitall.data.NutritionRepository
import com.mateoviscarra.doitall.data.persist.WeightUnit
import com.mateoviscarra.doitall.ui.theme.ThemeMode
import com.mateoviscarra.doitall.ui.theme.ThemePreferenceStore
import com.mateoviscarra.doitall.ui.theme.WorkoutTheme
import java.time.LocalDate
import kotlin.math.roundToInt

class QuickWeightDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = NutritionRepository(applicationContext)
        val todayIso = LocalDate.now().toString()
        val existingToday = repo.weightLogs.value.firstOrNull { it.dateIso == todayIso }
        val latest = repo.weightLogs.value.firstOrNull()
        val initialWeight = existingToday?.weight ?: latest?.weight
        val initialUnit = existingToday?.unit ?: latest?.unit ?: WeightUnit.KG
        val initialNote = existingToday?.note.orEmpty()

        setContent {
            val themeStore = remember { ThemePreferenceStore(applicationContext) }
            val themeMode by themeStore.themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            WorkoutTheme(darkTheme = isDark) {
                QuickWeightSheetDialog(
                    initialWeight = initialWeight,
                    initialUnit = initialUnit,
                    onDismiss = { finish() },
                    onSave = { weight, unit ->
                        repo.logWeight(
                            dateIso = todayIso,
                            weight = weight,
                            unit = unit,
                            note = initialNote
                        )
                        HealthWidgetProvider.refreshAll(applicationContext)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickWeightSheetDialog(
    initialWeight: Float?,
    initialUnit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (Float, WeightUnit) -> Unit
) {
    var weightText by remember {
        mutableStateOf(initialWeight?.let { HealthWidgetProvider.formatWeight(it) } ?: "")
    }
    var selectedUnit by remember { mutableStateOf(initialUnit) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val parsedWeight = weightText.replace(",", ".").toFloatOrNull()
    val canSave = parsedWeight != null && parsedWeight > 0f

    fun adjustBy(delta: Float) {
        val current = weightText.replace(",", ".").toFloatOrNull() ?: initialWeight ?: 70.0f
        val next = (((current + delta) * 10f).roundToInt() / 10f).coerceAtLeast(1f)
        weightText = HealthWidgetProvider.formatWeight(next)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MonitorWeight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Log Today's Weight",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight (${if (selectedUnit == WeightUnit.LBS) "lb" else "kg"})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSave && parsedWeight != null) {
                                onSave(parsedWeight, selectedUnit)
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(-0.5f, -0.1f, 0.1f, 0.5f).forEach { delta ->
                        val label = if (delta > 0) "+$delta" else "$delta"
                        AssistChip(
                            onClick = { adjustBy(delta) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedUnit == WeightUnit.KG,
                        onClick = { selectedUnit = WeightUnit.KG },
                        label = { Text("kg") }
                    )
                    FilterChip(
                        selected = selectedUnit == WeightUnit.LBS,
                        onClick = { selectedUnit = WeightUnit.LBS },
                        label = { Text("lb") }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = {
                            if (parsedWeight != null) {
                                onSave(parsedWeight, selectedUnit)
                            }
                        },
                        enabled = canSave
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
