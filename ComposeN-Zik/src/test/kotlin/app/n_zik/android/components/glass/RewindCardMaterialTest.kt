package app.n_zik.android.components.glass

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.ui.screens.rewind.rewindCaptureMode
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.components.ui.screens.rewind.slides.rewindActionBackground
import app.n_zik.android.components.ui.screens.rewind.slides.rewindCollageFrame
import app.n_zik.android.components.ui.screens.rewind.slides.rewindCardBackground
import app.n_zik.android.components.ui.screens.rewind.slides.rewindOutlinedActionBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import app.n_zik.android.components.ui.screens.rewind.slides.RewindStoryShell
import app.n_zik.android.components.ui.screens.rewind.slides.RewindCardSurface
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.ui.screens.rewind.slides.LocalRewindShaderWarm
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RewindCardMaterialTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test fun `real story cards and actions sample live backdrop artwork without capturing themselves`() {
        val art = mutableStateOf(Color.Green)
        val shape = RoundedCornerShape(12.dp)
        val optics = GlassSettings(enabled = true, blur = 0f, film = 0f, rim = 0f,
            depth = 0f, amount = 0f, vibrancy = 0f)
        try {
            compose.setContent {
                view = LocalView.current
                CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    Typography.fallback, shape, shape, shape), LocalRewindShaderWarm provides false,
                    LocalGlassPreviewSettings provides optics) {
                    RewindStoryShell(0, 1, Color.Blue, Color.White,
                        modifier = Modifier.size(320.dp, 360.dp).testTag("rewind.live.scene"),
                        showProgress = false, showBrand = false,
                        backgroundArt = { Box(Modifier.fillMaxSize().background(art.value)) }) {
                        Row {
                            Box(Modifier.size(64.dp).rewindCardBackground(Color.Red, shape, optics)
                                .testTag("rewind.live.card"))
                            Spacer(Modifier.width(24.dp))
                            Box(Modifier.size(64.dp).rewindActionBackground(Color.Red, shape, optics)
                                .testTag("rewind.live.action"))
                        }
                    }
                }
            }
            fun assertSamples(expected: Color) {
                for (tag in listOf("rewind.live.card", "rewind.live.action")) {
                    val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val p = windowPixels()
                    val actual = p[bounds.center.x.toInt(), bounds.center.y.toInt()]
                    assertEquals("$tag red", expected.red, actual.red, .03f)
                    assertEquals("$tag green", expected.green, actual.green, .03f)
                    assertEquals("$tag blue", expected.blue, actual.blue, .03f)
                }
            }
            assertSamples(Color.Green)
            compose.runOnIdle { art.value = Color.Blue }
            assertSamples(Color.Blue)
            compose.runOnIdle { art.value = Color.Transparent }
            val root = compose.onNodeWithTag("rewind.live.scene", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val scrimPixels = windowPixels()
            val renderedScrim = scrimPixels[(root.left + 10).toInt(), root.center.y.toInt()]
            assertSamples(renderedScrim)
            compose.runOnIdle { art.value = Color.Blue; rewindCaptureMode.value = true }
            assertSamples(Color.Red)
            compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = true }
            assertSamples(Color.Red)
            compose.runOnIdle { rewindShareCaptureActive.value = false }
            assertSamples(Color.Blue)
        } finally {
            compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = false }
        }
    }

    @Test fun `nested listener badge includes its parent card and capture keeps authored colors`() {
        val film = mutableStateOf(.45f)
        val shape = RoundedCornerShape(12.dp)
        val optics = GlassSettings(enabled = true, blur = 0f, film = 0f, rim = 0f,
            depth = 0f, amount = 0f, vibrancy = 0f)
        try {
            compose.setContent {
                view = LocalView.current
                val background = rememberCanvasBackdrop { drawRect(Color.Blue) }
                CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    Typography.fallback, shape, shape, shape), LocalGlassContainerBackdrop provides background,
                    LocalGlassMaterialSource provides background) {
                    Box(Modifier.size(240.dp, 120.dp).background(Color.Blue)) {
                        RewindCardSurface(Color.Red, shape, optics.copy(film = film.value)) { surface ->
                            Row(Modifier.size(200.dp, 80.dp).then(surface)) {
                                Box(Modifier.size(40.dp).rewindCardBackground(Color.Green, shape, optics)
                                    .testTag("nested.badge"))
                                Spacer(Modifier.width(40.dp))
                                Box(Modifier.size(40.dp).testTag("nested.parent"))
                            }
                        }
                    }
                }
            }
            fun color(tag: String): Color {
                val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val p = windowPixels()
                return p[bounds.center.x.toInt(), bounds.center.y.toInt()]
            }
            for (opacity in listOf(.45f, .85f)) {
                compose.runOnIdle { film.value = opacity }
                val parent = color("nested.parent")
                val badge = color("nested.badge")
                assertEquals("Badge includes parent red", parent.red, badge.red, .03f)
                assertEquals("Badge includes parent blue", parent.blue, badge.blue, .03f)
            }
            compose.runOnIdle { rewindCaptureMode.value = true }
            assertEquals(Color.Red, color("nested.parent"))
            assertEquals(Color.Green, color("nested.badge"))
            compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = true }
            assertEquals(Color.Red, color("nested.parent"))
            assertEquals(Color.Green, color("nested.badge"))
        } finally {
            compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = false }
        }
    }

    @Test fun `story share action samples preceding foreground but never its own glyph`() {
        val foreground = mutableStateOf(Color.Cyan)
        val shape = RoundedCornerShape(12.dp)
        val optics = GlassSettings(enabled = true, blur = 0f, film = 0f, rim = 0f,
            depth = 0f, amount = 0f, vibrancy = 0f)
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape), LocalRewindShaderWarm provides false,
                LocalGlassPreviewSettings provides optics) {
                RewindStoryShell(0, 1, Color.Green, Color.White,
                    modifier = Modifier.size(320.dp, 360.dp), showProgress = false, showBrand = false,
                    onShareSlide = {}) {
                    Box(Modifier.fillMaxSize().background(foreground.value).testTag("rewind.share.underlay"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        val label = ApplicationProvider.getApplicationContext<Application>().getString(R.string.rw_share_slide)
        fun assertShare(expected: Color) {
            val icon = compose.onNodeWithContentDescription(label, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val underlying = compose.onNodeWithTag("rewind.share.underlay", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            // The 24dp icon sits centered in a 40dp pane. Sample beside its own glyph.
            val point = icon.center.copy(x = icon.center.x - with(compose.density) { 16.dp.toPx() })
            org.junit.Assert.assertTrue("The real share pane must overlap the preceding foreground", underlying.contains(point))
            val p = windowPixels()
            val actual = p[point.x.toInt(), point.y.toInt()]
            assertEquals("Share red", expected.red, actual.red, .03f)
            assertEquals("Share green", expected.green, actual.green, .03f)
            assertEquals("Share blue", expected.blue, actual.blue, .03f)
        }
        assertShare(Color.Cyan)
        compose.runOnIdle { foreground.value = Color.Magenta }
        assertShare(Color.Magenta)
        repeat(3) { compose.mainClock.advanceTimeByFrame(); assertShare(Color.Magenta) }
    }

    private fun windowPixels(): PixelMap = compose.runOnIdle {
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }.asImageBitmap().toPixelMap()
    }

    @Test fun `outlined action modes use one shared rim and preserve native fill and export pixels`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val enabled = mutableStateOf(true)
        val shape = RoundedCornerShape(12.dp)
        val fill = Color.Red.copy(alpha = .15f)
        // Make accidental retention of the native border unmistakable in the full image.
        val border = Color.Magenta
        val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance,
                    areaModes = mapOf(GlassArea.ActionButtons to mode.value))
                Column(Modifier.background(Color.Black)) {
                    Box(Modifier.size(80.dp, 40.dp).testTag("outlined.native")
                        .background(fill, shape).border(1.dp, border, shape))
                    Box(Modifier.size(80.dp, 40.dp).testTag("outlined.actual")
                        .rewindOutlinedActionBackground(fill, border, shape, settings))
                    val expected = when {
                        !settings.usesGlassMaterial(GlassArea.ActionButtons) &&
                            !settings.usesGlassContour(GlassArea.ActionButtons) ->
                            Modifier.background(fill, shape).border(1.dp, border, shape)
                        !settings.usesGlassMaterial(GlassArea.ActionButtons) ->
                            Modifier.background(fill, shape).glassAreaContour(settings, GlassArea.ActionButtons, shape)
                        else -> Modifier.glassChipBackground(fill, shape, settings)
                    }
                    Box(Modifier.size(80.dp, 40.dp).testTag("outlined.expected").then(expected))
                }
            }
        }
        fun image(tag: String) = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val native = image("outlined.native")
        for (master in listOf(false, true)) {
            for (areaMode in GlassAreaMode.entries) {
                compose.runOnIdle { enabled.value = master; mode.value = areaMode }
                val actual = image("outlined.actual")
                assertEquals("$master/$areaMode must use exactly one native or shared rim",
                    true, samePixels(image("outlined.expected"), actual))
                if (!master || areaMode == GlassAreaMode.Disabled) assertSamePixels(native, actual)
                if (master && areaMode == GlassAreaMode.ContourOnly) {
                    assertFalse("ContourOnly replaces the native border with the common rim", samePixels(native, actual))
                    assertEquals("ContourOnly keeps the native fill", native[native.width / 2, native.height / 2],
                        actual[actual.width / 2, actual.height / 2])
                }
            }
        }
        try {
            for (areaMode in GlassAreaMode.entries) {
                compose.runOnIdle { enabled.value = true; mode.value = areaMode; rewindCaptureMode.value = true }
                assertSamePixels(native, image("outlined.actual"))
                compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = true }
                assertSamePixels(native, image("outlined.actual"))
                compose.runOnIdle { rewindShareCaptureActive.value = false }
            }
        } finally {
            compose.runOnIdle { rewindCaptureMode.value = false; rewindShareCaptureActive.value = false }
        }
    }

    @Test
    fun `collage artwork fills its entire card when Glass toggles`() {
        val enabled = mutableStateOf(false)
        val shape = RoundedCornerShape(12.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(
                    Modifier.size(120.dp, 96.dp).testTag("collage")
                        .rewindCollageFrame(Color.Black, shape, GlassSettings(enabled = enabled.value))
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Cyan, shape).testTag("artwork"))
                }
            }
        }
        val native = compose.onNodeWithTag("collage").captureToImage().toPixelMap()
        listOf(false, true, false).forEach { glassEnabled ->
            compose.runOnIdle { enabled.value = glassEnabled }
            assertEquals(
                "Glass must not inset the collage artwork inside a second frame",
                compose.onNodeWithTag("collage").fetchSemanticsNode().boundsInRoot,
                compose.onNodeWithTag("artwork").fetchSemanticsNode().boundsInRoot,
            )
            val pixels = compose.onNodeWithTag("collage").captureToImage().toPixelMap()
            assertEquals("The photograph stays sharp and untinted", Color.Cyan, pixels[pixels.width / 2, pixels.height / 2])
            if (glassEnabled) assertFalse("The shared Glass rim must draw above opaque collage photographs", samePixels(native, pixels))
        }
    }

    @Test
    fun `cards and outlined actions retain native and capture pixels while Glass changes live material`() {
        val mode = mutableStateOf(0)
        val shape = RoundedCornerShape(12.dp)
        val fill = Color.Red.copy(alpha = .15f)
        val border = Color.White.copy(alpha = .45f)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val settings = GlassSettings(enabled = mode.value >= 2)
                Column(Modifier.background(Color.Black).testTag("surfaces")) {
                    Box(Modifier.size(80.dp, 40.dp).then(
                        if (mode.value == 0) Modifier.background(fill, shape)
                        else Modifier.rewindCardBackground(fill, shape, settings)))
                    Box(Modifier.size(80.dp, 40.dp).then(
                        if (mode.value == 0) Modifier.background(fill, shape).border(1.dp, border, shape)
                        else Modifier.rewindOutlinedActionBackground(fill, border, shape, settings)))
                    Box(Modifier.size(80.dp, 40.dp).then(
                        if (mode.value == 0) Modifier.background(fill, shape)
                        else Modifier.rewindActionBackground(fill, shape, settings)))
                    Box(Modifier.size(80.dp, 40.dp).then(
                        if (mode.value == 0) Modifier
                        else Modifier.rewindCollageFrame(fill, shape, settings))) {
                        Box(Modifier.fillMaxSize().background(Color.Cyan, shape))
                    }
                }
            }
        }
        val native = pixels()
        compose.runOnIdle { mode.value = 1 }
        assertSamePixels(native, pixels())
        compose.runOnIdle { mode.value = 2 }
        assertFalse("live Glass should change the card and action appearance", samePixels(native, pixels()))
        try {
            compose.runOnIdle { rewindCaptureMode.value = true }
            assertSamePixels(native, pixels())
            compose.runOnIdle {
                rewindCaptureMode.value = false
                rewindShareCaptureActive.value = true
            }
            assertSamePixels(native, pixels())
        } finally {
            compose.runOnIdle {
                rewindCaptureMode.value = false
                rewindShareCaptureActive.value = false
            }
        }
    }

    private fun pixels() = compose.onNodeWithTag("surfaces").captureToImage().toPixelMap()

    private fun samePixels(expected: PixelMap, actual: PixelMap): Boolean =
        expected.width == actual.width && expected.height == actual.height &&
            (0 until expected.height).all { y -> (0 until expected.width).all { x -> expected[x, y] == actual[x, y] } }

    private fun assertSamePixels(expected: PixelMap, actual: PixelMap) {
        assertEquals("native and captured fills must retain their original pixels", true, samePixels(expected, actual))
    }
}
