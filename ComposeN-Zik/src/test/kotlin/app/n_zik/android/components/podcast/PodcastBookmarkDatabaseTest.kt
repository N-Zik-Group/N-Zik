package app.n_zik.android.components.podcast

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.models.Playlist
import app.n_zik.android.core.database.BookmarkStateManager
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
// runBlocking bridges synchronous JUnit entrypoints to Room's suspend transaction APIs.
class PodcastBookmarkDatabaseTest {
    private lateinit var db: DatabaseInitializer
    private val podcastId = "MPSPPLXK2KwV2tv0jwie5sRfW9Mrdz3gyZp9Vr"

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries().build()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
    }

    @After fun cleanup() {
        unmockkObject(DatabaseInitializer.Companion)
        db.close()
    }

    @Test fun `search and podcast page share create state and removal`() = runBlocking {
        assertEquals(PodcastBookmarkOperation.Create, togglePodcastBookmark(podcastId, "Podcast", emptyList()))
        val saved = Database.playlistTable.findByBrowseId(podcastId).first()
        assertEquals(podcastId, saved?.browseId)
        assertEquals(false, saved?.isYoutubePlaylist)
        assertEquals(false, saved?.isEditable)
        assertEquals(true, BookmarkStateManager.getPlaylistBookmarkStates(listOf(podcastId)).first()[podcastId])
        assertEquals(PodcastBookmarkOperation.Remove, togglePodcastBookmark(podcastId, "Podcast", emptyList()))
        assertNull(Database.playlistTable.findByBrowseId(podcastId).first())
        assertTrue(BookmarkStateManager.getPlaylistBookmarkStates(listOf(podcastId)).first().isEmpty())
    }

    @Test fun `old search remote bit is removed locally and editable import is preserved`() = runBlocking {
        Database.playlistTable.insert(Playlist(name = "Old search bookmark", browseId = podcastId,
            isEditable = false, isYoutubePlaylist = true))
        assertEquals(PodcastBookmarkOperation.Remove, togglePodcastBookmark(podcastId, "Podcast", emptyList()))
        assertNull(Database.playlistTable.findByBrowseId(podcastId).first())
        Database.playlistTable.insert(Playlist(name = "Edited import", browseId = podcastId,
            isEditable = true, isYoutubePlaylist = false))
        val imported = Database.playlistTable.findByBrowseId(podcastId).first()
        assertEquals(PodcastBookmarkOperation.ExistingImport, togglePodcastBookmark(podcastId, "New title", emptyList()))
        assertEquals(imported, Database.playlistTable.findByBrowseId(podcastId).first())
    }

    @Test fun `ordinary local playlist remains unbookmarked in batch state`() = runBlocking {
        Database.playlistTable.insert(Playlist(name = "Local import", browseId = "PL123", isYoutubePlaylist = false))
        val states = BookmarkStateManager.getPlaylistBookmarkStates(listOf("PL123", podcastId)).first()
        assertFalse(states.getValue("PL123"))
        assertFalse(states.containsKey(podcastId))
    }

    @Test fun `bookmark stores loaded episodes and removes only its own mappings`() = runBlocking {
        val episode = MediaItem.Builder().setMediaId("podcast-episode-1")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Episode 1").setArtist("Host").build())
            .build()
        assertEquals(PodcastBookmarkOperation.Create, togglePodcastBookmark(podcastId, "Podcast", listOf(episode)))
        val saved = requireNotNull(Database.playlistTable.findByBrowseId(podcastId).first())
        val songs = db.songPlaylistMapTable.songsByPositionDirect(saved.id)
        assertEquals(listOf("podcast-episode-1"), songs.map { it.id })
        assertEquals("Episode 1", songs.single().title)
        val otherId = Database.playlistTable.insert(Playlist(name = "Other playlist"))
        Database.songPlaylistMapTable.map(songId = episode.mediaId, playlistId = otherId)
        assertEquals(PodcastBookmarkOperation.Remove, togglePodcastBookmark(podcastId, "Podcast", listOf(episode)))
        assertTrue(db.songPlaylistMapTable.songsByPositionDirect(saved.id).isEmpty())
        assertEquals(listOf(episode.mediaId), db.songPlaylistMapTable.songsByPositionDirect(otherId).map { it.id })
    }

    private fun episode(id: String): MediaItem = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(id).setArtist("Host").build()).build()

    @Test fun `refresh exact podcast identity replaces target mappings and preserves row and other playlists`() = runBlocking {
        togglePodcastBookmark(podcastId, "Saved title", listOf(episode("old-episode")))
        val saved = requireNotNull(Database.playlistTable.findByBrowseId(podcastId).first())
            .copy(isYoutubePlaylist = true, isAutoSync = true)
        Database.playlistTable.update(saved)
        val otherId = Database.playlistTable.insert(Playlist(name = "Unrelated"))
        Database.songPlaylistMapTable.map(songId = "old-episode", playlistId = otherId)
        assertTrue(refreshPodcastPlaylist(saved.id, podcastId) { id ->
            assertEquals(podcastId, id)
            listOf(episode("new-episode-1"), episode("new-episode-2"))
        })
        assertEquals(saved, Database.playlistTable.findById(saved.id).first())
        assertEquals(listOf("new-episode-1", "new-episode-2"),
            db.songPlaylistMapTable.songsByPositionDirect(saved.id).map { it.id })
        assertEquals(listOf("old-episode"), db.songPlaylistMapTable.songsByPositionDirect(otherId).map { it.id })
    }

    @Test fun `failed podcast refresh preserves existing episodes`() = runBlocking {
        togglePodcastBookmark(podcastId, "Podcast", listOf(episode("old-episode")))
        val saved = requireNotNull(Database.playlistTable.findByBrowseId(podcastId).first())
        assertFalse(refreshPodcastPlaylist(saved.id, podcastId) { null })
        assertEquals(saved, Database.playlistTable.findById(saved.id).first())
        assertEquals(listOf("old-episode"), db.songPlaylistMapTable.songsByPositionDirect(saved.id).map { it.id })
    }

    @Test fun `refresh preserves editable podcast imports and rejects changed identities`() = runBlocking {
        togglePodcastBookmark(podcastId, "Podcast", listOf(episode("old-episode")))
        val saved = requireNotNull(Database.playlistTable.findByBrowseId(podcastId).first())
        Database.playlistTable.update(saved.copy(isEditable = true))
        assertFalse(refreshPodcastPlaylist(saved.id, podcastId) { listOf(episode("new-episode")) })
        assertEquals(listOf("old-episode"), db.songPlaylistMapTable.songsByPositionDirect(saved.id).map { it.id })
        assertEquals(PodcastBookmarkOperation.ExistingImport, removeSavedPodcastBookmark(saved.id))
        Database.playlistTable.update(saved.copy(browseId = "PLother"))
        assertFalse(refreshPodcastPlaylist(saved.id, podcastId) { listOf(episode("new-episode")) })
        assertEquals(listOf("old-episode"), db.songPlaylistMapTable.songsByPositionDirect(saved.id).map { it.id })
    }

    @Test fun `empty podcast feed preserves snapshot and missing bookmark is not recreated`() = runBlocking {
        togglePodcastBookmark(podcastId, "Podcast", listOf(episode("old-episode")))
        val saved = requireNotNull(Database.playlistTable.findByBrowseId(podcastId).first())
        assertFalse(refreshPodcastPlaylist(saved.id, podcastId) { emptyList() })
        assertEquals(listOf("old-episode"), db.songPlaylistMapTable.songsByPositionDirect(saved.id).map { it.id })
        Database.playlistTable.delete(saved)
        assertFalse(refreshPodcastPlaylist(saved.id, podcastId) { listOf(episode("new-episode")) })
        assertEquals(PodcastBookmarkOperation.Remove, removeSavedPodcastBookmark(saved.id))
        assertNull(Database.playlistTable.findByBrowseId(podcastId).first())
    }
}
