package com.example.domain

import com.example.data.model.ActivityDay
import com.example.data.model.DailyTask
import com.example.data.model.ExpenseEntry
import com.example.data.model.MotivationStyle
import com.example.data.model.PersonalGoal
import com.example.data.model.StreakSummary
import com.example.data.model.UserProfile
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

object LifeMateDateUtils {
    private val keyFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    private val displayDateFormat: SimpleDateFormat
        get() = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

    private val shortDateFormat: SimpleDateFormat
        get() = SimpleDateFormat("dd MMM", Locale.getDefault())

    fun todayKey(nowMillis: Long = System.currentTimeMillis()): String {
        return keyFormat.format(Date(nowMillis))
    }

    fun formatDisplayDate(nowMillis: Long = System.currentTimeMillis()): String {
        return displayDateFormat.format(Date(nowMillis))
    }

    fun formatShortDate(dateKey: String): String {
        val parsed = parseDateKey(dateKey) ?: return dateKey
        return shortDateFormat.format(parsed)
    }

    fun parseDateKey(dateKey: String): Date? {
        if (dateKey.isBlank()) return null
        return try {
            keyFormat.parse(dateKey)
        } catch (_: Exception) {
            null
        }
    }

    fun addDays(dateKey: String, deltaDays: Int): String {
        val date = parseDateKey(dateKey) ?: return dateKey
        val cal = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, deltaDays)
        }
        return keyFormat.format(cal.time)
    }

    fun startOfWeekKey(todayKey: String): String {
        val date = parseDateKey(todayKey) ?: return todayKey
        val cal = Calendar.getInstance().apply {
            time = date
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val candidate = keyFormat.format(cal.time)
        // If today is Sunday on Locale where MONDAY > today, step back 7 days
        return if (candidate > todayKey) addDays(candidate, -7) else candidate
    }

    fun startOfMonthKey(todayKey: String): String {
        return if (todayKey.length >= 7) "${todayKey.substring(0, 7)}-01" else todayKey
    }

    fun greetingPrefix(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
        return when (hourOfDay) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun formatInr(amount: Double): String {
        val indianLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
        val formatter = NumberFormat.getNumberInstance(indianLocale).apply {
            maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = 0
        }
        return "₹${formatter.format(amount)}"
    }

    fun formatGoalValue(value: Double, unit: String): String {
        return if (unit.trim() == "₹") {
            formatInr(value)
        } else {
            val formattedNum = if (value % 1.0 == 0.0) {
                value.roundToLong().toString()
            } else {
                String.format(Locale.US, "%.1f", value)
            }
            "$formattedNum $unit"
        }
    }
}

object ConsistencyCalculator {
    fun calculate(
        todayKey: String,
        tasks: List<DailyTask>,
        activityDays: List<ActivityDay>
    ): StreakSummary {
        val activeDates = mutableSetOf<String>()

        activityDays.forEach { day ->
            if (day.dateKey.isNotBlank() && (day.completedTasksCount > 0L || day.engagementCount > 0L)) {
                activeDates.add(day.dateKey)
            }
        }

        tasks.forEach { task ->
            if (task.completed && task.completedDate.isNotBlank()) {
                activeDates.add(task.completedDate)
            }
        }

        if (activeDates.isEmpty()) {
            return StreakSummary(
                currentStreak = 0,
                longestStreak = 0,
                isActiveToday = false,
                missedYesterday = false,
                explanation = "Complete your first task today to start your consistency streak."
            )
        }

        val sortedDates = activeDates.sorted()
        var longest = 0
        var run = 0
        var prevDate: String? = null
        for (date in sortedDates) {
            if (prevDate != null && LifeMateDateUtils.addDays(prevDate, 1) == date) {
                run += 1
            } else {
                run = 1
            }
            if (run > longest) longest = run
            prevDate = date
        }

        val activeToday = todayKey in activeDates
        val yesterdayKey = LifeMateDateUtils.addDays(todayKey, -1)
        val activeYesterday = yesterdayKey in activeDates

        return when {
            activeToday -> {
                var streak = 0
                var cursor = todayKey
                while (cursor in activeDates) {
                    streak += 1
                    cursor = LifeMateDateUtils.addDays(cursor, -1)
                }
                StreakSummary(
                    currentStreak = streak,
                    longestStreak = longest,
                    isActiveToday = true,
                    missedYesterday = false,
                    explanation = if (streak == 1) {
                        "Day 1 logged today! Show up tomorrow to build momentum."
                    } else {
                        "$streak consecutive days of progress. Your consistency is paying off."
                    }
                )
            }
            activeYesterday -> {
                var streak = 0
                var cursor = yesterdayKey
                while (cursor in activeDates) {
                    streak += 1
                    cursor = LifeMateDateUtils.addDays(cursor, -1)
                }
                StreakSummary(
                    currentStreak = streak,
                    longestStreak = longest,
                    isActiveToday = false,
                    missedYesterday = false,
                    explanation = "Your $streak-day streak is intact—complete 1 task today to extend it to ${streak + 1} days."
                )
            }
            else -> {
                val lastActive = sortedDates.last()
                val formattedMissed = LifeMateDateUtils.formatShortDate(yesterdayKey)
                val formattedLast = LifeMateDateUtils.formatShortDate(lastActive)
                StreakSummary(
                    currentStreak = 0,
                    longestStreak = longest,
                    isActiveToday = false,
                    missedYesterday = true,
                    explanation = "Your streak paused because no activity was logged on $formattedMissed (last active $formattedLast). One small task today starts a fresh streak."
                )
            }
        }
    }
}

interface MotivationProvider {
    fun generate(
        profile: UserProfile?,
        tasks: List<DailyTask>,
        expenses: List<ExpenseEntry>,
        goals: List<PersonalGoal>,
        streak: StreakSummary,
        todayKey: String
    ): String
}

class PersonalizedMotivationEngine : MotivationProvider {
    override fun generate(
        profile: UserProfile?,
        tasks: List<DailyTask>,
        expenses: List<ExpenseEntry>,
        goals: List<PersonalGoal>,
        streak: StreakSummary,
        todayKey: String
    ): String {
        val style = MotivationStyle.fromId(profile?.motivationStyle)
        val yesterdayKey = LifeMateDateUtils.addDays(todayKey, -1)

        val todayTasks = tasks.filter { it.scheduledDate == todayKey }
        val completedToday = todayTasks.count { it.completed }
        val totalToday = todayTasks.size

        val missedYesterdayCount = tasks.count { it.scheduledDate == yesterdayKey && !it.completed }
        val todaySpend = expenses.filter { it.expenseDate == todayKey }.sumOf { it.amount }
        val topActiveGoal = goals.firstOrNull { it.currentAmount < it.targetAmount && it.targetAmount > 0.0 }

        // 1. All today's tasks completed (with at least 1 task)
        if (totalToday > 0 && completedToday == totalToday) {
            return when (style) {
                MotivationStyle.FRIENDLY ->
                    "All $totalToday tasks done today! Take a breath and enjoy the win."
                MotivationStyle.STRICT ->
                    "$completedToday/$totalToday completed. Standards met—set tomorrow's priorities next."
                MotivationStyle.GOAL_FOCUSED ->
                    if (topActiveGoal != null) {
                        val remaining = (topActiveGoal.targetAmount - topActiveGoal.currentAmount).coerceAtLeast(0.0)
                        val remStr = LifeMateDateUtils.formatGoalValue(remaining, topActiveGoal.unit)
                        "100% of today's plan finished. You're $remStr away from \"${topActiveGoal.title}\"."
                    } else {
                        "Today's target is 100% complete. That discipline compounds every week."
                    }
            }
        }

        // 2. Missed tasks yesterday or paused streak (always supportive, never shaming)
        if (streak.missedYesterday || missedYesterdayCount > 0) {
            return when (style) {
                MotivationStyle.FRIENDLY ->
                    "Yesterday didn't go as planned. That's okay. Start with one small task today."
                MotivationStyle.STRICT ->
                    "Reset today. Pick your most important task and finish it before checking distractions."
                MotivationStyle.GOAL_FOCUSED ->
                    if (topActiveGoal != null) {
                        val remaining = (topActiveGoal.targetAmount - topActiveGoal.currentAmount).coerceAtLeast(0.0)
                        val remStr = LifeMateDateUtils.formatGoalValue(remaining, topActiveGoal.unit)
                        "Yesterday is behind you. One focused step today moves you closer—$remStr left on \"${topActiveGoal.title}\"."
                    } else {
                        "A missed day doesn't erase your progress. Complete one task now to rebuild momentum."
                    }
            }
        }

        // 3. Goal-focused nudge when an active goal is close or active
        if (style == MotivationStyle.GOAL_FOCUSED && topActiveGoal != null) {
            val remaining = (topActiveGoal.targetAmount - topActiveGoal.currentAmount).coerceAtLeast(0.0)
            val remStr = LifeMateDateUtils.formatGoalValue(remaining, topActiveGoal.unit)
            return "You're $remStr away from \"${topActiveGoal.title}\". Keep moving."
        }

        // 4. Active streak momentum
        if (streak.currentStreak >= 2) {
            return when (style) {
                MotivationStyle.FRIENDLY ->
                    "${streak.currentStreak} days completed. Keep the momentum going—one task at a time."
                MotivationStyle.STRICT ->
                    "${streak.currentStreak}-day streak active. Protect it by finishing your remaining ${totalToday - completedToday} task(s)."
                MotivationStyle.GOAL_FOCUSED ->
                    "${streak.currentStreak} consecutive days on track. Consistency turns targets into results."
            }
        }

        // 5. Partial progress today
        if (completedToday > 0 && totalToday > completedToday) {
            return when (style) {
                MotivationStyle.FRIENDLY ->
                    "Nice start—$completedToday of $totalToday done. Keep going, one small completed task moves you forward."
                MotivationStyle.STRICT ->
                    "$completedToday/$totalToday complete. Finish the remaining ${totalToday - completedToday} before wrapping up."
                MotivationStyle.GOAL_FOCUSED ->
                    "Progress is at ${(completedToday * 100) / totalToday}%. One more checked task pushes today over the line."
            }
        }

        // 6. Expense awareness if spending happened today with zero tasks yet
        if (todaySpend > 0.0 && completedToday == 0) {
            val spentStr = LifeMateDateUtils.formatInr(todaySpend)
            return when (style) {
                MotivationStyle.FRIENDLY ->
                    "You've logged $spentStr in spending today. Check off one quick task to balance your day."
                MotivationStyle.STRICT ->
                    "$spentStr logged today, 0 tasks done. Balance your day by completing your first task now."
                MotivationStyle.GOAL_FOCUSED ->
                    if (topActiveGoal != null) {
                        val remaining = (topActiveGoal.targetAmount - topActiveGoal.currentAmount).coerceAtLeast(0.0)
                        val remStr = LifeMateDateUtils.formatGoalValue(remaining, topActiveGoal.unit)
                        "$spentStr spent today—keep $remStr away from \"${topActiveGoal.title}\" in focus."
                    } else {
                        "$spentStr tracked today. Complete one task to make today count."
                    }
            }
        }

        // 7. Zero activity / fresh day
        val focusHint = profile?.dailyFocus?.takeIf { it.isNotBlank() }
        return when (style) {
            MotivationStyle.FRIENDLY ->
                "Your plan is waiting for you. Start with just one small task today."
            MotivationStyle.STRICT ->
                if (focusHint != null) {
                    "Focus: $focusHint. Action beats intention—complete your first task now."
                } else {
                    "Zero tasks checked yet. Pick one priority and execute it first."
                }
            MotivationStyle.GOAL_FOCUSED ->
                if (topActiveGoal != null) {
                    val remaining = (topActiveGoal.targetAmount - topActiveGoal.currentAmount).coerceAtLeast(0.0)
                    val remStr = LifeMateDateUtils.formatGoalValue(remaining, topActiveGoal.unit)
                    "You're $remStr away from \"${topActiveGoal.title}\". Take one step today."
                } else {
                    "Every milestone starts with a single checked task. Add or complete one today."
                }
        }
    }
}
