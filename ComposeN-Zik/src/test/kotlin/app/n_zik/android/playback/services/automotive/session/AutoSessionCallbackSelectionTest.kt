package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.session.MediaSession
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Format
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.it.fast4x.rimusic.repository.QuickPicksRepository
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.kreate.android.me.knighthat.utils.getLocalSongs
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_LUCKY_SHUFFLE
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
        // The real singleton object: the `downloaded shuffle` test sets its
        // `downloads` property directly (set + restore, no `mockkObject`) —
        // the object's inline instrumentation can be left stale by other
        // classes in the same JVM, and a direct set/restore is immune.
        callback = AutoSessionCallback(context, Database, MyDownloadHelper)
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

    private suspend fun upsertSongWithLikedAt(id: String, likedAt: Long?) {
        onDatabase {
            Database.songTable.upsert(
                Song(id = id, title = "t-$id", durationText = null, thumbnailUrl = null, likedAt = likedAt, totalPlayTimeMs = 1000)
            )
        }
    }

    private suspend fun mapSongToPlaylist(songId: String, playlistId: Long) {
        onDatabase { Database.songPlaylistMapTable.map(songId, playlistId) }
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
    fun `library all shuffle mirrors the phone all tab queue`() {
        // The phone's All-tab queue (HomeLibrary.getSelectedSongs) flattens EVERY
        // displayed playlist — YT, pinned and rewind included — then Shuffler.play
        // applies the triple dislike exclusion. The automotive queue must match:
        // before the fix it silently dropped the YT and pinned playlists.
        runBlocking {
            onDatabase {
                Database.playlistTable.insertIgnore(Playlist(id = 1, name = "YT One", browseId = "VL1", isYoutubePlaylist = true))
                Database.playlistTable.insertIgnore(Playlist(id = 2, name = "pinned:Two"))
                Database.playlistTable.insertIgnore(Playlist(id = 3, name = "rewind-monthly:202601"))
                Database.playlistTable.insertIgnore(Playlist(id = 4, name = "Plain"))
                upsertSong("liball-yt")
                upsertSong("liball-pinned")
                upsertSong("liball-rewind")
                upsertSong("liball-plain")
                Database.songPlaylistMapTable.map("liball-yt", 1)
                Database.songPlaylistMapTable.map("liball-pinned", 2)
                Database.songPlaylistMapTable.map("liball-rewind", 3)
                Database.songPlaylistMapTable.map("liball-plain", 4)
            }
        }
        val result = setMediaItems(AutoSessionConstants.ID_PLAYLISTS_LOCAL_SHUFFLE)
        assertEquals(4, result.mediaItems.size)
        assertEquals(setOf("liball-yt", "liball-pinned", "liball-rewind", "liball-plain"), result.mediaItems.map { it.mediaId }.toSet())
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

    // ---------- Rewind sub-group shuffles + selection ----------

    private suspend fun seedRewindPlaylists() {
        runBlocking {
            onDatabase {
                Database.playlistTable.insertIgnore(Playlist(id = 101, name = "rewind-monthly:202609"))
                Database.playlistTable.insertIgnore(Playlist(id = 102, name = "rewind-yearly:2026"))
                Database.playlistTable.insertIgnore(Playlist(id = 103, name = "rewind-alltime"))
                upsertSongWithLikedAt("rw-m-1", null)
                upsertSongWithLikedAt("rw-m-2", null)
                upsertSongWithLikedAt("rw-y-1", null)
                upsertSongWithLikedAt("rw-a-1", null)
            }
            mapSongToPlaylist("rw-m-1", 101)
            mapSongToPlaylist("rw-m-2", 101)
            mapSongToPlaylist("rw-y-1", 102)
            // rw-m-1 also belongs to the alltime playlist: the union must deduplicate.
            mapSongToPlaylist("rw-a-1", 103)
            mapSongToPlaylist("rw-m-1", 103)
        }
    }

    @Test
    fun `rewind month shuffle resolves only the monthly playlist songs`() {
        runBlocking { seedRewindPlaylists() }
        val result = setMediaItems(AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH_SHUFFLE)
        assertEquals(setOf("rw-m-1", "rw-m-2"), result.mediaItems.map { it.mediaId }.toSet())
        // The _PLAY_ALL variant resolves the same list.
        val playAll = setMediaItems(AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL"))
        assertEquals(setOf("rw-m-1", "rw-m-2"), playAll.mediaItems.map { it.mediaId }.toSet())
    }

    @Test
    fun `rewind year shuffle resolves only the yearly playlist songs`() {
        runBlocking { seedRewindPlaylists() }
        val result = setMediaItems(AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR_SHUFFLE)
        assertEquals(setOf("rw-y-1"), result.mediaItems.map { it.mediaId }.toSet())
        // The _PLAY_ALL variant resolves the same list.
        val playAll = setMediaItems(AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL"))
        assertEquals(setOf("rw-y-1"), playAll.mediaItems.map { it.mediaId }.toSet())
    }

    @Test
    fun `rewind all shuffle resolves the deduplicated union of monthly yearly and alltime`() {
        runBlocking { seedRewindPlaylists() }
        val result = setMediaItems(AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL_SHUFFLE)
        assertEquals(setOf("rw-m-1", "rw-m-2", "rw-y-1", "rw-a-1"), result.mediaItems.map { it.mediaId }.toSet())
        // The _PLAY_ALL variant resolves the same union.
        val playAll = setMediaItems(AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL"))
        assertEquals(setOf("rw-m-1", "rw-m-2", "rw-y-1", "rw-a-1"), playAll.mediaItems.map { it.mediaId }.toSet())
    }

    @Test
    fun `rewind playlist song selection resolves the playlist queue with the right start index`() {
        runBlocking { seedRewindPlaylists() }
        val result = setMediaItems("playlist/101/rw-m-2")
        assertEquals(setOf("rw-m-1", "rw-m-2"), result.mediaItems.map { it.mediaId }.toSet())
        assertEquals("rw-m-2", result.mediaItems[result.startIndex].mediaId)
    }

    // ---------- Disliked category ----------

    @Test
    fun `disliked shuffle resolves only the disliked songs`() {
        runBlocking {
            upsertSongWithLikedAt("dis-1", -1L)
            upsertSongWithLikedAt("dis-2", -1L)
            upsertSong("plain-1")
        }
        val result = setMediaItems(AutoSessionConstants.ID_SONGS_DISLIKED_SHUFFLE)
        assertEquals(setOf("dis-1", "dis-2"), result.mediaItems.map { it.mediaId }.toSet())
        // The _PLAY_ALL variant resolves the same list (the Disliked category is
        // never filtered, in either mode).
        val playAll = setMediaItems(AutoSessionConstants.ID_SONGS_DISLIKED_SHUFFLE.replace("_SHUFFLE", "_PLAY_ALL"))
        assertEquals(setOf("dis-1", "dis-2"), playAll.mediaItems.map { it.mediaId }.toSet())
    }

    @Test
    fun `disliked selection resolves the disliked queue with the right start index`() {
        runBlocking {
            upsertSongWithLikedAt("dis-1", -1L)
            upsertSongWithLikedAt("dis-2", -1L)
            upsertSong("plain-1")
        }
        val result = setMediaItems("SONGS_DISLIKED/dis-2")
        assertEquals(setOf("dis-1", "dis-2"), result.mediaItems.map { it.mediaId }.toSet())
        assertEquals("dis-2", result.mediaItems[result.startIndex].mediaId)
    }

    @Test
    fun `songs all shuffle never queues a disliked song`() {
        runBlocking {
            upsertSong("ok-1")
            upsertSong("ok-2")
            upsertSongWithLikedAt("dis-1", -1L)
        }
        val result = setMediaItems(AutoSessionConstants.ID_SONGS_ALL_SHUFFLE)
        assertEquals(setOf("ok-1", "ok-2"), result.mediaItems.map { it.mediaId }.toSet())
    }

    @Test
    fun `songs all shuffle queues the disliked songs when phone DislikeMode is disabled`() {
        runBlocking {
            upsertSong("ok-1")
            upsertSongWithLikedAt("dis-1", -1L)
        }
        // Phone settings: "exclude disliked" off -> the queue mirrors the unfiltered list.
        context.preferences.edit().putString(excludeDislikedSongsKey, DislikeMode.Disabled.name).apply()
        try {
            val result = setMediaItems(AutoSessionConstants.ID_SONGS_ALL_SHUFFLE)
            assertEquals(setOf("ok-1", "dis-1"), result.mediaItems.map { it.mediaId }.toSet())
        } finally {
            context.preferences.edit().remove(excludeDislikedSongsKey).apply()
        }
    }

    @Test
    fun `songs all selection builds its queue from the filtered list`() {
        runBlocking {
            upsertSong("ok-1")
            upsertSong("ok-2")
            upsertSongWithLikedAt("dis-1", -1L)
            upsertSongWithLikedAt("dis-2", -1L)
        }
        // The queue mirrors the filtered browse list: disliked songs stay out
        // (same excludedIds the browse SONGS_ALL branch applies).
        val result = setMediaItems("SONGS_ALL/ok-1")
        assertEquals(setOf("ok-1", "ok-2"), result.mediaItems.map { it.mediaId }.toSet())
        assertEquals("ok-1", result.mediaItems[result.startIndex].mediaId)
    }

    // ---------- Downloaded / OnDevice / Cached: disliked exclusion in the queues ----------

    /** Real [Download] with a given state (same pattern as `MyDownloadHelperPreInitTest`). */
    private fun fakeDownload(id: String, state: Int): Download {
        val request = DownloadRequest.Builder(id, Uri.parse("https://music.example.com/stream/$id")).build()
        return Download(request, state, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
    }

    @Test
    fun `downloaded shuffle never queues a disliked song`() {
        runBlocking {
            upsertSongWithLikedAt("dl-ok", null)
            upsertSongWithLikedAt("dl-dis", -1L)
        }
        // The real `var downloads` is set directly (and restored below) instead
        // of stubbing through `mockkObject`: the object's inline
        // instrumentation can be left stale by other test classes in the same
        // JVM, and a direct set/restore is immune to it.
        val originalDownloads = MyDownloadHelper.downloads
        try {
            // Both downloads are COMPLETED: only the dislike flag separates them.
            MyDownloadHelper.downloads = MutableStateFlow(mapOf("dl-ok" to fakeDownload("dl-ok", Download.STATE_COMPLETED), "dl-dis" to fakeDownload("dl-dis", Download.STATE_COMPLETED)))
            val result = setMediaItems(AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE)
            assertEquals(setOf("dl-ok"), result.mediaItems.map { it.mediaId }.toSet())

            // Phone settings: "exclude disliked" off -> the queue mirrors the unfiltered list.
            context.preferences.edit().putString(excludeDislikedSongsKey, DislikeMode.Disabled.name).apply()
            try {
                val disabled = setMediaItems(AutoSessionConstants.ID_SONGS_DOWNLOADED_SHUFFLE)
                assertEquals(setOf("dl-ok", "dl-dis"), disabled.mediaItems.map { it.mediaId }.toSet())
            } finally {
                context.preferences.edit().remove(excludeDislikedSongsKey).apply()
            }
        } finally {
            MyDownloadHelper.downloads = originalDownloads
        }
    }

    @Test
    fun `on-device shuffle never queues a disliked song`() {
        // The on-device queue comes from context.getLocalSongs (a real MediaStore
        // query) — stub the file facade so no query actually runs. Local song ids
        // must carry a numeric MediaStore row id (asMediaItem parses it).
        val songOk = Song(id = "local:5", title = "Local OK", durationText = null, thumbnailUrl = null, likedAt = null, totalPlayTimeMs = 1000)
        val songDis = Song(id = "local:6", title = "Local DIS", durationText = null, thumbnailUrl = null, likedAt = -1L, totalPlayTimeMs = 1000)
        // The exclusion set is built from the DB, so the disliked row must exist
        // there even though the queue itself comes from the stubbed facade.
        runBlocking {
            onDatabase {
                Database.songTable.upsert(songOk)
                Database.songTable.upsert(songDis)
            }
        }
        mockkStatic("app.kreate.android.me.knighthat.utils.OnDeviceMediaKt")
        try {
            every { any<Context>().getLocalSongs(any(), any()) } returns flowOf(mapOf(songOk to "/storage/emulated/0/Music/ok.mp3", songDis to "/storage/emulated/0/Music/dis.mp3"))
            val result = setMediaItems(AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE)
            assertEquals(setOf("local:5"), result.mediaItems.map { it.mediaId }.toSet())

            // Phone settings: "exclude disliked" off -> the queue mirrors the unfiltered list.
            context.preferences.edit().putString(excludeDislikedSongsKey, DislikeMode.Disabled.name).apply()
            try {
                val disabled = setMediaItems(AutoSessionConstants.ID_SONGS_ONDEVICE_SHUFFLE)
                assertEquals(setOf("local:5", "local:6"), disabled.mediaItems.map { it.mediaId }.toSet())
            } finally {
                context.preferences.edit().remove(excludeDislikedSongsKey).apply()
            }
        } finally {
            unmockkStatic("app.kreate.android.me.knighthat.utils.OnDeviceMediaKt")
        }
    }

    @Test
    fun `cached shuffle never queues a disliked song`() {
        runBlocking {
            onDatabase {
                upsertSong("cache-ok")
                upsertSongWithLikedAt("cache-dis", -1L)
                // contentLength != null is required by the cached queue filter.
                Database.formatTable.upsert(Format(songId = "cache-ok", contentLength = 123L))
                Database.formatTable.upsert(Format(songId = "cache-dis", contentLength = 456L))
            }
        }
        // The CACHED branch reads binder.cache — the setUp binder is relaxed
        // (isCached = false), so wire one that reports both songs as cached.
        callback.binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { callback.binder.cache.isCached("cache-ok", 0L, any()) } returns true
        every { callback.binder.cache.isCached("cache-dis", 0L, any()) } returns true
        val result = setMediaItems(AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE)
        assertEquals(setOf("cache-ok"), result.mediaItems.map { it.mediaId }.toSet())

        // Phone settings: "exclude disliked" off -> the cached disliked song
        // queues too, proving the exclusion above came from the dislike filter,
        // not from the cache.
        context.preferences.edit().putString(excludeDislikedSongsKey, DislikeMode.Disabled.name).apply()
        try {
            val disabled = setMediaItems(AutoSessionConstants.ID_SONGS_CACHED_SHUFFLE)
            assertEquals(setOf("cache-ok", "cache-dis"), disabled.mediaItems.map { it.mediaId }.toSet())
        } finally {
            context.preferences.edit().remove(excludeDislikedSongsKey).apply()
        }
    }

    @Test
    fun `lucky shuffle fallback never queues a disliked song`() {
        runBlocking {
            upsertSong("lib-1")
            upsertSongWithLikedAt("dis-1", -1L)
        }
        mockkObject(QuickPicksRepository)
        try {
            every { QuickPicksRepository.trendingList } returns MutableStateFlow<List<Song>>(emptyList())
            every { QuickPicksRepository.relatedPage } returns MutableStateFlow(null)
            val result = setMediaItems(ID_LUCKY_SHUFFLE)
            assertEquals(setOf("lib-1"), result.mediaItems.map { it.mediaId }.toSet())
        } finally {
            unmockkObject(QuickPicksRepository)
        }
    }

    // ---------- Albums / Artists: triple dislike exclusion in the queues ----------

    private suspend fun seedDislikedAlbumFixture() {
        onDatabase {
            upsertSong("alb-ok-1")
            upsertSong("alb-dis-1")
            Database.albumTable.insertIgnore(Album(id = "ALB-OK"))
            Database.albumTable.insertIgnore(Album(id = "ALB-DIS", dislikedAt = 1L))
            Database.songAlbumMapTable.map("alb-ok-1", "ALB-OK", 0)
            Database.songAlbumMapTable.map("alb-dis-1", "ALB-DIS", 0)
        }
    }

    @Test
    fun `albums library shuffle excludes songs of disliked albums`() {
        runBlocking { seedDislikedAlbumFixture() }
        val result = setMediaItems(AutoSessionConstants.ID_ALBUMS_LIBRARY_SHUFFLE)
        assertEquals(setOf("alb-ok-1"), result.mediaItems.map { it.mediaId }.toSet())
    }

    @Test
    fun `albums library shuffle keeps the songs of disliked albums when the albums DislikeMode is off`() {
        runBlocking { seedDislikedAlbumFixture() }
        context.preferences.edit().putString(excludeDislikedAlbumsKey, DislikeMode.Disabled.name).apply()
        try {
            val result = setMediaItems(AutoSessionConstants.ID_ALBUMS_LIBRARY_SHUFFLE)
            assertEquals(setOf("alb-ok-1", "alb-dis-1"), result.mediaItems.map { it.mediaId }.toSet())
        } finally {
            context.preferences.edit().remove(excludeDislikedAlbumsKey).apply()
        }
    }

    @Test
    fun `artist selection queue keeps every track (no triple filter, phone parity)`() {
        runBlocking {
            onDatabase {
                upsertSong("art-ok-1")
                upsertSong("art-dis-1")
                Database.artistTable.insertIgnore(Artist(id = "UCok"))
                Database.artistTable.insertIgnore(Artist(id = "UCdis", dislikedAt = 1L))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "art-ok-1", artistId = "UCok"))
                // art-dis-1 belongs to the normal artist AND the disliked one —
                // the phone's artist queue is NOT triple-filtered (user decision:
                // details follow the app), so it stays in the queue.
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "art-dis-1", artistId = "UCok"))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "art-dis-1", artistId = "UCdis"))
            }
        }
        val result = setMediaItems("artist/UCok/art-ok-1")
        assertEquals(setOf("art-ok-1", "art-dis-1"), result.mediaItems.map { it.mediaId }.toSet())
        assertEquals("art-ok-1", result.mediaItems[result.startIndex].mediaId)
    }

    @Test
    fun `songs all selection queue excludes songs of disliked artists and albums`() {
        runBlocking {
            onDatabase {
                upsertSong("ok-1")
                upsertSong("via-art")
                upsertSong("via-alb")
                Database.artistTable.insertIgnore(Artist(id = "UCdis", dislikedAt = 1L))
                Database.albumTable.insertIgnore(Album(id = "ALB-DIS", dislikedAt = 1L))
                Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "via-art", artistId = "UCdis"))
                Database.songAlbumMapTable.map("via-alb", "ALB-DIS", 0)
            }
        }
        val result = setMediaItems("SONGS_ALL/ok-1")
        assertEquals(setOf("ok-1"), result.mediaItems.map { it.mediaId }.toSet())
    }
}
