package com.example.auth

import com.example.data.local.LocalPreferencesStore
import com.example.data.model.MotivationStyle
import com.example.data.model.UserProfile
import com.example.data.repository.LifeMateRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed interface LocalSessionState {
    data object Loading : LocalSessionState
    data object SignedOut : LocalSessionState
    data class Authenticated(val userId: String) : LocalSessionState
}

/**
 * Manages isolated per-user local workspaces using DataStore + Room without any external cloud
 * or Firebase configuration requirements.
 */
class LocalAccountManager(
    private val repository: LifeMateRepository,
    private val prefsStore: LocalPreferencesStore
) {
    val activeUserIdFlow: Flow<String?> = prefsStore.activeUserIdFlow

    val existingProfilesFlow: Flow<List<UserProfile>> = repository.observeAllProfiles()

    suspend fun createNewWorkspace(
        initialName: String = "",
        email: String = ""
    ): String {
        val userId = "local_${UUID.randomUUID()}"
        repository.saveUserProfile(
            userId = userId,
            fullName = initialName.trim(),
            email = email.trim(),
            dateOfBirth = "",
            photoUrl = "",
            dailyFocus = "",
            motivationStyle = MotivationStyle.FRIENDLY.id,
            onboardingCompleted = false,
            darkModeEnabled = false,
            notificationsEnabled = false
        ).getOrThrow()
        prefsStore.setActiveUserId(userId)
        return userId
    }

    suspend fun selectWorkspace(userId: String) {
        prefsStore.setActiveUserId(userId)
    }

    suspend fun signOut() {
        prefsStore.setActiveUserId(null)
    }
}
