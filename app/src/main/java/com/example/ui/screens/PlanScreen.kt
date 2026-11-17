package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyTask
import com.example.domain.LifeMateDateUtils
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.TaskEditorDialog
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.LifeMateUiState

private enum class TaskFilter(val label: String) {
    TODAY("Today"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    ALL("All")
}

@Composable
fun PlanScreen(
    uiState: LifeMateUiState,
    onAddTask: (title: String, notes: String, date: String) -> Unit,
    onUpdateTask: (taskId: String, title: String, notes: String, date: String) -> Unit,
    onToggleTask: (DailyTask) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf(TaskFilter.TODAY) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<DailyTask?>(null) }
    var deletingTask by remember { mutableStateOf<DailyTask?>(null) }

    val filteredTasks = remember(uiState.allTasks, activeFilter, uiState.todayKey) {
        when (activeFilter) {
            TaskFilter.TODAY -> uiState.allTasks.filter { it.scheduledDate == uiState.todayKey }
            TaskFilter.PENDING -> uiState.allTasks.filter { !it.completed }
            TaskFilter.COMPLETED -> uiState.allTasks.filter { it.completed }
            TaskFilter.ALL -> uiState.allTasks
        }
    }

    val progressFraction = if (uiState.todayTotalCount > 0) {
        uiState.todayCompletedCount.toFloat() / uiState.todayTotalCount.toFloat()
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(targetValue = progressFraction, label = "plan_progress")

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daily Plan",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Today's Tasks • ${uiState.todayDisplayDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("plan_add_task_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Task")
                    }
                }
            }

            // Progress summary card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Today's Completion",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${uiState.todayCompletedCount} of ${uiState.todayTotalCount} tasks completed",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${uiState.todayProgressPercent}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }

            // Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TaskFilter.entries) { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("plan_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.CheckCircle,
                        title = when (activeFilter) {
                            TaskFilter.TODAY -> "No tasks for today yet"
                            TaskFilter.PENDING -> "You're all caught up!"
                            TaskFilter.COMPLETED -> "No completed tasks yet"
                            TaskFilter.ALL -> "Your plan is empty"
                        },
                        subtitle = "Create a task like \"Study for 1 hour\" or \"Exercise\" to keep your day structured.",
                        actionLabel = "Add Task",
                        onAction = { showCreateDialog = true }
                    )
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_card_${task.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleTask(task) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (task.completed) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                contentDescription = if (task.completed) "Mark incomplete" else "Mark complete",
                                tint = if (task.completed) SuccessGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (task.completed) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    textDecoration = if (task.completed) TextDecoration.LineThrough else null
                                )
                                if (task.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = task.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (task.scheduledDate == uiState.todayKey) {
                                        "Today"
                                    } else {
                                        LifeMateDateUtils.formatShortDate(task.scheduledDate)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { editingTask = task }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit task",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { deletingTask = task }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete task",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        TaskEditorDialog(
            existingTask = null,
            todayKey = uiState.todayKey,
            onDismiss = { showCreateDialog = false },
            onSave = onAddTask
        )
    }

    editingTask?.let { task ->
        TaskEditorDialog(
            existingTask = task,
            todayKey = uiState.todayKey,
            onDismiss = { editingTask = null },
            onSave = { title, notes, date ->
                onUpdateTask(task.id, title, notes, date)
            }
        )
    }

    deletingTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            title = { Text("Delete Task?") },
            text = { Text("Remove \"${task.title}\" from your plan?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTask(task.id)
                        deletingTask = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTask = null }) { Text("Cancel") }
            }
        )
    }
}
