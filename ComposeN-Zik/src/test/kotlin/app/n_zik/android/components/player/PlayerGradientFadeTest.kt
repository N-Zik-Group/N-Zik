package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import app.n_zik.android.enums.AnimatedGradient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class PlayerGradientFadeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `rapid selections finish the current pair before drawing the latest effect`() {
        val requested = mutableStateOf(AnimatedGradient.Linear)
        val active = mutableStateOf(true)
        lateinit var fade: PlayerGradientFade
        compose.mainClock.autoAdvance = false
        compose.setContent { fade = rememberPlayerGradientFade(requested.value, active.value) }

        fun await(condition: () -> Boolean) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeUntil(1_000, condition)
            compose.waitForIdle()
        }

        compose.runOnIdle { requested.value = AnimatedGradient.Mesh }
        await { fade.target == AnimatedGradient.Mesh && fade.outgoing == AnimatedGradient.Linear }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertTrue(fade.alpha.value > 0f && fade.alpha.value < 1f)
            requested.value = AnimatedGradient.InkFlow
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertEquals(AnimatedGradient.Linear, fade.outgoing)
            assertEquals(AnimatedGradient.Mesh, fade.target)
        }
        await { fade.target == AnimatedGradient.InkFlow && fade.outgoing == AnimatedGradient.Mesh }
        compose.runOnIdle {
            active.value = false
            requested.value = AnimatedGradient.OilFlow
        }
        await { fade.target == AnimatedGradient.OilFlow && fade.outgoing == null }
        compose.runOnIdle {
            assertNull(fade.outgoing)
            assertEquals(1f, fade.alpha.value, 0f)
        }
    }

    @Test
    fun `fixed shader washes follow the displayed mode throughout the transition`() {
        val ink = AnimatedGradient.InkFlow
        val stage = AnimatedGradient.Stage
        assertEquals(.4f, playerShaderWash(ink, 0f).alpha, .005f)
        assertEquals(.2f, playerShaderWash(ink, .5f).alpha, .005f)
        assertEquals(0f, playerShaderWash(ink, 1f).alpha, .005f)
        assertEquals(0f, playerShaderWash(stage, 0f).alpha, .005f)
        assertEquals(.15f, playerShaderWash(stage, .5f).alpha, .005f)
        assertEquals(.3f, playerShaderWash(stage, 1f).alpha, .005f)
    }
}
