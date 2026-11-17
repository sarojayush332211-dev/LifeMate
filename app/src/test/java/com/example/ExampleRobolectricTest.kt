package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LifeMate", appName)
    }

    @Test
    fun `main activity launches welcome screen and opens profile setup cleanly without firebase`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag("get_started_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("LifeMate").assertIsDisplayed()
            composeRule.onNodeWithText("LifeMate Setup Incomplete").assertDoesNotExist()
            composeRule.onNodeWithTag("get_started_button").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("get_started_button").performClick()

            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithTag("save_profile_setup_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("save_profile_setup_button").performScrollTo().assertIsDisplayed()
        }
    }
}
