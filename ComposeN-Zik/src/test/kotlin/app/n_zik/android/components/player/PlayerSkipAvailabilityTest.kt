package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.media3.common.Player
import androidx.media3.common.FlagSet
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import app.n_zik.android.playback.utils.playNextIfAvailable
import app.n_zik.android.playback.utils.playPreviousIfAvailable
import app.n_zik.android.playback.utils.seekPreviousIfAvailable
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerSkipAvailabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `queue and command changes refresh skip state without recreating player`() {
        val player = mockk<Player>(relaxed = true)
        var next = false
        val listener = slot<Player.Listener>()
        val visible = mutableStateOf(true)
        every { player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) } answers { next }
        every { player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) } returns false
        every { player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) } returns true
        every { player.addListener(capture(listener)) } returns Unit
        compose.setContent {
            if (visible.value) {
                val state = rememberPlayerSkipAvailability(player).value
                Text("${state.next}/${state.previousItem}/${state.previous}")
            } else Text("gone")
        }
        compose.onNodeWithText("false/false/true").assertExists()
        compose.runOnIdle {
            next = true
            listener.captured.onEvents(player, Player.Events(FlagSet.Builder().add(Player.EVENT_TIMELINE_CHANGED).build()))
        }
        compose.onNodeWithText("true/false/true").assertExists()
        compose.runOnIdle {
            next = false
            listener.captured.onAvailableCommandsChanged(Player.Commands.EMPTY)
        }
        compose.onNodeWithText("false/false/true").assertExists()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithText("gone").assertExists()
        verify { player.removeListener(listener.captured) }
    }

    @Test fun `unavailable skip blocks seek and all prepare resume side effects`() {
        val player = mockk<Player>(relaxed = true)
        every { player.isCommandAvailable(any()) } returns false
        assertFalse(player.playNextIfAvailable())
        assertFalse(player.playPreviousIfAvailable())
        assertFalse(player.seekPreviousIfAvailable())
        verify(exactly = 0) { player.seekToNextMediaItem() }
        verify(exactly = 0) { player.seekToPreviousMediaItem() }
        verify(exactly = 0) { player.seekToPrevious() }
        verify(exactly = 0) { player.prepare() }
        verify(exactly = 0) { player.playWhenReady = any() }
    }
}
