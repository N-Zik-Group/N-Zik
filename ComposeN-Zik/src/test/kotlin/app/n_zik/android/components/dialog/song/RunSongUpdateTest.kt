package app.n_zik.android.components.dialog.song

import android.content.Context
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.appContext
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.LyricsTable
import app.n_zik.android.core.database.SongAlbumMapTable
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.playback.services.PlayerServiceModern
import androidx.media3.datasource.cache.Cache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.ArtistConjunctions
import app.n_zik.innertube.requests.song
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.models.Thumbnail
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import app.n_zik.android.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor

/**
 * Composition tests for [runSongUpdate] - the extracted body of the song
 * « Update » dialog confirm action (fetch gate, checkbox-to-field wiring,
 * channel-anchored resolution, artist-link reconcile, stored-row rewrite,
 * cache gate, done toast).
 *
 * Pattern follows [app.n_zik.android.core.database.DatabaseUpsertChannelIdentityTest]:
 * [Database] is object-mocked with stubbed tables, the lazy Room initializer
 * is kept out of the JVM, and [Database.asyncTransaction] runs its block
 * inline on a stubbed transaction executor. [runBlocking] is used because
 * [runSongUpdate] is suspend and the JVM test has no dispatcher to await on.
 */
class RunSongUpdateTest {

    private val songTable = mockk<SongTable>(relaxed = true)
    private val artistTable = mockk<ArtistTable>(relaxed = true)
    private val mapTable = mockk<SongArtistMapTable>(relaxed = true)
    private val formatTable = mockk<FormatTable>(relaxed = true)
    private val albumMapTable = mockk<SongAlbumMapTable>(relaxed = true)
    private val albumTable = mockk<AlbumTable>(relaxed = true)
    private val lyricsTable = mockk<LyricsTable>(relaxed = true)
    private val internal = mockk<DatabaseInitializer>(relaxed = true)

    private val cache = mockk<Cache>(relaxed = true)
    private val downloadCache = mockk<Cache>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>()

    private val conjunctionsBackup = ArtistConjunctions.conjunctions

    /** A 5-field snapshot, as produced by `MediaItem.asSong` in the player menu. */
    private val entry = Song(
        id = "video_00001",
        title = "Snapshot title",
        artistsText = "Snapshot artist",
        durationText = null,
        thumbnailUrl = null
    )

    /** The full stored row (the values out-of-snapshot fields must keep). */
    private val storedRow = Song(
        id = "video_00001",
        title = "Stored title",
        artistsText = "Stored artist",
        durationText = "3:00",
        thumbnailUrl = "https://stored/thumb",
        likedAt = 123L,
        totalPlayTimeMs = 4567L,
        position = 4,
        isYoutubeSong = true
    )

    private fun author(name: String, browseId: String? = null) =
        Innertube.Info<NavigationEndpoint.Endpoint.Browse>(
            name = name,
            endpoint = browseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
        )

    private fun songItem(
        title: String,
        authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>,
        thumbnailUrl: String? = null
    ) = Innertube.SongItem(
        info = Innertube.Info(
            name = title,
            endpoint = NavigationEndpoint.Endpoint.Watch(
                videoId = "video_00001",
                watchEndpointMusicSupportedConfigs = NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs(
                    NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig("MUSIC_VIDEO_TYPE_ATV")
                )
            )
        ),
        authors = authors,
        album = null,
        durationText = null,
        thumbnail = thumbnailUrl?.let {
            Thumbnail(url = it, height = null, width = null)
        }
    )

    /** MockK's suspend-mocking support unwraps any `returns` value that is itself a `kotlin.Result` one level, which collides with `Innertube.song`'s `Result<SongItem?>?`-typed return; wrapping the intended value in one extra `Result.success(...)` layer absorbs that single unwrap. */
    @Suppress("UNCHECKED_CAST")
    private fun fetchResult(value: Result<Innertube.SongItem?>): Result<Innertube.SongItem?>? =
        Result.success(value) as Result<Innertube.SongItem?>?

