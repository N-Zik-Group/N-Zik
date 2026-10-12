package app.n_zik.android.playback.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongPlaylistMapTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.utils.discoverKey
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.preferences
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.*
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.nextPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class NZikRadioTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var binder: PlayerServiceModern.Binder
    private lateinit var mockPlayer: ExoPlayer
    private lateinit var nZikRadio: NZikRadio

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        // Mock binder and player
        binder = mockk(relaxed = true)
        mockPlayer = mockk<ExoPlayer>(relaxed = true)
        every { binder.player } returns mockPlayer
        every { mockPlayer.currentMediaItem } returns null

        // Mock static extension property Context.preferences
        mockkStatic("app.it.fast4x.rimusic.utils.PreferencesKt")
        every { context.preferences } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs

        // The blocked-op toast throttle is process-wide real-time state; reset it
        // so each guest-lock test starts with a clean 2s window.
        ListenTogetherGuestGuardPlayer.clearUiBlockedOpThrottleForTests()

        // Mock Toaster object
        mockkObject(Toaster)
        every { Toaster.i(any<Int>()) } just Runs
        every { Toaster.i(any<String>()) } just Runs
        every { Toaster.e(any<Int>()) } just Runs

        nZikRadio = NZikRadio(context, binder, CoroutineScope(Dispatchers.Unconfined))
    }

    @AfterEach
    fun teardown() {
        // Restores the real Main dispatcher (no-op when setupFetchHarness was never used).
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun setupState(autoFillEnabled: Boolean, discoverEnabled: Boolean) {
        every { sharedPreferences.getBoolean(autoLoadSongsInQueueKey, true) } returns autoFillEnabled
        every { sharedPreferences.getBoolean(discoverKey, false) } returns discoverEnabled
    }

    private fun createMediaItem(): MediaItem {
        return MediaItem.Builder().setMediaId("test_id").build()
    }

    // --- ISSUE #880 DETERMINISTIC HARNESS ---

    private fun mediaItem(id: String) = MediaItem.Builder().setMediaId(id).build()

    /**
     * Makes the relaxed [ExoPlayer] mock behave like a live queue backed by [queue].
     *
     * The media3 `Player` interface DOES declare `getMediaItems()`, but nothing in [NZikRadio]
     * reads it: `discoverFilter` uses the legacy extension `Player.mediaItems`, which is an
     * `AbstractList` over `mediaItemCount`/`getMediaItemAt`, and `injectIntoPlayer` reads
     * `mediaItemCount` directly — so stubbing exactly those two members (plus the mutating
     * `addMediaItems`/`removeMediaItems`/`moveMediaItem`) is what actually drives the queue in
     * both code paths and in the assertions below.
     */
    private fun stubQueue(queue: MutableList<MediaItem>) {
        every { mockPlayer.mediaItemCount } answers { queue.size }
        every { mockPlayer.getMediaItemAt(any()) } answers { queue[firstArg<Int>()] }
        every { mockPlayer.addMediaItems(any<List<MediaItem>>()) } answers {
            queue.addAll(firstArg())
            Unit
        }
        every { mockPlayer.removeMediaItems(any<Int>(), any<Int>()) } answers {
            queue.subList(firstArg<Int>(), secondArg<Int>()).clear()
            Unit
        }
        every { mockPlayer.moveMediaItem(any<Int>(), any<Int>()) } answers {
            queue.add(secondArg<Int>(), queue.removeAt(firstArg<Int>()))
            Unit
        }
    }

    /**
     * Deterministic fetch harness for the issue #880 tests:
     *
     * - [NZikRadio.fetchDispatcher] is swapped for an [UnconfinedTestDispatcher] (same seam
     *   pattern as `ShufflerTest`/`Shuffler.backgroundDispatcher`) so the fetch job runs to
     *   completion synchronously on the test thread — assertions right after `startRadio` /
     *   `autoFillQueue` are deterministic; no wall-clock polling, no `verify(timeout = ...)`.
     * - `Dispatchers.Main` (== `NzikDispatchers.UI`) is likewise an [UnconfinedTestDispatcher],
     *   because `fetchAndInject` hops to it for the queue read and the injection.
     * - `Innertube.nextPage` (top-level suspend extension in `NextPageKt`) is static-mocked;
     *   see [nextPageResult] for the double-wrap.
     * - [Database] is object-mocked: `asyncTransaction` is a no-op (its block — the
     *   `insertIgnore` writes — never runs) and `songTable` is a relaxed child mock whose
     *   dislike lookups come back empty, so the filters keep every fetched song.
     * - The real `SongItem.asMediaItem` (legacy, read-only) calls `Uri.parse` and
     *   `androidx.core.os.BundleKt.bundleOf`, which are unavailable on the JVM — both are
     *   static-mocked with relaxed stand-ins here and released by `unmockkAll()` in [teardown].
     */
    private fun setupFetchHarness() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        nZikRadio.fetchDispatcher = UnconfinedTestDispatcher()

        mockkStatic("it.fast4x.innertube.requests.NextPageKt")
        mockkObject(Database)
        every { Database.asyncTransaction(any(), any()) } just Runs
        every { Database.songTable } returns mockk<SongTable>(relaxed = true)
        // `asMediaItem` calls `Uri.parse(...)` and `bundleOf(...)` per song — the returned
        // mocks are only ever stored by the MediaItem/MediaMetadata builders.
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        mockkStatic("androidx.core.os.BundleKt")
        // the vararg signature of `bundleOf` requires the spread syntax
        every { bundleOf(*anyVararg<Pair<String, Any?>>()) } returns mockk(relaxed = true)
    }

    /**
     * Models production `forcePlay` = `setMediaItem(item, true)`, which replaces the WHOLE
     * queue with the tapped song. Faithful to the real Player semantics: the replace-branch of
     * `injectIntoPlayer` (`mediaItemCount > 1`) is therefore NOT exercised after a forcePlay —
     * only the "mid-queue" test (tapped == current, no forcePlay) exercises it.
     */
    private fun modelForcePlayReplacesQueue(queue: MutableList<MediaItem>) {
        every { mockPlayer.setMediaItem(any<MediaItem>(), any<Boolean>()) } answers {
            queue.clear()
            queue.add(firstArg())
            Unit
        }
    }

    /**
     * MockK 1.14.11's suspend-mocking support unwraps any `returns` value that is itself a
     * `kotlin.Result` one level — but ONLY for functions with a NULLABLE `Result<X>?`-typed
     * return (verified empirically: a non-nullable-Result return delivers the `returns` value
     * as-is, so it must stay single-wrapped). `Innertube.nextPage` returns
     * `Result<Innertube.NextPage>?`, so the intended `Result.success(page)` value is wrapped in
     * one extra `Result.success(...)` layer to absorb that single unwrap (same trick as
     * StreamResolverPageCacheTest).
     */
    @Suppress("UNCHECKED_CAST")
    private fun nextPageResult(value: Result<Innertube.NextPage>): Result<Innertube.NextPage>? =
        Result.success(value) as Result<Innertube.NextPage>?

    private fun songItem(videoId: String, playlistId: String? = null) =
        Innertube.SongItem(
            info = Innertube.Info(
                name = "song $videoId",
                endpoint = NavigationEndpoint.Endpoint.Watch(videoId = videoId, playlistId = playlistId)
            ),
            authors = emptyList(),
            album = null,
            durationText = null,
            thumbnail = null
        )

    /**
     * Stubs a SUCCESSFUL radio fetch for [videoId]: the seed call (no playlistId) returns a
     * page whose first item points at [playlistId], then the playlist call returns
     * [radioIds] (shuffled by `Shuffler.shuffle` before injection).
     */
    private fun stubRadioFetch(videoId: String, playlistId: String, radioIds: List<String>) {
        coEvery { Innertube.nextPage(videoId = videoId) } returns nextPageResult(
            Result.success(
                Innertube.NextPage(
                    itemsPage = Innertube.ItemsPage(
                        items = listOf(songItem("seed_$videoId", playlistId)),
                        continuation = null
                    ),
                    playlistId = playlistId
                )
            )
        )
        coEvery { Innertube.nextPage(videoId = videoId, playlistId = playlistId) } returns nextPageResult(
            Result.success(
                Innertube.NextPage(
                    itemsPage = Innertube.ItemsPage(
                        items = radioIds.map { songItem(it) },
                        continuation = null
                    ),
                    playlistId = playlistId
                )
            )
        )
    }

    /** Stubs an EMPTY/failed radio fetch: every `nextPage` for [videoId] comes back null. */
    private fun stubEmptyFetch(videoId: String) {
        coEvery { Innertube.nextPage(videoId = videoId) } returns null
    }

    /**
     * Stubs an empty fetch via the OTHER line: the seed call SUCCEEDS and returns a playlistId,
     * but the playlist call itself comes back null — same pre-filter empty branch reached through
     * `if (!playlistId.isNullOrBlank())` instead of the seed->fallback path.
     */
    private fun stubSeedOkButPlaylistNull(videoId: String, playlistId: String) {
        coEvery { Innertube.nextPage(videoId = videoId) } returns nextPageResult(
            Result.success(
                Innertube.NextPage(
                    itemsPage = Innertube.ItemsPage(
                        items = listOf(songItem("seed_$videoId", playlistId)),
                        continuation = null
                    ),
                    playlistId = playlistId
                )
            )
        )
        coEvery { Innertube.nextPage(videoId = videoId, playlistId = playlistId) } returns null
    }

    // --- MATRIX CASES ---

    // 1: ON, ON, ON -> Radio started (Discoveries only)
    @Test
    fun `case 1 - startRadio with Radio ON, AutoFill ON, Discover ON`() {
        setupState(autoFillEnabled = true, discoverEnabled = true)
        // Run the fetch through the deterministic harness (no real Innertube network job on the
        // IO pool) with a SUCCESSFUL fetch, so no stray empty-fetch error toast fires and the
        // case stays focused on the start toast it asserts.
        setupFetchHarness()
        stubRadioFetch("test_id", "PL_880_matrix", listOf("r1", "r2"))

        nZikRadio.startRadio(createMediaItem(), isExplicit = true)

        verify { Toaster.i(R.string.state_radio_on_discover) }
        assertTrue(nZikRadio.isRadioActive)
    }

    // 2: ON, ON, OFF -> Radio started (Includes known songs)
    @Test
    fun `case 2 - startRadio with Radio ON, AutoFill ON, Discover OFF`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        stubRadioFetch("test_id", "PL_880_matrix", listOf("r1", "r2"))

        nZikRadio.startRadio(createMediaItem(), isExplicit = true)

        verify { Toaster.i(R.string.state_radio_on_classic) }
    }

    // 3: ON, OFF, ON -> Radio started (Discoveries only)
    @Test
    fun `case 3 - startRadio with Radio ON, AutoFill OFF, Discover ON`() {
        setupState(autoFillEnabled = false, discoverEnabled = true)
        setupFetchHarness()
        stubRadioFetch("test_id", "PL_880_matrix", listOf("r1", "r2"))

        nZikRadio.startRadio(createMediaItem(), isExplicit = true)

        verify { Toaster.i(R.string.state_radio_on_discover) }
    }

    // 4: ON, OFF, OFF -> Radio started (Includes known songs)
    @Test
    fun `case 4 - startRadio with Radio ON, AutoFill OFF, Discover OFF`() {
        setupState(autoFillEnabled = false, discoverEnabled = false)
        setupFetchHarness()
        stubRadioFetch("test_id", "PL_880_matrix", listOf("r1", "r2"))

        nZikRadio.startRadio(createMediaItem(), isExplicit = true)

        verify { Toaster.i(R.string.state_radio_on_classic) }
    }

    // --- AUTO-CORRECTION AND ERROR CASES ---

    @Test
    fun `toggleDiscover when Radio OFF and AutoFill OFF shows error`() {
        setupState(autoFillEnabled = false, discoverEnabled = false)

        nZikRadio.toggleDiscover()

        verify { Toaster.e(R.string.state_discover_error) }
        verify(exactly = 0) { editor.putBoolean(discoverKey, any()) } // State shouldn't change
    }

    @Test
    fun `toggleDiscover toggles state and shows correct toast`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)

        nZikRadio.toggleDiscover()

        verify { editor.putBoolean(discoverKey, true) }
        verify { Toaster.i(R.string.state_discover_on) }
    }

    // Reminder matrix: AutoPlay ON + Discover ON -> Reminder: Auto-play is ON
    @Test
    fun `showReminderIfNeeded with AutoPlay ON and Discover ON`() {
        setupState(autoFillEnabled = true, discoverEnabled = true)

        nZikRadio.showReminderIfNeeded()

        verify { Toaster.i(R.string.state_reminder_discover) }
    }

    // Reminder matrix: AutoPlay OFF + Discover ON -> Auto-correction (Option C)
    @Test
    fun `showReminderIfNeeded with AutoPlay OFF and Discover ON triggers AutoCorrection`() {
        setupState(autoFillEnabled = false, discoverEnabled = true)

        nZikRadio.showReminderIfNeeded()

        // Verifies no toast is shown
        verify(exactly = 0) { Toaster.i(any<Int>()) }
        // Verifies Auto-Correction happened (Discover turned OFF)
        verify { editor.putBoolean(discoverKey, false) }
    }

    // --- GUEST LOCK (spec-listen-together-guest-lock-hardening) ---

    @Test
    fun `startRadio while guest locked is a no-op (RADIO_GUEST)`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        listenTogetherGuestLock.value = true

        try {
            nZikRadio.startRadio(createMediaItem(), isExplicit = true)

            assertFalse(nZikRadio.isRadioActive, "no radio state change for a locked guest")
            // No "Radio ON" toast, only the shared blocked toast (String overload).
            verify(exactly = 0) { Toaster.i(any<Int>()) }
            verify { Toaster.i(any<String>()) }
            // The blocked op stays atomic: no forcePlay, no queue injection.
            verify(exactly = 0) { mockPlayer.setMediaItem(any()) }
            verify(exactly = 0) { mockPlayer.addMediaItems(any()) }
        } finally {
            listenTogetherGuestLock.value = false
        }
    }

    @Test
    fun `toggleDiscover while guest locked is a no-op (DISCOVER_GUEST)`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        listenTogetherGuestLock.value = true

        try {
            nZikRadio.toggleDiscover()

            // No preference write, no "Discover ON/OFF" toast — only the shared blocked toast.
            verify(exactly = 0) { editor.putBoolean(discoverKey, any()) }
            verify(exactly = 0) { Toaster.i(any<Int>()) }
            verify { Toaster.i(any<String>()) }
        } finally {
            listenTogetherGuestLock.value = false
        }
    }

    @Test
    fun `autoFillQueue while guest locked skips the fetch and injection (QUIET_GUEST)`() {
        // The silent guard returns before any Innertube fetch / queue injection — in a room the
        // host owns the queue, and the auto-fill would only be no-op'd by the central guard
        // (a toast + a wasted fetch on every host-side track transition).
        setupState(autoFillEnabled = true, discoverEnabled = false)
        every { mockPlayer.currentMediaItem } returns createMediaItem()
        listenTogetherGuestLock.value = true

        try {
            nZikRadio.autoFillQueue()

            verify(exactly = 0) { mockPlayer.addMediaItems(any()) }
            verify(exactly = 0) { mockPlayer.setMediaItem(any()) }
            verify(exactly = 0) { Toaster.i(any<Int>()) }
            verify(exactly = 0) { Toaster.i(any<String>()) }
        } finally {
            listenTogetherGuestLock.value = false
        }
    }

    @Test
    fun `showReminderIfNeeded while guest locked performs no auto-correction (QUIET_GUEST)`() {
        // Discover ON + AutoFill OFF is the auto-correction case (it would flip the host-owned
        // Discover setting off) — a locked guest must get no state change and no toast at all.
        setupState(autoFillEnabled = false, discoverEnabled = true)
        listenTogetherGuestLock.value = true

        try {
            nZikRadio.showReminderIfNeeded()

            verify(exactly = 0) { editor.putBoolean(discoverKey, any()) }
            verify(exactly = 0) { Toaster.i(any<Int>()) }
            verify(exactly = 0) { Toaster.i(any<String>()) }
        } finally {
            listenTogetherGuestLock.value = false
        }
    }

    // --- ISSUE #880: reliable stop on the first tap + queue that really gets replaced ---

    /**
     * STOP_FROM_OTHER_SONG — regression for #880 scene 2: the "Stop radio" entry from ANOTHER
     * song's menu must stop the radio on the very first tap. The old mediaId equality check
     * re-started the radio on the tapped song instead (the first tap "did nothing").
     */
    @Test
    fun `radio ON stops on the first tap from another song menu`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        stubRadioFetch("A", "PL_880_a", listOf("r1", "r2"))

        // Start the radio on the current song (successful fetch -> queue gains the radio songs)
        nZikRadio.startRadio(mediaItem("A"), isExplicit = true)
        assertTrue(nZikRadio.isRadioActive)

        // The "Stop radio" entry of another song's menu: the FIRST tap must stop it
        nZikRadio.startRadio(mediaItem("B"), isExplicit = true)

        assertFalse(nZikRadio.isRadioActive, "the radio must stop on the very first tap")
        verify { Toaster.i(R.string.state_radio_off) }
        // No forcePlay for the tapped song...
        verify(exactly = 0) { mockPlayer.setMediaItem(any<MediaItem>(), any<Boolean>()) }
        // ...and no re-fetch: the initial start's seed + playlist calls exactly once, and NOT a
        // single fetch for the tapped song "B" (a re-fetch would only hit the strict static mock
        // on the IO thread and stay invisible without this assertion)
        coVerify(exactly = 1) { Innertube.nextPage(videoId = "A") }
        coVerify(exactly = 1) { Innertube.nextPage(videoId = "A", playlistId = "PL_880_a") }
        coVerify(exactly = 0) { Innertube.nextPage(videoId = "B") }
        coVerify(exactly = 0) { Innertube.nextPage(videoId = "B", playlistId = any()) }
        // The queue is untouched by the stop
        assertEquals(3, queue.size)
    }

    /**
     * STOP_NO_CURRENT: an explicit stop tap while `currentMediaItem` is null must stop the radio
     * on the first tap (the old check `currentMediaItem?.mediaId == mediaItem.mediaId` could
     * never match a null current item, so the tap re-started the radio instead).
     */
    @Test
    fun `radio ON stops on the first tap when currentMediaItem is null`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf<MediaItem>()
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns null
        every { mockPlayer.currentMediaItemIndex } returns 0
        modelForcePlayReplacesQueue(queue)
        stubRadioFetch("A", "PL_880_b", listOf("r1", "r2"))

        // Start: currentMediaItem is null -> forcePlay(A) replaces the (empty) queue
        nZikRadio.startRadio(mediaItem("A"), isExplicit = true)
        assertTrue(nZikRadio.isRadioActive)
        assertEquals(3, queue.size) // [A, r?, r?]

        // "Stop radio" from another song's menu — first tap stops
        nZikRadio.startRadio(mediaItem("B"), isExplicit = true)

        assertFalse(nZikRadio.isRadioActive, "the radio must stop on the first tap even when currentMediaItem is null")
        verify { Toaster.i(R.string.state_radio_off) }
        // forcePlay happened exactly once (the initial start) — the stop tap did not (re)play
        verify(exactly = 1) { mockPlayer.setMediaItem(any<MediaItem>(), any<Boolean>()) }
        coVerify(exactly = 1) { Innertube.nextPage(videoId = "A") }
        coVerify(exactly = 1) { Innertube.nextPage(videoId = "A", playlistId = "PL_880_b") }
    }

    /**
     * Canonical stop flow (AC "chanson courante ou non"): "Stop radio" from the CURRENT song's
     * own entry point must stop the radio on the first tap.
     */
    @Test
    fun `radio ON stops on the first tap when the tapped song is the current song`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        stubRadioFetch("A", "PL_880_c", listOf("r1", "r2"))

        nZikRadio.startRadio(mediaItem("A"), isExplicit = true)
        assertTrue(nZikRadio.isRadioActive)

        // "Stop radio" from the current song's own entry point — first tap stops
        nZikRadio.startRadio(mediaItem("A"), isExplicit = true)

        assertFalse(nZikRadio.isRadioActive, "tapping the current song while the radio is active must stop it")
        verify { Toaster.i(R.string.state_radio_off) }
        verify(exactly = 0) { mockPlayer.setMediaItem(any<MediaItem>(), any<Boolean>()) }
        coVerify(exactly = 1) { Innertube.nextPage(videoId = "A") }
    }

    /**
     * START_FROM_LIST (issue #880 scene 1): an explicit start on a song in the MIDDLE of a list
     * queue must leave a queue of only [current] + the radio songs — the list songs before AND
     * after the current one are gone (the old `removeMediaItems(curIndex + 1, endIndex)` left
     * the songs BEFORE the current one, so they played before the radio).
     */
    @Test
    fun `start from the middle of a queue keeps only the current song plus the radio songs`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"), mediaItem("B"), mediaItem("C"), mediaItem("D"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("B")
        every { mockPlayer.currentMediaItemIndex } returns 1
        stubRadioFetch("B", "PL_880_d", listOf("r1", "r2"))

        // Explicit start on the CURRENT song (no forcePlay -> the replace branch is exercised)
        nZikRadio.startRadio(mediaItem("B"), isExplicit = true)

        assertTrue(nZikRadio.isRadioActive)
        assertEquals(3, queue.size)
        assertEquals("B", queue[0].mediaId, "the current song must stay at the front")
        assertEquals(setOf("B", "r1", "r2"), queue.map { it.mediaId }.toSet(), "only the current song + the radio songs survive")
    }

    /**
     * FETCH_FAILED (explicit start on a NON-CURRENT song): an empty Innertube fetch must leave
     * the queue intact, keep the radio ON, and show `state_radio_fetch_error`.
     *
     * Fidelity: `forcePlay` = `setMediaItem(item, true)` replaces the WHOLE queue in
     * production, so the "intact" queue here is exactly [tapped song] (count 1) — and the
     * replace-branch of `injectIntoPlayer` cannot run, so `moveMediaItem`/`removeMediaItems`
     * must be verified absent (that branch is only exercised by the "mid-queue" test).
     */
    @Test
    fun `empty fetch on explicit start keeps the queue intact, radio ON and shows the error toast`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"), mediaItem("B"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        modelForcePlayReplacesQueue(queue)
        stubEmptyFetch("Z")

        nZikRadio.startRadio(mediaItem("Z"), isExplicit = true)

        // forcePlay already replaced the queue with [Z]; the empty fetch must not trim or inject
        assertEquals(listOf("Z"), queue.map { it.mediaId }, "the queue must stay exactly [tapped song]")
        assertTrue(nZikRadio.isRadioActive, "the radio stays ON after an empty fetch (a transition-driven retry only happens while the queue has >1 item)")
        assertFalse(nZikRadio.isLoading, "isLoading must be released after an empty fetch, or every future auto-fill is silently disabled")
        verify { Toaster.e(R.string.state_radio_fetch_error) }
        verify(exactly = 0) { mockPlayer.addMediaItems(any<List<MediaItem>>()) }
        verify(exactly = 0) { mockPlayer.removeMediaItems(any<Int>(), any<Int>()) }
        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
    }

    /**
     * EMPTY_AFTER_FILTERS (explicit start on a NON-CURRENT song): a successful fetch whose list
     * is emptied by the dislike filter must get the SAME treatment as an empty fetch — queue
     * intact, nothing injected, radio ON, `state_radio_fetch_error` toast.
     */
    @Test
    fun `list emptied by the dislike filter keeps the queue intact, nothing injected, same trace as an empty fetch`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"), mediaItem("B"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        modelForcePlayReplacesQueue(queue)
        stubRadioFetch("Z", "PL_880_f", listOf("r1", "r2"))

        // The dislike filter removes every fetched song
        val songTable = mockk<SongTable>(relaxed = true)
        every { Database.songTable } returns songTable
        coEvery { songTable.getAllDislikedIds() } returns listOf("r1", "r2")

        nZikRadio.startRadio(mediaItem("Z"), isExplicit = true)

        // Same outcome as FETCH_FAILED: queue intact, nothing injected, radio ON, traced
        assertEquals(listOf("Z"), queue.map { it.mediaId }, "the queue must stay exactly [tapped song]")
        assertTrue(nZikRadio.isRadioActive, "the radio stays ON when the filters remove every song")
        assertFalse(nZikRadio.isLoading, "isLoading must be released when the filters empty the list, or every future auto-fill is silently disabled")
        verify { Toaster.e(R.string.state_radio_fetch_error) }
        verify(exactly = 0) { mockPlayer.addMediaItems(any<List<MediaItem>>()) }
        verify(exactly = 0) { mockPlayer.removeMediaItems(any<Int>(), any<Int>()) }
        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
    }

    /**
     * RETRY (pact "réessaie silencieusement à la transition suivante"): after an explicit start
     * whose fetch came back empty (radio stays ON), a subsequent `autoFillQueue()` — which
     * `loadFromRadio` drives on each track transition while the queue has >1 item — retries the
     * fetch, and a successful retry fills the queue — silently.
     */
    @Test
    fun `after an empty fetch the next autoFillQueue with a successful fetch fills the queue`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        stubEmptyFetch("A")

        nZikRadio.startRadio(mediaItem("A"), isExplicit = true)

        assertTrue(nZikRadio.isRadioActive, "the radio stays ON after an empty fetch")
        assertEquals(listOf("A"), queue.map { it.mediaId }, "nothing is injected after the empty fetch")

        // The next track transition retries the fetch, which now succeeds
        stubRadioFetch("A", "PL_880_g", listOf("r1", "r2"))
        nZikRadio.autoFillQueue()

        assertTrue(nZikRadio.isRadioActive)
        assertEquals(3, queue.size)
        assertEquals(setOf("A", "r1", "r2"), queue.map { it.mediaId }.toSet(), "the retry must fill the queue")
        // Exactly one error toast (the explicit start) — the successful auto-fill stays silent
        verify(exactly = 1) { Toaster.e(R.string.state_radio_fetch_error) }
    }

    /**
     * Implicit empty-fetch silence ("Silent fetch (No toast)"): an `autoFillQueue()` whose fetch
     * comes back empty must NOT show `state_radio_fetch_error` — deleting the `if (isExplicit)`
     * gate in `traceEmptyFetch` makes this test red.
     */
    @Test
    fun `autoFillQueue with an empty fetch is silent (no toast, queue intact, radio ON)`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        stubRadioFetch("A", "PL_880_i", listOf("r1", "r2"))

        // Start the radio (successful fetch -> radio ON, queue filled)
        nZikRadio.startRadio(mediaItem("A"), isExplicit = true)
        assertTrue(nZikRadio.isRadioActive)

        // The next track transition: the fetch comes back empty — the auto-fill must stay silent
        stubEmptyFetch("A")
        nZikRadio.autoFillQueue()

        assertTrue(nZikRadio.isRadioActive, "the radio stays ON after a silent empty auto-fill")
        assertFalse(nZikRadio.isLoading, "isLoading must be released after the empty auto-fill")
        assertEquals(setOf("A", "r1", "r2"), queue.map { it.mediaId }.toSet(), "the queue must stay intact")
        verify(exactly = 0) { Toaster.e(R.string.state_radio_fetch_error) }
    }

    /**
     * RETRY, empty again: when the second `autoFillQueue()` retry comes back empty again, the
     * queue stays intact, the radio stays ON, `isLoading` is released (so the following
     * transition can retry again), and the error toast fired exactly once in total — only for
     * the explicit start, never for the implicit retry.
     */
    @Test
    fun `after an empty fetch an empty autoFillQueue retry keeps the queue intact and stays silent`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        stubEmptyFetch("A")

        nZikRadio.startRadio(mediaItem("A"), isExplicit = true)

        assertTrue(nZikRadio.isRadioActive, "the radio stays ON after the empty start fetch")
        assertEquals(listOf("A"), queue.map { it.mediaId })

        // The next transition retries — and the fetch comes back empty again
        nZikRadio.autoFillQueue()

        assertTrue(nZikRadio.isRadioActive, "the radio stays ON across empty retries")
        assertFalse(nZikRadio.isLoading, "isLoading must be released, or the following transitions stop retrying")
        assertEquals(listOf("A"), queue.map { it.mediaId }, "the queue must stay intact after the empty retry")
        // Exactly one error toast in total (the explicit start) — the implicit retry is silent
        verify(exactly = 1) { Toaster.e(R.string.state_radio_fetch_error) }
    }

    /**
     * FETCH_FAILED via the seed-success / playlist-null line: the seed call succeeds and returns
     * a playlistId, but the playlist fetch comes back null — same pre-filter empty outcome as a
     * fully null seed, reached through `if (!playlistId.isNullOrBlank())`.
     */
    @Test
    fun `seed success with a null playlist fetch keeps the queue intact, radio ON and shows the error toast`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"), mediaItem("B"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        modelForcePlayReplacesQueue(queue)
        stubSeedOkButPlaylistNull("Z", "PL_880_j")

        nZikRadio.startRadio(mediaItem("Z"), isExplicit = true)

        assertEquals(listOf("Z"), queue.map { it.mediaId }, "the queue must stay exactly [tapped song]")
        assertTrue(nZikRadio.isRadioActive, "the radio stays ON after an empty fetch")
        assertFalse(nZikRadio.isLoading)
        verify { Toaster.e(R.string.state_radio_fetch_error) }
        verify(exactly = 0) { mockPlayer.addMediaItems(any<List<MediaItem>>()) }
        verify(exactly = 0) { mockPlayer.removeMediaItems(any<Int>(), any<Int>()) }
        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
        // Both lines were exercised: the seed succeeded, the playlist call came back null
        coVerify(exactly = 1) { Innertube.nextPage(videoId = "Z") }
        coVerify(exactly = 1) { Innertube.nextPage(videoId = "Z", playlistId = "PL_880_j") }
    }

    /**
     * EMPTY_AFTER_FILTERS via the DISCOVER filter (not just the disliked-songs filter): every
     * fetched song is mapped AND liked, so the discover filter removes the whole list — same
     * trace policy as an empty fetch: queue intact, nothing injected, radio ON, error toast
     * (explicit start).
     */
    @Test
    fun `list emptied by the discover filter keeps the queue intact, nothing injected, same trace as an empty fetch`() {
        setupState(autoFillEnabled = true, discoverEnabled = true)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"), mediaItem("B"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        modelForcePlayReplacesQueue(queue)
        stubRadioFetch("Z", "PL_880_k", listOf("r1", "r2"))

        // The discover filter removes every fetched song (mapped AND liked)
        val mapTable = mockk<SongPlaylistMapTable>(relaxed = true)
        every { Database.songPlaylistMapTable } returns mapTable
        every { mapTable.isMappedSync("r1") } returns true
        every { mapTable.isMappedSync("r2") } returns true
        val songTable = mockk<SongTable>(relaxed = true)
        every { Database.songTable } returns songTable
        every { songTable.isLikedDirect("r1") } returns true
        every { songTable.isLikedDirect("r2") } returns true

        nZikRadio.startRadio(mediaItem("Z"), isExplicit = true)

        assertEquals(listOf("Z"), queue.map { it.mediaId }, "the queue must stay exactly [tapped song]")
        assertTrue(nZikRadio.isRadioActive, "the radio stays ON when the discover filter removes every song")
        assertFalse(nZikRadio.isLoading)
        verify { Toaster.e(R.string.state_radio_fetch_error) }
        verify(exactly = 0) { mockPlayer.addMediaItems(any<List<MediaItem>>()) }
        verify(exactly = 0) { mockPlayer.removeMediaItems(any<Int>(), any<Int>()) }
        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
    }

    /**
     * START_ON_OTHER — the most common production start flow: an explicit start on a song that
     * differs from `currentMediaItem` runs `forcePlay` (= `setMediaItem(item, true)`), which
     * replaces the WHOLE queue with [tapped song] in production — so a successful fetch simply
     * appends the radio songs to a 1-item queue, and the replace-branch of `injectIntoPlayer`
     * (`mediaItemCount > 1`) is skipped.
     */
    @Test
    fun `successful explicit start on a non-current song replaces the queue then appends the radio songs`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"), mediaItem("B"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        modelForcePlayReplacesQueue(queue)
        stubRadioFetch("Z", "PL_880_l", listOf("r1", "r2"))

        nZikRadio.startRadio(mediaItem("Z"), isExplicit = true)

        assertTrue(nZikRadio.isRadioActive)
        assertFalse(nZikRadio.isLoading)
        // forcePlay ran exactly once, replacing the queue with [Z]...
        verify(exactly = 1) { mockPlayer.setMediaItem(any<MediaItem>(), any<Boolean>()) }
        // ...and the successful fetch appended the radio songs to that 1-item queue — the
        // replace-branch is skipped at count 1, so no trim of the queue
        assertEquals(3, queue.size)
        assertEquals("Z", queue[0].mediaId, "the tapped song must stay at the front")
        assertEquals(setOf("Z", "r1", "r2"), queue.map { it.mediaId }.toSet(), "final queue is exactly [tapped song, r1, r2]")
        verify(exactly = 0) { mockPlayer.removeMediaItems(any<Int>(), any<Int>()) }
        verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
    }

    /**
     * Implicit start (quick picks, search — `isExplicit = false`) with an empty fetch: the
     * silent contract — no "Radio ON" toast, no `state_radio_fetch_error` toast, the radio is
     * OFF afterwards (the implicit branch stops the radio first), isLoading released, and the
     * queue intact (forcePlay already replaced it with [tapped song]).
     */
    @Test
    fun `implicit start with an empty fetch is fully silent and leaves the radio OFF`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()
        val queue = mutableListOf(mediaItem("A"))
        stubQueue(queue)
        every { mockPlayer.currentMediaItem } returns mediaItem("A")
        every { mockPlayer.currentMediaItemIndex } returns 0
        modelForcePlayReplacesQueue(queue)
        stubEmptyFetch("Z")

        nZikRadio.startRadio(mediaItem("Z"), isExplicit = false)

        assertFalse(nZikRadio.isRadioActive, "an implicit start never activates the radio (it stops it first)")
        assertFalse(nZikRadio.isLoading, "isLoading must be released after an implicit empty fetch")
        assertEquals(listOf("Z"), queue.map { it.mediaId }, "the queue must stay exactly [tapped song]")
        verify(exactly = 0) { Toaster.i(R.string.state_radio_on_classic) }
        verify(exactly = 0) { Toaster.i(R.string.state_radio_on_discover) }
        verify(exactly = 0) { Toaster.e(R.string.state_radio_fetch_error) }
    }

    /**
     * LIVENESS GUARD (pre-filter branch): a stopRadio() cancel that lands while the fetch is in
     * flight must not let the stale job trace, toast, or inject after the stop.
     *
     * The in-flight fetch is gated by a [CompletableDeferred] whose await is wrapped in
     * `withContext(NonCancellable)`: a plain `gate.await()` does NOT work — the job's
     * cancellation is then delivered at the suspending await itself (CancellationException
     * thrown inside `nextPage`), the guard is never reached, and the test would be green even
     * with the guard removed (a fake pin). With NonCancellable, the fetch survives
     * stopRadio()'s cancel, resumes with the pending cancellation, and reaches the isActive
     * check (false) with no suspension point in between — exactly the branch the guard covers.
     *
     * Discriminator: the stale toast itself cannot fire even without the guard — the pending
     * cancellation is absorbed by the `withContext(UI)` hop inside `traceEmptyFetch` before
     * `Toaster.e` runs — so the test additionally records the Timber output: without the guard,
     * the stale job reaches `traceEmptyFetch` and logs the "Radio fetch is empty" trace, which
     * the last assertion catches (acceptance check: removing the pre-filter guard makes this
     * test red).
     */
    @Test
    fun `a fetch canceled by stopRadio is dropped at the liveness guard (no stale trace, toast or injection)`() {
        setupState(autoFillEnabled = true, discoverEnabled = false)
        setupFetchHarness()

        // Record the Timber output of the (stale) fetch job — the observable effect of
        // traceEmptyFetch running without the guard.
        val logs = mutableListOf<String>()
        val recorder = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                logs.add((tag ?: "") + " | " + message)
            }
        }
        Timber.plant(recorder)
        try {
            // Probe: the recorder must actually receive logs — fail loudly if the plumbing does
            // not, instead of passing silently (which would turn this into a fake pin).
            Timber.tag("NZikRadio").e("harness probe %s", "ok")
            assertTrue(logs.any { it.contains("harness probe ok") }, "the Timber recorder must capture the probe log")

            val queue = mutableListOf(mediaItem("A"), mediaItem("B"))
            stubQueue(queue)
            every { mockPlayer.currentMediaItem } returns mediaItem("A")
            every { mockPlayer.currentMediaItemIndex } returns 0

            // Gate the in-flight fetch: the seed call suspends until [gate] completes, and the
            // await is wrapped in NonCancellable so it survives stopRadio()'s cancel.
            // (`coAnswers` — the suspend variant — because MockK's plain `answers` is not a
            // suspend lambda.)
            val gate = CompletableDeferred<Unit>()
            coEvery { Innertube.nextPage(videoId = "A") } coAnswers {
                withContext(NonCancellable) { gate.await() }
                null
            }

            nZikRadio.startRadio(mediaItem("A"), isExplicit = true)
            // The fetch is in flight at the gate. Stopping the radio cancels the fetch job.
            nZikRadio.stopRadio(showToast = true)
            // Resume the stale job — it must be dropped at the liveness guard.
            gate.complete(Unit)

            verify { Toaster.i(R.string.state_radio_off) }
            verify(exactly = 0) { Toaster.e(R.string.state_radio_fetch_error) }
            verify(exactly = 0) { mockPlayer.addMediaItems(any<List<MediaItem>>()) }
            verify(exactly = 0) { mockPlayer.removeMediaItems(any<Int>(), any<Int>()) }
            verify(exactly = 0) { mockPlayer.moveMediaItem(any(), any()) }
            assertFalse(nZikRadio.isRadioActive, "the radio is OFF after the stop")
            assertFalse(nZikRadio.isLoading, "isLoading stays released — the stale job must not touch it")
            assertEquals(listOf("A", "B"), queue.map { it.mediaId }, "the queue must be untouched")
            assertFalse(
                logs.any { it.startsWith("NZikRadio |") && it.contains("Radio fetch is empty") },
                "without the liveness guard the stale canceled job reaches traceEmptyFetch and logs the empty-fetch trace — no such log may exist"
            )
        } finally {
            Timber.uproot(recorder)
        }
    }
}
