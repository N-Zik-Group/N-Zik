package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuickPicksInitialLoadEffectTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `authenticated initial resolution and subsequent recompositions fetch once`() {
        val loggedIn = mutableStateOf(false)
        val renderedEmission = mutableIntStateOf(0)
        val stateKey = Any()
        var fetches = 0
        var accountReads = 0

        composeRule.setContent {
            Text("${loggedIn.value}/${renderedEmission.intValue}")
            QuickPicksInitialLoadEffect(
                stateKey = stateKey,
                resolveLoggedIn = { accountReads++; true },
                onLoggedInResolved = { loggedIn.value = it },
                load = { fetches++ },
            )
        }
        composeRule.waitForIdle()
        repeat(3) {
            composeRule.runOnIdle { renderedEmission.intValue++ }
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle {
            assertEquals(true, loggedIn.value)
            assertEquals(1, accountReads)
            assertEquals(1, fetches)
        }
    }
}
