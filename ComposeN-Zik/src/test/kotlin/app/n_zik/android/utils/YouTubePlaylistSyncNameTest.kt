package app.n_zik.android.utils

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.models.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class YouTubePlaylistSyncNameTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `remote snapshots stay ordinary playlists even when their title matches Rewind`() {
        val name = context.syncedYouTubePlaylistName(RewindPlaylists.ALLTIME_NAME, existing = null)

        assertFalse(RewindPlaylists.isReservedName(name))
        assertEquals(
            context.getString(R.string.rewind_title) + " " + context.getString(R.string.rewind_alltime_name),
            name,
        )
        assertEquals(
            "pinned:$name",
            context.syncedYouTubePlaylistName(RewindPlaylists.ALLTIME_NAME, Playlist(name = "pinned:Old title")),
        )
    }

    @Test
    fun `a remote refresh preserves a locally renamed playlist`() {
        val existing = Playlist(name = "pinned:modified:My title")

        assertEquals(existing.name, context.syncedYouTubePlaylistName(RewindPlaylists.ALLTIME_NAME, existing))
        assertEquals("New title", context.syncedYouTubePlaylistName("New title", existing = null))
    }
}
