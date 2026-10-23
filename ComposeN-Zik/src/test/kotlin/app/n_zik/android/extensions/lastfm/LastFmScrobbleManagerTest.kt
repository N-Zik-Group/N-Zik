package app.n_zik.android.extensions.lastfm

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.utils.albumTitleOrDb
import app.n_zik.android.utils.artistTextOrDb
import app.n_zik.android.utils.titleOrDb
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
import it.fast4x.lastfm.LastFm
import it.fast4x.lastfm.models.LastFmApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Tests the Metrolist-style scrobble semantics of [LastFmScrobbleManager]: Now Playing on start, scrobble after min(threshold % of duration, max delay) with the track-start timestamp, pause/resume preserving the remaining delay, cancellation on transition, local/short/blank metadata skips, session-expired (error code 9) handling for both Now Playing and scrobble, and the configurable options (NP/scrobble switches, custom thresholds and [LastFmScrobbleManager.onConfigChanged] re-arming). */
@OptIn(ExperimentalCoroutinesApi::class)
class LastFmScrobbleManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private var virtualNow: Long = 0L

    private fun onlineItem(id: String = "remoteVideoId1") = MediaItem.Builder().setMediaId(id).build()
    private fun localItem(id: String = "${LOCAL_KEY_PREFIX}123") = MediaItem.Builder().setMediaId(id).build()

    /** Runs the body inside a virtual-time test scope where both the manager's scope and the Main dispatcher share the same test scheduler. */
    private fun withManager(body: suspend TestScope.(manager: LastFmScrobbleManager) -> Unit) {
        runBlocking {
            runTest {
                val dispatcher = UnconfinedTestDispatcher(testScheduler)
                Dispatchers.setMain(dispatcher)
                val scope = CoroutineScope(dispatcher + SupervisorJob())
                virtualNow = 1_000_000L
                try {
                    val manager = LastFmScrobbleManager(context, scope, clock = { virtualNow })
                    body(manager)
                } finally {
                    scope.cancel()
                    Dispatchers.resetMain()
                }
            }
        }
    }

    @BeforeEach
    fun setup() {
        context = mockk()
        prefs = mockk()
        prefsEditor = mockk(relaxed = true)
        every { prefs.edit() } returns prefsEditor
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { context.encryptedPreferences } returns prefs

        // Config defaults = previous hard-coded behavior
        every { prefs.getBoolean(isLastfmNowPlayingEnabledKey, true) } returns true
        every { prefs.getBoolean(isLastfmScrobbleEnabledKey, true) } returns true
        every { prefs.getInt(lastfmMinTrackDurationSecondsKey, 30) } returns 30
        every { prefs.getInt(lastfmScrobbleThresholdPercentKey, 50) } returns 50
        every { prefs.getInt(lastfmMaxScrobbleDelaySecondsKey, 50) } returns 50

        mockkStatic("app.n_zik.android.utils.MediaItemUtilsKt")
        every { any<MediaItem>().artistTextOrDb() } returns "Artist"
        every { any<MediaItem>().titleOrDb() } returns "Title"
        every { any<MediaItem>().albumTitleOrDb() } returns "Album"

        mockkObject(LastFm)
        every { LastFm.initialize(any(), any()) } just Runs
        coEvery { LastFm.updateNowPlaying(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { LastFm.scrobble(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        mockkObject(Toaster)
        every { Toaster.e(any<Int>()) } just Runs
        every { Toaster.e(any<String>()) } just Runs
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Nested
    inner class DelayComputation {

        @Test
        fun `delay is 50 percent of the duration below the cap`() {
            assertEquals(30_000L, LastFmScrobbleManager.computeScrobbleDelayMs(60_000L))
            assertEquals(15_500L, LastFmScrobbleManager.computeScrobbleDelayMs(31_000L))
        }

        @Test
        fun `delay is capped at 50 seconds`() {
            assertEquals(50_000L, LastFmScrobbleManager.computeScrobbleDelayMs(200_000L))
            assertEquals(50_000L, LastFmScrobbleManager.computeScrobbleDelayMs(100_000L))
        }

        @Test
        fun `delay is zero for non-positive durations`() {
            assertEquals(0L, LastFmScrobbleManager.computeScrobbleDelayMs(-1L))
            assertEquals(0L, LastFmScrobbleManager.computeScrobbleDelayMs(0L))
        }

        @Test
        fun `delay honors a custom threshold and cap`() {
            val config = LastFmScrobbleConfig(scrobbleThresholdPercent = 90, maxScrobbleDelayMs = 120_000L)
            assertEquals(120_000L, LastFmScrobbleManager.computeScrobbleDelayMs(240_000L, config))
            val capped = LastFmScrobbleConfig(scrobbleThresholdPercent = 30, maxScrobbleDelayMs = 10_000L)
            assertEquals(10_000L, LastFmScrobbleManager.computeScrobbleDelayMs(60_000L, capped))
        }
    }

    @Nested
    inner class TrackStart {

        @Test
        fun `now playing is sent and scrobble fires after the delay`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                coVerify(exactly = 1) { LastFm.updateNowPlaying("Artist", "Title", "Album", 60L) }

                advanceTimeBy(30_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1000L, "Album", 60L) }
            }
        }

        @Test
        fun `track at or below 30 seconds is never scrobbled`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 30_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 30_000L)

                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `local tracks are never scrobbled`() {
            withManager { manager ->
                manager.onTrackTransition(localItem(), 300_000L)

                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `null media item stops tracking`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)
                manager.onTrackTransition(null, 0L)

                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `start is deferred when duration is unknown at transition`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), -1L)
                advanceTimeBy(60_000L)
                runCurrent()
                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }

                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 60_000L)

                coVerify(exactly = 1) { LastFm.updateNowPlaying("Artist", "Title", "Album", 60L) }
                advanceTimeBy(30_000L)
                runCurrent()
                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1000L, "Album", 60L) }
            }
        }

        @Test
        fun `blank metadata is never sent`() {
            withManager { manager ->
                val blankItem = onlineItem(id = "blankMeta")
                every { blankItem.titleOrDb() } returns ""

                manager.onTrackTransition(blankItem, 60_000L)

                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }
    }

    @Nested
    inner class ScrobbleOptions {

        @Test
        fun `now playing off sends no NP but still scrobbles`() {
            every { prefs.getBoolean(isLastfmNowPlayingEnabledKey, true) } returns false
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(30_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1000L, "Album", 60L) }
            }
        }

        @Test
        fun `scrobble off sends NP but never scrobbles`() {
            every { prefs.getBoolean(isLastfmScrobbleEnabledKey, true) } returns false
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.updateNowPlaying("Artist", "Title", "Album", 60L) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `both options off emit no LastFm requests`() {
            every { prefs.getBoolean(isLastfmNowPlayingEnabledKey, true) } returns false
            every { prefs.getBoolean(isLastfmScrobbleEnabledKey, true) } returns false
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `custom threshold and cap scrobble a 4 minute track at the cap`() {
            every { prefs.getInt(lastfmScrobbleThresholdPercentKey, 50) } returns 90
            every { prefs.getInt(lastfmMaxScrobbleDelaySecondsKey, 50) } returns 120
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 240_000L)

                advanceTimeBy(119_000L)
                runCurrent()
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }

                advanceTimeBy(1_000L)
                runCurrent()
                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1000L, "Album", 240L) }
            }
        }

        @Test
        fun `track below the configured minimum is never sent`() {
            every { prefs.getInt(lastfmMinTrackDurationSecondsKey, 30) } returns 60
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 40_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 40_000L)

                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `config change recalculates the pending delay from now without duplicating NP`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(10_000L)
                virtualNow += 10_000L
                every { prefs.getInt(lastfmScrobbleThresholdPercentKey, 50) } returns 90
                manager.onConfigChanged()

                // New delay: min(90% of 60s, 50s cap) = 50s from the change
                advanceTimeBy(49_000L)
                runCurrent()
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }

                advanceTimeBy(1_000L)
                runCurrent()
                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1000L, "Album", 60L) }
                coVerify(exactly = 1) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
            }
        }

        @Test
        fun `config change above the track duration cancels the timer`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 40_000L)

                advanceTimeBy(5_000L)
                virtualNow += 5_000L
                every { prefs.getInt(lastfmMinTrackDurationSecondsKey, 30) } returns 60
                manager.onConfigChanged()

                advanceTimeBy(120_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `disabling scrobble mid-track cancels the pending scrobble`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(10_000L)
                virtualNow += 10_000L
                every { prefs.getBoolean(isLastfmScrobbleEnabledKey, true) } returns false
                manager.onConfigChanged()

                advanceTimeBy(120_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `config change never re-sends now playing`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(5_000L)
                virtualNow += 5_000L
                every { prefs.getBoolean(isLastfmNowPlayingEnabledKey, true) } returns false
                manager.onConfigChanged()

                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 1) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `config change while paused recomputes the delay and the scrobble fires once`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)
                advanceTimeBy(10_000L)
                virtualNow += 10_000L
                manager.onPlayingStateChanged(isPlaying = false, onlineItem(), 60_000L)

                virtualNow += 10_000L
                every { prefs.getInt(lastfmScrobbleThresholdPercentKey, 50) } returns 90
                manager.onConfigChanged()

                virtualNow += 10_000L
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 60_000L)
                // New delay: min(90% of 60s, 50s cap) = 50s from the resume
                advanceTimeBy(49_000L)
                runCurrent()
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }

                advanceTimeBy(1_000L)
                runCurrent()
                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1000L, "Album", 60L) }
                coVerify(exactly = 1) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
            }
        }

        @Test
        fun `re-enabling scrobble mid-track does not scrobble the current track`() {
            every { prefs.getBoolean(isLastfmScrobbleEnabledKey, true) } returns false
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(5_000L)
                virtualNow += 5_000L
                every { prefs.getBoolean(isLastfmScrobbleEnabledKey, true) } returns true
                manager.onConfigChanged()

                advanceTimeBy(120_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `lowering the min duration makes an idle track eligible when it starts playing`() {
            every { prefs.getInt(lastfmMinTrackDurationSecondsKey, 30) } returns 30
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 20_000L)
                advanceTimeBy(1_000L)
                virtualNow += 1_000L
                runCurrent()
                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }

                every { prefs.getInt(lastfmMinTrackDurationSecondsKey, 30) } returns 10
                manager.onConfigChanged()
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 20_000L)

                coVerify(exactly = 1) { LastFm.updateNowPlaying("Artist", "Title", "Album", 20L) }
                advanceTimeBy(10_000L)
                runCurrent()
                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1001L, "Album", 20L) }
            }
        }

        @Test
        fun `lowering the min duration mid-play starts the currently playing track`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 20_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 20_000L)
                advanceTimeBy(1_000L)
                virtualNow += 1_000L
                runCurrent()
                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }

                every { prefs.getInt(lastfmMinTrackDurationSecondsKey, 30) } returns 10
                manager.onConfigChanged()

                coVerify(exactly = 1) { LastFm.updateNowPlaying("Artist", "Title", "Album", 20L) }
                advanceTimeBy(10_000L)
                runCurrent()
                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1001L, "Album", 20L) }
            }
        }

        @Test
        fun `lowering the min duration mid-play with scrobble off sends now playing only`() {
            every { prefs.getBoolean(isLastfmScrobbleEnabledKey, true) } returns false
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 20_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 20_000L)
                advanceTimeBy(1_000L)
                virtualNow += 1_000L
                runCurrent()
                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }

                every { prefs.getInt(lastfmMinTrackDurationSecondsKey, 30) } returns 10
                manager.onConfigChanged()

                coVerify(exactly = 1) { LastFm.updateNowPlaying("Artist", "Title", "Album", 20L) }
                advanceTimeBy(30_000L)
                runCurrent()
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }
    }

    @Nested
    inner class PauseResume {

        @Test
        fun `pause freezes the remaining delay and resume completes the scrobble`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)
                advanceTimeBy(10_000L)
                virtualNow += 10_000L
                manager.onPlayingStateChanged(isPlaying = false, onlineItem(), 60_000L)

                advanceTimeBy(30_000L)
                runCurrent()
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }

                virtualNow += 10_000L
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 60_000L)
                advanceTimeBy(20_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1000L, "Album", 60L) }
                coVerify(exactly = 1) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
            }
        }

        @Test
        fun `resume after full delay does not scrobble again`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)
                advanceTimeBy(30_000L)
                runCurrent()
                coVerify(exactly = 1) { LastFm.scrobble(any(), any(), any(), any(), any()) }

                manager.onPlayingStateChanged(isPlaying = false, onlineItem(), 60_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 60_000L)
                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `resuming with no remaining time does not scrobble`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)
                virtualNow += 30_000L
                manager.onPlayingStateChanged(isPlaying = false, onlineItem(), 60_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 60_000L)

                advanceTimeBy(60_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `resume with unknown duration keeps the known one so the scrobble still fires`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)
                advanceTimeBy(10_000L)
                virtualNow += 10_000L
                manager.onPlayingStateChanged(isPlaying = false, onlineItem(), 60_000L)

                // Stream still loading after a recovery seek: the resume callback
                // carries C.TIME_UNSET (-1); the tracked duration must be kept.
                virtualNow += 10_000L
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), -1L)
                advanceTimeBy(20_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.scrobble("Artist", "Title", 1000L, "Album", 60L) }
            }
        }
    }

    @Nested
    inner class TransitionAndIdempotence {

        @Test
        fun `new track cancels the previous pending scrobble`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(id = "trackA"), 60_000L)
                virtualNow += 5_000L
                manager.onTrackTransition(onlineItem(id = "trackB"), 60_000L)

                advanceTimeBy(120_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), 1000L, any(), any()) }
                coVerify(exactly = 1) { LastFm.scrobble(any(), any(), 1005L, any(), any()) }
            }
        }

        @Test
        fun `repeated playing callbacks are idempotent`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 60_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 60_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(), 60_000L)

                advanceTimeBy(30_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 1) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `playback state of another item is ignored`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(id = "trackA"), 60_000L)
                manager.onPlayingStateChanged(isPlaying = true, onlineItem(id = "trackB"), 300_000L)

                advanceTimeBy(120_000L)
                runCurrent()

                coVerify(exactly = 1) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }
    }

    @Nested
    inner class SessionExpired {

        @Test
        fun `expired session code 9 from scrobble clears stored keys`() {
            coEvery { LastFm.scrobble(any(), any(), any(), any(), any()) } returns
                    Result.failure(LastFmApiException(9, "Session invalid"))
            LastFm.sessionKey = "session-abc"
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(30_000L)
                runCurrent()

                assertNull(LastFm.sessionKey)
                verify { prefsEditor.remove(lastfmSessionKey) }
                verify { prefsEditor.remove(lastfmUsernameKey) }
                verify { Toaster.e(R.string.lastfm_session_expired) }
            }
        }

        @Test
        fun `expired session code 9 from now playing also clears stored keys`() {
            coEvery { LastFm.updateNowPlaying(any(), any(), any(), any()) } returns
                    Result.failure(LastFmApiException(9, "Session invalid"))
            LastFm.sessionKey = "session-abc"
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(30_000L)
                runCurrent()

                assertNull(LastFm.sessionKey)
                verify { prefsEditor.remove(lastfmSessionKey) }
                verify { prefsEditor.remove(lastfmUsernameKey) }
                verify(exactly = 1) { Toaster.e(R.string.lastfm_session_expired) }
            }
        }

        @Test
        fun `other api errors do not clear the session`() {
            coEvery { LastFm.scrobble(any(), any(), any(), any(), any()) } returns
                    Result.failure(LastFmApiException(6, "Invalid API key"))
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(30_000L)
                runCurrent()

                verify(exactly = 0) { prefsEditor.remove(lastfmSessionKey) }
                verify(exactly = 0) { prefsEditor.remove(lastfmUsernameKey) }
                verify(exactly = 0) { Toaster.e(any<Int>()) }
            }
        }
    }

    @Nested
    inner class NetworkResilience {

        @Test
        fun `offline scrobble is abandoned without clearing the session or crashing`() {
            coEvery { LastFm.scrobble(any(), any(), any(), any(), any()) } returns
                    Result.failure(Exception("UnknownHostException: ws.audioscrobbler.com"))
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(30_000L)
                runCurrent()

                // Matrix row "offline": scrobble abandoned, no queue, no crash,
                // and the (still valid) session must not be wiped
                verify(exactly = 0) { prefsEditor.remove(lastfmSessionKey) }
                verify(exactly = 0) { prefsEditor.remove(lastfmUsernameKey) }
                verify(exactly = 0) { Toaster.e(any<Int>()) }
            }
        }
    }

    @Nested
    inner class Destroy {

        @Test
        fun `destroy cancels the pending scrobble`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)
                manager.destroy()

                advanceTimeBy(120_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `manager is inert after destroy`() {
            withManager { manager ->
                manager.destroy()
                manager.onTrackTransition(onlineItem(), 60_000L)

                advanceTimeBy(120_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.updateNowPlaying(any(), any(), any(), any()) }
                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }

        @Test
        fun `config change after destroy is a no-op`() {
            withManager { manager ->
                manager.onTrackTransition(onlineItem(), 60_000L)
                manager.destroy()
                manager.onConfigChanged()

                advanceTimeBy(120_000L)
                runCurrent()

                coVerify(exactly = 0) { LastFm.scrobble(any(), any(), any(), any(), any()) }
            }
        }
    }
}
