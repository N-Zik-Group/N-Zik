package app.n_zik.android.components.navigation.nav

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.UiTypeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassIndicatorOpacityKey
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTabPresenceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear().putString(UiTypeKey, UiType.RiMusic.name)
            .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name).commit()
        // Isolate glyph opacity from the selector wash, which moves when its tab is removed.
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear()
            .putFloat(glassIndicatorOpacityKey, 0f).commit()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    @Test fun `glyphs disappear and reappear immediately without ghosts or stale actions in all positions and materials`() {
        var position by mutableStateOf(NavigationBarPosition.Top)
        var glass by mutableStateOf(false)
        var count by mutableStateOf(3)
        var removedClicks = 0
        var liveClicks = 0
        val controller = mockk<NavController>(relaxed = true)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val palette = ColorPalette.fallback.copy(text = Color.Red, textDisabled = Color.Green)
            CompositionLocalProvider(LocalAppearance provides Appearance(palette, Typography.fallback, shape, shape, shape)) {
                Box(Modifier.size(360.dp, 500.dp).background(Color.Black).testTag("viewport")) {
                    key(position, glass) {
                        val onClick: (Int) -> Unit = { if (it == 2) removedClicks++ else liveClicks++ }
                        if (position == NavigationBarPosition.Left || position == NavigationBarPosition.Right) {
                            Column(Modifier.size(64.dp, 480.dp)) {
                                val bar = VerticalNavigationBar(2, onClick, controller)
                                bar.add { item -> repeat(count) { item(it, "Tab $it", R.drawable.library) } }
                                bar.DrawTabs(rememberLayerBackdrop())
                            }
                        } else {
                            Box(Modifier.size(360.dp, 90.dp)) {
                                val bar = HorizontalNavigationBar(2, onClick, controller, Modifier.fillMaxSize())
                                bar.add { item -> repeat(count) { item(it, "Tab $it", R.drawable.library) } }
                                bar.Draw()
                            }
                        }
                    }
                }
            }
        }
        for (material in listOf(false, true)) for (side in NavigationBarPosition.entries) {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle {
                context.preferences.edit().putBoolean(glassEnabledKey, material)
                    .putString(navigationBarPositionKey, side.name).commit()
                position = side; glass = material; count = 3
            }
            compose.waitForIdle()
            val scenario = "position=$side glass=$material"
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val oldCenter = compose.onNodeWithContentDescription("Tab 2").fetchSemanticsNode().boundsInRoot.center - viewport.topLeft
            val before = redEnergy()
            assertTrue("Visible selected glyph: $scenario energy=$before", before > 5f)
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { count = 2; Snapshot.sendApplyNotifications() }
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Tab 2").assertDoesNotExist()
            compose.onNodeWithText("Tab 2").assertDoesNotExist()
            compose.onAllNodes(hasClickAction()).assertCountEquals(2)
            assertTrue("No departed glyph may remain: $scenario energy=${redEnergy()}", redEnergy() < .1f)
            compose.onNodeWithTag("viewport").performTouchInput { click(oldCenter) }
            compose.onNodeWithContentDescription("Tab 0").performClick()
            compose.runOnIdle { assertEquals(0, removedClicks); assertTrue(liveClicks > 0) }
            compose.mainClock.advanceTimeBy(400)
            assertTrue("Departure must be absent: $scenario energy=${redEnergy()}", redEnergy() < .1f)
            assertEquals(viewport, compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot)

            compose.runOnIdle { count = 3; Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            val entering = redEnergy()
            compose.mainClock.advanceTimeBy(128)
            val enteringMiddle = redEnergy()
            compose.mainClock.advanceTimeBy(400)
            val entered = redEnergy()
            assertTrue("The entering glyph is immediately visible: $scenario energy=$entering", entering > 5f)
            assertEquals("No entrance fade: $scenario", entering, enteringMiddle, .1f)
            assertEquals("No delayed entrance: $scenario", entering, entered, .1f)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun `vertical reordering reaches final placement immediately while viewport stays fixed`() {
        var reverse by mutableStateOf(false)
        val controller = mockk<NavController>(relaxed = true)
        context.preferences.edit().putString(navigationBarPositionKey, NavigationBarPosition.Left.name).commit()
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)) {
                Column(Modifier.size(64.dp, 400.dp).testTag("viewport"), verticalArrangement = Arrangement.SpaceEvenly) {
                    val bar = VerticalNavigationBar(0, {}, controller)
                    bar.add { item -> repeat(3) { item(it, "Tab $it", R.drawable.library) } }
                    bar.DrawButtons(if (reverse) listOf(2, 1, 0) else listOf(0, 1, 2))
                }
            }
        }
        compose.waitForIdle()
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        val original = compose.onNodeWithContentDescription("Tab 0").fetchSemanticsNode().boundsInRoot.center.y
        compose.runOnIdle { reverse = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        val start = compose.onNodeWithContentDescription("Tab 0").fetchSemanticsNode().boundsInRoot.center.y
        compose.mainClock.advanceTimeBy(128)
        val middle = compose.onNodeWithContentDescription("Tab 0").fetchSemanticsNode().boundsInRoot.center.y
        compose.mainClock.advanceTimeBy(400)
        val end = compose.onNodeWithContentDescription("Tab 0").fetchSemanticsNode().boundsInRoot.center.y
        assertTrue("Tab must reach the bottom slot immediately", start > original)
        assertEquals(start, middle, .01f)
        assertEquals(start, end, .01f)
        assertEquals(viewport, compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `reduced motion removes departed drawing immediately`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var count by mutableStateOf(3)
        val controller = mockk<NavController>(relaxed = true)
        context.preferences.edit().putString(navigationBarPositionKey, NavigationBarPosition.Left.name).commit()
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val palette = ColorPalette.fallback.copy(text = Color.Red, textDisabled = Color.Green)
            CompositionLocalProvider(LocalAppearance provides Appearance(palette, Typography.fallback, shape, shape, shape)) {
                Column(Modifier.size(64.dp, 400.dp).background(Color.Black).testTag("viewport")) {
                    val bar = VerticalNavigationBar(2, {}, controller)
                    bar.add { item -> repeat(count) { item(it, "Tab $it", R.drawable.library) } }
                    bar.DrawTabs(rememberLayerBackdrop())
                }
            }
        }
        compose.waitForIdle()
        assertTrue(redEnergy() > 5f)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { count = 2; Snapshot.sendApplyNotifications() }
        // Slot disposal invalidates DrawButtons after the first composition has committed.
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Tab 2").assertDoesNotExist()
        assertTrue(redEnergy() < .1f)
    }

    private fun redEnergy(): Float {
        val image = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
        var total = 0f
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val pixel = image[x, y]
            total += (pixel.red - maxOf(pixel.green, pixel.blue)).coerceAtLeast(0f)
        }
        return total
    }
}
