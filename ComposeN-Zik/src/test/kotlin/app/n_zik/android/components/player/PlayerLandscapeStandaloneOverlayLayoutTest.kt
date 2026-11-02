package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w1200dp-h600dp-land")
class PlayerLandscapeStandaloneOverlayLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `standalone overlays recover the full artwork column when the cover is hidden`() {
        val lyrics = mutableStateOf(false)
        val visualizer = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.requiredSize(1000.dp, 360.dp).testTag("viewport")) {
                PlayerLandscapeColumns(contentInsets = WindowInsets(0), artwork = {
                    PlayerLandscapeThumbnailFrame(
                        contentPadding = playerThumbnailPadding(90f, landscape = true),
                        artworkVisible = playerCoverArtworkVisible(true, lyrics.value, false,
                            visualizer.value, false),
                    ) {
                        Box(Modifier.fillMaxSize().testTag("cover"))
                    }
                    if (visualizer.value) Box(Modifier.fillMaxSize().testTag("visualizer"))
                    if (lyrics.value) Box(Modifier.weight(1f).fillMaxWidth().testTag("lyrics"))
                }, controls = {})
            }
        }
        compose.onNodeWithTag("cover").assertIsDisplayed()
        for (overlay in listOf("lyrics", "visualizer")) {
            compose.runOnIdle {
                lyrics.value = overlay == "lyrics"
                visualizer.value = overlay == "visualizer"
            }
            compose.onNodeWithTag("cover").assertDoesNotExist()
            compose.onNodeWithTag(overlay).assertIsDisplayed()
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val bounds = compose.onNodeWithTag(overlay).fetchSemanticsNode().boundsInRoot
            assertEquals("$overlay starts at the top of the artwork column", viewport.top, bounds.top, 1f)
            assertEquals("$overlay retains the entire column height", viewport.height, bounds.height, 1f)
        }
        compose.runOnIdle { visualizer.value = false }
        compose.onNodeWithTag("cover").assertIsDisplayed()
        compose.onNodeWithTag("visualizer").assertDoesNotExist()
    }
}
