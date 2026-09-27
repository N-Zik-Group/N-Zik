package app.n_zik.android.components.dialog.song

import android.content.Context
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
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
import io.mockk.Ordering
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.ArtistConjunctions
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.requests.song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor

/**
 * Composition tests for [runSongUpdateBatch] - the Home Songs toolbar batch
 * « Update » action (local filtering, sequential YTM fetch with rate-limiting,
 * reuse of the per-song [runSongUpdateCore], progress callback after every
 * processed song, cooperative cancel, single summary toast instead of the
 * per-song done toasts).
 *
 * Pattern follows [RunSongUpdateTest]: [Database] is object-mocked with stubbed
 * tables, the lazy Room initializer is kept out of the JVM, and
 * [Database.asyncTransaction] runs its block inline on a stubbed transaction
 * executor. [runBlocking] is used because [runSongUpdateBatch] is suspend and
 * the JVM test has no dispatcher to await on. The random 1-5 s fetch gap
 * between YTM fetches is injected as 0 in every test - rate-limiting is the
 * behavior under test only in production.
 */
class RunSongUpdateBatchTest {

    private val songTable = mockk<SongTable>(relaxed = true)
    private val artistTable = mockk<ArtistTable>(relaxed = true)
    private val mapTable = mockk<SongArtistMapTable>(relaxed = true)
    private val formatTable = mockk<FormatTable>(relaxed = true)
    private val internal = mockk<DatabaseInitializer>(relaxed = true)

    private val cache = mockk<Cache>(relaxed = true)
    private val downloadCache = mockk<Cache>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>()

    private val conjunctionsBackup = ArtistConjunctions.conjunctions

    /** Every row that passes through the per-song core (write capture). */
    private val updatedRows = mutableListOf<Song>()

    /** Progress callbacks recorded by [runSongUpdateBatch] (done to total). */
    private val progressCalls = mutableListOf<Pair<Int, Int>>()

    private val progressRecorder: ( Int, Int ) -> Unit = { done, total -> progressCalls.add( done to total ) }

    private val stored1 = Song(
        id = "video_1",
        title = "Stored title 1",
        artistsText = "Stored artist 1",
        durationText = "3:00",
        thumbnailUrl = "https://stored/thumb1",
        likedAt = 11L,
        totalPlayTimeMs = 4567L,
        position = 1,
        isYoutubeSong = true
    )
    private val stored2 = Song(
        id = "video_2",
        title = "Stored title 2",
        artistsText = "Stored artist 2",
        durationText = "3:30",
        thumbnailUrl = "https://stored/thumb2",
        likedAt = 22L,
        totalPlayTimeMs = 89L,
        position = 2,
        isYoutubeSong = true
    )
    private val localTrack = Song(
        id = "local:track_1",
        title = "Local track",
        artistsText = "Local artist",
        durationText = null,
        thumbnailUrl = null
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
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = "video")
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
     * single unwrap (same trick as RunSongUpdateTest).
     */
    @Suppress("UNCHECKED_CAST")
    private fun fetchResult(value: Result<Innertube.SongItem?>): Result<Innertube.SongItem?>? =
        Result.success(value) as Result<Innertube.SongItem?>?

    @Before
    fun setup() {
        progressCalls.clear()
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
        // The batch summary toast routes its count through the vararg overload
        // (a Long second arg - an Int would bind to s(messageId, duration)).
        every { Toaster.s(any(), any<Long>()) } just runs
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
        every { songTable.updateReplace(any()) } answers { updatedRows.add( firstArg() ) }
    }

    @After
    fun tearDown() {
        ArtistConjunctions.conjunctions = conjunctionsBackup
        unmockkAll()
    }

    @Test
    fun batchWithAFetchBoxFetchesEveryRemoteTargetSequentially() {
        every { songTable.findByIdDirect("video_1") } returns stored1
        every { songTable.findByIdDirect("video_2") } returns stored2
        val stored3 = stored2.copy( id = "video_3", title = "Stored title 3" )
        every { songTable.findByIdDirect("video_3") } returns stored3
        val item = songItem( "Fetched title", listOf(author("Tanchiky", "UC_A")) )

        val processed = runBlocking {
            coEvery { Innertube.song(any()) } returns fetchResult(Result.success(item))
            runSongUpdateBatch( binder, listOf( stored1, stored2, stored3 ), SongUpdateSelection( title = true ), fetchGapMs = { 0L }, progressRecorder )
        }

        assertEquals( 3, processed )
        // progress runs from the first to the last processed song
        assertEquals( listOf( 1 to 3, 2 to 3, 3 to 3 ), progressCalls )
        coVerify( exactly = 3 ) { Innertube.song( any() ) }
        // the requests are sequential, in target order
        coVerify( ordering = Ordering.ORDERED ) {
            Innertube.song( "video_1" )
            Innertube.song( "video_2" )
            Innertube.song( "video_3" )
        }
        verify( exactly = 3 ) { songTable.updateReplace( any() ) }
        assertEquals( 3, updatedRows.size )
        updatedRows.forEach { assertEquals( "Fetched title", it.title ) }
        // one summary toast with the processed count - never per-song done toasts
        verify( exactly = 1 ) { Toaster.s( R.string.updated_songs_count, 3L ) }
        verify( exactly = 0 ) { Toaster.done() }
        assertEquals( 3, progressCalls.size )
    }

