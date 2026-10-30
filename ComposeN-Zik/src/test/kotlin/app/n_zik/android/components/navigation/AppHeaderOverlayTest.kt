package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppHeaderOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `top tabs stay below status bar while only the app header leaves`() {
        assertEquals(88, appHeaderContentTopPx(88, 0, 24, true))
        assertEquals(56, appHeaderContentTopPx(88, -32, 24, true))
        assertEquals(24, appHeaderContentTopPx(88, -64, 24, true))
        assertEquals(24, appHeaderContentTopPx(88, -160, 24, true))
        assertEquals(0, appHeaderContentTopPx(88, -160, 24, false))
    }

    @Test fun `without top tabs hidden header releases the status bar reserve too`() {
        assertEquals(88, appHeaderContentTopPx(88, 0, 24, false))
        assertEquals(44, appHeaderContentTopPx(88, -32, 24, false))
        assertEquals(0, appHeaderContentTopPx(88, -64, 24, false))
        assertEquals(0, appHeaderContentTopPx(88, -160, 24, false))
    }

    @Test fun `scroll consumption includes released status padding and leaves the remainder to the list`() {
        val partial = appHeaderScrollStep(0f, -44f, 64f, 24f, false)
        assertEquals(-32f, partial.offsetPx, .001f)
        assertEquals(-44f, partial.consumedY, .001f)
        val hidden = appHeaderScrollStep(partial.offsetPx, -60f, 64f, 24f, false)
        assertEquals(-64f, hidden.offsetPx, .001f)
        assertEquals(-44f, hidden.consumedY, .001f)
        val revealed = appHeaderScrollStep(hidden.offsetPx, 100f, 64f, 24f, false)
        assertEquals(0f, revealed.offsetPx, .001f)
        assertEquals(88f, revealed.consumedY, .001f)
    }

    @Test fun `retained status padding preserves one to one header consumption`() {
        val partial = appHeaderScrollStep(0f, -32f, 64f, 24f, true)
        assertEquals(-32f, partial.offsetPx, .001f)
        assertEquals(-32f, partial.consumedY, .001f)
        val hidden = appHeaderScrollStep(partial.offsetPx, -60f, 64f, 24f, true)
        assertEquals(-64f, hidden.offsetPx, .001f)
        assertEquals(-32f, hidden.consumedY, .001f)
    }

    @Test fun `overlay header owns only its visible bounds and the page remains interactive`() {
        val hidden = mutableStateOf(false)
        var headerClicks = 0
        var pageClicks = 0
        compose.setContent {
            AppHeaderOverlay(
                modifier = Modifier.size(300.dp, 400.dp).testTag("scene"),
                header = {
                    Box(Modifier.offset { IntOffset(0, if (hidden.value) -64.dp.roundToPx() else 0) }
                        .fillMaxWidth().height(64.dp).testTag("header")
                        .clickable { headerClicks++ })
                },
            ) {
                Box(Modifier.fillMaxSize().testTag("page").clickable { pageClicks++ })
            }
        }
        compose.onNodeWithTag("header").assertHeightIsEqualTo(64.dp)
        // The overlay owns no Scaffold slot: the page always measures to the entire scene.
        compose.onNodeWithTag("page").assertHeightIsEqualTo(400.dp)
        compose.onNodeWithTag("scene").performTouchInput { click(Offset(center.x, height * .08f)) }
        compose.onNodeWithTag("scene").performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(1, headerClicks)
            assertEquals(1, pageClicks)
            hidden.value = true
        }
        compose.onNodeWithTag("page").assertHeightIsEqualTo(400.dp)
        compose.onNodeWithTag("scene").performTouchInput { click(Offset(center.x, height * .08f)) }
        compose.runOnIdle {
            assertEquals(1, headerClicks)
            assertEquals(2, pageClicks)
        }
    }

    @Test fun `top chrome covers scrolling rows and clips the departing header`() {
        val navTop = mutableStateOf(60)
        compose.setContent {
            AppHeaderOverlay(
                modifier = Modifier.size(200.dp, 160.dp).testTag("topScene"),
                headerClipBottomPx = { navTop.value },
                header = { Box(Modifier.fillMaxWidth().height(80.dp).background(Color.Green)) },
                navigation = {
                    Box(Modifier.offset { IntOffset(0, navTop.value) }
                        .fillMaxWidth().height(40.dp).background(Color.Blue))
                },
                topContentCover = {
                    Box(Modifier.fillMaxWidth().height(110.dp).background(Color.Red))
                },
            ) {
                Box(Modifier.fillMaxSize().background(Color.Yellow))
            }
        }
        fun pixel(y: Int) = compose.onNodeWithTag("topScene").captureToImage().toPixelMap()[100, y]
        assertEquals(Color.Green, pixel(20))
        assertEquals(Color.Blue, pixel(70))
        assertEquals(Color.Red, pixel(105))
        assertEquals(Color.Yellow, pixel(130))
        compose.runOnIdle { navTop.value = 30 }
        assertEquals(Color.Green, pixel(20))
        assertEquals(Color.Blue, pixel(40))
        assertEquals(Color.Red, pixel(105))
    }
}
