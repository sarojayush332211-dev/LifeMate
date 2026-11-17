package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseEntry
import com.example.domain.LifeMateDateUtils
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExpenseEditorDialog
import com.example.ui.theme.ExpenseEducationColor
import com.example.ui.theme.ExpenseEntertainmentColor
import com.example.ui.theme.ExpenseFoodColor
import com.example.ui.theme.ExpenseOtherColor
import com.example.ui.theme.ExpenseShoppingColor
import com.example.ui.theme.ExpenseTravelColor
import com.example.ui.viewmodel.LifeMateUiState

@Composable
fun ExpensesScreen(
    uiState: LifeMateUiState,
    onAddExpense: (amount: Double, category: String, description: String, date: String) -> Unit,
    onUpdateExpense: (expenseId: String, amount: Double, category: String, description: String, date: String) -> Unit,
    onDeleteExpense: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseEntry?>(null) }
    var deletingExpense by remember { mutableStateOf<ExpenseEntry?>(null) }

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
                            text = "Expenses",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Tracked in Indian Rupee (₹)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("expenses_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Expense")
                    }
                }
            }

            // 2x2 Summary KPI Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpendSummaryCard(
                            label = "Today's Spend",
                            amount = uiState.todaySpend,
                            modifier = Modifier.weight(1f)
                        )
                        SpendSummaryCard(
                            label = "This Week",
                            amount = uiState.weekSpend,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpendSummaryCard(
                            label = "This Month",
                            amount = uiState.monthSpend,
                            modifier = Modifier.weight(1f)
                        )
                        SpendSummaryCard(
                            label = "Total Spending",
                            amount = uiState.totalSpend,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Category Breakdown
            if (uiState.categoryBreakdown.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Category Breakdown",
                                style = MaterialTheme.typography.titleMedium
                            )
                            uiState.categoryBreakdown.forEach { summary ->
                                val (_, color) = categoryVisuals(summary.category)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = summary.category.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = LifeMateDateUtils.formatInr(summary.amount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { summary.fraction },
                                        color = color,
                                        trackColor = color.copy(alpha = 0.14f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(CircleShape),
                                        strokeCap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (uiState.expenses.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        title = "No expenses logged yet",
                        subtitle = "Log daily spends across Food, Travel, Shopping, Education, and more.",
                        actionLabel = "Log Expense",
                        onAction = { showCreateDialog = true }
                    )
                }
            } else {
                items(uiState.expenses, key = { it.id }) { expense ->
                    val cat = ExpenseCategory.fromLabel(expense.category)
                    val (icon, color) = categoryVisuals(cat)
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = cat.label,
                                    tint = color,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expense.description.ifBlank { cat.label },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${cat.label} • ${LifeMateDateUtils.formatShortDate(expense.expenseDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = LifeMateDateUtils.formatInr(expense.amount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { editingExpense = expense }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit expense",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { deletingExpense = expense }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete expense",
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
        ExpenseEditorDialog(
            existingExpense = null,
            todayKey = uiState.todayKey,
            onDismiss = { showCreateDialog = false },
            onSave = onAddExpense
        )
    }

    editingExpense?.let { expense ->
        ExpenseEditorDialog(
            existingExpense = expense,
            todayKey = uiState.todayKey,
            onDismiss = { editingExpense = null },
            onSave = { amount, category, description, date ->
                onUpdateExpense(expense.id, amount, category, description, date)
            }
        )
    }

    deletingExpense?.let { expense ->
        AlertDialog(
            onDismissRequest = { deletingExpense = null },
            title = { Text("Delete Expense?") },
            text = { Text("Delete ${LifeMateDateUtils.formatInr(expense.amount)} (${expense.category})?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteExpense(expense.id)
                        deletingExpense = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingExpense = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SpendSummaryCard(
    label: String,
    amount: Double,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = LifeMateDateUtils.formatInr(amount),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun categoryVisuals(category: ExpenseCategory): Pair<ImageVector, Color> {
    return when (category) {
        ExpenseCategory.FOOD -> Icons.Rounded.Restaurant to ExpenseFoodColor
        ExpenseCategory.TRAVEL -> Icons.Rounded.DirectionsBus to ExpenseTravelColor
        ExpenseCategory.SHOPPING -> Icons.Rounded.ShoppingBag to ExpenseShoppingColor
        ExpenseCategory.EDUCATION -> Icons.Rounded.School to ExpenseEducationColor
        ExpenseCategory.ENTERTAINMENT -> Icons.Rounded.Movie to ExpenseEntertainmentColor
        ExpenseCategory.OTHER -> Icons.Rounded.Category to ExpenseOtherColor
    }
}
