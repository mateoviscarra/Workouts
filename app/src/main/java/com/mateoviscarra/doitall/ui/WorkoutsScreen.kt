package com.mateoviscarra.doitall.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateoviscarra.doitall.data.Category
import com.mateoviscarra.doitall.data.CategoryExercise
import com.mateoviscarra.doitall.data.WorkoutDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    workoutDays: List<WorkoutDay>,
    categories: List<Category>,
    onDaySelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onCustomClick: () -> Unit,
    onEditRoutineClick: (Int) -> Unit,
    onNewRoutineClick: () -> Unit,
    onAddExercise: (categoryId: String, CategoryExercise) -> Unit,
    onEditExercise: (categoryId: String, CategoryExercise) -> Unit,
    onDeleteExercise: (categoryId: String, exerciseId: String) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    initialTab: Int = 0
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }

    Scaffold(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Routines")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exercises")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("History & PRs")
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    WorkoutListScreen(
                        workoutDays = workoutDays,
                        onDaySelected = onDaySelected,
                        onSettingsClick = onSettingsClick,
                        onCustomClick = onCustomClick,
                        onEditRoutineClick = onEditRoutineClick,
                        onNewRoutineClick = onNewRoutineClick,
                        showHeader = false
                    )
                }
                1 -> {
                    ExerciseLibraryScreen(
                        categories = categories,
                        onAddExercise = onAddExercise,
                        onEditExercise = onEditExercise,
                        onDeleteExercise = onDeleteExercise,
                        onAddCategory = onAddCategory,
                        onDeleteCategory = onDeleteCategory,
                        showHeader = false
                    )
                }
                2 -> {
                    HistoryScreen(
                        onBack = null,
                        showHeader = false
                    )
                }
            }
        }
    }
}
