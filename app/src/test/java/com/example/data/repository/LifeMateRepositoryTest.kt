package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LifeMateDatabase
import com.example.data.model.MotivationStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LifeMateRepositoryTest {

    private lateinit var database: LifeMateDatabase
    private lateinit var repository: LifeMateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LifeMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LifeMateRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `workspaces isolate tasks expenses and goals per user`() = runTest {
        repository.saveUserProfile(
            userId = "userA",
            fullName = "Alice",
            email = "",
            dateOfBirth = "",
            photoUrl = "",
            dailyFocus = "Deep work",
            motivationStyle = MotivationStyle.FRIENDLY.id,
            onboardingCompleted = true,
            darkModeEnabled = false,
            notificationsEnabled = false
        ).getOrThrow()

        repository.addTask("userA", "Alice Task", "Notes", "2026-11-17").getOrThrow()
        repository.addExpense("userA", 250.0, "Food", "Lunch", "2026-11-17").getOrThrow()
        repository.addGoal("userA", "Laptop", 50000.0, 5000.0, "₹", "2026-12-31", "2026-11-17").getOrThrow()

        assertEquals(1, repository.observeTasks("userA").first().size)
        assertEquals(1, repository.observeExpenses("userA").first().size)
        assertEquals(1, repository.observeGoals("userA").first().size)

        assertTrue(repository.observeTasks("userB").first().isEmpty())
        assertTrue(repository.observeExpenses("userB").first().isEmpty())
        assertTrue(repository.observeGoals("userB").first().isEmpty())
    }

    @Test
    fun `toggling task completion updates task status and daily activity log`() = runTest {
        val taskId = repository.addTask("user1", "Meditate", "10 mins", "2026-11-17").getOrThrow()
        val initialTask = repository.observeTasks("user1").first().single { it.id == taskId }
        assertEquals(false, initialTask.completed)

        repository.toggleTaskCompletion("user1", initialTask, "2026-11-17").getOrThrow()

        val completedTask = repository.observeTasks("user1").first().single { it.id == taskId }
        assertTrue(completedTask.completed)
        assertEquals("2026-11-17", completedTask.completedDate)

        val activity = repository.observeActivityDays("user1").first().single { it.dateKey == "2026-11-17" }
        assertEquals(1L, activity.completedTasksCount)
        assertEquals(1L, activity.engagementCount)
    }

    @Test
    fun `expense and goal CRUD operations persist cleanly`() = runTest {
        val expenseId = repository.addExpense("user1", 100.0, "Food", "Coffee", "2026-11-17").getOrThrow()
        repository.updateExpense("user1", expenseId, 150.0, "Food", "Coffee & snack", "2026-11-17").getOrThrow()
        val updatedExpense = repository.observeExpenses("user1").first().single()
        assertEquals(150.0, updatedExpense.amount, 0.001)
        assertEquals("Coffee & snack", updatedExpense.description)

        repository.deleteExpense("user1", expenseId).getOrThrow()
        assertTrue(repository.observeExpenses("user1").first().isEmpty())

        val goalId = repository.addGoal("user1", "Read Books", 24.0, 2.0, "books", "2026-12-31", "2026-11-17").getOrThrow()
        repository.updateGoal("user1", goalId, "Read Books", 24.0, 12.0, "books", "2026-12-31", "2026-11-17").getOrThrow()
        val updatedGoal = repository.observeGoals("user1").first().single()
        assertEquals(50, updatedGoal.progressPercent)
    }
}
