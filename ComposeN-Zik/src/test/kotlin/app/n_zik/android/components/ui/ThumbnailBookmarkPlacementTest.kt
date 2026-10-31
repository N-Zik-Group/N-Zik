package app.n_zik.android.components.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailBookmarkPlacementTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `shifted badge preserves the complete unshifted raster and its translated edge clicks`() {
        val enabled = mutableStateOf(false)
        val roundness = mutableStateOf(0.dp)
        val icon = mutableStateOf(R.drawable.bookmark)
        var clicks = 0
        var parentClicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(roundness.value)
            val settings = GlassSettings(enabled = enabled.value)
            CompositionLocalProvider(LocalAppearance provides Appearance(ColorPalette.fallback,
                Typography.fallback, shape, shape, shape), LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Column {
                        Box(Modifier.size(100.dp, 80.dp).background(Color.Black).testTag("reference")) {
                            Box(Modifier.absoluteOffset(32.dp, 24.dp).size(40.dp).background(Color.DarkGray))
                            // Independent placement of the same standard badge, with no negative offset.
                            Box(Modifier.absoluteOffset(24.dp, 48.dp)) {
                                HeaderIconButton({}, icon.value, Color.Blue, iconSize = 12.dp)
                            }
                        }
                        Box(Modifier.size(100.dp, 80.dp).background(Color.Black).testTag("shifted")
                            .clickable { parentClicks++ }) {
                            Box(Modifier.absoluteOffset(32.dp, 24.dp).size(40.dp).background(Color.DarkGray)) {
                                HeaderIconButton({ clicks++ }, icon.value, Color.Blue,
                                    modifier = Modifier.testTag("badge"), iconSize = 12.dp,
                                    placementModifier = Modifier.align(Alignment.BottomStart).absoluteOffset(x = (-8).dp))
                            }
                        }
                    }
                }
            }
        }
        for (master in listOf(false, true)) for (radius in listOf(0.dp, 24.dp)) {
            for (glyph in listOf(R.drawable.bookmark, R.drawable.bookmark_slash)) {
                compose.runOnIdle { enabled.value = master; roundness.value = radius; icon.value = glyph }
                val expected = compose.onNodeWithTag("reference").captureToImage().toPixelMap()
                val actual = compose.onNodeWithTag("shifted").captureToImage().toPixelMap()
                var ink = 0
                for (y in 0 until expected.height) for (x in 0 until expected.width) {
                    assertEquals("Translated badge must retain each pixel at $x,$y", expected[x, y], actual[x, y])
                    if (expected[x, y].blue > expected[x, y].red + .02f) ink++
                }
                assertTrue("The comparison must contain visible bookmark pixels", ink > 10)
            }
        }
        val action = compose.onNodeWithTag("badge").assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
        repeat(4) { edge -> action.performTouchInput {
            click(when (edge) {
                0 -> Offset(1f, center.y)
                1 -> Offset(width - 1f, center.y)
                2 -> Offset(center.x, 1f)
                else -> Offset(center.x, height - 1f)
            })
        } }
        compose.runOnIdle { assertEquals(4, clicks); assertEquals(0, parentClicks) }
    }

    @Test fun `real playlist card bookmark matches every pixel of the complete standard badge`() {
        val enabled = mutableStateOf(false)
        val roundness = mutableStateOf(0.dp)
        compose.setContent {
            val shape = RoundedCornerShape(roundness.value)
            // Neutral backgrounds let us compare the complete blue glyph mask across artwork and margins.
            val palette = ColorPalette.fallback.copy(accent = Color.Blue)
            CompositionLocalProvider(LocalAppearance provides Appearance(palette, Typography.fallback,
                shape, shape, shape), LocalGlassPreviewSettings provides GlassSettings(enabled = enabled.value)) {
                Column {
                    Box(Modifier.size(40.dp).background(Color.Black)) {
                        HeaderIconButton({}, R.drawable.bookmark, Color.Blue,
                            Modifier.testTag("standard"), iconSize = 12.dp)
                    }
                    Box(Modifier.size(160.dp, 100.dp).background(Color.Black)) {
                        PlaylistItem(thumbnailContent = {}, songCount = null, name = null, channelName = null,
                            thumbnailSizeDp = 40.dp, disableScrollingText = true, isYoutubePlaylist = true,
                            showInfo = false, modifier = Modifier.testTag("playlist"))
                    }
                }
            }
        }
        for (master in listOf(false, true)) for (radius in listOf(0.dp, 24.dp)) {
            compose.runOnIdle { enabled.value = master; roundness.value = radius }
            val standard = compose.onNodeWithTag("standard").captureToImage().toPixelMap()
            val badge = compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("playlist")))
            badge.assertWidthIsEqualTo(16.dp).assertHeightIsEqualTo(16.dp)
            val actual = badge.captureToImage().toPixelMap()
            var ink = 0
            for (y in 0 until standard.height) for (x in 0 until standard.width) {
                fun Color.isInk() = blue > red + .02f && blue > green + .02f
                assertEquals("Real card badge must retain the full standard glyph at $x,$y",
                    standard[x, y].isInk(), actual[x, y].isInk())
                if (actual[x, y].isInk()) ink++
            }
            assertTrue(ink > 10)
        }
    }
}
