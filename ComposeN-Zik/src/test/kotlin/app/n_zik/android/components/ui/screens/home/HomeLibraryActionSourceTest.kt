package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class HomeLibraryActionSourceTest {
    @get:Rule val compose = createComposeRule()

    // runBlocking bridges retained playback callbacks to synchronous JUnit assertions.
    @Test fun `retained toolbar callbacks follow filter selection and cached song changes`() = runBlocking {
        fun playlist(id: Long) = PlaylistPreview(Playlist(id, "Playlist $id", "PL$id"), 1)
        val targets = mutableStateOf(listOf(playlist(1)))
        val cached = mutableStateOf(listOf(Song.makePlaceholder("cached-1")))
        lateinit var firstSource: HomeLibraryActionSource
        lateinit var retainedPlayback: suspend () -> List<Song>
        lateinit var retainedExport: () -> List<Song>
        lateinit var retainedTargets: () -> List<PlaylistPreview>
        var initialized = false
        compose.setContent {
            val source = rememberHomeLibraryActionSource(targets.value, cached.value) { rows ->
                rows.map { Song.makePlaceholder("loaded-${it.playlist.id}") }
            }
            SideEffect {
                if (!initialized) {
                    firstSource = source
                    retainedPlayback = source::loadSongs
                    retainedExport = { source.cachedSongs }
                    retainedTargets = { source.targets }
                    initialized = true
                } else assertSame(firstSource, source)
            }
        }
        compose.waitForIdle()
        assertEquals(listOf("loaded-1"), retainedPlayback().map { it.id })
        assertEquals(listOf("cached-1"), retainedExport().map { it.id })
        compose.runOnIdle {
            // Same selection count, different visible playlist and reset async cache.
            targets.value = listOf(playlist(2))
            cached.value = emptyList()
        }
        compose.waitForIdle()
        assertEquals(listOf(2L), retainedTargets().map { it.playlist.id })
        assertEquals(listOf("loaded-2"), retainedPlayback().map { it.id })
        assertEquals(emptyList<Song>(), retainedExport())
        compose.runOnIdle { cached.value = listOf(Song.makePlaceholder("cached-2")) }
        compose.waitForIdle()
        assertEquals(listOf("cached-2"), retainedExport().map { it.id })
        compose.runOnIdle { targets.value = emptyList(); cached.value = emptyList() }
        compose.waitForIdle()
        assertEquals(emptyList<Song>(), retainedPlayback())
        assertEquals(emptyList<Song>(), retainedExport())
    }
}