    @Before
    fun setup() {
        mockkStatic("app.n_zik.android.utils.ProfilePreferencesKt")
        every { getActiveProfile(any()) } returns "active"
        mockkObject(Database)
        every { Database.songTable } returns songTable
        every { Database.artistTable } returns artistTable
        every { Database.songArtistMapTable } returns mapTable
        every { Database.formatTable } returns formatTable
        every { Database.songAlbumMapTable } returns albumMapTable
        every { Database.albumTable } returns albumTable
        every { Database.lyricsTable } returns lyricsTable

        // asyncTransaction runs its block on the Room transactionExecutor: keep
        // the real (lazy) Room database out of the JVM and run the block inline.
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns internal
        every { internal.transactionExecutor } returns Executor { it.run() }

        // Innertube.song is a top-level suspend extension (requests/Queue.kt).
        mockkStatic("app.n_zik.innertube.requests.QueueKt")
        mockkStatic("app.n_zik.android.components.dialog.song.SongUpdateVideoAuthorKt")
        coEvery { withVideoAuthorFallback(any(), any(), any(), any(), any()) } coAnswers {
            secondArg<Innertube.SongItem?>()
        }
        // Default: fetch yields nothing. A test that accidentally triggers a
        // fetch then degrades to "fetch failed" and fails its coVerify count.
        runBlocking {
            coEvery { Innertube.song(any()) } returns null
        }

        mockkObject(SongUpdateCoverReset)
        every { SongUpdateCoverReset.reset(any(), any(), any(), any()) } just runs

        mockkObject(SongUpdatePlaybackRefresh)
        coEvery { SongUpdatePlaybackRefresh.publish(any(), any(), any(), any()) } just runs

        mockkObject(Toaster)
        every { Toaster.done() } just runs
        every { Toaster.e(any<Int>(), any<Int>()) } just runs
        every { Toaster.e(any<String>(), any<Int>()) } just runs
        every { Toaster.w(any<String>(), any<Int>()) } just runs
        mockkObject(WaveformExtractor)
        every { WaveformExtractor.deleteWaveformOrThrow(any(), any()) } just runs
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns mockk<Context>(relaxed = true)

        every { binder.cache } returns cache
        every { binder.downloadCache } returns downloadCache

        // Deterministic regardless of the locale wiring done at app startup
        ArtistConjunctions.conjunctions = listOf("and")

        every { songTable.findByIdDirect(any()) } returns null
        every { artistTable.findByIdDirect(any()) } returns null
        every { artistTable.findByNameDirect(any()) } returns null
    }

    @After
    fun tearDown() {
        ArtistConjunctions.conjunctions = conjunctionsBackup
        unmockkAll()
    }

    @Test
    fun playtimeOnlyUpdateSkipsTheFetch() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow

        runBlocking {
            runSongUpdate(binder, entry, SongUpdateSelection(playtime = true))
            coVerify(exactly = 0) { Innertube.song(any()) }
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        assertEquals(0L, updated.captured.totalPlayTimeMs)
        assertEquals("Stored title", updated.captured.title)
        // no authors box -> no reconcile
        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
        verify(exactly = 1) { Toaster.done() }
    }

    @Test
    fun playtimeOnlyUpdateResetsNoFetchTtls() {
        // A playtime-only update has needsFetch == false, so no YTM fetch
        // happened and no metadata was refreshed: the 30-day fetch TTLs of

        // the artists, album and lyrics must NOT be invalidated (the
        // relaxed mocks would silently absorb a stray resetFetchTtl, so the

        // never-called assertions are what pin the guard).
        every { songTable.findByIdDirect("video_00001") } returns storedRow

        runBlocking {
            runSongUpdate(binder, entry, SongUpdateSelection(playtime = true))
            coVerify(exactly = 0) { Innertube.song(any()) }
        }

        verify(exactly = 1) { songTable.updateReplace(any()) }
        verify(exactly = 0) { artistTable.resetFetchTtl(any()) }
        verify(exactly = 0) { albumTable.resetFetchTtl(any()) }
        verify(exactly = 0) { lyricsTable.resetFetchTtlBySongId(any()) }
        verify(exactly = 1) { Toaster.done() }
    }

