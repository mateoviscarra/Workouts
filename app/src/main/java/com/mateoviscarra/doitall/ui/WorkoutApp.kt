package com.mateoviscarra.doitall.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mateoviscarra.doitall.calendar.CalendarManager
import com.mateoviscarra.doitall.data.persist.WorkoutStateStore
import com.mateoviscarra.doitall.ui.theme.ThemePreferenceStore
import com.mateoviscarra.doitall.viewmodel.WorkoutPlanViewModel

private const val ROUTE_WORKOUTS = "workouts"
private const val ROUTE_EXERCISES = "exercises"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_NUTRITION = "nutrition"
private const val ROUTE_ROUTINE_EDITOR = "routine_editor"
private const val ROUTE_DETAIL = "workout_detail"
private const val ROUTE_EDIT = "workout_edit"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CALENDAR_SETTINGS = "calendar_settings"
private const val ROUTE_CUSTOM = "custom_workout"

fun workoutDetailRoute(index: Int) = "$ROUTE_DETAIL/$index"

fun routineEditorRoute(dayIndex: Int = -1) = "$ROUTE_ROUTINE_EDITOR/$dayIndex"

private fun workoutEditRoute(dayIndex: Int, slotIndex: Int, pageIndex: Int) =
    "$ROUTE_EDIT/$dayIndex/$slotIndex/$pageIndex"