    @Test
    fun playtimeOnlyBatchSkipsEveryFetchAndTheRateLimit() {
        every { songTable.findByIdDirect("video_1") } returns stored1
        every { songTable.findByIdDirect("video_2") } returns stored2
        val stored3 = stored2.copy( id = "video_3" )
        every { songTable.findByIdDirect("video_3") } returns stored3

        val processed = runBlocking {
            runSongUpdateBatch( binder, listOf( stored1, stored2, stored3 ), SongUpdateSelection( playtime = true ), onProgress = progressRecorder )
        }

        assertEquals( 3, processed )
        coVerify( exactly = 0 ) { Innertube.song( any() ) }
        verify( exactly = 3 ) { songTable.updateReplace( any() ) }
        updatedRows.forEach { row ->
            assertEquals( 0L, row.totalPlayTimeMs )
            // unticked fetch boxes keep the stored values
            val stored = if ( row.id == "video_1" ) stored1 else stored2
            assertEquals( stored.title, row.title )
        }
        verify( exactly = 1 ) { Toaster.s( R.string.updated_songs_count, 3L ) }
        assertEquals( 3, progressCalls.size )
    }

    @Test
    fun localSongsAreSkippedSilentlyAndTheCountReflectsThat() {
        every { songTable.findByIdDirect("video_1") } returns stored1
        every { songTable.findByIdDirect("video_2") } returns stored2
        val item = songItem( "Fetched title", listOf(author("Tanchiky", "UC_A")) )

        val processed = runBlocking {
            coEvery { Innertube.song(any()) } returns fetchResult(Result.success(item))
            runSongUpdateBatch( binder, listOf( stored1, localTrack, stored2 ), SongUpdateSelection( title = true ), fetchGapMs = { 0L }, progressRecorder )
        }

        assertEquals( 2, processed )
        coVerify( exactly = 2 ) { Innertube.song( any() ) }
        coVerify( exactly = 0 ) { Innertube.song( "local:track_1" ) }
        verify( exactly = 2 ) { songTable.updateReplace( any() ) }
        assertEquals( setOf( "video_1", "video_2" ), updatedRows.map { it.id }.toSet() )
        verify( exactly = 1 ) { Toaster.s( R.string.updated_songs_count, 2L ) }
        // progress counts the remote targets only
        assertEquals( 2, progressCalls.size )
    }

    @Test
    fun emptyTargetsDoNothing() {
        val processed = runBlocking {
            runSongUpdateBatch( binder, emptyList(), SongUpdateSelection( title = true, playtime = true, cache = true ), onProgress = progressRecorder )
        }

        assertEquals( 0, processed )
        coVerify( exactly = 0 ) { Innertube.song( any() ) }
        verify( exactly = 0 ) { songTable.updateReplace( any() ) }
        verify( exactly = 0 ) { Toaster.s( any(), any<Long>() ) }
        verify( exactly = 0 ) { Toaster.done() }
        assertTrue( progressCalls.isEmpty() )
    }

    @Test
    fun allLocalTargetsDoNothing() {
        val processed = runBlocking {
            runSongUpdateBatch(
                binder,
                listOf( localTrack, localTrack.copy( id = "local:track_2" ) ),
                SongUpdateSelection( title = true, playtime = true ),
                onProgress = progressRecorder
            )
        }

        assertEquals( 0, processed )
        coVerify( exactly = 0 ) { Innertube.song( any() ) }
        verify( exactly = 0 ) { songTable.updateReplace( any() ) }
        verify( exactly = 0 ) { Toaster.s( any(), any<Long>() ) }
        assertTrue( progressCalls.isEmpty() )
    }

    @Test
    fun fetchFailureWritesNoFetchedFieldButStillCountsTheSong() {
        every { songTable.findByIdDirect("video_1") } returns stored1
        every { songTable.findByIdDirect("video_2") } returns stored2

        val processed = runBlocking {
            coEvery { Innertube.song(any()) } returns
                fetchResult( Result.failure<Innertube.SongItem?>( RuntimeException( "network down" ) ) )
            runSongUpdateBatch( binder, listOf( stored1, stored2 ), SongUpdateSelection( title = true, authors = true, thumbnail = true ), fetchGapMs = { 0L }, progressRecorder )
        }

        assertEquals( 2, processed )
        coVerify( exactly = 2 ) { Innertube.song( any() ) }
        verify( exactly = 2 ) { songTable.updateReplace( any() ) }
        updatedRows.forEach { row ->
            val stored = if ( row.id == "video_1" ) stored1 else stored2
            // the null fetch keeps every stored value
            assertEquals( stored.title, row.title )
            assertEquals( stored.artistsText, row.artistsText )
            assertEquals( stored.thumbnailUrl, row.thumbnailUrl )
        }
        // a failed fetch cannot back a reconcile
        verify( exactly = 0 ) { mapTable.deleteBySongId( any() ) }
        verify( exactly = 0 ) { mapTable.insertIgnore( any<SongArtistMap>() ) }
        // parity with the single action: the count reflects processed targets
        verify( exactly = 1 ) { Toaster.s( R.string.updated_songs_count, 2L ) }
        assertEquals( 2, progressCalls.size )
    }