    @Test
    fun aFetchBoxTriggersExactlyOneFetch() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        runBlocking {
            coEvery { Innertube.song(any()) } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(title = true))
            coVerify(exactly = 1) { Innertube.song("video_00001") }
        }
    }

    @Test
    fun titleBoxOnlyReplacesTheTitle() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(title = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        assertEquals("Fetched title", updated.captured.title)
        // unticked boxes keep the stored values
        assertEquals("Stored artist", updated.captured.artistsText)
        assertEquals("https://stored/thumb", updated.captured.thumbnailUrl)
    }

    @Test
    fun authorsBoxReplacesArtistsTextAndReconcilesTheLinks() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        val item = songItem(
            "Fetched title",
            listOf(author("Tanchiky", "UC_A"), author("siromaru", "UC_B"))
        )

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        // artistsText follows the fresh entry names (asSong join)
        assertEquals("Tanchiky, siromaru", updated.captured.artistsText)
        assertEquals("Stored title", updated.captured.title)

        // the complete list re-links inside the same transaction
        verify(exactly = 1) { mapTable.deleteBySongId("video_00001") }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_A")) }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_B")) }
        val artists = slot<List<Artist>>()
        verify(exactly = 1) { artistTable.upsert(capture(artists)) }
        assertEquals(setOf("Tanchiky", "siromaru"), artists.captured.map { it.name }.toSet())
        verify(exactly = 1) { Toaster.done() }
    }

    @Test
    fun thumbnailBoxAppliesTheFetchedThumbnail() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        val item = songItem("Fetched title", emptyList(), thumbnailUrl = "https://fetched/thumb")

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(thumbnail = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        assertEquals("https://fetched/thumb", updated.captured.thumbnailUrl)
        verify(exactly = 1) { SongUpdateCoverReset.reset(any(), "video_00001", "https://stored/thumb", "active") }
        assertEquals("Stored title", updated.captured.title)
    }

    @Test
    fun existingChannelRowKeepsItsStoredNameInTheReconcile() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        every { artistTable.findByIdDirect("UC_A") } returns Artist(id = "UC_A", name = "Stored Name A")
        every { artistTable.findByIdDirect("UC_B") } returns null
        val item = songItem(
            "Fetched title",
            listOf(author("Entry A", "UC_A"), author("Entry B", "UC_B"))
        )

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val artists = slot<List<Artist>>()
        verify(exactly = 1) { artistTable.upsert(capture(artists)) }
        assertEquals(2, artists.captured.size)
        // the existing channel row keeps its stored name, the missing row
        // starts from the entry name
        assertEquals("Stored Name A", artists.captured.first { it.id == "UC_A" }.name)
        assertEquals("Entry B", artists.captured.first { it.id == "UC_B" }.name)
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_A")) }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_B")) }
    }

    @Test
    fun updateReplacePayloadTakesOutOfSnapshotFieldsFromTheStoredRow() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        // the entry carries model defaults (null/0/-1/false) - a snapshot built
        // from a MediaItem would clobber the stored row with those if the

        // re-read were missing
        assertEquals(null, entry.likedAt)
        assertEquals(0L, entry.totalPlayTimeMs)
        assertEquals(-1, entry.position)
        assertEquals(false, entry.isYoutubeSong)

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(title = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        assertEquals(123L, updated.captured.likedAt)
        assertEquals(4567L, updated.captured.totalPlayTimeMs)
        assertEquals(4, updated.captured.position)
        assertTrue(updated.captured.isYoutubeSong)
        // durationText is not a dialog field: the stored value survives
        assertEquals("3:00", updated.captured.durationText)
    }

    @Test
    fun `playback cache reset preserves offline bytes and shared format metadata`() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow

        runBlocking {
            runSongUpdate(binder, entry, SongUpdateSelection(cache = true))
        }

        verify(exactly = 1) { cache.removeResource("video_00001") }
        verify(exactly = 0) { downloadCache.removeResource(any()) }
        verify(exactly = 1) { WaveformExtractor.deleteWaveformOrThrow(any(), "video_00001") }
        verify(exactly = 0) { formatTable.deleteBySongId(any()) }
        verify(exactly = 0) { formatTable.updateContentLengthOf(any()) }
        verify(exactly = 0) { formatTable.upsert(any()) }
    }

    @Test
    fun cacheOpsAreSkippedWhenTheCacheBoxIsUnticked() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(title = true))
        }

        verify(exactly = 0) { SongUpdateCoverReset.reset(any(), any(), any(), any()) }
        verify(exactly = 0) { cache.removeResource(any()) }
        verify(exactly = 0) { downloadCache.removeResource(any()) }
        verify(exactly = 0) { WaveformExtractor.deleteWaveformOrThrow(any(), any()) }
        verify(exactly = 0) { formatTable.deleteBySongId(any()) }
        verify(exactly = 0) { formatTable.updateContentLengthOf(any()) }
    }

    @Test
    fun incompleteAuthorListUpdatesArtistsTextButLeavesLinksUntouched() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        every { artistTable.findByNameDirect("LocalArtist") } returns
            Artist(id = "LOCAL_1", name = "LocalArtist")
        val item = songItem(
            "Fetched title",
            listOf(author("Tanchiky", "UC_A"), author("LocalArtist", null))
        )

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        assertEquals("Tanchiky, LocalArtist", updated.captured.artistsText)
        // a names-only entry can't back the list: the mapping is never erased
        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
        verify(exactly = 0) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun junkAuthorEntriesAreSkippedConsistentlyByTextAndLinks() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        val item = songItem(
            "Fetched title",
            listOf(
                author("&", "UC_SEP"),   // pure separator carrying a browse id
                author("and", "UC_AND"), // standalone conjunction with a browse id
                author("  ", null),      // blank name
                author("Tanchiky", "UC_A"),
                author("siromaru", "UC_B")
            )
        )

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        // The stored artistsText is written from the asSong copy of the entry
        // rules, the links from ArtistMappingReconcile.parseAuthorEntries:

        // both copies must skip the same junk entries, so the displayed text
        // and the re-linked artists describe the same artists.
        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        assertEquals("Tanchiky, siromaru", updated.captured.artistsText)

        val artists = slot<List<Artist>>()
        verify(exactly = 1) { artistTable.upsert(capture(artists)) }
        assertEquals(setOf("Tanchiky", "siromaru"), artists.captured.map { it.name }.toSet())
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_A")) }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_B")) }
        // the junk entries end up in neither copy
        verify(exactly = 0) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_SEP")) }
        verify(exactly = 0) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_AND")) }
    }

    @Test
    fun modifiedStoredArtistsTextIsResetAndReconciled() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow.copy(artistsText = "modified:Custom")
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        // Explicit update replaces the custom artist value.
        assertEquals("Tanchiky", updated.captured.artistsText)
        // The complete provider list also replaces its links.
        verify(exactly = 1) { mapTable.deleteBySongId("video_00001") }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_A")) }
    }

    @Test
    fun blankFetchedArtistsKeepTheStoredValue() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        // the video queue fetch returns an empty author list (the channel
        // byline is filtered out): fetched artistsText is ""
        val item = songItem("Fetched title", emptyList())

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        // the stored value survives the blank fetch
        assertEquals("Stored artist", updated.captured.artistsText)
        assertEquals("Stored title", updated.captured.title)
        // an empty list reconciles nothing
        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
        verify(exactly = 0) { mapTable.insertIgnore(any<SongArtistMap>()) }
        verify(exactly = 0) { artistTable.upsert(any<List<Artist>>()) }
    }

    @Test
    fun nonBlankFetchedArtistsStillOverwriteTheStoredValue() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        // the blank-fetch guard must not fire on a real fetch
        assertEquals("Tanchiky", updated.captured.artistsText)
        verify(exactly = 1) { mapTable.deleteBySongId("video_00001") }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_00001", "UC_A")) }
    }

    @Test
    fun modifiedStoredArtistsTextSurvivesABlankFetch() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow.copy(artistsText = "modified:Custom")
        val item = songItem("Fetched title", emptyList())

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        // the custom value is authoritative even against a blank fetch
        assertEquals("modified:Custom", updated.captured.artistsText)
        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
        verify(exactly = 0) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun updateResetsTheArtistAlbumAndLyricsFetchTtls() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        every { mapTable.findArtistsOfDirect("video_00001") } returns
            listOf( Artist( id = "UC_A", name = "A" ), Artist( id = "UC_B", name = "B" ) )
        // A song can be mapped to several albums: every mapped album's TTL is
        // reset, not just one arbitrary row (findAlbumsOfDirect, no LIMIT 1).
        every { albumMapTable.findAlbumsOfDirect("video_00001") } returns
            listOf( Album( id = "ALB_1" ), Album( id = "ALB_2" ) )
        val item = songItem( "Fetched title", emptyList() )

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult( Result.success( item ) )
            runSongUpdate( binder, entry, SongUpdateSelection( authors = true ) )
        }

        // the 30-day fetch TTLs are reset so the next playback re-fetches
        verify( exactly = 1 ) { artistTable.resetFetchTtl( "UC_A" ) }
        verify( exactly = 1 ) { artistTable.resetFetchTtl( "UC_B" ) }
        verify( exactly = 1 ) { albumTable.resetFetchTtl( "ALB_1" ) }
        verify( exactly = 1 ) { albumTable.resetFetchTtl( "ALB_2" ) }
        verify( exactly = 1 ) { lyricsTable.resetFetchTtlBySongId( "video_00001" ) }
    }

    @Test
    fun fetchFailureWritesNoFetchedField() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        // Mapped rows must exist for the TTL assertions below to be able to
        // fail: a failed fetch must not reset them (the gate is

        // `fetchedItem != null`, not "a fetch was requested").
        every { mapTable.findArtistsOfDirect("video_00001") } returns listOf(Artist(id = "UC_A", name = "A"))
        every { albumMapTable.findAlbumsOfDirect("video_00001") } returns listOf(Album(id = "ALB_1"))

        runBlocking {
            coEvery { Innertube.song("video_00001") } returns
                fetchResult(Result.failure<Innertube.SongItem?>(RuntimeException("network down")))
            runSongUpdate(binder, entry, SongUpdateSelection(title = true, authors = true, thumbnail = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        assertEquals("Stored title", updated.captured.title)
        assertEquals("Stored artist", updated.captured.artistsText)
        assertEquals("https://stored/thumb", updated.captured.thumbnailUrl)
        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
        // nothing was re-fetched, so the cached pages are still as fresh as
        // before: no TTL invalidation on a failed fetch.
        verify(exactly = 0) { artistTable.resetFetchTtl(any()) }
        verify(exactly = 0) { albumTable.resetFetchTtl(any()) }
        verify(exactly = 0) { lyricsTable.resetFetchTtlBySongId(any()) }
        verify(exactly = 0) { Toaster.done() }
        verify(exactly = 1) { Toaster.e(any<String>(), android.widget.Toast.LENGTH_LONG) }
        coVerify(exactly = 0) { SongUpdatePlaybackRefresh.publish(any(), any(), any(), any()) }
    }

    @Test
    fun missingProviderCoverKeepsCustomCover() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow.copy(thumbnailUrl = "modified:file:///custom.jpg")
        // The suspend dialog action is awaited synchronously in this JVM test.
        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(songItem("Fetched", emptyList())))
            runSongUpdate(binder, entry, SongUpdateSelection(thumbnail = true))
        }
        val updated = slot<Song>()
        verify { songTable.updateReplace(capture(updated)) }
        assertEquals("modified:file:///custom.jpg", updated.captured.thumbnailUrl)
        verify(exactly = 0) { SongUpdateCoverReset.reset(any(), any(), any(), any()) }
    }

    @Test
    fun lateDatabaseFailureDoesNotResetCoverOrInvalidateCaches() {
        every { songTable.findByIdDirect("video_00001") } returns storedRow
        every { lyricsTable.resetFetchTtlBySongId("video_00001") } throws IllegalStateException("late write failure")
        // The suspend dialog action is awaited synchronously in this JVM test.
        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(songItem("Fresh", emptyList(), "https://provider/cover")))
            runSongUpdate(binder, entry, SongUpdateSelection(thumbnail = true, cache = true))
        }
        verify(exactly = 1) { internal.beginTransaction() }
        verify(exactly = 0) { internal.setTransactionSuccessful() }
        verify(exactly = 1) { internal.endTransaction() }
        verify(exactly = 0) { SongUpdateCoverReset.reset(any(), any(), any(), any()) }
        verify(exactly = 0) { cache.removeResource(any()) }
        verify(exactly = 0) { downloadCache.removeResource(any()) }
        verify(exactly = 0) { WaveformExtractor.deleteWaveformOrThrow(any(), any()) }
    }

    @Test
    fun queuedUpdateDoesNothingAfterTheActiveProfileChanges() {
        val pending = mutableListOf<Runnable>()
        every { internal.transactionExecutor } returns Executor { pending.add(it) }
        // Hold the executor task until after switching the profile.
        runBlocking {
            coEvery { Innertube.song("video_00001") } returns fetchResult(Result.success(songItem("Fresh", emptyList(), "https://provider/cover")))
            val update = launch(start = CoroutineStart.UNDISPATCHED) {
                runSongUpdate(binder, entry, SongUpdateSelection(thumbnail = true))
            }
            assertEquals(1, pending.size)
            verify(exactly = 0) { Toaster.done() }
            every { getActiveProfile(any()) } returns "other-profile"
            pending.single().run()
            update.join()
        }
        verify(exactly = 0) { internal.beginTransaction() }
        verify(exactly = 0) { songTable.updateReplace(any()) }
        verify(exactly = 0) { SongUpdateCoverReset.reset(any(), any(), any(), any()) }
    }

    @Test
    fun pendingTransactionCannotReportSuccessOrRefreshPlayback() {
        val pending = mutableListOf<Runnable>()
        every { internal.transactionExecutor } returns Executor { pending.add(it) }
        every { songTable.findByIdDirect(entry.id) } returns storedRow
        // Await the asynchronous dialog action while controlling the actual transaction executor.
        runBlocking {
            coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(songItem("Fresh", emptyList())))
            val update = launch(start = CoroutineStart.UNDISPATCHED) {
                runSongUpdate(binder, entry, SongUpdateSelection(title = true))
            }
            assertTrue(update.isActive)
            verify(exactly = 0) { Toaster.done() }
            coVerify(exactly = 0) { SongUpdatePlaybackRefresh.publish(any(), any(), any(), any()) }
            pending.single().run()
            update.join()
        }
        verify(exactly = 1) { internal.setTransactionSuccessful() }
        verify(exactly = 1) { internal.endTransaction() }
        coVerify(exactly = 1) { SongUpdatePlaybackRefresh.publish(binder, match { it.title == "Fresh" }, any(), "active") }
        verify(exactly = 1) { Toaster.done() }
    }

    @Test
    fun previouslyUnstoredMenuTargetIsInsertedBeforeBeingUpdated() {
        every { songTable.findByIdDirect(entry.id) } returns null
        // The suspend dialog action must finish its transaction before assertions.
        runBlocking {
            coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(songItem("Fresh", emptyList())))
            runSongUpdate(binder, entry, SongUpdateSelection(title = true))
        }
        verify(io.mockk.Ordering.ORDERED) {
            songTable.insertIgnore(entry)
            songTable.updateReplace(match { it.id == entry.id && it.title == "Fresh" })
        }
    }

    @Test
    fun invalidYoutubeIdentityPreservesCustomMetadataButStillAppliesLocalReset() {
        val invalid = storedRow.copy(id = "unmatched:import", title = "modified:Custom")
        every { songTable.findByIdDirect(invalid.id) } returns invalid
        // Await the transaction before checking preservation and independent local work.
        runBlocking {
            runSongUpdate(binder, invalid, SongUpdateSelection(title = true, playtime = true))
        }
        coVerify(exactly = 0) { Innertube.song(any()) }
        verify { songTable.updateReplace(match { it.title == invalid.title && it.totalPlayTimeMs == 0L }) }
        verify(exactly = 0) { Toaster.done() }
        verify(exactly = 1) { Toaster.w(any<String>(), android.widget.Toast.LENGTH_LONG) }
    }

    @Test
    fun `different fetched video cannot replace metadata or remove custom cover`() {
        every { songTable.findByIdDirect(entry.id) } returns storedRow
        val wrong = songItem("Wrong song", listOf(author("Wrong artist", "UC_WRONG")), "https://wrong/cover")
            .copy(info = Innertube.Info("Wrong song", NavigationEndpoint.Endpoint.Watch(videoId = "other_00001")))
        runBlocking {
            coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(wrong))
            runSongUpdate(binder, entry, SongUpdateSelection(true, true, true, true, true))
        }
        verify { songTable.updateReplace(match {
            it.title == storedRow.title && it.artistsText == storedRow.artistsText &&
                it.thumbnailUrl == storedRow.thumbnailUrl && it.totalPlayTimeMs == 0L
        }) }
        verify(exactly = 0) { SongUpdateCoverReset.reset(any(), any(), any(), any()) }
        verify(exactly = 0) { artistTable.upsert(any<List<Artist>>()) }
        coVerify(exactly = 0) { SongUpdatePlaybackRefresh.publish(any(), any(), any(), any()) }
        verify { cache.removeResource(entry.id) }
    }

    @Test
    fun `artist reset removes linked custom row name and propagates agreeing copies`() {
        val artist = Artist("UC_A", "modified:Tangerl", bookmarkedAt = 123L)
        every { songTable.findByIdDirect(entry.id) } returns storedRow.copy(artistsText = "modified:Tangerl")
        every { artistTable.findByIdDirect(artist.id) } returns artist
        every { mapTable.findArtistsOfDirect(entry.id) } returns listOf(artist)
        every { mapTable.allSongsByDirect(artist.id) } returns listOf(
            storedRow.copy(id = "linked_0001", artistsText = "Tangerl"),
            storedRow.copy(id = "custom_0001", artistsText = "modified:Different"),
        )
        runBlocking {
            coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(songItem("Fresh", listOf(author("Tanger", "UC_A")))))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }
        verify { artistTable.upsert(match<List<Artist>> { it.single() == artist.copy(name = "Tanger") }) }
        verify { songTable.updateArtists("linked_0001", "Tanger") }
        verify(exactly = 0) { songTable.updateArtists("custom_0001", any()) }
        verify { songTable.updateReplace(match { it.artistsText == "Tanger" }) }
    }

    @Test
    fun `artist reset preserves custom rows not previously linked to the target`() {
        val artist = Artist("UC_A", "modified:Other custom")
        every { songTable.findByIdDirect(entry.id) } returns storedRow
        every { artistTable.findByIdDirect(artist.id) } returns artist
        every { mapTable.findArtistsOfDirect(entry.id) } returns emptyList()
        runBlocking {
            coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(songItem("Fresh", listOf(author("Tanger", "UC_A")))))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }
        verify { artistTable.upsert(listOf(artist)) }
        verify(exactly = 0) { mapTable.allSongsByDirect(any()) }
    }

    @Test
    fun `provider 404 with local resets reports partial completion`() = runBlocking {
        val response = mockk<io.ktor.client.statement.HttpResponse>(relaxed = true)
        every { response.status } returns io.ktor.http.HttpStatusCode.NotFound
        coEvery { Innertube.song(entry.id) } returns fetchResult(Result.failure(
            io.ktor.client.plugins.ClientRequestException(response, "Not Found")))
        val report = runSongUpdateReport(binder, entry, SongUpdateSelection(title = true, playtime = true, favorite = true))
        assertTrue(report.partial)
        assertEquals(setOf(SongUpdateIssue.METADATA_UNAVAILABLE), report.issues)
        verify { songTable.updateReplace(match { it.totalPlayTimeMs == 0L && it.likedAt == null }) }
    }

    @Test
    fun `fetch-only failure never claims other resets completed`() = runBlocking {
        coEvery { Innertube.song(entry.id) } returns fetchResult(Result.failure(IllegalStateException("Unavailable")))
        val report = runSongUpdateReport(binder, entry, SongUpdateSelection(title = true))
        assertEquals(false, report.completed)
        assertEquals(false, report.anyCompleted)
        assertEquals(setOf(SongUpdateIssue.METADATA_UNAVAILABLE), report.issues)
    }

    @Test
    fun `fetch IO failure is network but database IO failure is storage`() = runBlocking {
        coEvery { Innertube.song(entry.id) } returns fetchResult(Result.failure(java.net.UnknownHostException()))
        val network = runSongUpdateReport(binder, entry, SongUpdateSelection(title = true))
        assertEquals(setOf(SongUpdateIssue.NETWORK), network.issues)
        every { songTable.updateReplace(any()) } throws java.io.IOException("Disk failure")
        val storage = runSongUpdateReport(binder, entry, SongUpdateSelection(playtime = true))
        assertEquals(setOf(SongUpdateIssue.STORAGE), storage.issues)
        assertEquals(false, storage.anyCompleted)
    }

    @Test
    fun `missing artists and cover are named while valid title completes`() = runBlocking {
        coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(songItem("Fresh", emptyList())))
        val report = runSongUpdateReport(binder, entry, SongUpdateSelection(title = true, authors = true, thumbnail = true))
        assertTrue(report.partial)
        assertEquals(setOf(SongUpdateIssue.MISSING_ARTISTS, SongUpdateIssue.MISSING_COVER), report.issues)
        verify(exactly = 0) { SongUpdateCoverReset.reset(any(), any(), any(), any()) }
    }

    @Test
    fun `cache failure after committed reset is partial and never network`() = runBlocking {
        every { cache.removeResource(entry.id) } throws java.io.IOException("Disk failure")
        val report = runSongUpdateReport(binder, entry, SongUpdateSelection(playtime = true, cache = true))
        assertTrue(report.partial)
        assertEquals(setOf(SongUpdateIssue.CACHE_CLEAR), report.issues)
        verify(exactly = 1) { internal.setTransactionSuccessful() }
    }

    @Test
    fun `cover failure after committed metadata is partial`() = runBlocking {
        coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(songItem("Fresh", emptyList(), "https://provider/cover")))
        every { SongUpdateCoverReset.reset(any(), any(), any(), any()) } throws java.io.IOException("Disk failure")
        val report = runSongUpdateReport(binder, entry, SongUpdateSelection(thumbnail = true))
        assertTrue(report.partial)
        assertEquals(setOf(SongUpdateIssue.COVER_RESET), report.issues)
    }

    @Test
    fun `missing playback service and changed profile have distinct reasons`() = runBlocking {
        val cacheReport = runSongUpdateReport(null, entry, SongUpdateSelection(cache = true))
        assertEquals(setOf(SongUpdateIssue.CACHE_UNAVAILABLE), cacheReport.issues)
        every { getActiveProfile(any()) } returns "other"
        val profileReport = runSongUpdateReport(binder, entry, SongUpdateSelection(playtime = true), "active")
        assertEquals(setOf(SongUpdateIssue.PROFILE_CHANGED), profileReport.issues)
        assertEquals(false, profileReport.anyCompleted)
        verify(exactly = 0) { songTable.updateReplace(any()) }
    }

    @Test
    fun `cancellation inside provider Result propagates without resetting anything`() = runBlocking {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        coEvery { Innertube.song(entry.id) } returns fetchResult(Result.failure(cancellation))
        try {
            runSongUpdateReport(binder, entry, SongUpdateSelection(title = true, playtime = true))
            org.junit.Assert.fail("Cancellation must propagate")
        } catch (actual: kotlinx.coroutines.CancellationException) {
            org.junit.Assert.assertSame(cancellation, actual)
        }
        verify(exactly = 0) { songTable.updateReplace(any()) }
    }


    @Test
    fun `incomplete artist links report partial while preserving mapping safeguards`() = runBlocking {
        coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(
            songItem("Fresh", listOf(author("Named artist")))))
        val report = runSongUpdateReport(binder, entry, SongUpdateSelection(authors = true))
        assertTrue(report.partial)
        assertEquals(setOf(SongUpdateIssue.INCOMPLETE_ARTISTS), report.issues)
        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
    }

    @Test
    fun `wrong video reports ignored information without touching custom cover`() = runBlocking {
        val wrong = songItem("Wrong", emptyList(), "https://wrong/cover").copy(
            info = Innertube.Info("Wrong", NavigationEndpoint.Endpoint.Watch(videoId = "other_00001")))
        coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(wrong))
        val report = runSongUpdateReport(binder, entry, SongUpdateSelection(thumbnail = true))
        assertEquals(setOf(SongUpdateIssue.WRONG_SONG), report.issues)
        assertEquals(false, report.anyCompleted)
        verify(exactly = 0) { SongUpdateCoverReset.reset(any(), any(), any(), any()) }
    }


    @Test
    fun `explicit provider without a title preserves stored title and reports missing title`() = runBlocking {
        every { songTable.findByIdDirect(entry.id) } returns storedRow
        for (title in listOf<String?>(null, "", "   ")) {
            val provider = songItem("Temporary", emptyList()).let {
                it.copy(info = requireNotNull(it.info).copy(name = title), explicit = true)
            }
            coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(provider))
            val failed = runSongUpdateReport(binder, entry, SongUpdateSelection(title = true))
            assertEquals(setOf(SongUpdateIssue.MISSING_TITLE), failed.issues)
            assertEquals(false, failed.anyCompleted)
            val partial = runSongUpdateReport(binder, entry, SongUpdateSelection(title = true, playtime = true))
            assertTrue(partial.partial)
            assertEquals(setOf(SongUpdateIssue.MISSING_TITLE), partial.issues)
        }
        verify(exactly = 6) { songTable.updateReplace(match { it.title == storedRow.title }) }
        coVerify(exactly = 0) { SongUpdatePlaybackRefresh.publish(any(), any(), any(), any()) }
    }

    @Test
    fun `author fallback issue reaches partial report while selected local reset completes`() = runBlocking {
        coEvery { Innertube.song(entry.id) } returns fetchResult(Result.success(songItem("Fresh", emptyList())))
        coEvery { withVideoAuthorFallback(any(), any(), any(), any(), any()) } coAnswers {
            arg<(SongUpdateIssue) -> Unit>(3)(SongUpdateIssue.NETWORK)
            secondArg<Innertube.SongItem?>()
        }
        val report = runSongUpdateReport(binder, entry, SongUpdateSelection(authors = true, playtime = true))
        assertTrue(report.partial)
        assertTrue(SongUpdateIssue.NETWORK in report.issues)
        verify { songTable.updateReplace(match { it.totalPlayTimeMs == 0L }) }
    }


    @Test
    fun `waveform deletion failure reports partial after playback cache removal completes`() = runBlocking {
        every { WaveformExtractor.deleteWaveformOrThrow(any(), entry.id) } throws java.io.IOException("Waveform file locked")
        val report = runSongUpdateReport(binder, entry, SongUpdateSelection(cache = true))
        assertTrue(report.partial)
        assertEquals(setOf(SongUpdateIssue.CACHE_CLEAR), report.issues)
        verify(exactly = 1) { cache.removeResource(entry.id) }
        verify(exactly = 1) { WaveformExtractor.deleteWaveformOrThrow(any(), entry.id) }
        coVerify(exactly = 0) { Innertube.song(any()) }
    }

}
