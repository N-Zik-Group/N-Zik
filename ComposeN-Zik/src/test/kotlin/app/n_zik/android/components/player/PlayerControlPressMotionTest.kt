package app.n_zik.android.components.player

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.buttonzoomoutKey
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerControlPressMotionTest {
    @get:Rule val compose = createComposeRule()
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences
    @Before fun setup() { preferences.edit().putBoolean(buttonzoomoutKey, true).commit() }
    @After fun cleanup() { preferences.edit().remove(buttonzoomoutKey).commit() }

    @Test fun `one press shrinks both surface and glyph and cancel restores them`() {
        val source = MutableInteractionSource()
        val enabled = mutableStateOf(true)
        compose.setContent {
            Box(Modifier.size(100.dp, 60.dp).playerControlPressMotion(source, enabled.value)) {
                Box(Modifier.fillMaxSize().background(Color.Red).testTag("surface"))
                Box(Modifier.align(Alignment.Center).size(24.dp).background(Color.White).testTag("glyph"))
            }
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val surface = bounds("surface")
        val glyph = bounds("glyph")
        compose.mainClock.autoAdvance = false
        val press = PressInteraction.Press(Offset(10f, 10f))
        compose.runOnIdle { source.tryEmit(press) }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(surface.width * 0.8f, bounds("surface").width, 1f)
        assertEquals(glyph.width * 0.8f, bounds("glyph").width, 1f)
        assertEquals(bounds("surface").center.x, bounds("glyph").center.x, 0.01f)
        assertEquals(bounds("surface").center.y, bounds("glyph").center.y, 0.01f)
        compose.runOnIdle { source.tryEmit(PressInteraction.Cancel(press)) }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(surface.width, bounds("surface").width, 1f)
        val hoverPress = PressInteraction.Press(Offset.Zero)
        compose.runOnIdle { source.tryEmit(hoverPress) }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { source.tryEmit(PressInteraction.Release(hoverPress)) }
        compose.mainClock.advanceTimeByFrame()
        val hover = HoverInteraction.Enter()
        compose.runOnIdle {
            source.tryEmit(hover)
            source.tryEmit(HoverInteraction.Exit(hover))
        }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("Hover must not cancel the return animation", surface.width, bounds("surface").width, 1f)
        val nextPress = PressInteraction.Press(Offset.Zero)
        compose.runOnIdle {
            enabled.value = false
            source.tryEmit(nextPress)
        }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(surface.width, bounds("surface").width, 1f)
    }
}
