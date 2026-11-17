package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.data.model.PersonalGoal
import com.example.domain.LifeMateDateUtils
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GoalEditorDialog
import com.example.ui.components.GoalProgressUpdateDialog
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.LifeMateUiState

@Composable
fun GoalsScreen(
    uiState: LifeMateUiState,
    onAddGoal: (title: String, target: Double, current: Double, unit: String, targetDate: String) -> Unit,
    onUpdateGoal: (goalId: String, title: String, target: Double, current: Double, unit: String, targetDate: String) -> Unit,
    onDeleteGoal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<PersonalGoal?>(null) }
    var progressGoal by remember { mutableStateOf<PersonalGoal?>(null) }
    var deletingGoal by remember { mutableStateOf<PersonalGoal?>(null) }

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
                            text = "Goals",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Track savings, learning, and personal milestones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("goals_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Goal")
                    }
                }
            }

            if (uiState.goals.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Flag,
                        title = "No goals yet",
                        subtitle = "Create a goal such as \"Save ₹10,000\" and update your progress step by step.",
                        actionLabel = "Create Goal",
                        onAction = { showCreateDialog = true }
                    )
                }
            } else {
                items(uiState.goals, key = { it.id }) { goal ->
                    val animFraction by animateFloatAsState(
                        targetValue = goal.progressFraction,
                        label = "goal_progress_${goal.id}"
                    )
                    val isComplete = goal.currentAmount >= goal.targetAmount && goal.targetAmount > 0.0

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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = goal.title,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${LifeMateDateUtils.formatGoalValue(goal.currentAmount, goal.unit)} / ${LifeMateDateUtils.formatGoalValue(goal.targetAmount, goal.unit)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = if (isComplete) {
                                        SuccessGreen.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    }
                                ) {
                                    Text(
                                        text = "${goal.progressPercent}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isComplete) SuccessGreen else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { animFraction },
                                color = if (isComplete) SuccessGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(CircleShape),
                                strokeCap = StrokeCap.Round
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (goal.targetDate.isNotBlank()) {
                                        "Target: ${LifeMateDateUtils.formatShortDate(goal.targetDate)}"
                                    } else {
                                        "Flexible timeline"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(
                                        onClick = { progressGoal = goal },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Update")
                                    }
                                    IconButton(onClick = { editingGoal = goal }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Edit goal",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { deletingGoal = goal }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete goal",
                                            tint = MaterialTheme.colorScheme.error
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

    if (showCreateDialog) {
        GoalEditorDialog(
            existingGoal = null,
            onDismiss = { showCreateDialog = false },
            onSave = onAddGoal
        )
    }

    editingGoal?.let { goal ->
        GoalEditorDialog(
            existingGoal = goal,
            onDismiss = { editingGoal = null },
            onSave = { title, target, current, unit, targetDate ->
                onUpdateGoal(goal.id, title, target, current, unit, targetDate)
            }
        )
    }

    progressGoal?.let { goal ->
        GoalProgressUpdateDialog(
            goal = goal,
            onDismiss = { progressGoal = null },
            onSaveProgress = { newAmount ->
                onUpdateGoal(
                    goal.id,
                    goal.title,
                    goal.targetAmount,
                    newAmount,
                    goal.unit,
                    goal.targetDate
                )
            }
        )
    }

    deletingGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = { deletingGoal = null },
            title = { Text("Delete Goal?") },
            text = { Text("Remove \"${goal.title}\" permanently?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGoal(goal.id)
                        deletingGoal = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGoal = null }) { Text("Cancel") }
            }
        )
    }
}
