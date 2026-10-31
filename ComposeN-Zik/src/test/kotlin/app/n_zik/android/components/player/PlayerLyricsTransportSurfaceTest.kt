package app.n_zik.android.components.player

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLyricsTransportSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `landscape lyrics fill follows transport role and jelly requires its material`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val master = mutableStateOf(true)
        val source = MutableInteractionSource()
        var renderedMode: GlassAreaMode? = null
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape)) {
                val settings = GlassSettings(enabled = master.value, quality = GlassQuality.Performance,
                    film = 1f, rim = 1f, tintStrength = 0f, rippleIntensity = 0f,
                    // Shared actions stay disabled: the real transport role must be reselected.
                    areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled),
                    contextModes = mapOf(GlassContextArea(GlassScope.PlayerTransport, GlassArea.ActionButtons) to mode.value))
                SideEffect { renderedMode = settings.inScope(GlassScope.PlayerTransport).mode(GlassArea.ActionButtons) }
                Box(Modifier.size(100.dp).background(Color.Black).testTag("lyrics.transport"),
                    contentAlignment = Alignment.Center) {
                    PlayerTransportControlMotion(source, settings = settings) {
                        // Production helper used by the actual landscape LyricsScreen Play fill.
                        Box(Modifier.size(45.dp).playerTransportFilledBackground(Color.Blue, settings = settings))
                        Box(Modifier.size(24.dp).align(Alignment.Center).background(Color.Cyan))
                    }
                }
            }
        }
        fun pixels(): PixelMap {
            if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val image = compose.onNodeWithTag("lyrics.transport").captureToImage().toPixelMap()
            assertEquals("Capture must observe the current resolved role", mode.value, renderedMode)
            return image
        }
        fun changed(a: PixelMap, b: PixelMap): Int {
            var count = 0
            for (y in 0 until a.height) for (x in 0 until a.width) if (a[x, y] != b[x, y]) count++
            return count
        }
        fun glyphWidth(image: PixelMap): Int {
            val columns = mutableSetOf<Int>()
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val c = image[x, y]
                if (c.red < .2f && c.green > .8f && c.blue > .8f) columns.add(x)
            }
            return columns.size
        }
        val disabled = pixels()
        compose.runOnIdle { mode.value = GlassAreaMode.ContourOnly }
        val contour = pixels()
        val contourChanges = changed(disabled, contour)
        assertTrue("ContourOnly must decorate the preserved native fill ($contourChanges changed pixels)", contourChanges > 10)
        compose.runOnIdle { mode.value = GlassAreaMode.GlassOnly }
        val glass = pixels()
        compose.runOnIdle { mode.value = GlassAreaMode.GlassAndContour }
        val both = pixels()
        assertTrue("GlassOnly must remove the selected rim", changed(glass, both) > 10)

        // Pause only while measuring the press animation; normal mode changes above
        // must complete recomposition and the Android draw before their pixel capture.
        compose.mainClock.autoAdvance = false
        for (value in GlassAreaMode.entries) {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { mode.value = value }
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(160)
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(1600)
            val resting = pixels()
            val press = PressInteraction.Press(Offset(20f, 20f))
            compose.runOnIdle { source.tryEmit(press) }
            shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(600)
            val held = pixels()
            if (value.hasMaterial) assertTrue("Material $value moves its actual foreground",
                glyphWidth(held) > glyphWidth(resting))
            else assertEquals("Native/contour $value must not acquire jelly", 0, changed(resting, held))
            compose.runOnIdle { source.tryEmit(PressInteraction.Release(press)) }
            compose.mainClock.advanceTimeBy(1600)
        }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { master.value = false; mode.value = GlassAreaMode.Disabled }
        val native = pixels()
        for (value in GlassAreaMode.entries) {
            compose.runOnIdle { mode.value = value }
            assertEquals("Master OFF preserves the native fill for $value", 0, changed(native, pixels()))
        }
    }
}
