package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MotivationStyle(val id: String, val label: String, val subtitle: String) {
    FRIENDLY(
        id = "friendly",
        label = "Friendly",
        subtitle = "Warm, supportive encouragement that celebrates small wins"
    ),
    STRICT(
        id = "strict",
        label = "Strict",
        subtitle = "Direct, disciplined accountability without excuses"
    ),
    GOAL_FOCUSED(
        id = "goal_focused",
        label = "Goal-focused",
        subtitle = "Outcome-driven nudges tied to your targets and metrics"
    );

    companion object {
        fun fromId(id: String?): MotivationStyle =
            entries.firstOrNull { it.id == id } ?: FRIENDLY
    }
}

enum class ExpenseCategory(val label: String) {
    FOOD("Food"),
    TRAVEL("Travel"),
    SHOPPING("Shopping"),
    EDUCATION("Education"),
    ENTERTAINMENT("Entertainment"),
    OTHER("Other");

    companion object {
        fun fromLabel(label: String?): ExpenseCategory =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: OTHER
    }
}

enum class SubscriptionTier {
    FREE,
    PRO_PREVIEW
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val dateOfBirth: String = "",
    val photoUrl: String = "",
    val dailyFocus: String = "",
    val motivationStyle: String = MotivationStyle.FRIENDLY.id,
    val onboardingCompleted: Boolean = false,
    val darkModeEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DailyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val completed: Boolean = false,
    val scheduledDate: String = "",
    val completedDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ExpenseEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val category: String = ExpenseCategory.FOOD.label,
    val description: String = "",
    val expenseDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PersonalGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val unit: String = "₹",
    val targetDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val progressFraction: Float
        get() = if (targetAmount > 0.0) {
            (currentAmount / targetAmount).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt()
}

@IgnoreExtraProperties
data class ActivityDay(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val completedTasksCount: Long = 0L,
    val engagementCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class StreakSummary(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val isActiveToday: Boolean = false,
    val missedYesterday: Boolean = false,
    val explanation: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
