package app.n_zik.android.components.playlist

import android.app.Application
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
// runBlocking bridges JUnit's synchronous entrypoints to Room's suspend transaction API.
class SourcePlaylistTitleRefreshTest {
    private lateinit var db: DatabaseInitializer
    private lateinit var playlist: Playlist

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries().build()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
        val row = Playlist(name = "Saved", browseId = "MPSPPLpodcast", isEditable = false)
        playlist = row.copy(id = db.playlistTable.insert(row))
    }

    @After fun cleanup() {
        unmockkObject(DatabaseInitializer.Companion)
        db.close()
    }

    private fun media(id: String, title: String?): MediaItem = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build()

    private fun seed(id: String, title: String): Song {
        val song = Song(id = id, title = title, artistsText = "Local artist", durationText = "4:20",
            thumbnailUrl = "https://example.test/local-cover", likedAt = 456L, totalPlayTimeMs = 98765L,
            position = 12, isYoutubeSong = true).also { it.playCount = 17 }
        db.songTable.insertIgnore(song)
        db.songPlaylistMapTable.map(songId = id, playlistId = playlist.id)
        return song
    }

    @Test fun `source rename updates existing title without changing statistics likes or other metadata`() = runBlocking {
        val before = seed("episode", "Old source title")
        assertTrue(refreshPlaylistSource(playlist) { listOf(media("episode", "New source title")) })
        val after = requireNotNull(db.songTable.findByIdDirect("episode"))
        assertEquals(before.copy(title = "New source title"), after)
        assertEquals(17, after.playCount)
        assertEquals(listOf("episode"), db.songPlaylistMapTable.songsByPositionDirect(playlist.id).map { it.id })
    }

    @Test fun `custom modified titles and blank missing source titles remain unchanged`() = runBlocking {
        val titles = mapOf("custom" to "modified:My custom title", "upper" to "MODIFIED:Another custom title",
            "blank" to "Retain on blank", "missing" to "Retain on missing")
        titles.forEach { (id, title) -> seed(id, title) }
        assertTrue(refreshPlaylistSource(playlist) { listOf(media("custom", "Remote replacement"),
            media("upper", "Remote replacement"), media("blank", "  "), media("missing", null)) })
        titles.forEach { (id, title) ->
            assertEquals(title, db.songTable.findByIdDirect(id)?.title)
            assertEquals(17, db.songTable.findByIdDirect(id)?.playCount)
        }
    }

    @Test fun `failed or rejected source does not update existing title`() = runBlocking {
        val before = seed("episode", "Keep old title")
        assertFalse(refreshPlaylistSource(playlist) { null })
        assertFalse(refreshPlaylistSource(playlist) { emptyList() })
        assertFalse(refreshPlaylistSource(playlist) {
            db.playlistTable.update(playlist.copy(browseId = "PLdifferent"))
            listOf(media("episode", "Must not apply"))
        })
        assertEquals(before, db.songTable.findByIdDirect("episode"))
        assertEquals(17, db.songTable.findByIdDirect("episode")?.playCount)
    }
}
