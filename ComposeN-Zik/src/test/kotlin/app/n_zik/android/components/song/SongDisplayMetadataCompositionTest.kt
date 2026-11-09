package app.n_zik.android.components.song

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.it.fast4x.rimusic.models.Song
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SongDisplayMetadataCompositionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `mounted online row follows database edit and reset without replacing its input`() {
        val incoming = Song("track", "Online", "Artist", "3:00", "online-cover")
        val stored = MutableStateFlow<Song?>(null)
        val displayed = AtomicReference<Song?>()
        val collected = AtomicReference<Song?>()
        val observed = stored.onEach(collected::set)
        compose.setContent {
            val song = rememberSongDisplayMetadata(incoming, observed)
            SideEffect { displayed.set(song) }
        }
        compose.waitUntil { displayed.get()?.title == "Online" }
        compose.waitUntil { stored.subscriptionCount.value == 1 }
        compose.runOnIdle { stored.value = incoming.copy(title = "Edited", thumbnailUrl = "file:///custom.jpg") }
        compose.waitUntil { collected.get()?.title == "Edited" }
        compose.waitUntil {
            pumpUi()
            displayed.get()?.title == "Edited"
        }
        assertEquals("file:///custom.jpg", displayed.get()?.thumbnailUrl)
        compose.runOnIdle { stored.value = incoming.copy(title = "Reset", artistsText = null, thumbnailUrl = null) }
        compose.waitUntil { collected.get()?.title == "Reset" }
        compose.waitUntil {
            pumpUi()
            displayed.get()?.title == "Reset"
        }
        assertNull(displayed.get()?.thumbnailUrl)
        assertNull(displayed.get()?.artistsText)
    }

    private fun pumpUi() {
        // DATA snapshot notifications also need Robolectric's looper, independently of Compose frames.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

}
