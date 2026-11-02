package app.n_zik.android.components.glass

import app.n_zik.android.components.ExplicitBadge
import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassDescriptionControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native expansion stays passive while glass adds one working action`() {
        val enabled = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            AppearanceProvider {
                GlassDescriptionExpansion({ clicks++ }, settings = GlassSettings(enabled = enabled.value)) {
                    BasicText("Show more")
                }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.runOnIdle { enabled.value = true }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText("Show more").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun `explicit surface preserves badge bounds without adding an action`() {
        val enabled = mutableStateOf(false)
        var iconId = 0
        compose.setContent {
            AppearanceProvider {
                iconId = explicitBadgeIconId(GlassSettings(enabled = enabled.value))
                Box(Modifier.size(18.dp).glassExplicitBadgeBackground(GlassSettings(enabled = enabled.value)).testTag("badge"))
            }
        }
        listOf(false, true).forEach { glass ->
            compose.runOnIdle { enabled.value = glass }
            compose.onNodeWithTag("badge").assertWidthIsEqualTo(18.dp).assertHeightIsEqualTo(18.dp)
            compose.onAllNodes(hasClickAction()).assertCountEquals(0)
            compose.runOnIdle { assertEquals(if (glass) R.drawable.explicit_glass_glyph else R.drawable.explicit, iconId) }
        }
    }

    @Test fun `explicit badge retains the same film as neighboring glass instead of becoming clear`() {
        compose.setContent {
            AppearanceProvider {
                val settings = GlassSettings()
                val background: DrawScope.() -> Unit = remember {
                    { drawRect(DefaultDarkColorPalette.background0) }
                }
                val source = rememberCanvasBackdrop(background)
                val shape = RoundedCornerShape(24.dp)
                Row {
                    Box(Modifier.size(18.dp).testTag("explicit")
                        .glassExplicitBadgeBackground(settings))
                    Box(Modifier.size(18.dp).testTag("reference")
                        .glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
                            ownBackdrop = source, drawShadow = false))
                }
            }
        }
        fun center(tag: String): Color {
            val image = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            return image[image.width / 2, image.height / 2]
        }
        val actual = center("explicit")
        val expected = center("reference")
        assertEquals(expected.red, actual.red, 1f / 255f)
        assertEquals(expected.green, actual.green, 1f / 255f)
        assertEquals(expected.blue, actual.blue, 1f / 255f)
    }

    @Test fun `Glass locations share the mini-player component at their original sizes`() {
        val otherLocation = mutableStateOf(false)
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.size(100.dp)) {
                    ExplicitBadge(
                        if (otherLocation.value) Color.Red else Color.Green,
                        Modifier.size(if (otherLocation.value) 18.dp else 14.dp),
                        settings = GlassSettings(),
                    )
                }
            }
        }
        val glyph = compose.onNodeWithTag("glass.explicit.glyph", useUnmergedTree = true)
        glyph.assertWidthIsEqualTo(14.dp).assertHeightIsEqualTo(14.dp)
        compose.runOnIdle { otherLocation.value = true }
        glyph.assertWidthIsEqualTo(18.dp).assertHeightIsEqualTo(18.dp)

    }

    @Test fun `generic 14dp Glass badge matches the original mini-player IconButton pixels at rest`() {
        compose.setContent {
            AppearanceProvider {
                val settings = GlassSettings()
                val surface = rememberCanvasBackdrop { drawRect(Color(0xff294b72)) }
                val rendered = remember(surface) { RenderedMaterialBackdrop(surface) }
                val raw = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides rendered,
                    LocalGlassMaterialSource provides raw) {
                    Row {
                        // The original mini-player composition: same icon, tint, size,
                        // processed container and background modifier, including native clips.
                        app.it.fast4x.rimusic.ui.components.themed.IconButton(
                            icon = explicitBadgeIconId(settings),
                            color = DefaultDarkColorPalette.text,
                            onClick = {},
                            modifier = Modifier.size(14.dp).glassExplicitBadgeBackground(settings)
                                .testTag("original.mini.badge"),
                        )
                        ExplicitBadge(Color.Red, Modifier.size(14.dp).testTag("generic.badge"), settings)
                    }
                }
            }
        }
        val original = compose.onNodeWithTag("original.mini.badge").captureToImage().toPixelMap()
        val generic = compose.onNodeWithTag("generic.badge").captureToImage().toPixelMap()
        assertEquals(original.width, generic.width)
        assertEquals(original.height, generic.height)
        var visibleGlyphPixels = 0
        for (y in 0 until original.height) for (x in 0 until original.width) {
            val expected = original[x, y]
            val actual = generic[x, y]
            assertEquals("red at $x,$y", expected.red, actual.red, 1f / 255f)
            assertEquals("green at $x,$y", expected.green, actual.green, 1f / 255f)
            assertEquals("blue at $x,$y", expected.blue, actual.blue, 1f / 255f)
            assertEquals("alpha at $x,$y", expected.alpha, actual.alpha, 1f / 255f)
            if (actual.red > .7f && actual.green > .7f && actual.blue > .7f) visibleGlyphPixels++
        }
        assertTrue("Comparison must include the visible E glyph", visibleGlyphPixels > 0)
    }

    @Test fun `generic badge uses native action and caller bounds when action Glass is disabled`() {
        val settings = mutableStateOf(GlassSettings(enabled = false))
        compose.setContent {
            AppearanceProvider {
                ExplicitBadge(Color.Red, Modifier.size(18.dp).testTag("badge"), settings.value)
            }
        }
        listOf(GlassSettings(enabled = false),
            GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled))).forEach {
            compose.runOnIdle { settings.value = it }
            compose.onNodeWithTag("badge").assertWidthIsEqualTo(18.dp).assertHeightIsEqualTo(18.dp)
            compose.onAllNodes(hasClickAction()).assertCountEquals(1)
            compose.onAllNodesWithTag("glass.explicit.glyph", useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test fun `row badge samples the same rendered surface as mini-player instead of raw scene`() {
        compose.setContent {
            AppearanceProvider {
                val rendered = rememberCanvasBackdrop { drawRect(Color.Blue) }
                val raw = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides rendered,
                    LocalGlassMaterialSource provides raw) {
                    ExplicitBadge(Color.White, Modifier.size(18.dp).testTag("rendered.badge"),
                        GlassSettings(film = 0f, rim = 0f, blur = 0f, vibrancy = 0f))
                }
            }
        }
        val pixels = compose.onNodeWithTag("rendered.badge").captureToImage().toPixelMap()
        assertEquals(Color.Blue, pixels[2, pixels.height / 2])
    }

    @Test fun `explicit glyph follows held motion without acquiring a click action`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.size(120.dp).background(Color.Black)) {
                    ExplicitBadge(Color.White, Modifier.size(18.dp).testTag("badge.hit"), settings = GlassSettings())
                }
            }
        }
        val hit = compose.onNodeWithTag("badge.hit")
        val ink = compose.onNodeWithTag("glass.explicit.glyph", useUnmergedTree = true)
        fun glyphPosition(): Offset {
            val coordinates = ink.fetchSemanticsNode().layoutInfo.coordinates
            return coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
        }
        val idle = glyphPosition()
        hit.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        hit.performTouchInput { moveTo(center + Offset(45f, 20f), 100) }
        compose.mainClock.advanceTimeBy(600)
        val held = glyphPosition()
        assertTrue("The E glyph, not only a ripple, must move", held.x > idle.x)
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        hit.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(idle.x, glyphPosition().x, .1f)
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
