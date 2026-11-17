package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.MotivationStyle
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class WorkspaceUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String,
    val isNewRegistration: Boolean
)

sealed interface AuthSessionState {
    data object Unauthenticated : AuthSessionState
    data object Authenticating : AuthSessionState
    data class Authenticated(val user: WorkspaceUser) : AuthSessionState
    data class Error(val message: String) : AuthSessionState
}

sealed interface AuthSignInOutcome {
    data class Success(val user: WorkspaceUser) : AuthSignInOutcome
    data object Cancelled : AuthSignInOutcome
    data class Failure(val message: String) : AuthSignInOutcome
}

/**
 * Centralized Firebase Authentication & personal workspace manager.
 *
 * Uses Google Sign-In via Jetpack Credential Manager exclusively (covering both returning sign-in
 * and first-time user registration) and ensures each authenticated user has an isolated
 * Firestore workspace document under `/users/{uid}` for their tasks, expenses, and goals.
 */
class FirebaseAuthManager(
    private val context: Context,
    private val auth: FirebaseAuth = Firebase.auth,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
    ),
    private val credentialManager: CredentialManager = CredentialManager.create(context)
) {

    private val _sessionState = MutableStateFlow<AuthSessionState>(
        auth.currentUser?.toWorkspaceUser(isNewRegistration = false)
            ?.let { AuthSessionState.Authenticated(it) }
            ?: AuthSessionState.Unauthenticated
    )
    val sessionState: StateFlow<AuthSessionState> = _sessionState.asStateFlow()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing workspace.")

    fun observeAuthUser(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            trySend(user)
            if (user == null && _sessionState.value !is AuthSessionState.Authenticating) {
                _sessionState.value = AuthSessionState.Unauthenticated
            } else if (user != null) {
                val existing = (_sessionState.value as? AuthSessionState.Authenticated)?.user
                val workspaceUser = user.toWorkspaceUser(
                    isNewRegistration = existing?.uid == user.uid && existing.isNewRegistration
                )
                _sessionState.value = AuthSessionState.Authenticated(workspaceUser)
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Attempts silent sign-in for returning users without displaying an interactive prompt.
     */
    suspend fun attemptSilentSignIn(): AuthSignInOutcome {
        val existing = auth.currentUser
        if (existing != null) {
            ensurePersonalWorkspace(existing)
            val workspaceUser = existing.toWorkspaceUser(isNewRegistration = false)
            _sessionState.value = AuthSessionState.Authenticated(workspaceUser)
            return AuthSignInOutcome.Success(workspaceUser)
        }

        val clientId = resolveWebClientId() ?: return AuthSignInOutcome.Failure(
            "Google Sign-In configuration missing."
        )

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            completeGoogleCredentialExchange(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _sessionState.value = AuthSessionState.Unauthenticated
            AuthSignInOutcome.Cancelled
        }
    }

    /**
     * Launches interactive Google Sign-In / Registration via Jetpack Credential Manager.
     * On first sign-in, registers the user in Firebase Auth and creates their isolated workspace.
     */
    suspend fun signInOrRegisterWithGoogle(activity: Activity): AuthSignInOutcome {
        val clientId = resolveWebClientId() ?: run {
            val msg = "Google Sign-In configuration missing."
            _sessionState.value = AuthSessionState.Error(msg)
            return AuthSignInOutcome.Failure(msg)
        }

        _sessionState.value = AuthSessionState.Authenticating

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            completeGoogleCredentialExchange(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            _sessionState.value = AuthSessionState.Unauthenticated
            AuthSignInOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            val msg = e.localizedMessage ?: "Google Sign-In failed. Please try again."
            _sessionState.value = AuthSessionState.Error(msg)
            AuthSignInOutcome.Failure(msg)
        }
    }

    /**
     * Ensures the authenticated user has a personal Firestore workspace document under `/users/{uid}`.
     * Does not overwrite existing profile/onboarding state for returning users.
     */
    suspend fun ensurePersonalWorkspace(user: FirebaseUser): Result<Boolean> = runCatching {
        val userRef = db.collection("users").document(user.uid)
        try {
            val created = db.runTransaction { tx ->
                val snapshot = tx.get(userRef)
                if (snapshot.exists()) {
                    false
                } else {
                    val fallbackName = user.displayName?.trim()?.takeIf { it.isNotBlank() }
                        ?: user.email?.substringBefore("@")?.trim()?.takeIf { it.isNotBlank() }
                        ?: "LifeMate Member"
                    val payload = mapOf(
                        "userId" to user.uid,
                        "fullName" to fallbackName.take(80),
                        "email" to (user.email ?: "").take(160),
                        "dateOfBirth" to "",
                        "photoUrl" to (user.photoUrl?.toString() ?: "").take(1024),
                        "dailyFocus" to "",
                        "motivationStyle" to MotivationStyle.FRIENDLY.id,
                        "onboardingCompleted" to false,
                        "darkModeEnabled" to false,
                        "notificationsEnabled" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    tx.set(userRef, payload)
                    true
                }
            }.await()
            created
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }
    }

    /**
     * Clears Jetpack Credential Manager state before signing out of Firebase Auth.
     */
    suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
                _sessionState.value = AuthSessionState.Unauthenticated
            }
        }
    }

    private suspend fun completeGoogleCredentialExchange(
        credential: androidx.credentials.Credential
    ): AuthSignInOutcome {
        if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val msg = "Unexpected credential type returned."
            _sessionState.value = AuthSessionState.Error(msg)
            return AuthSignInOutcome.Failure(msg)
        }

        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val authResult = auth.signInWithCredential(authCredential).await()
        val firebaseUser = authResult.user ?: run {
            val msg = "Authentication succeeded without a Firebase user."
            _sessionState.value = AuthSessionState.Error(msg)
            return AuthSignInOutcome.Failure(msg)
        }

        val isNewAuthUser = authResult.additionalUserInfo?.isNewUser == true
        val createdWorkspace = ensurePersonalWorkspace(firebaseUser).getOrElse { false }
        val workspaceUser = firebaseUser.toWorkspaceUser(
            isNewRegistration = isNewAuthUser || createdWorkspace
        )
        _sessionState.value = AuthSessionState.Authenticated(workspaceUser)
        return AuthSignInOutcome.Success(workspaceUser)
    }

    private fun resolveWebClientId(): String? {
        return try {
            val resId = context.resources.getIdentifier(
                "default_web_client_id",
                "string",
                context.packageName
            )
            if (resId != 0) context.getString(resId).takeIf { it.isNotBlank() } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun FirebaseUser.toWorkspaceUser(isNewRegistration: Boolean): WorkspaceUser {
        return WorkspaceUser(
            uid = uid,
            displayName = displayName ?: "",
            email = email ?: "",
            photoUrl = photoUrl?.toString() ?: "",
            isNewRegistration = isNewRegistration
        )
    }

    private companion object {
        const val TAG = "FirebaseAuthManager"
    }
}
