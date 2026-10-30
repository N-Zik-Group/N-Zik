package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.ui.screens.profiles.ProfileAvatarFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassProfileAvatarFrameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `avatar rim follows exact image bounds with no exterior pixels or interior distortion`() {
        val area = mutableStateOf(GlassArea.SettingsCards)
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val enabled = mutableStateOf(true)
        val page = DefaultDarkColorPalette.background0
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.size(64.dp).background(page).testTag("host"), contentAlignment = Alignment.Center) {
                    ProfileAvatarFrame(
                        size = 32.dp, area = area.value, modifier = Modifier.testTag("avatar"),
                        settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance,
                            film = 1f, tintStrength = 1f, blur = 16f,
                            areaModes = mapOf(area.value to mode.value,
                                GlassArea.ActionButtons to GlassAreaMode.GlassAndContour)),
                    ) {
                        Canvas(Modifier.size(32.dp)) {
                            drawRect(Color.Magenta, size = Size(size.width / 2, size.height))
                            drawRect(Color.Cyan, topLeft = Offset(size.width / 2, 0f),
                                size = Size(size.width / 2, size.height))
                        }
                    }
                }
            }
        }
        val avatar = compose.onNodeWithTag("avatar")
        fun verifyImageAndBounds(native: Boolean = false) {
            avatar.assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
            val image = avatar.captureToImage().toPixelMap()
            val inset = if (native) 0 else with(compose.density) { 4.dp.roundToPx() }
            for (y in inset until image.height - inset) for (x in inset until image.width - inset) {
                assertEquals(if (x < image.width / 2) Color.Magenta else Color.Cyan, image[x, y])
            }
        }
        fun verifyNoExteriorPixels() {
            val host = compose.onNodeWithTag("host").captureToImage().toPixelMap()
            val left = with(compose.density) { 16.dp.roundToPx() }
            val right = with(compose.density) { 48.dp.roundToPx() }
            for (y in 0 until host.height) for (x in 0 until host.width) {
                if (x < left || x >= right || y < left || y >= right) assertEquals(page, host[x, y])
            }
        }
        fun edgePixel(): Color {
            val image = avatar.captureToImage().toPixelMap()
            return image[0, image.height / 2]
        }
        listOf(GlassArea.SettingsCards, GlassArea.Header).forEach { role ->
            compose.runOnIdle { area.value = role; mode.value = GlassAreaMode.Disabled }
            verifyImageAndBounds(native = true)
            verifyNoExteriorPixels()
            assertEquals(Color.Magenta, edgePixel())
            listOf(GlassAreaMode.GlassOnly, GlassAreaMode.GlassAndContour, GlassAreaMode.ContourOnly).forEach { material ->
                compose.runOnIdle { mode.value = material }
                verifyImageAndBounds()
                verifyNoExteriorPixels()
                assertNotEquals(Color.Magenta, edgePixel())
            }
            compose.runOnIdle { enabled.value = false }
            verifyImageAndBounds(native = true)
            verifyNoExteriorPixels()
            assertEquals(Color.Magenta, edgePixel())
            compose.runOnIdle { enabled.value = true }
        }
    }
}
