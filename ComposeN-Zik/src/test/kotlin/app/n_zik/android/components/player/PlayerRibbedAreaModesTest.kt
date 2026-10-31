package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassOpticsTransitionProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerRibbedAreaModesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real ribbed producer honors four modes above native fallback and keeps its cheap contour`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val master = mutableStateOf(true)
        val progress = mutableStateOf(1f)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape),
                LocalGlassOpticsTransitionProgress provides progress.value) {
                Box(Modifier.size(100.dp).background(Color.Black).testTag("ribbed")) {
                    val settings = GlassSettings(enabled = master.value, quality = GlassQuality.Performance,
                        film = 1f, rim = 1f, tintStrength = 0f,
                        areaModes = mapOf(GlassArea.ActionButtons to mode.value))
                    // Same producer/fallback order used by both real legacy control variants.
                    if (!PlayerTransportRibbedSurface(Color.Blue, settings = settings)) {
                        Box(Modifier.size(100.dp).clip(PlayerTransportRibbedShape).background(Color.Blue))
                    }
                }
            }
        }
        fun pixels() = compose.onNodeWithTag("ribbed").captureToImage().toPixelMap()
        fun set(value: GlassAreaMode, opticalProgress: Float = 1f): PixelMap {
            compose.runOnIdle { mode.value = value; progress.value = opticalProgress }
            return pixels()
        }
        fun changed(first: PixelMap, second: PixelMap): Int {
            var count = 0
            for (y in 0 until first.height) for (x in 0 until first.width)
                if (first[x, y] != second[x, y]) count++
            return count
        }
        val disabled = set(GlassAreaMode.Disabled)
        val contour = set(GlassAreaMode.ContourOnly)
        assertTrue("ContourOnly must draw above the later native image", changed(disabled, contour) > 25)
        val glass = set(GlassAreaMode.GlassOnly, 0f)
        val cheapContour = set(GlassAreaMode.GlassAndContour, 0f)
        assertTrue("Covered material retains its selected contour", changed(glass, cheapContour) > 25)
        for (image in listOf(disabled, contour, glass, cheapContour)) {
            assertEquals(Color.Blue, image[image.width / 2, image.height / 2])
            assertEquals(Color.Black, image[0, 0])
        }
        compose.runOnIdle { master.value = false }
        val native = pixels()
        for (value in GlassAreaMode.entries) {
            assertEquals("Master OFF keeps every native pixel for $value", 0, changed(native, set(value)))
        }
    }
}
