package com.example.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lightweight Compose callback adapter delegating to [FirebaseAuthManager].
 */
object GoogleAuthHelper {

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        val manager = FirebaseAuthManager(context = context, credentialManager = credentialManager)
        scope.launch {
            when (manager.attemptSilentSignIn()) {
                is AuthSignInOutcome.Success -> onAuthSuccess()
                else -> onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        val activity = context as? Activity
        if (activity == null) {
            onAuthError("Unable to launch Google Sign-In from current context.")
            return
        }
        val manager = FirebaseAuthManager(context = context, credentialManager = credentialManager)
        scope.launch {
            when (val outcome = manager.signInOrRegisterWithGoogle(activity)) {
                is AuthSignInOutcome.Success -> onAuthSuccess()
                is AuthSignInOutcome.Cancelled -> onAuthCancelled()
                is AuthSignInOutcome.Failure -> onAuthError(outcome.message)
            }
        }
    }

    fun signOut(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        val manager = FirebaseAuthManager(context = context, credentialManager = credentialManager)
        scope.launch {
            manager.signOut()
            onSignOutComplete()
        }
    }
}
