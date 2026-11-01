package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerCoverCrossfadeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `loaded cover fades instead of switching immediately`() {
        val frame = mutableStateOf(PlayerCoverFrame<Painter>(PlayerCoverIdentity("a", "a"), ColorPainter(Color.Red)))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlayerCoverCrossfade(frame.value, Modifier.size(80.dp).testTag("cover"))
        }
        fun pixel() = compose.onNodeWithTag("cover").captureToImage().toPixelMap().let {
            it[it.width / 2, it.height / 2]
        }
        assertTrue(pixel().red > 0.9f)
        compose.runOnIdle {
            frame.value = PlayerCoverFrame(PlayerCoverIdentity("b", "b"), ColorPainter(Color.Blue))
        }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("The outgoing image remains at the start", pixel().red > 0.9f)
        compose.mainClock.advanceTimeBy(PLAYER_COVER_FADE_MS / 2L)
        val middle = pixel()
        assertTrue("Both images participate during the fade", middle.red > 0.05f && middle.blue > 0.05f)
        compose.mainClock.advanceTimeBy(PLAYER_COVER_FADE_MS.toLong())
        assertTrue("The replacement finishes fully visible", pixel().blue > 0.9f)
    }
}
