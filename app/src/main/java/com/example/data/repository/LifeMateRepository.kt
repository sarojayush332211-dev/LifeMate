package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.ActivityDayEntity
import com.example.data.local.DailyTaskEntity
import com.example.data.local.ExpenseEntryEntity
import com.example.data.local.LifeMateDatabase
import com.example.data.local.PersonalGoalEntity
import com.example.data.local.UserProfileEntity
import com.example.data.local.toDomain
import com.example.data.model.ActivityDay
import com.example.data.model.DailyTask
import com.example.data.model.ExpenseEntry
import com.example.data.model.PersonalGoal
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.math.max

class LifeMateRepository(private val database: LifeMateDatabase) {

    constructor(context: Context) : this(LifeMateDatabase.getInstance(context))

    private val dao = database.lifeMateDao()

    fun observeAllProfiles(): Flow<List<UserProfile>> =
        dao.observeAllProfiles().map { list -> list.map { it.toDomain() } }

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        dao.observeUserProfile(userId).map { it?.toDomain() }

    suspend fun saveUserProfile(
        userId: String,
        fullName: String,
        email: String,
        dateOfBirth: String,
        photoUrl: String,
        dailyFocus: String,
        motivationStyle: String,
        onboardingCompleted: Boolean,
        darkModeEnabled: Boolean,
        notificationsEnabled: Boolean
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = dao.getUserProfile(userId)
            val entity = UserProfileEntity(
                id = userId,
                userId = userId,
                fullName = fullName.trim(),
                email = email.trim(),
                dateOfBirth = dateOfBirth.trim(),
                photoUrl = photoUrl.trim(),
                dailyFocus = dailyFocus.trim(),
                motivationStyle = motivationStyle,
                onboardingCompleted = onboardingCompleted,
                darkModeEnabled = darkModeEnabled,
                notificationsEnabled = notificationsEnabled,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
            dao.upsertUserProfile(entity)
        }
    }

