package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.DailyTask
import com.example.data.model.ExpenseEntry
import com.example.data.model.PersonalGoal
import com.example.data.model.StreakSummary
import com.example.data.model.UserProfile
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.GoalsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlanScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.theme.LifeMateTheme
import com.example.ui.viewmodel.LifeMateUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class PrimaryScreensComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleState() = LifeMateUiState(
        isLoading = false,
        profile = UserProfile(
            id = "user1",
            userId = "user1",
            fullName = "Ayush Sharma",
            email = "ayush@example.com",
            dailyFocus = "Study 1 hr & save ₹500",
            motivationStyle = "friendly",
            onboardingCompleted = true
        ),
        displayName = "Ayush Sharma",
        email = "ayush@example.com",
        todayTasks = listOf(
            DailyTask(id = "t1", userId = "user1", title = "Read 20 pages", scheduledDate = "2026-11-17")
        ),
        allTasks = listOf(
            DailyTask(id = "t1", userId = "user1", title = "Read 20 pages", scheduledDate = "2026-11-17")
        ),
        todayTotalCount = 1,
        expenses = listOf(
            ExpenseEntry(id = "e1", userId = "user1", amount = 120.0, category = "Food", description = "Lunch", expenseDate = "2026-11-17")
        ),
        todaySpend = 120.0,
        goals = listOf(
            PersonalGoal(id = "g1", userId = "user1", title = "Emergency Fund", targetAmount = 10000.0, currentAmount = 2500.0)
        ),
        primaryGoal = PersonalGoal(id = "g1", userId = "user1", title = "Emergency Fund", targetAmount = 10000.0, currentAmount = 2500.0),
        streakSummary = StreakSummary(currentStreak = 4, longestStreak = 6, isActiveToday = true),
        motivationMessage = "Keep going. One small completed task moves you forward."
    )

    @Test
    fun `startup configuration error screen renders cleanly`() {
        composeRule.setContent {
            LifeMateTheme {
                StartupConfigurationErrorScreen(onRetry = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("LifeMate Setup Incomplete").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun `profile setup screen renders cleanly`() {
        composeRule.setContent {
            LifeMateTheme {
                ProfileSetupScreen(
                    initialName = "Ayush",
                    initialAvatarModel = null,
                    isSaving = false,
                    onPickPhoto = {},
                    onSaveProfile = { _, _, _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_profile_setup_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `home screen renders cleanly`() {
        composeRule.setContent {
            LifeMateTheme {
                HomeScreen(
                    uiState = sampleState(),
                    onDismissWelcomeBanner = {},
                    onToggleTask = {},
                    onNavigateToPlan = {},
                    onNavigateToExpenses = {},
                    onNavigateToGoals = {},
                    onNavigateToProfile = {},
                    onQuickAddTask = {},
                    onQuickAddGoal = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_greeting_text").assertIsDisplayed()
    }

    @Test
    fun `plan screen renders cleanly`() {
        composeRule.setContent {
            LifeMateTheme {
                PlanScreen(
                    uiState = sampleState(),
                    onAddTask = { _, _, _ -> },
                    onUpdateTask = { _, _, _, _ -> },
                    onToggleTask = {},
                    onDeleteTask = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("plan_add_task_button").assertIsDisplayed()
    }

    @Test
    fun `expenses screen renders cleanly`() {
        composeRule.setContent {
            LifeMateTheme {
                ExpensesScreen(
                    uiState = sampleState(),
                    onAddExpense = { _, _, _, _ -> },
                    onUpdateExpense = { _, _, _, _, _ -> },
                    onDeleteExpense = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("expenses_add_button").assertIsDisplayed()
    }

    @Test
    fun `goals screen renders cleanly`() {
        composeRule.setContent {
            LifeMateTheme {
                GoalsScreen(
                    uiState = sampleState(),
                    onAddGoal = { _, _, _, _, _ -> },
                    onUpdateGoal = { _, _, _, _, _, _ -> },
                    onDeleteGoal = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("goals_add_button").assertIsDisplayed()
    }

    @Test
    fun `profile screen renders cleanly`() {
        composeRule.setContent {
            LifeMateTheme {
                ProfileScreen(
                    uiState = sampleState(),
                    onPickPhoto = {},
                    onUpdateProfile = { _, _, _, _ -> },
                    onToggleDarkMode = {},
                    onToggleNotifications = {},
                    onResetLocalData = {},
                    onLogout = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_name_text").assertIsDisplayed()
    }
}
