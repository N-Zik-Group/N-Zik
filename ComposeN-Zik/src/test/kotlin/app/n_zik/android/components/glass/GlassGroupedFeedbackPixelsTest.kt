package app.n_zik.android.components.glass

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Captures the actual For You dice/play/settings group, including the global indication. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassGroupedFeedbackPixelsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `For You held and dragged settings moves the complete connected capsule`() {
        var clicks = 0
        val settings = GlassSettings(enabled = true, quality = GlassQuality.Performance)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                GlassRippleProvider(settings) {
                    Box(Modifier.size(220.dp, 100.dp).background(Color.Black).testTag("scene")) {
                        GlassActionGroup(settings, GlassArea.ActionButtons,
                            modifier = Modifier.padding(start = 24.dp, top = 24.dp), separatorBefore = setOf(2)) {
                            listOf(R.drawable.dice, R.drawable.play, R.drawable.settings).forEachIndexed { index, icon ->
                                GlassActionGroupItem(onClick = { clicks++ }, modifier = Modifier.testTag("cell.$index")) {
                                    Icon(painterResource(icon), null, Modifier.size(GlassActionIconSize), tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val scene = compose.onNodeWithTag("scene")
        val button = compose.onNodeWithTag("cell.2")
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val neighbor = compose.onNodeWithTag("cell.0").fetchSemanticsNode().boundsInRoot
        val resting = scene.captureToImage()
        export("for-you-resting", resting)
        button.performTouchInput { down(center) }
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(350)
        fun checkFrame(name: String) {
            val actual = scene.captureToImage()
            export(name, actual)
            val before = resting.toPixelMap()
            val after = actual.toPixelMap()
            var changedInside = 0
            var changedOutside = 0
            var changedNeighbor = 0
            for (y in 0 until after.height) for (x in 0 until after.width) {
                val a = before[x, y]
                val b = after[x, y]
                val delta = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
                if (delta > .025f) {
                    if (x + .5f >= bounds.left && x + .5f < bounds.right &&
                        y + .5f >= bounds.top && y + .5f < bounds.bottom) changedInside++
                    else changedOutside++
                    if (x + .5f >= neighbor.left && x + .5f < neighbor.right &&
                        y + .5f >= neighbor.top && y + .5f < neighbor.bottom) changedNeighbor++
                }
            }
            assertTrue("$name must show real press pixels", changedInside > 30)
            assertTrue("$name must move the shared material beyond the held cell", changedOutside > 30)
            assertTrue("$name must move the distant dice glyph and its material too", changedNeighbor > 10)
            assertEquals("The drawing spring must preserve the native menu anchor", bounds,
                button.fetchSemanticsNode().boundsInRoot)
        }
        checkFrame("for-you-held")
        button.performTouchInput { moveTo(Offset(-20f, center.y)) }
        compose.mainClock.advanceTimeByFrame()
        checkFrame("for-you-dragged")
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
        export("for-you-released", scene.captureToImage())
        compose.runOnIdle { assertEquals("Dragging outside cancels the native click", 0, clicks) }
        button.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals("A fresh native tap fires exactly once", 1, clicks) }
    }

    private fun export(name: String, image: ImageBitmap) {
        val directory = File("build/reports/glass-feedback-pixels").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
