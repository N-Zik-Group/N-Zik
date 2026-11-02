package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.ui.screens.rewind.RewindPeriod
import app.n_zik.android.components.ui.screens.rewind.emptyRewindData
import app.n_zik.android.components.ui.screens.rewind.slides.RewindIntroCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RewindCoveredClockTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `intro wave freezes under opaque coverage and resumes without replaying its reveal`() {
        var covered by mutableStateOf(false)
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
                LocalGlassUnderlayCovered provides covered) {
                Box(Modifier.fillMaxSize().testTag("intro")) {
                    RewindIntroCard(emptyRewindData(RewindPeriod.Global), "Listener", 0, 16, true, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(4_000)
        fun pixels(): IntArray {
            val image = compose.onNodeWithTag("intro").captureToImage()
            return IntArray(image.width * image.height).also { image.readPixels(it) }
        }
        val live = pixels()
        compose.mainClock.advanceTimeBy(240)
        assertFalse("Production intro wave must actually animate before suspension", live.contentEquals(pixels()))
        compose.runOnIdle { covered = true }
        compose.mainClock.advanceTimeByFrame()
        val frozen = pixels()
        compose.mainClock.advanceTimeBy(480)
        assertTrue("A fully hidden wave must retain its phase", frozen.contentEquals(pixels()))
        compose.onNode(hasClickAction()).assertIsEnabled()
        compose.runOnIdle { covered = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(240)
        assertFalse("Uncovering resumes the same wave", frozen.contentEquals(pixels()))
        compose.onNode(hasClickAction()).assertIsEnabled()
    }
}
