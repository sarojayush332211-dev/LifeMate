package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyTask
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseEntry
import com.example.data.model.PersonalGoal
import com.example.domain.LifeMateDateUtils

enum class QuickAddTab(val label: String) {
    TASK("Task"),
    EXPENSE("Expense"),
    GOAL("Goal")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddModalSheet(
    initialTab: QuickAddTab,
    todayKey: String,
    onDismiss: () -> Unit,
    onAddTask: (title: String, notes: String, date: String) -> Unit,
    onAddExpense: (amount: Double, category: String, description: String, date: String) -> Unit,
    onAddGoal: (title: String, target: Double, current: Double, unit: String, targetDate: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by remember { mutableIntStateOf(initialTab.ordinal) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Quick Add",
                style = MaterialTheme.typography.headlineSmall
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                QuickAddTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = QuickAddTab.entries.size
                        ),
                        modifier = Modifier.testTag("quick_add_tab_${tab.name.lowercase()}")
                    ) {
                        Text(tab.label)
                    }
                }
            }

            when (QuickAddTab.entries[selectedTabIndex]) {
                QuickAddTab.TASK -> {
                    var title by remember { mutableStateOf("") }
                    var notes by remember { mutableStateOf("") }
                    var date by remember { mutableStateOf(todayKey) }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task title") },
                        placeholder = { Text("e.g. Study for 1 hour") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_task_title_input")
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    DatePickerField(
                        label = "Scheduled date",
                        value = date,
                        onDateSelected = { if (it.isNotBlank()) date = it },
                        allowClear = false
                    )
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onAddTask(title, notes, date)
                                onDismiss()
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("quick_save_task_button")
                    ) {
                        Text("Add Task")
                    }
                }

                QuickAddTab.EXPENSE -> {
                    var amountText by remember { mutableStateOf("") }
                    var category by remember { mutableStateOf(ExpenseCategory.FOOD.label) }
                    var description by remember { mutableStateOf("") }
                    var date by remember { mutableStateOf(todayKey) }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                amountText = input
                            }
                        },
                        label = { Text("Amount (₹)") },
                        placeholder = { Text("120") },
                        prefix = { Text("₹ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_expense_amount_input")
                    )
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ExpenseCategory.entries.forEach { item ->
                            FilterChip(
                                selected = category == item.label,
                                onClick = { category = item.label },
                                label = { Text(item.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        placeholder = { Text("Lunch, Metro ticket, Books...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DatePickerField(
                        label = "Date",
                        value = date,
                        onDateSelected = { if (it.isNotBlank()) date = it },
                        allowClear = false
                    )
                    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                    Button(
                        onClick = {
                            if (parsedAmount > 0.0) {
                                onAddExpense(parsedAmount, category, description, date)
                                onDismiss()
                            }
                        },
                        enabled = parsedAmount > 0.0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("quick_save_expense_button")
                    ) {
                        Text("Save Expense")
                    }
                }

                QuickAddTab.GOAL -> {
                    var title by remember { mutableStateOf("") }
                    var targetText by remember { mutableStateOf("") }
                    var currentText by remember { mutableStateOf("0") }
                    var unit by remember { mutableStateOf("₹") }
                    var targetDate by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Goal name") },
                        placeholder = { Text("Save ₹10,000") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_goal_title_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                    targetText = input
                                }
                            },
                            label = { Text("Target") },
                            placeholder = { Text("10000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_goal_target_input")
                        )
                        OutlinedTextField(
                            value = currentText,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                    currentText = input
                                }
                            },
                            label = { Text("Current") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Unit", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("₹", "pages", "hrs", "km", "days", "%").forEach { unitOption ->
                            FilterChip(
                                selected = unit == unitOption,
                                onClick = { unit = unitOption },
                                label = { Text(unitOption) }
                            )
                        }
                    }
                    DatePickerField(
                        label = "Target date (optional)",
                        value = targetDate,
                        onDateSelected = { targetDate = it },
                        allowClear = true,
                        placeholder = "No deadline"
                    )
                    val target = targetText.toDoubleOrNull() ?: 0.0
                    val current = currentText.toDoubleOrNull() ?: 0.0
                    Button(
                        onClick = {
                            if (title.isNotBlank() && target > 0.0) {
                                onAddGoal(title, target, current, unit, targetDate)
                                onDismiss()
                            }
                        },
                        enabled = title.isNotBlank() && target > 0.0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("quick_save_goal_button")
                    ) {
                        Text("Create Goal")
                    }
                }
            }
        }
    }
}

