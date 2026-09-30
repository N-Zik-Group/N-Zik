package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.it.fast4x.rimusic.repository.QuickPicksRepository
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_LUCKY_SHUFFLE
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test-only application: MainApplication.onCreate() migrates credentials via
 * AndroidKeyStore (MasterKey), which is not available on the Robolectric JVM.
 * MainApplication is final, so Dependencies.application is a mock wired to
 * this app's context — enough for the Room DB under test (same pattern as
 * `MediaItemUtilsTest`).
 */
class TestApplication : Application()

/**
 * Issue #777 — consumer-side pin of [AutoSessionCallback.onSetMediaItems]: the
 * selection mediaIds emitted by the browse detail handlers must resolve to a
 * NON-empty queue built from the right song list, with the right start index.
 *
 * The parser alone is pinned by `AutoMediaIdContractTest`; this class proves the
 * resolver actually consumes those ids — a regression that makes
 * `parseSongSelection` return null for well-formed ids, drops the
 * `playlistSongsById` lookup, or inverts the lucky-shuffle fallback would
 * leave `AutoMediaIdContractTest` green while breaking playback on the head unit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoSessionCallbackSelectionTest {

    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        context = app
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
        callback = AutoSessionCallback(context, Database, mockk(relaxed = true))
        AutoSearchState.clear()
    }

    @After
    fun tearDown() {
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
        AutoSearchState.clear()
    }

    /**
     * The shared [DatabaseInitializer.Instance] has no `allowMainThreadQueries()`,
     * so every DAO call must run off the main thread (same pattern as
     * `MediaItemUtilsTest`). runBlocking is forced by the synchronous test API
     * (AGENTS.md runBlocking exception).
     */
    private suspend fun <T> onDatabase(block: suspend () -> T): T = withContext(NzikDispatchers.DATA) { block() }

    private suspend fun upsertSong(id: String, liked: Boolean = false) {
        onDatabase {
            // totalPlayTimeMs > 0: `sortAll(excludeHidden = true)` (the lucky-shuffle
            // fallback) only sees songs that have been played at least once.
            Database.songTable.upsert(
                Song(id = id, title = "t-$id", durationText = null, thumbnailUrl = null, likedAt = if (liked) 1L else null, totalPlayTimeMs = 1000)
            )
        }
    }

    /** Drives the public selection entry point and returns the resolved queue. */
    private fun setMediaItems(mediaId: String, startIndex: Int = 0): MediaSession.MediaItemsWithStartPosition {
        // The session is never touched by the resolution path — a mock is enough.
        val session = mockk<MediaSession>()
        val future = callback.onSetMediaItems(
            session,
            mockk<MediaSession.ControllerInfo>(),
            mutableListOf(MediaItem.Builder().setMediaId(mediaId).build()),
            startIndex,
            0L
        )
        // ListenableFuture.get() is a blocking API — runBlocking is forced here
        // (AGENTS.md runBlocking exception).
        return runBlocking { future.get() }
    }

    @Test
    fun `local numeric playlist shuffle resolves its own songs`() {
        runBlocking {
            onDatabase {
                Database.playlistTable.insertIgnore(Playlist(id = 123, name = "PL 123"))
                upsertSong("p123-1")
                upsertSong("p123-2")
                Database.songPlaylistMapTable.map("p123-1", 123)
                Database.songPlaylistMapTable.map("p123-2", 123)
            }
        }
        val result = setMediaItems("PLAYLIST_SHUFFLE/123")
        assertEquals(setOf("p123-1", "p123-2"), result.mediaItems.map { it.mediaId }.toSet())
    }

    @Test
    fun `online playlist shuffle resolves the songs scoped to that playlist`() {
        AutoSearchState.playlistSongsById =
            mapOf("OLAKabc" to listOf(Song.makePlaceholder("ol-1"), Song.makePlaceholder("ol-2")))
        val result = setMediaItems("PLAYLIST_SHUFFLE/OLAKabc")
        assertEquals(setOf("ol-1", "ol-2"), result.mediaItems.map { it.mediaId }.toSet())
    }

    @Test
    fun `favorites pseudo-playlist shuffle resolves the local favorites`() {
        // Pre-fix this id resolved through the in-memory map (never populated for
        // pseudo ids) and produced an empty queue — "Unable to perform the selection".
        runBlocking {
            onDatabase {
                upsertSong("fav-1", liked = true)
                upsertSong("fav-2", liked = true)
                upsertSong("not-fav")
            }
        }
        val result = setMediaItems("PLAYLIST_SHUFFLE/FAVORITES")
        assertEquals(setOf("fav-1", "fav-2"), result.mediaItems.map { it.mediaId }.toSet())
    }

    @Test
    fun `artist detail selection resolves the artist queue with the right start index`() {
        runBlocking {
            onDatabase {
                upsertSong("art-1")
                upsertSong("art-2")
                Database.artistTable.insertIgnore(Artist(id = "UCartist"))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "art-1", artistId = "UCartist"))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "art-2", artistId = "UCartist"))
            }
        }
        val result = setMediaItems("artist/UCartist/art-2")
        assertEquals(setOf("art-1", "art-2"), result.mediaItems.map { it.mediaId }.toSet())
        assertEquals("art-2", result.mediaItems[result.startIndex].mediaId)
    }

    @Test
    fun `album detail selection resolves the album queue with the right start index`() {
        runBlocking {
            onDatabase {
                upsertSong("alb-1")
                upsertSong("alb-2")
                Database.albumTable.insertIgnore(Album(id = "LOCAL_ALBUM_9"))
                Database.songAlbumMapTable.map("alb-1", "LOCAL_ALBUM_9", 0)
                Database.songAlbumMapTable.map("alb-2", "LOCAL_ALBUM_9", 1)
            }
        }
        val result = setMediaItems("album/LOCAL_ALBUM_9/alb-1")
        assertEquals(setOf("alb-1", "alb-2"), result.mediaItems.map { it.mediaId }.toSet())
        assertEquals("alb-1", result.mediaItems[result.startIndex].mediaId)
    }

    @Test
    fun `lucky shuffle falls back to the local library when quick picks are empty`() {
        runBlocking {
            onDatabase {
                upsertSong("lib-1")
                upsertSong("lib-2")
            }
        }
        mockkObject(QuickPicksRepository)
        try {
            every { QuickPicksRepository.trendingList } returns MutableStateFlow<List<Song>>(emptyList())
            every { QuickPicksRepository.relatedPage } returns MutableStateFlow(null)
            val result = setMediaItems(ID_LUCKY_SHUFFLE)
            assertEquals(setOf("lib-1", "lib-2"), result.mediaItems.map { it.mediaId }.toSet())
        } finally {
            unmockkObject(QuickPicksRepository)
        }
    }

    @Test
    fun `malformed detail selection resolves to an empty queue without throwing`() {
        // The legacy two-segment "artist/{songId}" shape: the resolver must fail
        // gracefully (empty queue + warning log), not throw out of onSetMediaItems.
        val result = setMediaItems("artist/broken")
        assertTrue(result.mediaItems.isEmpty())
    }
}
