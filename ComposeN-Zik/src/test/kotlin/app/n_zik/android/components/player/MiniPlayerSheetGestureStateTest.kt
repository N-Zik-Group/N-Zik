package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MiniPlayerSheetGestureStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `positive frames do not recompose and negative dismissal stays distinct from rest`() {
        val progress = mutableFloatStateOf(0f)
        val observations = mutableListOf<MiniPlayerSheetGestureState>()
        composeRule.setContent {
            val gesture = rememberMiniPlayerSheetGestureState { progress.floatValue }.value
            SideEffect { observations += gesture }
        }
        fun move(value: Float) {
            composeRule.runOnIdle { progress.floatValue = value }
            composeRule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        move(0.01f)
        move(0.3f)
        move(1f)
        move(0.5f)
        move(-0.01f)
        move(-0.5f)
        move(0f)
        composeRule.runOnIdle {
            assertEquals(listOf(
                MiniPlayerSheetGestureState(false, true),
                MiniPlayerSheetGestureState(true, false),
                MiniPlayerSheetGestureState(false, false),
                MiniPlayerSheetGestureState(false, true),
            ), observations)
        }
    }
}