@Composable
fun TaskEditorDialog(
    existingTask: DailyTask?,
    todayKey: String,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, date: String) -> Unit
) {
    var title by remember(existingTask) { mutableStateOf(existingTask?.title ?: "") }
    var notes by remember(existingTask) { mutableStateOf(existingTask?.notes ?: "") }
    var date by remember(existingTask) { mutableStateOf(existingTask?.scheduledDate?.ifBlank { todayKey } ?: todayKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingTask == null) "New Task" else "Edit Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task title") },
                    placeholder = { Text("e.g. Exercise for 30 min") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_dialog_title_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Scheduled date",
                    value = date,
                    onDateSelected = { if (it.isNotBlank()) date = it },
                    allowClear = false
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, notes, date)
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("task_dialog_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorDialog(
    existingExpense: ExpenseEntry?,
    todayKey: String,
    onDismiss: () -> Unit,
    onSave: (amount: Double, category: String, description: String, date: String) -> Unit
) {
    var amountText by remember(existingExpense) {
        mutableStateOf(
            existingExpense?.amount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var category by remember(existingExpense) {
        mutableStateOf(existingExpense?.category ?: ExpenseCategory.FOOD.label)
    }
    var description by remember(existingExpense) { mutableStateOf(existingExpense?.description ?: "") }
    var date by remember(existingExpense) {
        mutableStateOf(existingExpense?.expenseDate?.ifBlank { todayKey } ?: todayKey)
    }

    val amount = amountText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingExpense == null) "Add Expense" else "Edit Expense") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amountText = input
                        }
                    },
                    label = { Text("Amount (₹)") },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_dialog_amount_input")
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat.label,
                            onClick = { category = cat.label },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Date",
                    value = date,
                    onDateSelected = { if (it.isNotBlank()) date = it },
                    allowClear = false
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (amount > 0.0) {
                        onSave(amount, category, description, date)
                        onDismiss()
                    }
                },
                enabled = amount > 0.0,
                modifier = Modifier.testTag("expense_dialog_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalEditorDialog(
    existingGoal: PersonalGoal?,
    onDismiss: () -> Unit,
    onSave: (title: String, target: Double, current: Double, unit: String, targetDate: String) -> Unit
) {
    var title by remember(existingGoal) { mutableStateOf(existingGoal?.title ?: "") }
    var targetText by remember(existingGoal) {
        mutableStateOf(
            existingGoal?.targetAmount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var currentText by remember(existingGoal) {
        mutableStateOf(
            existingGoal?.currentAmount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: "0"
        )
    }
    var unit by remember(existingGoal) { mutableStateOf(existingGoal?.unit ?: "₹") }
    var targetDate by remember(existingGoal) { mutableStateOf(existingGoal?.targetDate ?: "") }

    val target = targetText.toDoubleOrNull() ?: 0.0
    val current = currentText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingGoal == null) "Create Goal" else "Edit Goal") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Goal name") },
                    placeholder = { Text("Save ₹10,000") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_dialog_title_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                targetText = input
                            }
                        },
                        label = { Text("Target") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                currentText = input
                            }
                        },
                        label = { Text("Current") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Unit", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("₹", "pages", "hrs", "km", "days", "%").forEach { item ->
                        FilterChip(
                            selected = unit == item,
                            onClick = { unit = item },
                            label = { Text(item) }
                        )
                    }
                }
                DatePickerField(
                    label = "Target date (optional)",
                    value = targetDate,
                    onDateSelected = { targetDate = it },
                    allowClear = true,
                    placeholder = "No deadline"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && target > 0.0) {
                        onSave(title, target, current, unit, targetDate)
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank() && target > 0.0,
                modifier = Modifier.testTag("goal_dialog_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun GoalProgressUpdateDialog(
    goal: PersonalGoal,
    onDismiss: () -> Unit,
    onSaveProgress: (newCurrentAmount: Double) -> Unit
) {
    var currentText by remember(goal) {
        mutableStateOf(
            if (goal.currentAmount % 1.0 == 0.0) goal.currentAmount.toLong().toString()
            else goal.currentAmount.toString()
        )
    }
    val quickIncrements = if (goal.unit.trim() == "₹") listOf(100.0, 500.0, 1000.0) else listOf(1.0, 5.0, 10.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Progress") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${goal.title} (Target: ${LifeMateDateUtils.formatGoalValue(goal.targetAmount, goal.unit)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            currentText = input
                        }
                    },
                    label = { Text("Current progress (${goal.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickIncrements.forEach { step ->
                        OutlinedButton(
                            onClick = {
                                val base = currentText.toDoubleOrNull() ?: goal.currentAmount
                                val next = base + step
                                currentText = if (next % 1.0 == 0.0) next.toLong().toString() else next.toString()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            val label = if (goal.unit.trim() == "₹") "+₹${step.toInt()}" else "+${step.toInt()}"
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val newValue = currentText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    onSaveProgress(newValue.coerceAtLeast(0.0))
                    onDismiss()
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