    @Test
    fun modifiedArtistsTextSkipsTheReconcileForThatSongOnly() {
        every { songTable.findByIdDirect("video_1") } returns stored1.copy( artistsText = "modified:Custom" )
        every { songTable.findByIdDirect("video_2") } returns stored2
        val item = songItem(
            "Fetched title",
            listOf( author("Tanchiky", "UC_A"), author("siromaru", "UC_B") )
        )

        runBlocking {
            coEvery { Innertube.song(any()) } returns fetchResult(Result.success(item))
            runSongUpdateBatch( binder, listOf( stored1, stored2 ), SongUpdateSelection( authors = true ), fetchGapMs = { 0L }, progressRecorder )
        }

        verify( exactly = 2 ) { songTable.updateReplace( any() ) }
        val row1 = updatedRows.first { it.id == "video_1" }
        val row2 = updatedRows.first { it.id == "video_2" }
        // the custom value is authoritative and survives the merge
        assertEquals( "modified:Custom", row1.artistsText )
        // the fresh complete list replaces the plain stored value
        assertEquals( "Tanchiky, siromaru", row2.artistsText )
        // links: untouched for the modified song, re-linked for the other one
        verify( exactly = 0 ) { mapTable.deleteBySongId( "video_1" ) }
        verify( exactly = 1 ) { mapTable.deleteBySongId( "video_2" ) }
        val inserts = mutableListOf<SongArtistMap>()
        verify( exactly = 2 ) { mapTable.insertIgnore( capture( inserts ) ) }
        assertEquals( 0, inserts.count { it.songId == "video_1" } )
        assertEquals( listOf( "UC_A", "UC_B" ).toSet(), inserts.filter { it.songId == "video_2" }.map { it.artistId }.toSet() )
        val artists = slot<List<Artist>>()
        verify( exactly = 1 ) { artistTable.upsert( capture( artists ) ) }
        assertEquals( setOf( "Tanchiky", "siromaru" ), artists.captured.map { it.name }.toSet() )
        assertEquals( 2, progressCalls.size )
    }

    @Test
    fun cancelAfterTheFirstSongStopsTheBatchBetweenSongs() {
        every { songTable.findByIdDirect("video_1") } returns stored1
        every { songTable.findByIdDirect("video_2") } returns stored2
        val item = songItem( "Fetched title", listOf(author("Tanchiky", "UC_A")) )
        var processed = -1
        var batchJob: Job? = null

        runBlocking {
            coEvery { Innertube.song(any()) } returns fetchResult( Result.success(item) )
            // Unconfined: the batch runs inline, so the cancel lands exactly
            // in the gap right after the first song is fully processed. The
            // scope is the cancel target - the job var is still null while
            // the inline body runs (its assignment happens only after
            // launch returns).
            val scope = CoroutineScope( SupervisorJob() + Dispatchers.Unconfined )
            batchJob = scope.launch {
                processed = runSongUpdateBatch(
                    binder,
                    listOf( stored1, stored2 ),
                    SongUpdateSelection( title = true )
                ) { done, total ->
                    progressCalls.add( done to total )
                    if( done == 1 ) scope.cancel()
                }
            }
            batchJob.join()
        }

        // the first song is fully written, the second is never started
        assertEquals( 1, processed )
        assertEquals( 1, updatedRows.size )
        coVerify( exactly = 1 ) { Innertube.song( any() ) }
        verify( exactly = 1 ) { songTable.updateReplace( any() ) }
        // a cancelled batch shows no summary toast
        verify( exactly = 0 ) { Toaster.s( any(), any<Long>() ) }
        assertEquals( listOf( 1 to 2 ), progressCalls )
    }

    @Test
    fun alreadyCancelledContextProcessesNothing() {
        val cancelled = Job()
        cancelled.cancel()
        // A cancelled context must yield no fetch, no write, no toast, no
        // progress - the cooperative check returns the processed count (0).
        val processed = try {
            runBlocking( cancelled ) {
                runSongUpdateBatch( binder, listOf( stored1, stored2 ), SongUpdateSelection( title = true ), onProgress = progressRecorder )
            }
        } catch( _: CancellationException ) {
            0
        }
        assertEquals( 0, processed )
        coVerify( exactly = 0 ) { Innertube.song( any() ) }
        verify( exactly = 0 ) { songTable.updateReplace( any() ) }
        verify( exactly = 0 ) { Toaster.s( any(), any<Long>() ) }
        assertTrue( progressCalls.isEmpty() )
    }
}
