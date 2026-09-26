package app.n_zik.android.components.dialog.song

import android.content.Context
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.appContext
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.FormatTable
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
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.ArtistConjunctions
import it.fast4x.innertube.requests.song
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import kotlinx.coroutines.runBlocking
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
    private val internal = mockk<DatabaseInitializer>(relaxed = true)

    private val cache = mockk<Cache>(relaxed = true)
    private val downloadCache = mockk<Cache>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>()

    private val conjunctionsBackup = ArtistConjunctions.conjunctions

    /** A 5-field snapshot, as produced by `MediaItem.asSong` in the player menu. */
    private val entry = Song(
        id = "video_1",
        title = "Snapshot title",
        artistsText = "Snapshot artist",
        durationText = null,
        thumbnailUrl = null
    )

    /** The full stored row (the values out-of-snapshot fields must keep). */
    private val storedRow = Song(
        id = "video_1",
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
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = "video_1")
        ),
        authors = authors,
        album = null,
        durationText = null,
        thumbnail = thumbnailUrl?.let {
            Thumbnail(url = it, height = null, width = null)
        }
    )

    /**
     * MockK's suspend-mocking support unwraps any `returns` value that is
     * itself a `kotlin.Result` one level, which collides with
     * `Innertube.song`'s `Result<SongItem?>?`-typed return; wrapping the
     * intended value in one extra `Result.success(...)` layer absorbs that
     * single unwrap (same trick as SongMatchingDialogSearchOffMainTest).
     */
    @Suppress("UNCHECKED_CAST")
    private fun fetchResult(value: Result<Innertube.SongItem?>): Result<Innertube.SongItem?>? =
        Result.success(value) as Result<Innertube.SongItem?>?

    @Before
    fun setup() {
        mockkObject(Database)
        every { Database.songTable } returns songTable
        every { Database.artistTable } returns artistTable
        every { Database.songArtistMapTable } returns mapTable
        every { Database.formatTable } returns formatTable

        // asyncTransaction runs its block on the Room transactionExecutor: keep
        // the real (lazy) Room database out of the JVM and run the block inline.
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns internal
        every { internal.transactionExecutor } returns Executor { it.run() }

        // Innertube.song is a top-level suspend extension (requests/Queue.kt).
        mockkStatic("it.fast4x.innertube.requests.QueueKt")
        // Default: fetch yields nothing. A test that accidentally triggers a
        // fetch then degrades to "fetch failed" and fails its coVerify count.
        runBlocking {
            coEvery { Innertube.song(any()) } returns null
        }

        mockkObject(Toaster)
        every { Toaster.done() } just runs
        mockkObject(WaveformExtractor)
        every { WaveformExtractor.deleteWaveform(any(), any()) } just runs
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
        every { songTable.findByIdDirect("video_1") } returns storedRow

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
    fun aFetchBoxTriggersExactlyOneFetch() {
        every { songTable.findByIdDirect("video_1") } returns storedRow
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        runBlocking {
            coEvery { Innertube.song(any()) } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(title = true))
            coVerify(exactly = 1) { Innertube.song("video_1") }
        }
    }

    @Test
    fun titleBoxOnlyReplacesTheTitle() {
        every { songTable.findByIdDirect("video_1") } returns storedRow
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        runBlocking {
            coEvery { Innertube.song("video_1") } returns fetchResult(Result.success(item))
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
        every { songTable.findByIdDirect("video_1") } returns storedRow
        val item = songItem(
            "Fetched title",
            listOf(author("Tanchiky", "UC_A"), author("siromaru", "UC_B"))
        )

        runBlocking {
            coEvery { Innertube.song("video_1") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        // artistsText follows the fresh entry names (asSong join)
        assertEquals("Tanchiky, siromaru", updated.captured.artistsText)
        assertEquals("Stored title", updated.captured.title)

        // the complete list re-links inside the same transaction
        verify(exactly = 1) { mapTable.deleteBySongId("video_1") }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_1", "UC_A")) }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_1", "UC_B")) }
        val artists = slot<List<Artist>>()
        verify(exactly = 1) { artistTable.upsert(capture(artists)) }
        assertEquals(setOf("Tanchiky", "siromaru"), artists.captured.map { it.name }.toSet())
        verify(exactly = 1) { Toaster.done() }
    }

    @Test
    fun thumbnailBoxAppliesTheFetchedThumbnail() {
        every { songTable.findByIdDirect("video_1") } returns storedRow
        val item = songItem("Fetched title", emptyList(), thumbnailUrl = "https://fetched/thumb")

        runBlocking {
            coEvery { Innertube.song("video_1") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(thumbnail = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        assertEquals("https://fetched/thumb", updated.captured.thumbnailUrl)
        assertEquals("Stored title", updated.captured.title)
    }

    @Test
    fun existingChannelRowKeepsItsStoredNameInTheReconcile() {
        every { songTable.findByIdDirect("video_1") } returns storedRow
        every { artistTable.findByIdDirect("UC_A") } returns Artist(id = "UC_A", name = "Stored Name A")
        every { artistTable.findByIdDirect("UC_B") } returns null
        val item = songItem(
            "Fetched title",
            listOf(author("Entry A", "UC_A"), author("Entry B", "UC_B"))
        )

        runBlocking {
            coEvery { Innertube.song("video_1") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val artists = slot<List<Artist>>()
        verify(exactly = 1) { artistTable.upsert(capture(artists)) }
        assertEquals(2, artists.captured.size)
        // the existing channel row keeps its stored name, the missing row
        // starts from the entry name
        assertEquals("Stored Name A", artists.captured.first { it.id == "UC_A" }.name)
        assertEquals("Entry B", artists.captured.first { it.id == "UC_B" }.name)
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_1", "UC_A")) }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_1", "UC_B")) }
    }

    @Test
    fun updateReplacePayloadTakesOutOfSnapshotFieldsFromTheStoredRow() {
        every { songTable.findByIdDirect("video_1") } returns storedRow
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        // the entry carries model defaults (null/0/-1/false) - a snapshot built
        // from a MediaItem would clobber the stored row with those if the
        // re-read were missing
        assertEquals(null, entry.likedAt)
        assertEquals(0L, entry.totalPlayTimeMs)
        assertEquals(-1, entry.position)
        assertEquals(false, entry.isYoutubeSong)

        runBlocking {
            coEvery { Innertube.song("video_1") } returns fetchResult(Result.success(item))
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
    fun cacheBoxWipesEveryCache() {
        every { songTable.findByIdDirect("video_1") } returns storedRow

        runBlocking {
            runSongUpdate(binder, entry, SongUpdateSelection(cache = true))
        }

        verify(exactly = 1) { cache.removeResource("video_1") }
        verify(exactly = 1) { downloadCache.removeResource("video_1") }
        verify(exactly = 1) { WaveformExtractor.deleteWaveform(any(), "video_1") }
        verify(exactly = 1) { formatTable.deleteBySongId("video_1") }
        verify(exactly = 1) { formatTable.updateContentLengthOf("video_1") }
    }

    @Test
    fun cacheOpsAreSkippedWhenTheCacheBoxIsUnticked() {
        every { songTable.findByIdDirect("video_1") } returns storedRow
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        runBlocking {
            coEvery { Innertube.song("video_1") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(title = true))
        }

        verify(exactly = 0) { cache.removeResource(any()) }
        verify(exactly = 0) { downloadCache.removeResource(any()) }
        verify(exactly = 0) { WaveformExtractor.deleteWaveform(any(), any()) }
        verify(exactly = 0) { formatTable.deleteBySongId(any()) }
        verify(exactly = 0) { formatTable.updateContentLengthOf(any()) }
    }

    @Test
    fun incompleteAuthorListUpdatesArtistsTextButLeavesLinksUntouched() {
        every { songTable.findByIdDirect("video_1") } returns storedRow
        every { artistTable.findByNameDirect("LocalArtist") } returns
            Artist(id = "LOCAL_1", name = "LocalArtist")
        val item = songItem(
            "Fetched title",
            listOf(author("Tanchiky", "UC_A"), author("LocalArtist", null))
        )

        runBlocking {
            coEvery { Innertube.song("video_1") } returns fetchResult(Result.success(item))
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
        every { songTable.findByIdDirect("video_1") } returns storedRow
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
            coEvery { Innertube.song("video_1") } returns fetchResult(Result.success(item))
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
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_1", "UC_A")) }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("video_1", "UC_B")) }
        // the junk entries end up in neither copy
        verify(exactly = 0) { mapTable.insertIgnore(SongArtistMap("video_1", "UC_SEP")) }
        verify(exactly = 0) { mapTable.insertIgnore(SongArtistMap("video_1", "UC_AND")) }
    }

    @Test
    fun modifiedStoredArtistsTextIsPreservedAndSkipsTheReconcile() {
        every { songTable.findByIdDirect("video_1") } returns storedRow.copy(artistsText = "modified:Custom")
        val item = songItem("Fetched title", listOf(author("Tanchiky", "UC_A")))

        runBlocking {
            coEvery { Innertube.song("video_1") } returns fetchResult(Result.success(item))
            runSongUpdate(binder, entry, SongUpdateSelection(authors = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        // the custom value is authoritative
        assertEquals("modified:Custom", updated.captured.artistsText)
        // its links are left alone
        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
        verify(exactly = 0) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun fetchFailureWritesNoFetchedField() {
        every { songTable.findByIdDirect("video_1") } returns storedRow

        runBlocking {
            coEvery { Innertube.song("video_1") } returns
                fetchResult(Result.failure<Innertube.SongItem?>(RuntimeException("network down")))
            runSongUpdate(binder, entry, SongUpdateSelection(title = true, authors = true, thumbnail = true))
        }

        val updated = slot<Song>()
        verify(exactly = 1) { songTable.updateReplace(capture(updated)) }
        assertEquals("Stored title", updated.captured.title)
        assertEquals("Stored artist", updated.captured.artistsText)
        assertEquals("https://stored/thumb", updated.captured.thumbnailUrl)
        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
    }
}
