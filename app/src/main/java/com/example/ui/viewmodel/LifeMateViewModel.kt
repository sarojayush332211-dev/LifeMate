package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalPreferencesStore
import com.example.data.model.ActivityDay
import com.example.data.model.DailyTask
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseEntry
import com.example.data.model.PersonalGoal
import com.example.data.model.StreakSummary
import com.example.data.model.SubscriptionTier
import com.example.data.model.UserProfile
import com.example.data.repository.LifeMateRepository
import com.example.domain.ConsistencyCalculator
import com.example.domain.LifeMateDateUtils
import com.example.domain.MotivationProvider
import com.example.domain.PersonalizedMotivationEngine
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategorySpendSummary(
    val category: ExpenseCategory,
    val amount: Double,
    val fraction: Float
)

data class LifeMateUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val profile: UserProfile? = null,
    val needsOnboarding: Boolean = false,
    val showWelcomeBanner: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val avatarModel: Any? = null,
    val todayKey: String = LifeMateDateUtils.todayKey(),
    val todayDisplayDate: String = LifeMateDateUtils.formatDisplayDate(),
    val greeting: String = LifeMateDateUtils.greetingPrefix(),
    val allTasks: List<DailyTask> = emptyList(),
    val todayTasks: List<DailyTask> = emptyList(),
    val todayCompletedCount: Int = 0,
    val todayTotalCount: Int = 0,
    val todayProgressPercent: Int = 0,
    val expenses: List<ExpenseEntry> = emptyList(),
    val todaySpend: Double = 0.0,
    val weekSpend: Double = 0.0,
    val monthSpend: Double = 0.0,
    val totalSpend: Double = 0.0,
    val categoryBreakdown: List<CategorySpendSummary> = emptyList(),
    val goals: List<PersonalGoal> = emptyList(),
    val primaryGoal: PersonalGoal? = null,
    val streakSummary: StreakSummary = StreakSummary(),
    val motivationMessage: String = "",
    val darkModeEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE
)

