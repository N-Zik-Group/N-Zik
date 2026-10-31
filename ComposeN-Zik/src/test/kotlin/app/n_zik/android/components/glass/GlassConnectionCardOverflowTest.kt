package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.listentogether.ConnectionStatusCard
import app.n_zik.android.listentogether.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w480dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassConnectionCardOverflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `actual connected card keeps both complete button panes outside animated seats and opaque neighbors`() {
        val extraHeight = mutableStateOf(0.dp)
        var reconnect = ""
        var disconnect = ""
        var reconnects = 0
        var disconnects = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            val palette = DefaultDarkColorPalette.copy(background2 = Color.Blue, accent = Color.Green)
            val settings = GlassSettings(enabled = true, quality = GlassQuality.Performance,
                tintStrength = 0f, film = 1f, rim = 0f)
            reconnect = stringResource(R.string.listen_together_reconnect)
            disconnect = stringResource(R.string.listen_together_disconnect)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                palette, Typography.fallback, shape, shape, shape,
            ), LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Box(Modifier.size(440.dp, 400.dp).background(Color.Black).testTag("scene")) {
                        GlassControlOverflowColumn(Modifier.padding(start = 40.dp).width(360.dp)) {
                            // The actual connection card contains another animated owner around
                            // Reconnect/Disconnect. Exercise nested size changes without replacing it.
                            Column(Modifier.fillMaxWidth().glassAwareContentSize(settings = settings)) {
                                ConnectionStatusCard(ConnectionState.CONNECTED, {},
                                    onDisconnect = { disconnects++ }, onReconnect = { reconnects++ })
                                Spacer(Modifier.height(extraHeight.value))
                            }
                        }
                        // A raised real card must overdraw its sibling, not disappear under it.
                        Box(Modifier.align(Alignment.TopStart).width(70.dp).fillMaxHeight()
                            .zIndex(2f).background(Color.Black))
                        Box(Modifier.align(Alignment.TopEnd).width(54.dp).fillMaxHeight()
                            .zIndex(2f).background(Color.Black))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val action = compose.onNodeWithText(reconnect)
        val seat = action.fetchSemanticsNode().boundsInRoot
        val disconnectAction = compose.onNodeWithText(disconnect)
        val disconnectSeat = disconnectAction.fetchSemanticsNode().boundsInRoot
        fun materialPane(blue: Boolean): Rect {
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            val ink = buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    // The green status dot/header share Disconnect's role color. Restrict
                    // that sample to the actual button's vertical band and right-hand seat.
                    if (!blue && (x < disconnectSeat.left - 8f || y < disconnectSeat.top - 8f ||
                        y > disconnectSeat.bottom + 8f)) continue
                    val c = pixels[x, y]
                    if (if (blue) c.blue > .75f && c.red < .3f && c.green < .3f
                        else c.green > .75f && c.red < .3f && c.blue < .3f) add(x to y)
                }
            }
            assertTrue("The real ${if (blue) "Reconnect" else "Disconnect"} material must paint", ink.isNotEmpty())
            return Rect(ink.minOf { it.first }.toFloat(), ink.minOf { it.second }.toFloat(),
                (ink.maxOf { it.first } + 1).toFloat(), (ink.maxOf { it.second } + 1).toFloat())
        }
        val resting = materialPane(blue = true)
        action.performTouchInput { down(center); moveTo(center + Offset(-500f, 0f), 100) }
        compose.mainClock.advanceTimeBy(700)
        val held = materialPane(blue = true)
        assertTrue("Whole pane must cross the old animated Column edge: rest=$resting held=$held",
            held.left < resting.left - 8f && held.left < 70f && held.width >= resting.width)
        assertTrue("A clipped sliver cannot substitute for the whole reconnect pane", held.height >= resting.height)
        assertEquals("Drawing must preserve the real button's seat", seat, action.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { extraHeight.value = 80.dp }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { extraHeight.value = 0.dp }
        compose.mainClock.advanceTimeBy(64)
        val interrupted = materialPane(blue = true)
        assertTrue("Interrupted parent resize keeps the whole button", interrupted.width >= resting.width)
        assertEquals("Interrupted resize cannot jump the hit seat", seat, action.fetchSemanticsNode().boundsInRoot)
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(resting, materialPane(blue = true))
        compose.runOnIdle { assertEquals(0, reconnects); assertEquals(0, disconnects) }
        action.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle { assertEquals(1, reconnects); assertEquals(0, disconnects) }

        val disconnectResting = materialPane(blue = false)
        disconnectAction.performTouchInput { down(center); moveTo(center + Offset(500f, 0f), 100) }
        compose.mainClock.advanceTimeBy(700)
        val disconnectHeld = materialPane(blue = false)
        assertTrue("Whole Disconnect pane crosses the old right edge and opaque sidebar: rest=$disconnectResting held=$disconnectHeld seat=$disconnectSeat",
            disconnectHeld.right > disconnectResting.right + 8f && disconnectHeld.right > 386f &&
                disconnectHeld.width >= disconnectResting.width && disconnectHeld.height >= disconnectResting.height)
        assertEquals("Disconnect drawing must preserve the right button's real seat", disconnectSeat,
            disconnectAction.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { extraHeight.value = 80.dp }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { extraHeight.value = 0.dp }
        compose.mainClock.advanceTimeBy(64)
        val disconnectInterrupted = materialPane(blue = false)
        assertTrue("Interrupted resize cannot cut Disconnect's right half: rest=$disconnectResting held=$disconnectInterrupted",
            disconnectInterrupted.right > 386f && disconnectInterrupted.width >= disconnectResting.width)
        assertEquals(disconnectSeat, disconnectAction.fetchSemanticsNode().boundsInRoot)
        disconnectAction.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(disconnectResting, materialPane(blue = false))
        compose.runOnIdle { assertEquals(1, reconnects); assertEquals(0, disconnects) }
        disconnectAction.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle { assertEquals(1, reconnects); assertEquals(1, disconnects) }
    }
}
