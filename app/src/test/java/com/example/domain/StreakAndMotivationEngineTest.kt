package com.example.domain

import com.example.data.model.ActivityDay
import com.example.data.model.DailyTask
import com.example.data.model.ExpenseEntry
import com.example.data.model.MotivationStyle
import com.example.data.model.PersonalGoal
import com.example.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakAndMotivationEngineTest {

    @Test
    fun streakCalculation_consecutiveDays_returnsAccurateStreak() {
        val today = "2026-11-16"
        val activity = listOf(
            ActivityDay(dateKey = "2026-11-14", completedTasksCount = 1, engagementCount = 1),
            ActivityDay(dateKey = "2026-11-15", completedTasksCount = 2, engagementCount = 2),
            ActivityDay(dateKey = "2026-11-16", completedTasksCount = 1, engagementCount = 1)
        )
        val result = ConsistencyCalculator.calculate(today, emptyList(), activity)
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
        assertTrue(result.isActiveToday)
        assertFalse(result.missedYesterday)
    }

    @Test
    fun streakCalculation_missedYesterday_resetsFairlyWithExplanation() {
        val today = "2026-11-16"
        val activity = listOf(
            ActivityDay(dateKey = "2026-11-13", completedTasksCount = 2, engagementCount = 2),
            ActivityDay(dateKey = "2026-11-14", completedTasksCount = 1, engagementCount = 1)
        )
        val result = ConsistencyCalculator.calculate(today, emptyList(), activity)
        assertEquals(0, result.currentStreak)
        assertEquals(2, result.longestStreak)
        assertTrue(result.missedYesterday)
        assertTrue(result.explanation.contains("paused"))
    }

    @Test
    fun motivationEngine_adaptsToUserActivityAndStyle() {
        val engine = PersonalizedMotivationEngine()
        val today = "2026-11-16"
        val goal = PersonalGoal(
            id = "g1",
            title = "Save ₹10,000",
            targetAmount = 10000.0,
            currentAmount = 7500.0,
            unit = "₹"
        )
        val streak = ConsistencyCalculator.calculate(today, emptyList(), emptyList())
        val goalProfile = UserProfile(
            fullName = "Ayush",
            motivationStyle = MotivationStyle.GOAL_FOCUSED.id
        )
        val msg = engine.generate(
            profile = goalProfile,
            tasks = emptyList<DailyTask>(),
            expenses = emptyList<ExpenseEntry>(),
            goals = listOf(goal),
            streak = streak,
            todayKey = today
        )
        assertTrue(msg.contains("₹2,500"))
        assertTrue(msg.contains("Save ₹10,000"))
    }

    @Test
    fun formatInr_formatsIndianCommas() {
        assertEquals("₹120", LifeMateDateUtils.formatInr(120.0))
        assertEquals("₹10,000", LifeMateDateUtils.formatInr(10000.0))
    }
}
