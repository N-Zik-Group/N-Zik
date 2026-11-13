package app.n_zik.android.components.player.lyrics

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.LyricsTable
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import com.metrolist.music.betterlyrics.BetterLyrics
import com.metrolist.music.betterlyrics.TTMLParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import app.n_zik.kugou.KuGou
import app.n_zik.lrclib.LrcLib
import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class LyricsFetchWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val metadata = MediaMetadata.Builder()
        .setTitle("Test Title")
        .setArtist("Test Artist")
        .build()

    private val lyricsDao = mockk<LyricsTable>()
    private val albumDao = mockk<AlbumTable>()
    private val upserts = mutableListOf<Lyrics>()

    private val lyricsUpdates = mutableListOf<Lyrics?>()
    private val errorUpdates = mutableListOf<Boolean>()
    private val checkedLrc = mutableListOf<Boolean>()
    private val checkedKugou = mutableListOf<Boolean>()
    private val checkedInnertube = mutableListOf<Boolean>()
    private val fetchingStates = mutableListOf<Boolean>()

    // LrcLib/KuGou/Innertube lyrics() are `suspend fun`s returning `kotlin.Result<T>`;
    // MockK cannot mock those reliably (ABI-level limitation: the continuation's own

    // Result and the declared Result<T> get confused -- ClassCastException at runtime,
    // confirmed even with `coAnswers`). LyricsFetchWorker takes the three calls as

    // constructor-injected lambdas (like its dispatcher params), so tests control them
    // as plain values and call counts below, without MockK touching those functions.
    private var lrcLibLyricsResult: Result<LrcLib.Lyrics?>? = Result.success(null)
    private var lrcLibLyricsUnsyncedResult: Result<LrcLib.Lyrics?>? = Result.success(null)
    private var kuGouLyricsResult: Result<KuGou.Lyrics?>? = Result.success(null)
    private var innertubeLyricsResult: Result<String?>? = Result.success(null)

    private var lrcLibLyricsCalls = 0
    private var lrcLibLyricsUnsyncedCalls = 0
    private var kuGouLyricsCalls = 0
    private var innertubeLyricsCalls = 0

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun setupMocks() {
        mockkObject(Database)
        every { Database.lyricsTable } returns lyricsDao
        every { Database.albumTable } returns albumDao

        coEvery { Database.transaction(any()) } coAnswers {
            firstArg<suspend Database.() -> Unit>().invoke(Database)
        }
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(emptyList())
        every { lyricsDao.findBySongIdAndType(any(), any()) } returns flowOf(null)
        every { lyricsDao.upsert(any()) } answers {
            upserts += firstArg<Lyrics>()
            1L
        }
        every { albumDao.findBySongId(any()) } returns flowOf(null)

        mockkObject(BetterLyrics)
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } returns null

        lrcLibLyricsResult = Result.success(null)
        lrcLibLyricsUnsyncedResult = Result.success(null)
        kuGouLyricsResult = Result.success(null)
        innertubeLyricsResult = Result.success(null)
        lrcLibLyricsCalls = 0
        lrcLibLyricsUnsyncedCalls = 0
        kuGouLyricsCalls = 0
        innertubeLyricsCalls = 0
    }

    private fun resetGlobals() {
        globalLastKaraokeAttemptMediaId = null
        globalLastSyncedAttemptMediaId = null
        globalLastUnSyncedAttemptMediaId = null
    }

    private fun worker(
        scheduler: TestCoroutineScheduler,
        mediaDispatcher: CoroutineDispatcher = StandardTestDispatcher(scheduler)
    ) = LyricsFetchWorker(
        dataDispatcher = StandardTestDispatcher(scheduler),
        mediaDispatcher = mediaDispatcher,
        uiDispatcher = StandardTestDispatcher(scheduler),
        lrcLibLyrics = { _, _, _, _ -> lrcLibLyricsCalls++; lrcLibLyricsResult },
        lrcLibLyricsUnsynced = { _, _, _, _ -> lrcLibLyricsUnsyncedCalls++; lrcLibLyricsUnsyncedResult },
        kuGouLyrics = { _, _, _ -> kuGouLyricsCalls++; kuGouLyricsResult },
        innertubeLyrics = { _ -> innertubeLyricsCalls++; innertubeLyricsResult }
    )

    private suspend fun fetch(
        worker: LyricsFetchWorker,
        lyricsType: LyricsType,
        enabledProviders: Set<LyricsProvider> = LyricsProvider.entries.toSet(),
        durationProvider: () -> Long = { 213_000L }
    ) {
        worker.fetch(
            context = context,
            mediaId = "song1",
            lyricsType = lyricsType,
            artistName = "Test Artist",
            title = "Test Title",
            mediaMetadata = metadata,
            durationProvider = durationProvider,
            playerEnableLyricsPopupMessage = false,
            onLyricsUpdated = { lyricsUpdates += it },
            onErrorUpdated = { errorUpdates += it },
            onCheckedLrcUpdated = { checkedLrc += it },
            onCheckedKugouUpdated = { checkedKugou += it },
            onCheckedInnertubeUpdated = { checkedInnertube += it },
            onFetchingStateChanged = { fetchingStates += it },
            enabledProviders = enabledProviders
        )
    }

    @Test
    fun `unsynced path saves LrcLib unsynced lyrics and skips other sources`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsUnsyncedResult = Result.success(LrcLib.Lyrics("plain lyrics text"))

        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Unsynced.name, upserts.first().type)
        assertEquals("plain lyrics text", upserts.first().data)
        assertEquals(1, lrcLibLyricsUnsyncedCalls)
        assertEquals(0, kuGouLyricsCalls)
        assertEquals(0, innertubeLyricsCalls)
        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(listOf(true, false), fetchingStates)
        assertEquals(null, lyricsUpdates.last())
    }

    @Test
    fun `second fetch for same song is deduplicated without network`() = runTest {
        setupMocks()
        resetGlobals()

        fetch(worker(testScheduler), LyricsType.Karaoke)
        fetch(worker(testScheduler), LyricsType.Karaoke)

        coVerify(exactly = 1) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(0, upserts.size)
        assertEquals("song1", globalLastKaraokeAttemptMediaId)
    }

    @Test
    fun `cancellation during duration wait aborts chain without network calls`() = runTest {
        setupMocks()
        resetGlobals()

        val fetchJob = launch {
            worker(testScheduler).fetch(
                context = context,
                mediaId = "song1",
                lyricsType = LyricsType.Karaoke,
                artistName = "Test Artist",
                title = "Test Title",
                mediaMetadata = metadata,
                durationProvider = { C.TIME_UNSET },
                playerEnableLyricsPopupMessage = false,
                onLyricsUpdated = { lyricsUpdates += it },
                onErrorUpdated = { errorUpdates += it },
                onCheckedLrcUpdated = { checkedLrc += it },
                onCheckedKugouUpdated = { checkedKugou += it },
                onCheckedInnertubeUpdated = { checkedInnertube += it },
                onFetchingStateChanged = { fetchingStates += it }
            )
        }
        delay(300)
        fetchJob.cancelAndJoin()

        assertTrue(fetchJob.isCancelled)
        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(0, lrcLibLyricsCalls)
        assertEquals(0, kuGouLyricsCalls)
        assertEquals(0, upserts.size)
        assertEquals(null, globalLastKaraokeAttemptMediaId)

        fetch(worker(testScheduler), LyricsType.Karaoke)

        coVerify(exactly = 1) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
    }

    @Test
    fun `karaoke path parses TTML on media dispatcher and saves karaoke lyrics`() = runTest {
        setupMocks()
        resetGlobals()

        val mediaExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "media-test") }
        val fetchThreads = CopyOnWriteArrayList<String>()
        val parseThreads = CopyOnWriteArrayList<String>()
        val lrcThreads = CopyOnWriteArrayList<String>()
        val parsedLines = listOf(
            TTMLParser.ParsedLine(
                text = "Hello world",
                startTime = 1.0,
                words = listOf(
                    TTMLParser.ParsedWord("Hello", 1.0, 1.5),
                    TTMLParser.ParsedWord("world", 1.5, 2.0)
                )
            )
        )

        mockkObject(TTMLParser)
        every { TTMLParser.parseTTML(any()) } answers {
            parseThreads += Thread.currentThread().name
            parsedLines
        }
        every { TTMLParser.toLRC(any()) } answers {
            lrcThreads += Thread.currentThread().name
            "[00:01.00]Hello world\n<Hello:1.0:1.5|world:1.5:2.0>\n"
        }
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } answers {
            fetchThreads += Thread.currentThread().name
            "<tt></tt>"
        }

        fetch(worker(testScheduler, mediaDispatcher = mediaExecutor.asCoroutineDispatcher()), LyricsType.Karaoke)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Karaoke.name, upserts.first().type)
        assertTrue(upserts.first().data.orEmpty().contains("[00:01.00]Hello world"))

        assertEquals(listOf("media-test"), parseThreads)
        assertEquals(listOf("media-test"), lrcThreads)
        assertEquals(1, fetchThreads.size)
        assertTrue(fetchThreads[0] != "media-test", "fetchTTML must not run on the media dispatcher")

        unmockkAll()
        mediaExecutor.shutdown()
    }

    @Test
    fun `synced path saves BetterLyrics synced lyrics with real TTML parser`() = runTest {
        setupMocks()
        resetGlobals()

        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:00:01.000">Hello world</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } returns ttml

        fetch(worker(testScheduler), LyricsType.Synced)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Synced.name, upserts.first().type)
        assertTrue(upserts.first().data.orEmpty().contains("[00:01.00]Hello world"))
        assertEquals(0, lrcLibLyricsCalls)
    }

    @Test
    fun `unsynced fallback uses YouTube when LrcLib fails`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsUnsyncedResult = Result.failure(Exception("lrc down"))
        innertubeLyricsResult = Result.success("youtube lyrics")

        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Unsynced.name, upserts.first().type)
        assertEquals("youtube lyrics", upserts.first().data)
        assertTrue(checkedInnertube.contains(true))
    }

    @Test
    fun `synced fallback chain falls through to KuGou`() = runTest {
        setupMocks()
        resetGlobals()
        val kugouText = "[00:01.00]Hello\n[00:02.00]World"
        lrcLibLyricsResult = Result.failure(Exception("lrc down"))
        kuGouLyricsResult = Result.success(KuGou.Lyrics(kugouText))

        fetch(worker(testScheduler), LyricsType.Synced)

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Synced.name, upserts.first().type)
        assertEquals(kugouText, upserts.first().data)
        assertEquals(0, innertubeLyricsCalls)
    }

    @Test
    fun `edited lyrics already stored trigger no network call and no write in their own mode`() = runTest {
        setupMocks()
        resetGlobals()
        val edited = Lyrics("song1", LyricsType.Unsynced.name, "my own text", isEdited = true)
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(edited))

        // Only the Unsynced check is active in this mode, and the edited row's own check is off.
        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertEquals(0, lrcLibLyricsCalls)
        assertEquals(0, lrcLibLyricsUnsyncedCalls)
        assertEquals(0, kuGouLyricsCalls)
        assertEquals(0, innertubeLyricsCalls)
        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(0, upserts.size)
        assertEquals(edited, lyricsUpdates.last())
    }

    @Test
    fun `edited row with expired TTL triggers no fetch and no write`() = runTest {
        setupMocks()
        resetGlobals()
        val edited = Lyrics(
            songId = "song1",
            type = LyricsType.Karaoke.name,
            data = "[00:01.00]Hello\n<Hello:1.0:1.5|world:1.5:2.0>",
            isEdited = true,
            lastFetchedAt = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        )
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(edited))

        fetch(worker(testScheduler), LyricsType.Karaoke)

        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(0, lrcLibLyricsCalls)
        assertEquals(0, upserts.size)
        assertEquals(edited, lyricsUpdates.last())
    }

    @Test
    fun `fetch stamps lastFetchedAt on the written row`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsUnsyncedResult = Result.success(LrcLib.Lyrics("plain lyrics text"))

        val before = System.currentTimeMillis()
        fetch(worker(testScheduler), LyricsType.Unsynced)
        val after = System.currentTimeMillis()

        assertEquals(1, upserts.size)
        val stamp = checkNotNull(upserts.first().lastFetchedAt) { "expected lastFetchedAt to be stamped on the written row" }
        assertTrue(stamp >= before && stamp <= after)
    }

    @Test
    fun `expired word-timed row is re-fetched and overwritten with a fresh stamp`() = runTest {
        setupMocks()
        resetGlobals()
        val expired = Lyrics(
            songId = "song1",
            type = LyricsType.Karaoke.name,
            data = "[00:00.00]Old\n<Old:0.0:1.0>",
            lastFetchedAt = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        )
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(expired))

        val parsedLines = listOf(
            TTMLParser.ParsedLine(
                text = "Hello world",
                startTime = 1.0,
                words = listOf(
                    TTMLParser.ParsedWord("Hello", 1.0, 1.5),
                    TTMLParser.ParsedWord("world", 1.5, 2.0)
                )
            )
        )
        mockkObject(TTMLParser)
        every { TTMLParser.parseTTML(any()) } returns parsedLines
        every { TTMLParser.toLRC(any()) } returns "[00:01.00]Hello world\n<Hello:1.0:1.5|world:1.5:2.0>\n"
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } returns "<tt></tt>"

        val before = System.currentTimeMillis()
        fetch(worker(testScheduler), LyricsType.Karaoke)
        val after = System.currentTimeMillis()

        assertEquals(1, upserts.size)
        assertEquals(LyricsType.Karaoke.name, upserts.first().type)
        assertTrue(upserts.first().data.orEmpty().contains("Hello world"))
        val stamp = checkNotNull(upserts.first().lastFetchedAt) { "expected lastFetchedAt to be stamped on the overwritten row" }
        assertTrue(stamp >= before && stamp <= after)
    }

    @Test
    fun `expired word-timed row keeps its data and stamp when every source fails`() = runTest {
        setupMocks()
        resetGlobals()
        val expired = Lyrics(
            songId = "song1",
            type = LyricsType.Karaoke.name,
            data = "[00:00.00]Old\n<Old:0.0:1.0>",
            lastFetchedAt = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        )
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(expired))
        // Every source fails, like a network outage.
        lrcLibLyricsResult = Result.failure(Exception("network down"))
        lrcLibLyricsUnsyncedResult = Result.failure(Exception("network down"))
        kuGouLyricsResult = Result.failure(Exception("network down"))
        innertubeLyricsResult = Result.failure(Exception("network down"))

        fetch(worker(testScheduler), LyricsType.Auto)

        assertTrue(upserts.isEmpty()) // nothing written: stored row and its stamp are untouched
        coVerify(exactly = 1) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(1, lrcLibLyricsCalls) // the whole fallback chain was attempted
        assertEquals(1, kuGouLyricsCalls)
        assertEquals(1, lrcLibLyricsUnsyncedCalls)
        assertEquals(1, innertubeLyricsCalls)
    }

    @Test
    fun `fresh word-timed karaoke row is not re-fetched when only a Synced sibling is missing`() = runTest {
        setupMocks()
        resetGlobals()
        val fresh = Lyrics(
            songId = "song1",
            type = LyricsType.Karaoke.name,
            data = "[00:00.00]Fresh\n<Fresh:0.0:1.0>",
            lastFetchedAt = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000
        )
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(fresh))
        lrcLibLyricsResult = Result.success(LrcLib.Lyrics("[00:01.00]synced from LrcLib"))

        fetch(worker(testScheduler), LyricsType.Auto)

        // BetterLyrics would rewrite and re-stamp the fresh Karaoke row: it must stay untouched.
        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(1, lrcLibLyricsCalls)
        assertTrue(upserts.none { it.type == LyricsType.Karaoke.name })
        assertEquals(listOf(LyricsType.Synced.name), upserts.map { it.type })
    }

    @Test
    fun `only the Unsynced check runs when karaoke and synced rows are fresh and unsynced is missing`() = runTest {
        setupMocks()
        resetGlobals()
        val freshStamp = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000
        val karaoke = Lyrics("song1", LyricsType.Karaoke.name, "[00:00.00]K\n<K:0.0:1.0>", lastFetchedAt = freshStamp)
        val synced = Lyrics("song1", LyricsType.Synced.name, "[00:00.00]S\n<S:0.0:1.0>", lastFetchedAt = freshStamp)
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(karaoke, synced))
        lrcLibLyricsUnsyncedResult = Result.success(LrcLib.Lyrics("[00:01.00]plain lyrics text")) // plainText comes from timestamped lines

        fetch(worker(testScheduler), LyricsType.Auto)

        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(0, lrcLibLyricsCalls)
        assertEquals(0, kuGouLyricsCalls)
        assertEquals(1, lrcLibLyricsUnsyncedCalls)
        assertEquals(listOf(LyricsType.Unsynced.name), upserts.map { it.type })
    }

    @Test
    fun `empty LrcLib result does not wipe an existing non-empty unsynced row`() = runTest {
        setupMocks()
        resetGlobals()
        val stored = Lyrics("song1", LyricsType.Unsynced.name, "my stored lyrics") // unstamped, not edited
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(stored))
        every { lyricsDao.findBySongIdAndType("song1", LyricsType.Unsynced.name) } returns flowOf(stored)
        lrcLibLyricsUnsyncedResult = Result.success(null) // no lyrics at the source

        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertTrue(upserts.isEmpty()) // the empty result must not clear the stored row
        assertEquals(1, lrcLibLyricsUnsyncedCalls)
    }

    @Test
    fun `empty LrcLib synced result does not wipe an existing non-empty synced row`() = runTest {
        setupMocks()
        resetGlobals()
        val stored = Lyrics("song1", LyricsType.Synced.name, "synced lines") // unstamped, not edited
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(stored))
        every { lyricsDao.findBySongIdAndType("song1", LyricsType.Synced.name) } returns flowOf(stored)
        // BetterLyrics (default null) and LrcLib synced (default null) both come back empty.

        fetch(worker(testScheduler), LyricsType.Synced)

        assertTrue(upserts.isEmpty()) // the empty result must not clear the stored row
    }

    @Test
    fun `fetched lyrics do not overwrite an edit saved while the fetch was in flight`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsUnsyncedResult = Result.success(LrcLib.Lyrics("fetched text"))
        // The emission the fetch decided on held no lyrics; the edit lands before the write.
        every { lyricsDao.findBySongIdAndType("song1", LyricsType.Unsynced.name) } returns
            flowOf(Lyrics("song1", LyricsType.Unsynced.name, "my own text", isEdited = true))

        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertEquals(1, lrcLibLyricsUnsyncedCalls)
        assertEquals(0, upserts.size)
    }

    @Test
    fun `fetched lyrics replace a stored row that is edited but emptied`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsUnsyncedResult = Result.success(LrcLib.Lyrics("fetched text"))
        every { lyricsDao.findBySongIdAndType("song1", LyricsType.Unsynced.name) } returns
            flowOf(Lyrics("song1", LyricsType.Unsynced.name, "", isEdited = true))

        fetch(worker(testScheduler), LyricsType.Unsynced)

        assertEquals(1, upserts.size)
        assertEquals("fetched text", upserts.first().data)
        assertEquals(false, upserts.first().isEdited)
    }

    @Test
    fun `Auto skips disabled providers and continues from a blank synced result`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsResult = Result.success(LrcLib.Lyrics(" "))
        val timedLyrics = "[00:01.00]Hello"
        kuGouLyricsResult = Result.success(KuGou.Lyrics(timedLyrics))

        fetch(worker(testScheduler), LyricsType.Auto, setOf(LyricsProvider.LrcLib, LyricsProvider.KuGou))

        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(1, lrcLibLyricsCalls)
        assertEquals(1, kuGouLyricsCalls)
        assertEquals(0, lrcLibLyricsUnsyncedCalls)
        assertEquals(timedLyrics, upserts.single().data)
    }

    @Test
    fun `Auto continues from null synced responses to the enabled plain text source`() = runTest {
        setupMocks()
        resetGlobals()
        kuGouLyricsResult = null
        innertubeLyricsResult = Result.success("A line without timings")

        fetch(worker(testScheduler), LyricsType.Auto, setOf(LyricsProvider.KuGou, LyricsProvider.YouTube))

        assertEquals(0, lrcLibLyricsCalls)
        assertEquals(0, lrcLibLyricsUnsyncedCalls)
        assertEquals(1, kuGouLyricsCalls)
        assertEquals(1, innertubeLyricsCalls)
        assertEquals(LyricsType.Unsynced.name, upserts.single().type)
    }

    @Test
    fun `Auto can reach YouTube without a track duration`() = runTest {
        setupMocks()
        resetGlobals()
        innertubeLyricsResult = Result.success("A line without timings")

        fetch(worker(testScheduler), LyricsType.Auto, setOf(LyricsProvider.KuGou, LyricsProvider.YouTube)) {
            C.TIME_UNSET
        }

        assertEquals(0, kuGouLyricsCalls)
        assertEquals(1, innertubeLyricsCalls)
        assertEquals(LyricsType.Unsynced.name, upserts.single().type)
    }

    @Test
    fun `Sync with only LrcLib enabled neither invokes other sources nor requests plain text`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsResult = Result.success(null)
        lrcLibLyricsUnsyncedResult = Result.success(LrcLib.Lyrics("Untimed lyrics"))

        fetch(worker(testScheduler), LyricsType.Synced, setOf(LyricsProvider.LrcLib))

        coVerify(exactly = 0) { BetterLyrics.fetchTTML(any(), any(), any(), any()) }
        assertEquals(1, lrcLibLyricsCalls)
        assertEquals(0, lrcLibLyricsUnsyncedCalls)
        assertEquals(0, kuGouLyricsCalls)
        assertEquals(0, innertubeLyricsCalls)
        assertTrue(upserts.isEmpty())
    }

    @Test
    fun `provider cancellation stops Auto before the next source`() = runTest {
        setupMocks()
        resetGlobals()
        lrcLibLyricsResult = Result.failure(CancellationException("cancelled"))

        val fetchJob = launch {
            fetch(worker(testScheduler), LyricsType.Auto, setOf(LyricsProvider.LrcLib, LyricsProvider.KuGou))
        }
        fetchJob.join()

        assertTrue(fetchJob.isCancelled)
        assertEquals(1, lrcLibLyricsCalls)
        assertEquals(0, kuGouLyricsCalls)
        assertTrue(upserts.isEmpty())
    }

    @Test
    fun `Sync projects BetterLyrics word timings to line timings`() = runTest {
        setupMocks()
        resetGlobals()
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:01.000" end="00:00:03.000">
                  <span begin="00:00:01.000" end="00:00:02.000">Hello</span>
                  <span begin="00:00:02.000" end="00:00:03.000">world</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } returns ttml

        fetch(worker(testScheduler), LyricsType.Synced, setOf(LyricsProvider.BetterLyrics))

        val row = upserts.single()
        assertEquals(LyricsType.Synced.name, row.type)
        assertTrue(row.data.orEmpty().startsWith("[00:01.00]"))
        assertTrue(row.data.orEmpty().lineSequence().none { it.startsWith("<") })
    }

    @Test
    fun `Auto stores a line-only BetterLyrics result as Synced`() = runTest {
        setupMocks()
        resetGlobals()
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } returns """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div><p begin="00:00:01.000">A timed line</p></div></body>
            </tt>
        """.trimIndent()

        fetch(worker(testScheduler), LyricsType.Auto, setOf(LyricsProvider.BetterLyrics))

        assertEquals(LyricsType.Synced.name, upserts.single().type)
        assertEquals(null, globalLastUnSyncedAttemptMediaId)
    }

    @Test
    fun `Auto preserves a manual Sync edit when BetterLyrics only finds line timings`() = runTest {
        setupMocks()
        resetGlobals()
        val edit = Lyrics("song1", LyricsType.Synced.name, "[00:01.00]My lyrics", isEdited = true)
        val oldAuto = Lyrics("song1", LyricsType.Karaoke.name, "[00:01.00]Old automatic result")
        every { lyricsDao.findAllBySongId(any()) } returns flowOf(listOf(oldAuto, edit))
        coEvery { BetterLyrics.fetchTTML(any(), any(), any(), any()) } returns """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div><p begin="00:00:01.000">Provider result</p></div></body>
            </tt>
        """.trimIndent()

        fetch(worker(testScheduler), LyricsType.Auto, setOf(LyricsProvider.BetterLyrics))

        assertEquals(edit, lyricsUpdates.single())
        assertTrue(upserts.isEmpty())
    }

    @Test
    fun `reopening a completed empty search reports that no lyrics are available`() = runTest {
        setupMocks()
        resetGlobals()
        val providers = setOf(LyricsProvider.YouTube)

        fetch(worker(testScheduler), LyricsType.Unsynced, providers)
        errorUpdates.clear()
        checkedLrc.clear()
        checkedKugou.clear()
        checkedInnertube.clear()
        fetch(worker(testScheduler), LyricsType.Unsynced, providers)

        assertEquals(1, innertubeLyricsCalls)
        assertEquals(listOf(true), errorUpdates)
        assertEquals(listOf(true), checkedLrc)
        assertEquals(listOf(true), checkedKugou)
        assertEquals(listOf(true), checkedInnertube)
    }
}
