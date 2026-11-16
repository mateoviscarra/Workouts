package com.mateoviscarra.doitall.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mateoviscarra.doitall.data.WorkoutDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    workoutDays: List<WorkoutDay>,
    exerciseNamesById: Map<String, String> = emptyMap(),
    onDaySelected: (index: Int) -> Unit,
    onSettingsClick: () -> Unit,
    onCustomClick: () -> Unit,
    onEditRoutineClick: (dayIndex: Int) -> Unit,
    onNewRoutineClick: () -> Unit,
    onMoveDay: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    showHeader: Boolean = true
) {
    if (showHeader) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
            WorkoutListContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                workoutDays = workoutDays,
                exerciseNamesById = exerciseNamesById,
                onDaySelected = onDaySelected,
                onCustomClick = onCustomClick,
                onEditRoutineClick = onEditRoutineClick,
                onNewRoutineClick = onNewRoutineClick,
                onMoveDay = onMoveDay
            )
        }
    } else {
        WorkoutListContent(
            modifier = Modifier.fillMaxSize(),
            workoutDays = workoutDays,
            exerciseNamesById = exerciseNamesById,
            onDaySelected = onDaySelected,
            onCustomClick = onCustomClick,
            onEditRoutineClick = onEditRoutineClick,
            onNewRoutineClick = onNewRoutineClick,
            onMoveDay = onMoveDay
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutListContent(
    modifier: Modifier = Modifier,
    workoutDays: List<WorkoutDay>,
    exerciseNamesById: Map<String, String>,
    onDaySelected: (index: Int) -> Unit,
    onCustomClick: () -> Unit,
    onEditRoutineClick: (dayIndex: Int) -> Unit,
    onNewRoutineClick: () -> Unit,
    onMoveDay: (fromIndex: Int, toIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val cardioStore = remember { CardioPreferencesStore(context.applicationContext) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick action buttons: Custom & New Routine
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCustomClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Button(
                    onClick = onNewRoutineClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = "New Routine",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }

        // Preserve user-defined order for all non-cardio routines regardless of naming
        val cardioDay = workoutDays.firstOrNull {
            it.day.trim().equals("Cardio", ignoreCase = true)
        }
        val nonCardioDays = workoutDays.filter { it != cardioDay }

        if (nonCardioDays.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Main Split",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hold & drag to reorder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DraggableRoutineGrid(
                        nonCardioDays = nonCardioDays,
                        allWorkoutDays = workoutDays,
                        onDaySelected = onDaySelected,
                        onEditRoutineClick = onEditRoutineClick,
                        onMoveDay = onMoveDay
                    )
                }
            }
        }

        // Dedicated Cardio Block
        if (cardioDay != null) {
            val cardioIndex = workoutDays.indexOf(cardioDay)
            val cardioIds = cardioDay.slots.flatMap { it.exerciseIds }.distinct()
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Cardio",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Card(
                        onClick = { onDaySelected(cardioIndex) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        )
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = cardioDay.day,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Pick one activity • Set duration & speed/level",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onEditRoutineClick(cardioIndex) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Cardio Options",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            if (cardioIds.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    cardioIds.forEach { exId ->
                                        val label = exerciseNamesById[exId]
                                            ?: exId.replace("_", " ").trim().replaceFirstChar { it.uppercase() }
                                        AssistChip(
                                            onClick = {
                                                cardioStore.setSelectedActivityId(exId)
                                                onDaySelected(cardioIndex)
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
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
}

@Composable
private fun DraggableRoutineGrid(
    nonCardioDays: List<WorkoutDay>,
    allWorkoutDays: List<WorkoutDay>,
    onDaySelected: (index: Int) -> Unit,
    onEditRoutineClick: (dayIndex: Int) -> Unit,
    onMoveDay: (fromIndex: Int, toIndex: Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val latestAllDays by rememberUpdatedState(allWorkoutDays)
    val latestOnMoveDay by rememberUpdatedState(onMoveDay)

    var orderedDays by remember { mutableStateOf(nonCardioDays) }
    var draggedDayKey by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var grabAnchorOrigin by remember { mutableStateOf(Offset.Zero) }
    var suppressClickUntilMs by remember { mutableLongStateOf(0L) }

    // Keep optimistic list synced with upstream state when not actively dragging
    LaunchedEffect(nonCardioDays, draggedDayKey) {
        if (draggedDayKey == null) {
            orderedDays = nonCardioDays
        }
    }

    // Slot rectangles indexed by grid position (0..orderedDays.lastIndex) in local grid coordinates
    val slotRectsRef = remember { mutableListOf<Rect>() }
    val latestOrderedDays by rememberUpdatedState(orderedDays)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        val hitIndex = slotRectsRef.indexOfFirst { it.contains(startOffset) }
                        val hitDay = latestOrderedDays.getOrNull(hitIndex)
                        if (hitDay != null) {
                            draggedDayKey = hitDay.day
                            grabAnchorOrigin = slotRectsRef[hitIndex].topLeft
                            dragDelta = Offset.Zero
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val key = draggedDayKey ?: return@detectDragGesturesAfterLongPress
                        change.consume()
                        dragDelta += dragAmount

                        val currentDays = latestOrderedDays
                        val currentGridIdx = currentDays.indexOfFirst { it.day == key }
                        val currentSlotRect = slotRectsRef.getOrNull(currentGridIdx)
                            ?: return@detectDragGesturesAfterLongPress

                        val draggedCenter = Offset(
                            x = grabAnchorOrigin.x + dragDelta.x + currentSlotRect.width / 2f,
                            y = grabAnchorOrigin.y + dragDelta.y + currentSlotRect.height / 2f
                        )

                        val targetGridIdx = slotRectsRef.indexOfFirst { it.contains(draggedCenter) }
                        if (targetGridIdx in currentDays.indices && targetGridIdx != currentGridIdx) {
                            val movingDay = currentDays[currentGridIdx]
                            val targetDay = currentDays[targetGridIdx]

                            val nextOrder = currentDays.toMutableList()
                            val item = nextOrder.removeAt(currentGridIdx)
                            nextOrder.add(targetGridIdx, item)
                            orderedDays = nextOrder

                            val fromPlanIdx = latestAllDays.indexOfFirst { it.day == movingDay.day }
                            val toPlanIdx = latestAllDays.indexOfFirst { it.day == targetDay.day }
                            if (fromPlanIdx != -1 && toPlanIdx != -1 && fromPlanIdx != toPlanIdx) {
                                latestOnMoveDay(fromPlanIdx, toPlanIdx)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        draggedDayKey = null
                        dragDelta = Offset.Zero
                        suppressClickUntilMs = SystemClock.uptimeMillis() + 300L
                    },
                    onDragCancel = {
                        draggedDayKey = null
                        dragDelta = Offset.Zero
                        suppressClickUntilMs = SystemClock.uptimeMillis() + 300L
                    }
                )
            }
    ) {
        ThreeColumnRoutineLayout(
            horizontalSpacing = 8.dp,
            verticalSpacing = 8.dp,
            onSlotRectsComputed = { rects ->
                slotRectsRef.clear()
                slotRectsRef.addAll(rects)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            orderedDays.forEachIndexed { gridIdx, day ->
                key(day.day) {
                    val isDraggingThis = draggedDayKey == day.day
                    val planIndex = allWorkoutDays.indexOfFirst { it.day == day.day }
                        .takeIf { it >= 0 } ?: gridIdx

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isDraggingThis) 10f else 0f)
                            .graphicsLayer {
                                if (isDraggingThis) {
                                    val currentTopLeft = slotRectsRef.getOrNull(gridIdx)?.topLeft ?: grabAnchorOrigin
                                    translationX = (grabAnchorOrigin.x + dragDelta.x) - currentTopLeft.x
                                    translationY = (grabAnchorOrigin.y + dragDelta.y) - currentTopLeft.y
                                    scaleX = 1.04f
                                    scaleY = 1.04f
                                }
                            }
                    ) {
                        WorkoutButton(
                            day = day,
                            isDragging = isDraggingThis,
                            modifier = Modifier.fillMaxSize(),
                            onClick = {
                                if (draggedDayKey == null && SystemClock.uptimeMillis() > suppressClickUntilMs) {
                                    onDaySelected(planIndex)
                                }
                            },
                            onEdit = {
                                if (draggedDayKey == null && SystemClock.uptimeMillis() > suppressClickUntilMs) {
                                    onEditRoutineClick(planIndex)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreeColumnRoutineLayout(
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    onSlotRectsComputed: (List<Rect>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val numColumns = 3
        val hSpacePx = horizontalSpacing.roundToPx()
        val vSpacePx = verticalSpacing.roundToPx()
        val totalHSpace = hSpacePx * (numColumns - 1)
        val colWidth = ((constraints.maxWidth - totalHSpace) / numColumns).coerceAtLeast(0)

        // Pass 1: measure each item at colWidth with wrap height to determine optimal uniform height
        val wrapConstraints = Constraints(
            minWidth = colWidth,
            maxWidth = colWidth,
            minHeight = 0,
            maxHeight = constraints.maxHeight
        )
        val naturalPlaceables = measurables.map { it.measure(wrapConstraints) }
        val uniformCardHeight = (naturalPlaceables.maxOfOrNull { it.height } ?: 0).coerceAtLeast(50.dp.roundToPx())

        // Pass 2: force uniform height so card bounds match cell bounds and drag-swapping is seamless
        val uniformConstraints = Constraints(
            minWidth = colWidth,
            maxWidth = colWidth,
            minHeight = uniformCardHeight,
            maxHeight = uniformCardHeight
        )
        val finalPlaceables = measurables.mapIndexed { idx, measurable ->
            if (naturalPlaceables[idx].height == uniformCardHeight) {
                naturalPlaceables[idx]
            } else {
                measurable.measure(uniformConstraints)
            }
        }

        val numRows = (finalPlaceables.size + numColumns - 1) / numColumns
        val totalHeight = if (numRows == 0) {
            0
        } else {
            numRows * uniformCardHeight + vSpacePx * (numRows - 1)
        }

        val computedRects = ArrayList<Rect>(finalPlaceables.size)
        finalPlaceables.forEachIndexed { idx, _ ->
            val rowIdx = idx / numColumns
            val colIdx = idx % numColumns
            val left = colIdx * (colWidth + hSpacePx)
            val top = rowIdx * (uniformCardHeight + vSpacePx)
            computedRects.add(
                Rect(
                    left = left.toFloat(),
                    top = top.toFloat(),
                    right = (left + colWidth).toFloat(),
                    bottom = (top + uniformCardHeight).toFloat()
                )
            )
        }
        onSlotRectsComputed(computedRects)

        layout(constraints.maxWidth, totalHeight) {
            finalPlaceables.forEachIndexed { idx, placeable ->
                val rect = computedRects[idx]
                placeable.placeRelative(rect.left.toInt(), rect.top.toInt())
            }
        }
    }
}

@Composable
private fun WorkoutButton(
    day: WorkoutDay,
    isDragging: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    isAccent: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 1.dp
        ),
        border = if (isDragging) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (isAccent) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = day.day,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Routine",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            val countText = if (day.slots.isNotEmpty()) "${day.slots.size} exercises" else "Empty"
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