class LifeMateViewModel(
    private val repository: LifeMateRepository,
    private val prefsStore: LocalPreferencesStore,
    private val currentUserId: String,
    private val googleDisplayName: String,
    private val googleEmail: String,
    private val googlePhotoUrl: String,
    private val motivationProvider: MotivationProvider = PersonalizedMotivationEngine()
) : ViewModel() {

    private val _showWelcomeBanner = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private fun <T> Flow<T>.unwrapFirestoreCatch(tag: String): Flow<Result<T>> = this
        .map<T, Result<T>> { Result.success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Stream error ($tag)", rootError)
            emit(Result.failure(rootError))
        }

    private val profileStream: Flow<Result<UserProfile?>> =
        repository.observeUserProfile(currentUserId).unwrapFirestoreCatch("profile")

    private val tasksStream: Flow<Result<List<DailyTask>>> =
        repository.observeTasks(currentUserId).unwrapFirestoreCatch("tasks")

    private val expensesStream: Flow<Result<List<ExpenseEntry>>> =
        repository.observeExpenses(currentUserId).unwrapFirestoreCatch("expenses")

    private val goalsStream: Flow<Result<List<PersonalGoal>>> =
        repository.observeGoals(currentUserId).unwrapFirestoreCatch("goals")

    private val activityStream: Flow<Result<List<ActivityDay>>> =
        repository.observeActivityDays(currentUserId).unwrapFirestoreCatch("activity")

    private val coreStreams: Flow<CoreSnapshot> = combine(
        profileStream,
        tasksStream,
        expensesStream,
        goalsStream,
        activityStream
    ) { profileRes, tasksRes, expensesRes, goalsRes, activityRes ->
        CoreSnapshot(profileRes, tasksRes, expensesRes, goalsRes, activityRes)
    }

    val uiState: StateFlow<LifeMateUiState> = combine(
        coreStreams,
        prefsStore.localAvatarFlow(currentUserId),
        prefsStore.darkModeFlow(currentUserId),
        _showWelcomeBanner
    ) { snapshot: CoreSnapshot, localAvatarPath: String?, localDarkMode: Boolean?, welcomeBanner: Boolean ->
        val firstError = listOfNotNull(
            snapshot.profileRes.exceptionOrNull(),
            snapshot.tasksRes.exceptionOrNull(),
            snapshot.expensesRes.exceptionOrNull(),
            snapshot.goalsRes.exceptionOrNull(),
            snapshot.activityRes.exceptionOrNull()
        ).firstOrNull()

        val profile = snapshot.profileRes.getOrNull()
        val tasks = (snapshot.tasksRes.getOrNull() ?: emptyList())
            .sortedWith(compareBy<DailyTask> { it.completed }.thenByDescending { it.createdAt ?: Timestamp(0, 0) })
        val expenses = (snapshot.expensesRes.getOrNull() ?: emptyList())
            .sortedWith(compareByDescending<ExpenseEntry> { it.expenseDate }.thenByDescending { it.createdAt ?: Timestamp(0, 0) })
        val goals = (snapshot.goalsRes.getOrNull() ?: emptyList())
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        val activityDays = snapshot.activityRes.getOrNull() ?: emptyList()

        val todayKey = LifeMateDateUtils.todayKey()
        val weekStart = LifeMateDateUtils.startOfWeekKey(todayKey)
        val monthStart = LifeMateDateUtils.startOfMonthKey(todayKey)

        val todayTasks = tasks.filter { it.scheduledDate == todayKey }
        val todayCompleted = todayTasks.count { it.completed }
        val todayTotal = todayTasks.size
        val todayPercent = if (todayTotal > 0) (todayCompleted * 100) / todayTotal else 0

        val todaySpend = expenses.filter { it.expenseDate == todayKey }.sumOf { it.amount }
        val weekSpend = expenses.filter { it.expenseDate in weekStart..todayKey }.sumOf { it.amount }
        val monthSpend = expenses.filter { it.expenseDate in monthStart..todayKey }.sumOf { it.amount }
        val totalSpend = expenses.sumOf { it.amount }

        val categoryBreakdown = ExpenseCategory.entries.map { category ->
            val sum = expenses.filter { it.category.equals(category.label, ignoreCase = true) }
                .sumOf { it.amount }
            val fraction = if (totalSpend > 0.0) (sum / totalSpend).toFloat().coerceIn(0f, 1f) else 0f
            CategorySpendSummary(category = category, amount = sum, fraction = fraction)
        }.filter { it.amount > 0.0 }.sortedByDescending { it.amount }

        val streakSummary = ConsistencyCalculator.calculate(
            todayKey = todayKey,
            tasks = tasks,
            activityDays = activityDays
        )

        val motivation = motivationProvider.generate(
            profile = profile,
            tasks = tasks,
            expenses = expenses,
            goals = goals,
            streak = streakSummary,
            todayKey = todayKey
        )

        val resolvedName = profile?.fullName?.takeIf { it.isNotBlank() }
            ?: googleDisplayName.takeIf { it.isNotBlank() }
            ?: "Friend"

        val resolvedEmail = profile?.email?.takeIf { it.isNotBlank() } ?: googleEmail
        val resolvedAvatar: Any? = localAvatarPath
            ?: profile?.photoUrl?.takeIf { it.isNotBlank() }
            ?: googlePhotoUrl.takeIf { it.isNotBlank() }

        val primaryGoal = goals.firstOrNull { it.currentAmount < it.targetAmount } ?: goals.firstOrNull()

        LifeMateUiState(
            isLoading = false,
            errorMessage = firstError?.localizedMessage,
            profile = profile,
            needsOnboarding = profile == null || !profile.onboardingCompleted,
            showWelcomeBanner = welcomeBanner,
            displayName = resolvedName,
            email = resolvedEmail,
            avatarModel = resolvedAvatar,
            todayKey = todayKey,
            todayDisplayDate = LifeMateDateUtils.formatDisplayDate(),
            greeting = LifeMateDateUtils.greetingPrefix(),
            allTasks = tasks,
            todayTasks = todayTasks,
            todayCompletedCount = todayCompleted,
            todayTotalCount = todayTotal,
            todayProgressPercent = todayPercent,
            expenses = expenses,
            todaySpend = todaySpend,
            weekSpend = weekSpend,
            monthSpend = monthSpend,
            totalSpend = totalSpend,
            categoryBreakdown = categoryBreakdown,
            goals = goals,
            primaryGoal = primaryGoal,
            streakSummary = streakSummary,
            motivationMessage = motivation,
            darkModeEnabled = profile?.darkModeEnabled ?: localDarkMode ?: false,
            notificationsEnabled = profile?.notificationsEnabled ?: false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = LifeMateUiState(
            isLoading = true,
            displayName = googleDisplayName,
            email = googleEmail,
            avatarModel = googlePhotoUrl.takeIf { it.isNotBlank() }
        )
    )

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun dismissWelcomeBanner() {
        _showWelcomeBanner.value = false
    }

    fun saveOnboardingProfile(
        fullName: String,
        dateOfBirth: String,
        dailyFocus: String,
        motivationStyle: String
    ) {
        if (fullName.isBlank()) {
            _statusMessage.value = "Please enter your name."
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val current = uiState.value.profile
            val result = repository.saveUserProfile(
                userId = currentUserId,
                fullName = fullName,
                email = current?.email?.ifBlank { googleEmail } ?: googleEmail,
                dateOfBirth = dateOfBirth,
                photoUrl = current?.photoUrl?.ifBlank { googlePhotoUrl } ?: googlePhotoUrl,
                dailyFocus = dailyFocus,
                motivationStyle = motivationStyle,
                onboardingCompleted = true,
                darkModeEnabled = current?.darkModeEnabled ?: uiState.value.darkModeEnabled,
                notificationsEnabled = current?.notificationsEnabled ?: false
            )
            _isSaving.value = false
            result.onSuccess {
                _showWelcomeBanner.value = true
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to save profile."
            }
        }
    }

    fun updateProfileDetails(
        fullName: String,
        dateOfBirth: String,
        dailyFocus: String,
        motivationStyle: String
    ) {
        if (fullName.isBlank()) {
            _statusMessage.value = "Name cannot be empty."
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val current = uiState.value.profile
            val result = repository.saveUserProfile(
                userId = currentUserId,
                fullName = fullName,
                email = current?.email?.ifBlank { googleEmail } ?: googleEmail,
                dateOfBirth = dateOfBirth,
                photoUrl = current?.photoUrl?.ifBlank { googlePhotoUrl } ?: googlePhotoUrl,
                dailyFocus = dailyFocus,
                motivationStyle = motivationStyle,
                onboardingCompleted = true,
                darkModeEnabled = current?.darkModeEnabled ?: uiState.value.darkModeEnabled,
                notificationsEnabled = current?.notificationsEnabled ?: false
            )
            _isSaving.value = false
            result.onSuccess {
                _statusMessage.value = "Profile updated"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Could not update profile."
            }
        }
    }

    fun savePickedPhoto(uri: Uri) {
        viewModelScope.launch {
            val savedPath = prefsStore.savePickedAvatar(currentUserId, uri)
            if (savedPath != null) {
                _statusMessage.value = "Profile photo updated"
            } else {
                _statusMessage.value = "Could not load selected image"
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.setDarkMode(currentUserId, enabled)
            repository.updateThemePreference(currentUserId, enabled).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Could not sync theme preference."
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNotificationsPreference(currentUserId, enabled)
                .onSuccess {
                    _statusMessage.value = if (enabled) {
                        "Daily reminders enabled"
                    } else {
                        "Daily reminders turned off"
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Could not update notification preference."
                }
        }
    }

    fun resetLocalData() {
        viewModelScope.launch {
            prefsStore.resetLocalData(currentUserId)
            _statusMessage.value = "Local cache & preferences reset"
        }
    }

    fun addTask(title: String, notes: String = "", scheduledDate: String = LifeMateDateUtils.todayKey()) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addTask(currentUserId, title, notes, scheduledDate).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to add task."
            }
        }
    }

    fun updateTask(taskId: String, title: String, notes: String, scheduledDate: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.updateTask(currentUserId, taskId, title, notes, scheduledDate).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to update task."
            }
        }
    }

    fun toggleTaskCompletion(task: DailyTask) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(
                userId = currentUserId,
                task = task,
                todayKey = LifeMateDateUtils.todayKey()
            ).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to update task."
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(currentUserId, taskId).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to delete task."
            }
        }
    }

    fun addExpense(amount: Double, category: String, description: String, expenseDate: String) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            repository.addExpense(currentUserId, amount, category, description, expenseDate)
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to add expense."
                }
        }
    }

    fun updateExpense(
        expenseId: String,
        amount: Double,
        category: String,
        description: String,
        expenseDate: String
    ) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            repository.updateExpense(currentUserId, expenseId, amount, category, description, expenseDate)
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to update expense."
                }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            repository.deleteExpense(currentUserId, expenseId).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to delete expense."
            }
        }
    }

    fun addGoal(
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        unit: String,
        targetDate: String
    ) {
        if (title.isBlank() || targetAmount <= 0.0) return
        viewModelScope.launch {
            repository.addGoal(
                userId = currentUserId,
                title = title,
                targetAmount = targetAmount,
                currentAmount = currentAmount,
                unit = unit,
                targetDate = targetDate,
                todayKey = LifeMateDateUtils.todayKey()
            ).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to create goal."
            }
        }
    }

    fun updateGoal(
        goalId: String,
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        unit: String,
        targetDate: String
    ) {
        if (title.isBlank() || targetAmount <= 0.0) return
        viewModelScope.launch {
            repository.updateGoal(
                userId = currentUserId,
                goalId = goalId,
                title = title,
                targetAmount = targetAmount,
                currentAmount = currentAmount,
                unit = unit,
                targetDate = targetDate,
                todayKey = LifeMateDateUtils.todayKey()
            ).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to update goal."
            }
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            repository.deleteGoal(currentUserId, goalId).onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to delete goal."
            }
        }
    }

    private data class CoreSnapshot(
        val profileRes: Result<UserProfile?>,
        val tasksRes: Result<List<DailyTask>>,
        val expensesRes: Result<List<ExpenseEntry>>,
        val goalsRes: Result<List<PersonalGoal>>,
        val activityRes: Result<List<ActivityDay>>
    )

    private companion object {
        const val TAG = "LifeMateVM"
    }
}
