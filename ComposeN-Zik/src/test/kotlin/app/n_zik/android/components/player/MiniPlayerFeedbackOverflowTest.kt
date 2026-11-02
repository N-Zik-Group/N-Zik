@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class)

package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.screens.player.collapsedAnchor
import app.it.fast4x.rimusic.ui.screens.player.rememberPlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.CustomBottomSheet
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassEnabledKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniPlayerFeedbackOverflowTest {
    @get:Rule val compose = createComposeRule()
    @After fun cleanup() {
        ApplicationProvider.getApplicationContext<Application>().preferences.edit().remove(glassEnabledKey).commit()
    }

    @Test fun `idle mask and buffer remain exact and active outsets follow each edge in pixels`() {
        val base = Outline.Rectangle(Rect(16f, 0f, 284f, 60f))
        assertSame(base, miniPlayerFeedbackOutline(base, emptyList()))
        val idle = miniPlayerFeedbackOutsets(Size(300f, 60f), emptyList(), Density(2f))
        assertEquals(0.dp, idle.left)
        assertEquals(0.dp, idle.bottom)
        val active = miniPlayerFeedbackOutsets(Size(300f, 60f),
            listOf(Rect(-3.5f, -5.25f, 308.5f, 71.5f)), Density(2f))
        assertEquals(2.dp, active.left)
        assertEquals(3.dp, active.top)
        assertEquals(4.5.dp, active.right)
        assertEquals(6.dp, active.bottom)
    }

    @Test fun `bottom glass mini keeps its moving glyph beyond the card through fade and restores navigation`() =
        exerciseSheet(top = false, glass = true)

    @Test fun `top glass mini keeps its moving glyph beyond the card through fade and restores navigation`() =
        exerciseSheet(top = true, glass = true)

    @Test fun `native mini retains outside navigation at top`() = exerciseSheet(top = true, glass = false)
    @Test fun `native mini retains outside navigation at bottom`() = exerciseSheet(top = false, glass = false)

    private fun exerciseSheet(top: Boolean, glass: Boolean) {
        val preferences = ApplicationProvider.getApplicationContext<Application>().preferences
        preferences.edit().putBoolean(glassEnabledKey, glass).commit()
        lateinit var sheet: PlayerSheetState
        var navigationClicks = 0
        var hiddenPlayerClicks = 0
        var actionClicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape)) {
                sheet = rememberPlayerSheetState(0.dp, 300.dp, 60.dp, collapsedAnchor)
                Box(Modifier.size(300.dp).background(Color.Black).testTag("scene")) {
                    Box(Modifier.fillMaxSize().clickable { navigationClicks++ })
                    CustomBottomSheet(sheet, bottomPadding = { 60.dp },
                        topPadding = if (top) ({ 60.dp }) else null,
                        collapsedContent = {
                            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                Box(Modifier.fillMaxSize().background(Color.DarkGray, shape))
                                Box(Modifier.align(Alignment.CenterEnd)) {
                                    GlassMiniPlayerIconButton(R.drawable.reorder, Color.Blue,
                                        { actionClicks++ }, "mini action",
                                        settings = GlassSettings(enabled = glass, quality = GlassQuality.Performance))
                                }
                            }
                        }) {
                        Box(Modifier.fillMaxSize().background(Color.Green).clickable { hiddenPlayerClicks++ })
                    }
                }
            }
        }
        fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
        advance(500)
        val scene = compose.onNodeWithTag("scene")
        val centerY = if (top) 90f else 210f
        fun checkNavigation() {
            val before = navigationClicks
            // The rail, navbar band and rounded corner are outside the original card.
            listOf(Offset(4f, centerY), Offset(296f, centerY), Offset(150f, 280f),
                Offset(16f, centerY - 29f)).forEach { point ->
                scene.performTouchInput { click(point) }
            }
            compose.runOnIdle {
                assertEquals(before + 4, navigationClicks)
                assertEquals(0, hiddenPlayerClicks)
                assertTrue(sheet.isCollapsed)
            }
        }
        checkNavigation()
        val action = compose.onNodeWithContentDescription("mini action")
        action.performTouchInput { click() }
        advance(1500)
        compose.runOnIdle { assertEquals(1, actionClicks) }
        if (glass) {
            action.performTouchInput { down(center); moveTo(center + Offset(500f, 0f), 100) }
            advance(700)
            fun blueOverflow(): Int {
                val pixels = scene.captureToImage().toPixelMap()
                var count = 0
                for (y in 0 until pixels.height) for (x in 285 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.blue > .6f && color.red < .4f && color.green < .4f) count++
                }
                return count
            }
            assertTrue("The real glyph must draw past the collapsed card", blueOverflow() > 5)
            compose.runOnIdle { sheet.snapTo(108.dp) }
            advance(32)
            assertTrue("The fade's offscreen buffer must include the moving glyph", blueOverflow() > 5)
            compose.runOnIdle { sheet.snapTo(204.dp) }
            advance(32)
            val deployed = scene.captureToImage().toPixelMap()
            for (y in 0 until deployed.height) for (x in 295 until deployed.width) {
                val pixel = deployed[x, y]
                assertTrue("Full-player content stays inside its own card during mini overflow",
                    !(pixel.green > .05f && pixel.green > pixel.red + .03f && pixel.green > pixel.blue + .03f))
            }
            action.performTouchInput { cancel() }
            compose.runOnIdle { sheet.snapTo(60.dp) }
            advance(1800)
            assertEquals("The active-only mask must close after the spring settles", 0, blueOverflow())
            checkNavigation()
        }
    }
}
