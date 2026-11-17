package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ActivityDay
import com.example.data.model.DailyTask
import com.example.data.model.ExpenseEntry
import com.example.data.model.PersonalGoal
import com.example.data.model.UserProfile
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.max

class LifeMateRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)
    private fun tasksCol(userId: String) = userDoc(userId).collection("tasks")
    private fun expensesCol(userId: String) = userDoc(userId).collection("expenses")
    private fun goalsCol(userId: String) = userDoc(userId).collection("goals")
    private fun activityCol(userId: String) = userDoc(userId).collection("activity_days")

    private fun unwrapAndLog(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception) ?: error
        handleFirestoreError(exceptionToLog, op, path)
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val ref = userDoc(userId)
        return ref.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.GET, ref.path)
                throw error
            }
    }

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
        val ref = userDoc(userId)
        try {
            db.runTransaction { tx ->
                val existing = tx.get(ref)
                if (existing.exists()) {
                    val updates = mapOf(
                        "fullName" to fullName.trim(),
                        "email" to email.trim(),
                        "dateOfBirth" to dateOfBirth.trim(),
                        "photoUrl" to photoUrl.trim(),
                        "dailyFocus" to dailyFocus.trim(),
                        "motivationStyle" to motivationStyle,
                        "onboardingCompleted" to onboardingCompleted,
                        "darkModeEnabled" to darkModeEnabled,
                        "notificationsEnabled" to notificationsEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    tx.update(ref, updates)
                } else {
                    val payload = mapOf(
                        "userId" to userId,
                        "fullName" to fullName.trim(),
                        "email" to email.trim(),
                        "dateOfBirth" to dateOfBirth.trim(),
                        "photoUrl" to photoUrl.trim(),
                        "dailyFocus" to dailyFocus.trim(),
                        "motivationStyle" to motivationStyle,
                        "onboardingCompleted" to onboardingCompleted,
                        "darkModeEnabled" to darkModeEnabled,
                        "notificationsEnabled" to notificationsEnabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    tx.set(ref, payload)
                }
                null
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun updateThemePreference(userId: String, darkModeEnabled: Boolean): Result<Unit> = runCatching {
        val ref = userDoc(userId)
        try {
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "darkModeEnabled" to darkModeEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun updateNotificationsPreference(userId: String, enabled: Boolean): Result<Unit> = runCatching {
        val ref = userDoc(userId)
        try {
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "notificationsEnabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    fun observeTasks(userId: String): Flow<List<DailyTask>> {
        val col = tasksCol(userId)
        return col.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DailyTask::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun addTask(
        userId: String,
        title: String,
        notes: String,
        scheduledDate: String
    ): Result<String> = runCatching {
        val docRef = tasksCol(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim(),
            "notes" to notes.trim(),
            "completed" to false,
            "scheduledDate" to scheduledDate,
            "completedDate" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateTask(
        userId: String,
        taskId: String,
        title: String,
        notes: String,
        scheduledDate: String
    ): Result<Unit> = runCatching {
        require(taskId.isNotBlank()) { "Task ID cannot be blank" }
        val docRef = tasksCol(userId).document(taskId)
        try {
            docRef.update(
                mapOf(
                    "title" to title.trim(),
                    "notes" to notes.trim(),
                    "scheduledDate" to scheduledDate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleTaskCompletion(
        userId: String,
        task: DailyTask,
        todayKey: String
    ): Result<Unit> = runCatching {
        require(task.id.isNotBlank()) { "Task ID cannot be blank" }
        val newCompleted = !task.completed
        val newCompletedDate = if (newCompleted) todayKey else ""
        val docRef = tasksCol(userId).document(task.id)
        try {
            docRef.update(
                mapOf(
                    "completed" to newCompleted,
                    "completedDate" to newCompletedDate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val activityDate = if (newCompleted) todayKey else task.completedDate.ifBlank { todayKey }
            recordDailyActivityInternal(
                userId = userId,
                dateKey = activityDate,
                completedDelta = if (newCompleted) 1L else -1L,
                engagementDelta = if (newCompleted) 1L else 0L
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> = runCatching {
        require(taskId.isNotBlank()) { "Task ID cannot be blank" }
        val docRef = tasksCol(userId).document(taskId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeExpenses(userId: String): Flow<List<ExpenseEntry>> {
        val col = expensesCol(userId)
        return col.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ExpenseEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun addExpense(
        userId: String,
        amount: Double,
        category: String,
        description: String,
        expenseDate: String
    ): Result<String> = runCatching {
        val docRef = expensesCol(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "amount" to amount,
            "category" to category,
            "description" to description.trim(),
            "expenseDate" to expenseDate,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            recordDailyActivityInternal(
                userId = userId,
                dateKey = expenseDate,
                completedDelta = 0L,
                engagementDelta = 1L
            )
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
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
        val docRef = expensesCol(userId).document(expenseId)
        try {
            docRef.update(
                mapOf(
                    "amount" to amount,
                    "category" to category,
                    "description" to description.trim(),
                    "expenseDate" to expenseDate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteExpense(userId: String, expenseId: String): Result<Unit> = runCatching {
        require(expenseId.isNotBlank()) { "Expense ID cannot be blank" }
        val docRef = expensesCol(userId).document(expenseId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeGoals(userId: String): Flow<List<PersonalGoal>> {
        val col = goalsCol(userId)
        return col.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PersonalGoal::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun addGoal(
        userId: String,
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        unit: String,
        targetDate: String,
        todayKey: String
    ): Result<String> = runCatching {
        val docRef = goalsCol(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim(),
            "targetAmount" to targetAmount,
            "currentAmount" to currentAmount.coerceAtLeast(0.0),
            "unit" to unit.trim().ifBlank { "₹" },
            "targetDate" to targetDate.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            recordDailyActivityInternal(
                userId = userId,
                dateKey = todayKey,
                completedDelta = 0L,
                engagementDelta = 1L
            )
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
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
        val docRef = goalsCol(userId).document(goalId)
        try {
            docRef.update(
                mapOf(
                    "title" to title.trim(),
                    "targetAmount" to targetAmount,
                    "currentAmount" to currentAmount.coerceAtLeast(0.0),
                    "unit" to unit.trim().ifBlank { "₹" },
                    "targetDate" to targetDate.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordDailyActivityInternal(
                userId = userId,
                dateKey = todayKey,
                completedDelta = 0L,
                engagementDelta = 1L
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteGoal(userId: String, goalId: String): Result<Unit> = runCatching {
        require(goalId.isNotBlank()) { "Goal ID cannot be blank" }
        val docRef = goalsCol(userId).document(goalId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeActivityDays(userId: String): Flow<List<ActivityDay>> {
        val col = activityCol(userId)
        return col.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ActivityDay::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, col.path)
                throw error
            }
    }

    private suspend fun recordDailyActivityInternal(
        userId: String,
        dateKey: String,
        completedDelta: Long,
        engagementDelta: Long
    ) {
        if (dateKey.isBlank()) return
        val docRef = activityCol(userId).document(dateKey)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    val prevCompleted = snap.getLong("completedTasksCount") ?: 0L
                    val prevEngagement = snap.getLong("engagementCount") ?: 0L
                    tx.update(
                        docRef,
                        mapOf(
                            "completedTasksCount" to max(0L, prevCompleted + completedDelta).coerceAtMost(1000L),
                            "engagementCount" to max(0L, prevEngagement + engagementDelta).coerceAtMost(1000L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.set(
                        docRef,
                        mapOf(
                            "userId" to userId,
                            "dateKey" to dateKey,
                            "completedTasksCount" to max(0L, completedDelta).coerceAtMost(1000L),
                            "engagementCount" to max(0L, engagementDelta).coerceAtMost(1000L),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                null
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
        }
    }
}
