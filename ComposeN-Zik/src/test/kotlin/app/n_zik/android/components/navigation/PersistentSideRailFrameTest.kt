package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w800dp-h360dp-land-mdpi")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PersistentSideRailFrameTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setActiveProfile("default", context)
        context.preferences.edit().putString(playerPositionKey, PlayerPosition.Bottom.name).commit()
    }

    @Test fun playbackKeepsLeftRailAtItsSystemBounds() = verifyPlaybackBounds(NavigationBarPosition.Left)

    @Test fun playbackKeepsRightRailAtItsSystemBounds() = verifyPlaybackBounds(NavigationBarPosition.Right)

    private fun verifyPlaybackBounds(position: NavigationBarPosition) {
        val hasMedia = mutableStateOf(false)
        val playerBottom = mutableIntStateOf(16)
        val player = mockk<Player>()
        val binder = mockk<PlayerServiceModern.Binder>()
        every { binder.player } returns player
        every { player.currentMediaItem } answers {
            if (hasMedia.value) MediaItem.Builder().setMediaId("landscape-song").build() else null
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalPlayerServiceBinder provides if (hasMedia.value) binder else null,
                LocalPlayerAwareWindowInsets provides WindowInsets(bottom = playerBottom.intValue),
            ) {
                Box(Modifier.size(600.dp, 320.dp).testTag("scene")) {
                    PersistentSideRailFrame(position, safeInsets = WindowInsets(12, 24, 20, 16)) {
                        Box(Modifier.fillMaxSize().testTag("rail"))
                    }
                }
            }
        }
        val rail = compose.onNodeWithTag("rail")
        val initial = rail.fetchSemanticsNode().boundsInRoot
        val scene = compose.onNodeWithTag("scene").fetchSemanticsNode().boundsInRoot
        assertEquals(scene.top + 24f, initial.top, 0f)
        assertEquals(scene.bottom - 16f, initial.bottom, 0f)
        if (position == NavigationBarPosition.Right) {
            assertEquals(scene.right - 20f, initial.right, 0f)
        } else {
            assertEquals(scene.left + 12f, initial.left, 0f)
        }

        // Expanded, compact and dismissed minis alter page clearance, never the side rail.
        for ((media, bottom) in listOf(true to 112, true to 64, false to 16)) {
            compose.runOnIdle {
                hasMedia.value = media
                playerBottom.intValue = bottom
            }
            assertEquals(initial, rail.fetchSemanticsNode().boundsInRoot)
        }
    }
}
