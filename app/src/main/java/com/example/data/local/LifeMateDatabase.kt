package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ActivityDay
import com.example.data.model.DailyTask
import com.example.data.model.ExpenseEntry
import com.example.data.model.PersonalGoal
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "users")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val fullName: String,
    val email: String,
    val dateOfBirth: String,
    val photoUrl: String,
    val dailyFocus: String,
    val motivationStyle: String,
    val onboardingCompleted: Boolean,
    val darkModeEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["userId"])]
)
data class DailyTaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val notes: String,
    val completed: Boolean,
    val scheduledDate: String,
    val completedDate: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["userId"])]
)
data class ExpenseEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val amount: Double,
    val category: String,
    val description: String,
    val expenseDate: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "goals",
    indices = [Index(value = ["userId"])]
)
data class PersonalGoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val unit: String,
    val targetDate: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "activity_days",
    indices = [Index(value = ["userId", "dateKey"], unique = true)]
)
data class ActivityDayEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateKey: String,
    val completedTasksCount: Long,
    val engagementCount: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    id = id,
    userId = userId,
    fullName = fullName,
    email = email,
    dateOfBirth = dateOfBirth,
    photoUrl = photoUrl,
    dailyFocus = dailyFocus,
    motivationStyle = motivationStyle,
    onboardingCompleted = onboardingCompleted,
    darkModeEnabled = darkModeEnabled,
    notificationsEnabled = notificationsEnabled,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

fun DailyTaskEntity.toDomain(): DailyTask = DailyTask(
    id = id,
    userId = userId,
    title = title,
    notes = notes,
    completed = completed,
    scheduledDate = scheduledDate,
    completedDate = completedDate,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

fun ExpenseEntryEntity.toDomain(): ExpenseEntry = ExpenseEntry(
    id = id,
    userId = userId,
    amount = amount,
    category = category,
    description = description,
    expenseDate = expenseDate,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

fun PersonalGoalEntity.toDomain(): PersonalGoal = PersonalGoal(
    id = id,
    userId = userId,
    title = title,
    targetAmount = targetAmount,
    currentAmount = currentAmount,
    unit = unit,
    targetDate = targetDate,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

fun ActivityDayEntity.toDomain(): ActivityDay = ActivityDay(
    id = id,
    userId = userId,
    dateKey = dateKey,
    completedTasksCount = completedTasksCount,
    engagementCount = engagementCount,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

@Dao
interface LifeMateDao {

    @Query("SELECT * FROM users ORDER BY updatedAtMillis DESC")
    fun observeAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    fun observeUserProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserProfile(userId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    @Query("UPDATE users SET darkModeEnabled = :darkModeEnabled, updatedAtMillis = :updatedAtMillis WHERE userId = :userId")
    suspend fun updateThemePreference(userId: String, darkModeEnabled: Boolean, updatedAtMillis: Long)

    @Query("UPDATE users SET notificationsEnabled = :enabled, updatedAtMillis = :updatedAtMillis WHERE userId = :userId")
    suspend fun updateNotificationsPreference(userId: String, enabled: Boolean, updatedAtMillis: Long)

    @Query("SELECT * FROM tasks WHERE userId = :userId ORDER BY completed ASC, createdAtMillis DESC")
    fun observeTasks(userId: String): Flow<List<DailyTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: DailyTaskEntity)

    @Query("UPDATE tasks SET title = :title, notes = :notes, scheduledDate = :scheduledDate, updatedAtMillis = :updatedAtMillis WHERE id = :taskId AND userId = :userId")
    suspend fun updateTaskFields(
        userId: String,
        taskId: String,
        title: String,
        notes: String,
        scheduledDate: String,
        updatedAtMillis: Long
    )

    @Query("UPDATE tasks SET completed = :completed, completedDate = :completedDate, updatedAtMillis = :updatedAtMillis WHERE id = :taskId AND userId = :userId")
    suspend fun updateTaskCompletion(
        userId: String,
        taskId: String,
        completed: Boolean,
        completedDate: String,
        updatedAtMillis: Long
    )

    @Query("DELETE FROM tasks WHERE id = :taskId AND userId = :userId")
    suspend fun deleteTask(userId: String, taskId: String)

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY expenseDate DESC, createdAtMillis DESC")
    fun observeExpenses(userId: String): Flow<List<ExpenseEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: ExpenseEntryEntity)

    @Query("UPDATE expenses SET amount = :amount, category = :category, description = :description, expenseDate = :expenseDate, updatedAtMillis = :updatedAtMillis WHERE id = :expenseId AND userId = :userId")
    suspend fun updateExpenseFields(
        userId: String,
        expenseId: String,
        amount: Double,
        category: String,
        description: String,
        expenseDate: String,
        updatedAtMillis: Long
    )

    @Query("DELETE FROM expenses WHERE id = :expenseId AND userId = :userId")
    suspend fun deleteExpense(userId: String, expenseId: String)

    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observeGoals(userId: String): Flow<List<PersonalGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: PersonalGoalEntity)

    @Query("UPDATE goals SET title = :title, targetAmount = :targetAmount, currentAmount = :currentAmount, unit = :unit, targetDate = :targetDate, updatedAtMillis = :updatedAtMillis WHERE id = :goalId AND userId = :userId")
    suspend fun updateGoalFields(
        userId: String,
        goalId: String,
        title: String,
        targetAmount: Double,
        currentAmount: Double,
        unit: String,
        targetDate: String,
        updatedAtMillis: Long
    )

    @Query("DELETE FROM goals WHERE id = :goalId AND userId = :userId")
    suspend fun deleteGoal(userId: String, goalId: String)

    @Query("SELECT * FROM activity_days WHERE userId = :userId ORDER BY dateKey ASC")
    fun observeActivityDays(userId: String): Flow<List<ActivityDayEntity>>

    @Query("SELECT * FROM activity_days WHERE userId = :userId AND dateKey = :dateKey LIMIT 1")
    suspend fun getActivityDay(userId: String, dateKey: String): ActivityDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivityDay(day: ActivityDayEntity)
}

@Database(
    entities = [
        UserProfileEntity::class,
        DailyTaskEntity::class,
        ExpenseEntryEntity::class,
        PersonalGoalEntity::class,
        ActivityDayEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LifeMateDatabase : RoomDatabase() {
    abstract fun lifeMateDao(): LifeMateDao

    companion object {
        @Volatile
        private var INSTANCE: LifeMateDatabase? = null

        fun getInstance(context: Context): LifeMateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LifeMateDatabase::class.java,
                    "lifemate.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