    suspend fun updateThemePreference(userId: String, darkModeEnabled: Boolean): Result<Unit> = runCatching {
        dao.updateThemePreference(
            userId = userId,
            darkModeEnabled = darkModeEnabled,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun updateNotificationsPreference(userId: String, enabled: Boolean): Result<Unit> = runCatching {
        dao.updateNotificationsPreference(
            userId = userId,
            enabled = enabled,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun observeTasks(userId: String): Flow<List<DailyTask>> =
        dao.observeTasks(userId).map { list -> list.map { it.toDomain() } }

    suspend fun addTask(
        userId: String,
        title: String,
        notes: String,
        scheduledDate: String
    ): Result<String> = runCatching {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertTask(
            DailyTaskEntity(
                id = id,
                userId = userId,
                title = title.trim(),
                notes = notes.trim(),
                completed = false,
                scheduledDate = scheduledDate,
                completedDate = "",
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        id
    }

    suspend fun updateTask(
        userId: String,
        taskId: String,
        title: String,
        notes: String,
        scheduledDate: String
    ): Result<Unit> = runCatching {
        require(taskId.isNotBlank()) { "Task ID cannot be blank" }
        dao.updateTaskFields(
            userId = userId,
            taskId = taskId,
            title = title.trim(),
            notes = notes.trim(),
            scheduledDate = scheduledDate,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun toggleTaskCompletion(
        userId: String,
        task: DailyTask,
        todayKey: String
    ): Result<Unit> = runCatching {
        require(task.id.isNotBlank()) { "Task ID cannot be blank" }
        val newCompleted = !task.completed
        val newCompletedDate = if (newCompleted) todayKey else ""
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.updateTaskCompletion(
                userId = userId,
                taskId = task.id,
                completed = newCompleted,
                completedDate = newCompletedDate,
                updatedAtMillis = now
            )
            val activityDate = if (newCompleted) todayKey else task.completedDate.ifBlank { todayKey }
            recordDailyActivityInternal(
                userId = userId,
                dateKey = activityDate,
                completedDelta = if (newCompleted) 1L else -1L,
                engagementDelta = if (newCompleted) 1L else 0L
            )
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> = runCatching {
        require(taskId.isNotBlank()) { "Task ID cannot be blank" }
        dao.deleteTask(userId, taskId)
    }

    fun observeExpenses(userId: String): Flow<List<ExpenseEntry>> =
        dao.observeExpenses(userId).map { list -> list.map { it.toDomain() } }

    suspend fun addExpense(
        userId: String,
        amount: Double,
        category: String,
        description: String,
        expenseDate: String
    ): Result<String> = runCatching {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.upsertExpense(
                ExpenseEntryEntity(
                    id = id,
                    userId = userId,
                    amount = amount,
                    category = category,
                    description = description.trim(),
                    expenseDate = expenseDate,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            recordDailyActivityInternal(
                userId = userId,
                dateKey = expenseDate,
                completedDelta = 0L,
                engagementDelta = 1L
            )
        }
        id
    }

    suspend fun updateExpense(
        userId: String,
        expenseId: String,
        amount: Double,
        category: String,
        description: String,
        expenseDate: String
    ): Result<Unit> = runCatching {
        require(expenseId.isNotBlank()) { "Expense ID cannot be blank" }
        dao.updateExpenseFields(
            userId = userId,
            expenseId = expenseId,
            amount = amount,
            category = category,
            description = description.trim(),
            expenseDate = expenseDate,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun deleteExpense(userId: String, expenseId: String): Result<Unit> = runCatching {
        require(expenseId.isNotBlank()) { "Expense ID cannot be blank" }
        dao.deleteExpense(userId, expenseId)
    }

    fun observeGoals(userId: String): Flow<List<PersonalGoal>> =
        dao.observeGoals(userId).map { list -> list.map { it.toDomain() } }

    suspend fun addGoal(
        userId: String,
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        unit: String,
        targetDate: String,
        todayKey: String
    ): Result<String> = runCatching {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.upsertGoal(
                PersonalGoalEntity(
                    id = id,
                    userId = userId,
                    title = title.trim(),
                    targetAmount = targetAmount,
                    currentAmount = currentAmount.coerceAtLeast(0.0),
                    unit = unit.trim().ifBlank { "₹" },
                    targetDate = targetDate.trim(),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            recordDailyActivityInternal(
                userId = userId,
                dateKey = todayKey,
                completedDelta = 0L,
                engagementDelta = 1L
            )
        }
        id
    }

    suspend fun updateGoal(
        userId: String,
        goalId: String,
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        unit: String,
        targetDate: String,
        todayKey: String
    ): Result<Unit> = runCatching {
        require(goalId.isNotBlank()) { "Goal ID cannot be blank" }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.updateGoalFields(
                userId = userId,
                goalId = goalId,
                title = title.trim(),
                targetAmount = targetAmount,
                currentAmount = currentAmount.coerceAtLeast(0.0),
                unit = unit.trim().ifBlank { "₹" },
                targetDate = targetDate.trim(),
                updatedAtMillis = now
            )
            recordDailyActivityInternal(
                userId = userId,
                dateKey = todayKey,
                completedDelta = 0L,
                engagementDelta = 1L
            )
        }
    }

    suspend fun deleteGoal(userId: String, goalId: String): Result<Unit> = runCatching {
        require(goalId.isNotBlank()) { "Goal ID cannot be blank" }
        dao.deleteGoal(userId, goalId)
    }

    fun observeActivityDays(userId: String): Flow<List<ActivityDay>> =
        dao.observeActivityDays(userId).map { list -> list.map { it.toDomain() } }

    private suspend fun recordDailyActivityInternal(
        userId: String,
        dateKey: String,
        completedDelta: Long,
        engagementDelta: Long
    ) {
        if (dateKey.isBlank()) return
        val now = System.currentTimeMillis()
        val existing = dao.getActivityDay(userId, dateKey)
        if (existing != null) {
            dao.upsertActivityDay(
                existing.copy(
                    completedTasksCount = max(0L, existing.completedTasksCount + completedDelta).coerceAtMost(1000L),
                    engagementCount = max(0L, existing.engagementCount + engagementDelta).coerceAtMost(1000L),
                    updatedAtMillis = now
                )
            )
        } else {
            dao.upsertActivityDay(
                ActivityDayEntity(
                    id = "${userId}_$dateKey",
                    userId = userId,
                    dateKey = dateKey,
                    completedTasksCount = max(0L, completedDelta).coerceAtMost(1000L),
                    engagementCount = max(0L, engagementDelta).coerceAtMost(1000L),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }
    }
}
