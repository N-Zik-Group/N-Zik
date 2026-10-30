package app.n_zik.android.components.settings.common

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsSearchVisibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun fullyFadedContentReleasesSpaceAndCannotBeClickedOrFoundInSemantics() {
        val focused = mutableStateOf(false)
        val query = mutableStateOf("")
        var clicks = 0
        composeRule.setContent {
            val focus = rememberSettingsSearchFocus(focused.value, query.value.isBlank())
            Box(Modifier.testTag("space")) {
                settingsSearchContent(focus) {
                    Box(Modifier.size(80.dp).testTag("action").clickable { clicks++ })
                }
            }
        }
        val bounds = composeRule.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("action").performClick()
        assertEquals(1, clicks)
        composeRule.runOnIdle { focused.value = true }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("space").assertHeightIsEqualTo(0.dp)
        composeRule.onNodeWithTag("action").assertDoesNotExist()
        composeRule.onNodeWithTag("action", useUnmergedTree = true).assertIsNotDisplayed()
        composeRule.onRoot().performTouchInput { click(Offset(bounds.center.x, bounds.center.y)) }
        composeRule.runOnIdle { assertEquals(1, clicks); query.value = "result" }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("action").performClick()
        composeRule.runOnIdle { assertEquals(2, clicks) }
    }
}
