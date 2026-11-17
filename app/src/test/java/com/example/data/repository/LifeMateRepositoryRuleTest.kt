package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeMateRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun profileAndTasks_ownerCrud_succeedsAndSyncs() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = LifeMateRepository(firestore)

        val profileResult = withTimeout(TIMEOUT_MS) {
            repository.saveUserProfile(
                userId = uid,
                fullName = "Alice Tester",
                email = ALICE_EMAIL,
                dateOfBirth = "1999-04-12",
                photoUrl = "",
                dailyFocus = "Finish daily sprint",
                motivationStyle = "goal_focused",
                onboardingCompleted = true,
                darkModeEnabled = true,
                notificationsEnabled = false
            )
        }
        assertTrue(profileResult.isSuccess)

        val taskId = withTimeout(TIMEOUT_MS) {
            repository.addTask(
                userId = uid,
                title = "Study for 1 hour",
                notes = "Kotlin coroutines",
                scheduledDate = "2026-11-16"
            ).getOrThrow()
        }
        assertTrue(taskId.isNotBlank())

        val tasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks(uid).first { list -> list.any { it.id == taskId } }
        }
        val createdTask = tasks.first { it.id == taskId }
        assertEquals("Study for 1 hour", createdTask.title)

        val toggleResult = withTimeout(TIMEOUT_MS) {
            repository.toggleTaskCompletion(uid, createdTask, "2026-11-16")
        }
        assertTrue(toggleResult.isSuccess)

        val updatedTasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks(uid).first { list -> list.any { it.id == taskId && it.completed } }
        }
        assertTrue(updatedTasks.first { it.id == taskId }.completed)
    }

    @Test
    fun expensesAndGoals_ownerCrud_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = LifeMateRepository(firestore)

        val expId = withTimeout(TIMEOUT_MS) {
            repository.addExpense(
                userId = uid,
                amount = 120.0,
                category = "Food",
                description = "Healthy lunch",
                expenseDate = "2026-11-16"
            ).getOrThrow()
        }
        val expenses = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeExpenses(uid).first { list -> list.any { it.id == expId } }
        }
        assertEquals(120.0, expenses.first { it.id == expId }.amount, 0.01)

        val goalId = withTimeout(TIMEOUT_MS) {
            repository.addGoal(
                userId = uid,
                title = "Save ₹10,000",
                targetAmount = 10000.0,
                currentAmount = 4500.0,
                unit = "₹",
                targetDate = "2026-12-31",
                todayKey = "2026-11-16"
            ).getOrThrow()
        }
        val goals = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeGoals(uid).first { list -> list.any { it.id == goalId } }
        }
        assertEquals(45, goals.first { it.id == goalId }.progressPercent)
    }

    @Test
    fun observeTasks_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = LifeMateRepository(firestore)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.addTask(aliceUid, "Alice Private Task", "", "2026-11-16").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = LifeMateRepository(firestore)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeTasks(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val fsError = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
    }

    @Test
    fun observeTasks_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = LifeMateRepository(firestore)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeTasks("some_uid_123").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val fsError = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.lifemate@test.com"
        const val BOB_EMAIL = "bob.lifemate@test.com"
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
