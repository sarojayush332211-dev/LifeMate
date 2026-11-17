package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAuthManagerRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensurePersonalWorkspace_initializesIsolatedUserDocOnceAndPreservesReturningUser() = runBlocking {
        val uid = signInTestUser("workspace.owner@test.com")
        val firebaseUser = checkNotNull(auth.currentUser)
        val context = ApplicationProvider.getApplicationContext<Context>()

        val authManager = FirebaseAuthManager(
            context = context,
            auth = auth,
            db = firestore,
            credentialManager = CredentialManager.create(context)
        )

        assertEquals(uid, authManager.requireUserId())

        val firstCall = withTimeout(5000L) {
            authManager.ensurePersonalWorkspace(firebaseUser)
        }
        assertTrue(firstCall.isSuccess)

        val secondCall = withTimeout(5000L) {
            authManager.ensurePersonalWorkspace(firebaseUser)
        }
        assertTrue(secondCall.isSuccess)
        assertFalse(secondCall.getOrThrow())
    }
}
