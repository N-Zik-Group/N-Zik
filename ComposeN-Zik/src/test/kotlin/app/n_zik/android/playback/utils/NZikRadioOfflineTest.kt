package app.n_zik.android.playback.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.utils.preferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.nextPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Offline audit (2026-10-22, F3/F4 + A5a): an offline radio spends no network attempt —
 * `fetchAndInject` skips the Innertube fetch while the monitor reads offline, so an explicit
 * start gets exactly ONE "No Connection" report (the player gate's offline fast-fail — the
 * radio side adds no second toast, F3) and the auto-fill makes no DNS-failing fetch per track
 * transition (F4); the queue fills when the network is back (auto-fill retry). Plus the A5a
 * predicate: offline => "No Connection" instead of the generic fetch-error string (only
 * reachable on a mid-fetch drop, since the offline fetch itself is skipped up front).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NZikRadioOfflineTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var binder: PlayerServiceModern.Binder
    private lateinit var mockPlayer: ExoPlayer
    private lateinit var nZikRadio: NZikRadio

    @BeforeEach
    fun setup() {
        // The trace/inject paths hop to NzikDispatchers.UI (Dispatchers.Main) — drive them
        // deterministically (same seam as NZikRadioTest).
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        binder = mockk(relaxed = true)
        mockPlayer = mockk<ExoPlayer>(relaxed = true)
        every { binder.player } returns mockPlayer
        every { mockPlayer.currentMediaItem } returns null

        mockkStatic("app.it.fast4x.rimusic.utils.PreferencesKt")
        every { context.preferences } returns sharedPreferences

        mockkObject(Toaster)
        every { Toaster.i(any<Int>()) } just Runs
        every { Toaster.e(any<Int>()) } just Runs
        every { Toaster.noInternet() } just Runs

        // Offline (the subject of this file)
        mockkStatic("app.it.fast4x.rimusic.utils.UtilsKt")
        every { isNetworkConnected(context) } returns false

        // Deterministic fetch: the job runs to completion synchronously on the test thread
        // (same seam as NZikRadioTest.setupFetchHarness).
        nZikRadio = NZikRadio(context, binder, CoroutineScope(Dispatchers.Unconfined))
        nZikRadio.fetchDispatcher = UnconfinedTestDispatcher()
        mockkStatic("it.fast4x.innertube.requests.NextPageKt")
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun mediaItem(id: String) = MediaItem.Builder().setMediaId(id).build()

    // --- A5a predicate ---

    @Test
    fun `an empty radio fetch is a network failure while offline`() {
        assertTrue(isRadioFetchNetworkFailure(context))
    }

    @Test
    fun `an empty radio fetch is not a network failure while online`() {
        every { isNetworkConnected(context) } returns true

        assertFalse(isRadioFetchNetworkFailure(context))
    }

    // --- F4: the offline auto-fill spends no network attempt ---

    @Test
    fun `an offline autoFillQueue skips the innertube fetch entirely (F4)`() {
        every { sharedPreferences.getBoolean(autoLoadSongsInQueueKey, true) } returns true
        every { mockPlayer.currentMediaItem } returns mediaItem("A")

        nZikRadio.autoFillQueue()

        coVerify(exactly = 0) { Innertube.nextPage(videoId = "A") }
        coVerify(exactly = 0) { Innertube.nextPage(videoId = "A", playlistId = any()) }
        assertFalse(nZikRadio.isLoading, "the skipped fetch must leave isLoading released for the next transition")
        verify(exactly = 0) { mockPlayer.addMediaItems(any()) }
        verify(exactly = 0) { Toaster.e(any<Int>()) }
    }

    // --- F3: the offline explicit start adds no radio-side report ---

    @Test
    fun `an offline explicit start does no radio-side fetch or error toast (F3)`() {
        // currentMediaItem is null -> forcePlay(Z) runs, then the fetch must be skipped offline
        nZikRadio.startRadio(mediaItem("Z"), isExplicit = true)

        assertTrue(nZikRadio.isRadioActive, "the radio stays ON (the queue fills when the network is back)")
        assertFalse(nZikRadio.isLoading, "the skipped fetch must leave isLoading released")
        coVerify(exactly = 0) { Innertube.nextPage(videoId = "Z") }
        coVerify(exactly = 0) { Innertube.nextPage(videoId = "Z", playlistId = any()) }
        // The single "No Connection" report for an offline explicit start is the player gate's
        // (offline fast-fail) — the radio side must not add a second toast.
        verify(exactly = 0) { Toaster.e(any<Int>()) }
        verify(exactly = 0) { Toaster.noInternet() }
    }

    // --- A5a: the mid-fetch drop (the only path where the offline branch is reachable) ---

    @Test
    fun `an explicit radio fetch that drops mid-fetch reports no connection (A5a)`() {
        // Online at launch (the F4 guard passes), then the network drops before the fetch returns.
        var networkChecks = 0
        every { isNetworkConnected(context) } answers {
            networkChecks++
            networkChecks == 1
        }
        coEvery { Innertube.nextPage(videoId = "Z") } returns null

        nZikRadio.startRadio(mediaItem("Z"), isExplicit = true)

        verify(exactly = 1) { Toaster.noInternet() }
        verify(exactly = 0) { Toaster.e(any<Int>()) }
    }

    // --- F4 recovery: the skipped fetch runs once the network is back ---

    @Test
    fun `the skipped radio fetch runs once the network is back (F4)`() {
        every { sharedPreferences.getBoolean(autoLoadSongsInQueueKey, true) } returns true
        every { mockPlayer.currentMediaItem } returns mediaItem("A")

        nZikRadio.autoFillQueue()
        coVerify(exactly = 0) { Innertube.nextPage(videoId = "A") }

        // Network back: the auto-fill fetch actually runs (empty page -> silent trace, no toast).
        every { isNetworkConnected(context) } returns true
        coEvery { Innertube.nextPage(videoId = "A") } returns null

        nZikRadio.autoFillQueue()

        coVerify(timeout = 5_000, atLeast = 1) { Innertube.nextPage(videoId = "A") }
        verify(exactly = 0) { Toaster.e(any<Int>()) }
        verify(exactly = 0) { Toaster.noInternet() }
    }
}
