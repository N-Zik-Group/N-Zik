package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.navigation.appHeaderContentInsetPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w800dp-h360dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeHomeChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hiddenLandscapeHeaderDoesNotReserveItsRowAboveBottomOrAbsentPlayer() {
        assertEquals(24f, appHeaderContentInsetPx(64f, 24f, true, 0f), 0f)
        assertEquals(56f, appHeaderContentInsetPx(64f, 24f, true, .5f), 0f)
        assertEquals(88f, appHeaderContentInsetPx(64f, 24f, true, 1f), 0f)
        // Quick Picks and other pages retain their scroll-owned initial clearance.
        assertEquals(88f, appHeaderContentInsetPx(64f, 24f, false, 0f), 0f)
    }

    @Test fun statusBackingFollowsToolbarEvenWithoutTopMiniPlayer() {
        val offset = mutableFloatStateOf(0f)
        val height = mutableIntStateOf(0)
        val scrollOffset = mutableFloatStateOf(0f)
        val motion = HomeHeaderMotionRegistry(scrollOffset)
        val owner = Any()
        motion.attach(owner) {
            offset.floatValue = homeHeaderOffsetForAppHeader(motion.offset(), 64f, height.intValue)
        }
        compose.setContent {
            CompositionLocalProvider(LocalHomeTopCover provides true) {
                Box(Modifier.size(240.dp, 300.dp).testTag("scene")) {
                    Box(Modifier.fillMaxSize().background(Color.Blue))
                    HomeCollapsingHeader(
                        headerOffset = offset,
                        headerHeight = height,
                        topContentInset = { 24.dp },
                        backgroundColor = Color.Red,
                        panelColor = Color.Red,
                        shape = RoundedCornerShape(0.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
        fun statusPixel() = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[120, 4]
        assertEquals(Color.Red, statusPixel())
        compose.runOnIdle { motion.scrollBy(-32f, 64f) }
        val halfway = statusPixel()
        assertTrue(halfway.red > 0f && halfway.red < 1f)
        assertTrue(halfway.blue > 0f && halfway.blue < 1f)
        compose.runOnIdle { motion.scrollBy(-32f, 64f) }
        assertEquals(Color.Blue, statusPixel())
        compose.runOnIdle { motion.scrollBy(64f, 64f) }
        assertEquals(Color.Red, statusPixel())
    }
}
