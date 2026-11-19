package app.n_zik.android.ui.screens.player

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerExpansionStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `landscape and PiP recreation preserve both portrait choices`() {
        val portraitPreference = mutableStateOf(false)
        val landscape = mutableStateOf(false)
        val playerMounted = mutableStateOf(true)
        var expansion = portraitPreference
        composeRule.setContent {
            if (playerMounted.value) {
                val state = rememberPlayerExpansionState(landscape.value, portraitPreference)
                SideEffect { expansion = state }
            }
        }

        for (portraitExpanded in listOf(false, true)) {
            composeRule.runOnIdle { expansion.value = portraitExpanded }
            assertEquals(portraitExpanded, portraitPreference.value)
            composeRule.runOnIdle { landscape.value = true }
            composeRule.runOnIdle {
                assertTrue(expansion.value)
                assertEquals(portraitExpanded, portraitPreference.value)
                expansion.value = false
            }
            composeRule.runOnIdle {
                assertFalse(expansion.value)
                assertEquals(portraitExpanded, portraitPreference.value)
                landscape.value = false
            }
            composeRule.runOnIdle {
                assertEquals(portraitExpanded, expansion.value)
                landscape.value = true
            }
            composeRule.runOnIdle {
                assertTrue(expansion.value)
                playerMounted.value = false
            }
            composeRule.runOnIdle { landscape.value = false }
            composeRule.runOnIdle { playerMounted.value = true }
            composeRule.runOnIdle { assertEquals(portraitExpanded, expansion.value) }
        }
    }
}