@Composable
fun WorkoutApp(
    viewModel: WorkoutPlanViewModel = viewModel(),
    themeStore: ThemePreferenceStore? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val actualThemeStore = themeStore ?: remember { ThemePreferenceStore(context.applicationContext) }
    val store = remember { WorkoutStateStore(context.applicationContext) }
    val calendarManager = remember { CalendarManager(context.applicationContext) }
    
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val legacyPlan by viewModel.legacyPlan.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelRoute = currentRoute in listOf(ROUTE_WORKOUTS, ROUTE_NUTRITION)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTopLevelRoute) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.FitnessCenter, contentDescription = "Workouts") },
                        label = { Text("Workouts") },
                        selected = currentRoute == ROUTE_WORKOUTS,
                        onClick = {
                            if (currentRoute != ROUTE_WORKOUTS) {
                                navController.navigate(ROUTE_WORKOUTS) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Restaurant, contentDescription = "Nutrition") },
                        label = { Text("Nutrition") },
                        selected = currentRoute == ROUTE_NUTRITION,
                        onClick = {
                            if (currentRoute != ROUTE_NUTRITION) {
                                navController.navigate(ROUTE_NUTRITION) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_WORKOUTS,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_WORKOUTS) {
                WorkoutsScreen(
                    workoutDays = legacyPlan.schedule,
                    categories = plan.categories,
                    onDaySelected = { index ->
                        navController.navigate(workoutDetailRoute(index))
                    },
                    onSettingsClick = {
                        navController.navigate(ROUTE_SETTINGS)
                    },
                    onCustomClick = {
                        navController.navigate(ROUTE_CUSTOM)
                    },
                    onEditRoutineClick = { index ->
                        navController.navigate(routineEditorRoute(index))
                    },
                    onNewRoutineClick = {
                        navController.navigate(routineEditorRoute(-1))
                    },
                    onAddExercise = { categoryId, exercise ->
                        viewModel.addExerciseToCategory(categoryId, exercise)
                    },
                    onEditExercise = { categoryId, exercise ->
                        viewModel.editExerciseInCategory(categoryId, exercise)
                    },
                    onDeleteExercise = { categoryId, exerciseId ->
                        viewModel.deleteExerciseFromCategory(categoryId, exerciseId)
                    },
                    onAddCategory = { name ->
                        viewModel.addCategory(name)
                    },
                    onDeleteCategory = { categoryId ->
                        viewModel.deleteCategory(categoryId)
                    }
                )
            }
            composable(ROUTE_EXERCISES) {
                ExerciseLibraryScreen(
                    categories = plan.categories,
                    onAddExercise = { categoryId, exercise ->
                        viewModel.addExerciseToCategory(categoryId, exercise)
                    },
                    onEditExercise = { categoryId, exercise ->
                        viewModel.editExerciseInCategory(categoryId, exercise)
                    },
                    onDeleteExercise = { categoryId, exerciseId ->
                        viewModel.deleteExerciseFromCategory(categoryId, exerciseId)
                    },
                    onAddCategory = { name ->
                        viewModel.addCategory(name)
                    },
                    onDeleteCategory = { categoryId ->
                        viewModel.deleteCategory(categoryId)
                    }
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    onBack = null
                )
            }
            composable(ROUTE_NUTRITION) {
                NutritionScreen(
                    onNavigateToSettings = {
                        navController.navigate(ROUTE_SETTINGS)
                    }
                )
            }
            composable(
                route = "$ROUTE_ROUTINE_EDITOR/{dayIndex}",
                arguments = listOf(
                    navArgument("dayIndex") { type = NavType.IntType }
                )
            ) { entry ->
                val dayIndex = entry.arguments?.getInt("dayIndex") ?: -1
                val initialDay = if (dayIndex in plan.days.indices) plan.days[dayIndex] else null
                val allExercises = remember(plan.categories) { plan.allExercises() }

                RoutineEditorScreen(
                    initialDay = initialDay,
                    categories = plan.categories,
                    allExercises = allExercises,
                    onBack = { navController.popBackStack() },
                    onSave = { savedDay ->
                        if (dayIndex in plan.days.indices) {
                            val originalDay = plan.days[dayIndex]
                            viewModel.updateDay(originalDay.day, savedDay)
                        } else {
                            viewModel.addDay(savedDay)
                        }
                        navController.popBackStack()
                    },
                    onDelete = if (dayIndex in plan.days.indices) {
                        {
                            val originalDay = plan.days[dayIndex]
                            viewModel.deleteDay(originalDay.day)
                            navController.popBackStack()
                        }
                    } else null,
                    onAddExerciseToCatalog = { categoryId, exercise ->
                        viewModel.addExerciseToCategory(categoryId, exercise)
                    }
                )
            }
            composable(
                route = "$ROUTE_DETAIL/{index}",
                arguments = listOf(
                    navArgument("index") { type = NavType.IntType }
                )
            ) { entry ->
                val index = entry.arguments?.getInt("index") ?: return@composable
                val day = legacyPlan.schedule.getOrNull(index) ?: return@composable
                WorkoutDetailScreen(
                    workoutPlan = legacyPlan,
                    dayKey = day.day,
                    workoutDay = day,
                    onBack = { navController.popBackStack() },
                    onEditVariation = { slotIndex, pageIndex ->
                        navController.navigate(workoutEditRoute(index, slotIndex, pageIndex))
                    },
                    onEditRoutine = {
                        navController.navigate(routineEditorRoute(index))
                    },
                    calendarManager = calendarManager,
                    planViewModel = viewModel
                )
            }
            composable(
                route = "$ROUTE_EDIT/{dayIndex}/{slotIndex}/{pageIndex}",
                arguments = listOf(
                    navArgument("dayIndex") { type = NavType.IntType },
                    navArgument("slotIndex") { type = NavType.IntType },
                    navArgument("pageIndex") { type = NavType.IntType }
                )
            ) { entry ->
                val dayIndex = entry.arguments?.getInt("dayIndex") ?: return@composable
                val slotIndex = entry.arguments?.getInt("slotIndex") ?: return@composable
                val pageIndex = entry.arguments?.getInt("pageIndex") ?: return@composable
                
                val day = legacyPlan.schedule.getOrNull(dayIndex) ?: return@composable
                EditVariationScreen(
                    workoutPlan = legacyPlan,
                    dayKey = day.day,
                    workoutDay = day,
                    slotIndex = slotIndex,
                    pageIndex = pageIndex,
                    store = store,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    workoutPlan = legacyPlan,
                    calendarManager = calendarManager,
                    themeStore = actualThemeStore,
                    onBack = { navController.popBackStack() },
                    onCalendarSettingsClick = {
                        navController.navigate(ROUTE_CALENDAR_SETTINGS)
                    },
                    onDataRestored = {
                        viewModel.reloadPlan()
                    }
                )
            }
            composable(ROUTE_CALENDAR_SETTINGS) {
                com.mateoviscarra.doitall.calendar.CalendarSettingsScreen(
                    onBack = { navController.popBackStack() },
                    calendarManager = calendarManager
                )
            }
            composable(ROUTE_CUSTOM) {
                CustomWorkoutScreen(
                    calendarManager = calendarManager,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
