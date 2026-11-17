package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FirebaseApp.getApps(context).toList().forEach { app ->
            runCatching { app.delete() }
        }
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LifeMate", appName)
    }

    @Test
    fun `main activity starts cleanly and recovers if firebase was not yet initialized`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FirebaseApp.getApps(context).toList().forEach { it.delete() }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("LifeMate").assertIsDisplayed()
            composeRule.onNodeWithTag("continue_with_google_button").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `main activity renders login screen cleanly when firebase is initialized`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:12657824765:android:5f1155fddd5d97e1287f94")
                    .setProjectId("gen-lang-client-0123959303")
                    .setApiKey("test-api-key-placeholder")
                    .build()
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("LifeMate").assertIsDisplayed()
            composeRule.onNodeWithTag("continue_with_google_button").performScrollTo().assertIsDisplayed()
        }
    }
}
