package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.glass.backdrop.effects.blur
import app.n_zik.android.components.glass.backdrop.effects.lens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassColdIdleActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(sdk = [32])
    fun `no selected tint preserves changing native themed film across action presentations`() {
        val yellow = Color(0xFFC8AA22)
        val purple = Color(0xFF8844CC)
        val palette = mutableStateOf(DefaultDarkColorPalette.copy(background0 = Color.Black, background1 = yellow))
        val settings = GlassSettings(source = GlassTintSource.None, quality = GlassQuality.Balanced,
            film = .5f, vibrancy = 0f, rim = 0f)
        compose.setContent {
            AppearanceProvider(palette.value) {
                assertEquals("API 32 must exercise the optical renderer", GlassRenderer.Blur, glassRenderer(settings))
                Column(Modifier.background(Color.Black)) {
                    GlassActionGroup(settings, GlassArea.ActionButtons, Modifier.testTag("theme.group")) {
                        repeat(3) { GlassActionGroupItem({}) {} }
                    }
                    GlassSectionActions(listOf(GlassSectionAction(R.drawable.chevron_forward, "theme.more") {}),
                        Modifier.testTag("theme.arrow"), settings)
                    listOf(true, false).forEach { enabled ->
                        GlassToolbarRow(1, Modifier.testTag("theme.toolbar.$enabled"), settings) {
                            TabToolBar.Icon(painterResource(R.drawable.chevron_forward),
                                enabled = enabled, onClick = {})
                        }
                    }
                    GlassActionGroup(settings.copy(film = 1f), GlassArea.ActionButtons,
                        Modifier.testTag("theme.override"), filmColor = Color.Green) {
                        repeat(3) { GlassActionGroupItem({}) {} }
                    }
                }
            }
        }
        fun assertThemed(color: Color) {
            val expected = color.copy(alpha = .5f).compositeOver(Color.Black)
            listOf("theme.group", "theme.arrow", "theme.toolbar.true", "theme.toolbar.false").forEach { tag ->
                assertSurfacePixel(tag, expected)
            }
            assertSurfacePixel("theme.override", Color.Green)
        }
        assertThemed(yellow)
        compose.runOnIdle { palette.value = palette.value.copy(background1 = purple) }
        assertThemed(purple)
    }

    @Test
    @Config(sdk = [32])
    fun `section arrow and group immediately sample the same changing safe container`() {
        val sourceColor = mutableStateOf(Color.Red)
        val settings = GlassSettings(source = GlassTintSource.None, quality = GlassQuality.Balanced,
            film = 0f, blur = 0f, vibrancy = 0f, rim = 0f)
        compose.setContent {
            AppearanceProvider {
                val source = rememberCanvasBackdrop { drawRect(sourceColor.value) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides source,
                    LocalGlassBackdrop provides null, LocalGlassArtworkColor provides null) {
                    Column(Modifier.background(Color.Black)) {
                        GlassActionGroup(settings, GlassArea.ActionButtons, Modifier.testTag("container.group")) {
                            repeat(3) { GlassActionGroupItem({}) {} }
                        }
                        GlassSectionActions(listOf(GlassSectionAction(R.drawable.chevron_forward, "container.more") {}),
                            Modifier.testTag("container.arrow"), settings)
                    }
                }
            }
        }
        listOf(Color.Red, Color.Blue).forEach { expected ->
            if (expected == Color.Blue) compose.runOnIdle { sourceColor.value = expected }
            assertSurfacePixel("container.group", expected)
            assertSurfacePixel("container.arrow", expected)
        }
    }

    private fun assertSurfacePixel(tag: String, expected: Color) {
        val image = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        // Stay clear of the glyph, rim and group separators.
        val actual = image[image.width / 8, image.height / 2]
        listOf(actual.red to expected.red, actual.green to expected.green,
            actual.blue to expected.blue).forEach { (channel, expectedChannel) ->
            assertEquals("$tag must show its current themed film and safe source", expectedChannel, channel, 3f / 255f)
        }
    }

    @Test fun `cold default refraction actions do not change after unrelated redraw`() {
        assertColdIdleActions(GlassSettings())
    }

    @Test fun `cold custom refraction actions do not change after unrelated redraw`() {
        assertColdIdleActions(GlassSettings(dispersion = true, depthEffect = true))
    }

    @Test fun `cold quality refraction actions do not change after unrelated redraw`() {
        assertColdIdleActions(GlassSettings(quality = GlassQuality.Quality, dispersion = true, depthEffect = true))
    }

    private fun assertColdIdleActions(settings: GlassSettings) {
        val redraw = mutableIntStateOf(0)
        val host = GlassMenuHostState()
        var parentPaints = 0
        var compositions = 0
        var renderer: GlassRenderer? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            compositions++
            AppearanceProvider {
                renderer = glassRenderer(settings)
                CompositionLocalProvider(
                    LocalGlassMenuHost provides host,
                    LocalGlassArtworkColor provides null,
                    LocalGlassBackdrop provides null,
                    LocalGlassContainerBackdrop provides null,
                ) {
                    Column(Modifier.background(DefaultDarkColorPalette.background0).drawWithContent {
                        redraw.intValue
                        parentPaints++
                        drawContent()
                    }) {
                        // The For You heading uses this group directly, inside Skeleton's
                        // null-backdrop scope; its independent canvas exists from frame one.
                        GlassActionGroup(settings, GlassArea.ActionButtons, Modifier.testTag("idle.group")) {
                            repeat(3) { GlassActionGroupItem({}) {} }
                        }
                        GlassSectionActions(
                            listOf(GlassSectionAction(R.drawable.chevron_forward, "idle.more") {}),
                            Modifier.testTag("idle.arrow"), settings,
                        )
                    }
                }
            }
        }
        fun pixels(tag: String): List<Color> {
            val image = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            return (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image[x, y] } }
        }
        val firstGroup = pixels("idle.group")
        val firstArrow = pixels("idle.arrow")
        assertEquals("this regression must exercise the production AGSL lens", GlassRenderer.Refraction, renderer)
        val groupImage = compose.onNodeWithTag("idle.group").captureToImage().toPixelMap()
        val center = groupImage[groupImage.width / 4, groupImage.height / 2]
        val expectedFilm = DefaultDarkColorPalette.background1.copy(alpha = settings.film)
            .compositeOver(DefaultDarkColorPalette.background0)
        // The center is outside the lens edge and rim. Allow saturation of the
        // slightly purple page source, but reject a blank or film-only capture.
        listOf(center.red to expectedFilm.red, center.green to expectedFilm.green,
            center.blue to expectedFilm.blue).forEach { (actual, expected) ->
            assertEquals("first production capsule must already sample its page canvas", expected, actual, 7f / 255f)
        }
        val firstPaints = parentPaints
        val firstCompositions = compositions
        compose.runOnIdle { redraw.intValue++ }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(firstGroup, pixels("idle.group"))
        assertEquals(firstArrow, pixels("idle.arrow"))
        compose.runOnIdle {
            assertTrue("the unrelated parent redraw must actually run", parentPaints > firstPaints)
            assertEquals("no media or composition change may repair the initial surface", firstCompositions, compositions)
            assertEquals(null, host.artworkColor)
            assertEquals(0f, host.backdropLuminance, 0f)
        }
    }

    @Test fun `cold first canvas capture receives measured effects before any repair redraw`() {
        val effectSizes = mutableListOf<Size>()
        val captureEffectSizes = mutableListOf<Size>()
        val effectsAtCapture = mutableListOf<Boolean>()
        val lensesAtCapture = mutableListOf<Boolean>()
        var measuredLensApplied = false
        var currentEffects: BackdropEffectScope? = null
        val swap = mutableStateOf(false)
        val redraw = mutableIntStateOf(0)
        var compositions = 0
        var parentPaints = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            compositions++
            val source: DrawScope.() -> Unit = remember {
                {
                    val effects = checkNotNull(currentEffects)
                    captureEffectSizes += effects.size
                    effectsAtCapture += effects.renderEffect != null
                    lensesAtCapture += measuredLensApplied
                    drawRect(if (swap.value) Color.Blue else Color.Red)
                    drawRect(if (swap.value) Color.Red else Color.Blue,
                        size = Size(size.width / 2f, size.height))
                }
            }
            val backdrop = rememberCanvasBackdrop(source)
            val effects: BackdropEffectScope.() -> Unit = remember {
                {
                    currentEffects = this
                    effectSizes += size
                    measuredLensApplied = false
                    blur(2f)
                    if (size != Size.Unspecified && size.minDimension > 24f) {
                        lens(refractionHeight = 8f, refractionAmount = 12f,
                            depthEffect = true, dispersion = 1f)
                        measuredLensApplied = true
                    }
                }
            }
            val shape = remember { { RoundedCornerShape(12.dp) } }
            Box(Modifier.drawWithContent {
                redraw.intValue
                parentPaints++
                drawContent()
            }) {
                Box(Modifier.size(132.dp, 40.dp).testTag("cold.capture")
                    .drawPlainBackdrop(backdrop, shape, effects))
            }
        }
        fun assertCurrentPattern(swapped: Boolean) {
            val image = compose.onNodeWithTag("cold.capture").captureToImage().toPixelMap()
            val left = image[image.width / 4, image.height / 2]
            val right = image[image.width * 3 / 4, image.height / 2]
            val blue = if (swapped) right else left
            val red = if (swapped) left else right
            assertTrue("first capture must show CURRENT blue half, not blank/stale pixels: $blue",
                blue.blue > .8f && blue.red < .2f && blue.green < .2f)
            assertTrue("first capture must show CURRENT red half, not blank/stale pixels: $red",
                red.red > .8f && red.blue < .2f && red.green < .2f)
        }
        assertCurrentPattern(swapped = false)
        compose.runOnIdle {
            assertTrue("attach must exercise the unmeasured effect state", effectSizes.contains(Size.Unspecified))
            assertTrue("the first frame must record its independent canvas", captureEffectSizes.isNotEmpty())
            assertTrue("no empty-size source may be cached", captureEffectSizes.all {
                it != Size.Unspecified && it.width > 0f && it.height > 0f
            })
            assertTrue("the first measured capture must already have its effect chain", effectsAtCapture.all { it })
            assertTrue("the first capture must already include the measured AGSL lens", lensesAtCapture.all { it })
        }
        val firstPaints = parentPaints
        val firstCompositions = compositions
        compose.runOnIdle { redraw.intValue++ }
        compose.mainClock.advanceTimeByFrame()
        assertCurrentPattern(swapped = false)
        compose.runOnIdle {
            assertTrue("the external redraw must actually run", parentPaints > firstPaints)
            swap.value = true
        }
        // No warm-up capture or second frame may repair this first changed-source image.
        assertCurrentPattern(swapped = true)
        compose.runOnIdle {
            assertEquals("source freshness must not depend on recomposition", firstCompositions, compositions)
        }
    }

    @Composable private fun AppearanceProvider(
        palette: ColorPalette = DefaultDarkColorPalette,
        content: @Composable () -> Unit,
    ) {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = palette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
