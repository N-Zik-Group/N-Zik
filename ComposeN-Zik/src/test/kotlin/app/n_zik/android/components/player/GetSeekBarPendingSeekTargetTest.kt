package app.n_zik.android.components.player

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PauseBetweenSongs
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.GetSeekBar
import app.it.fast4x.rimusic.utils.pauseBetweenSongsKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #881 (gh-881), Phase 3 Fix E (review patch): the composable wiring of the held
 * (optimistic) seek target in [GetSeekBar] + DurationIndicator. The pure release decision is
 * pinned by [PendingSeekScrubTest]; this test pins what the UI does with the state:
 *
 * - a skip-button seek HOLDS its target on the label while the player position is still stale
 *   (the original "snap back to the old position" complaint), and a consecutive tap
 *   accumulates from the HELD position, not the stale player position,
 * - the hold RELEASES once the player position converges on the target (no stuck bar: a
 *   later position change is shown directly, and a fresh tap accumulates from the live
 *   position),
 * - a forward tap with an unset duration issues NO seek (minOf against C.TIME_UNSET is a
 *   garbage target; the `newPosition < 0` guard is pinned at the UI level),
 * - Phase 3.3: the position cache ticks while the sheet is collapsed — the removed
 *   visibility gate would have frozen it there, and the pause-between-songs trigger
 *   (the cache's only consumer in GetSeekBar) would never fire.
 *
 * The held value is observed two ways: the label text (formatAsDuration of the held target —
 * the user-visible part) and the NEXT click's seek target (the skip buttons compute their
 * adjustment from `skipBasePosition`: drag ?: un-converged held target ?: the player's LIVE
 * position, read at tap time — Phase 3.1). The label is rendered by OutlinedText as TWO BasicTexts
 * (main + outline), hence `onAllNodesWithText`.
 *
 * JUnit 4 + [RobolectricTestRunner] (the `createComposeRule()` rule only works with JUnit 4,
 * executed through the project's junit-vintage-engine on the JUnit 5 platform) with the plain
 * [Application] so the app's heavy init (DI, Room, player) is skipped — same pattern as
 * HeaderVersionBadgeTest. The player/binder are mocks; `position()` is test-driven Compose
 * state so convergence is observable. Clicks use `performSemanticsAction(OnClick)` because
 * `performClick()` hit-tests — under Robolectric an off-screen or stale-bounds node is a
 * silent no-op (see the comment in OnboardingProfileScreenWiringTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GetSeekBarPendingSeekTargetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mockPlayer = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    // Compose state (like the real position() source): changes re-compose the bar + label.
    // Label strings are formatAsDuration (DateUtils "00:30" minus ONE leading zero):
    // 30_000 → "0:30", 35_000 → "0:35", 40_000 → "0:40".
    private var fakePosition: Long by mutableStateOf(BASE_POSITION_MS)

    private val media = UiMedia(
        id = "abc12345678",
        title = "Song",
        artist = "Artist",
        duration = MEDIA_DURATION_MS,
        isLocal = false,
    )

    @Before
    fun setUp() {
        fakePosition = BASE_POSITION_MS
        every { binder.player } returns mockPlayer
        // Phase 3.1: the skip buttons read their base LIVE from the player at tap time —
        // the mock player mirrors the test-driven position (an in-sync UI by default).
        every { mockPlayer.currentPosition } answers { fakePosition }
        listenTogetherGuestLock.value = false
    }

    @After
    fun tearDown() {
        listenTogetherGuestLock.value = false
        unmockkAll()
    }

    private fun content(duration: Long = MEDIA_DURATION_MS, sheetProgress: Float = 0.9f) {
        // Manual clock: the release-effect poll (50 ms) is driven by advanceTimeBy. The 10 s
        // safety timeout uses the REAL SystemClock, so it cannot fire inside a unit test —
        // the hold must be observed while it is still valid.
        // Phase 3.3 (gh-881): the sheet progress no longer gates the position poll cache
        // (the gate was removed) — it only drives the hand-over visuals; 0.9f keeps the
        // existing tests on the expanded sheet.
        val sheetState = mockk<PlayerSheetState> { every { progress } returns sheetProgress }
        composeRule.mainClock.autoAdvance = false
        every { mockPlayer.duration } returns duration
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerServiceBinder provides binder,
                LocalPlayerSheetState provides sheetState,
            ) {
                GetSeekBar(
                    position = { fakePosition },
                    duration = { duration },
                    mediaId = media.id,
                    media = media,
                    shouldBePlaying = true,
                    isBuffering = false,
                )
            }
        }
    }

    private fun clickForward() {
        composeRule
            .onNodeWithContentDescription("Forward")
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun clickRewind() {
        composeRule
            .onNodeWithContentDescription("Rewind")
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    // OutlinedText renders the label TWICE (main + outline BasicText), so match on the
    // collection of nodes. (This Compose version's collection has no assertExists.)
    private fun assertLabelPresent(text: String) {
        assertTrue(
            "Label '$text' not found on the timeline",
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun assertLabelGone(text: String) {
        assertFalse(
            "Label '$text' still present — the label is stuck on a stale/held value",
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `a skip-button seek is held on the label while the player position is stale`() {
        content()
        // Baseline: the label shows the player position.
        assertLabelPresent("0:30")

        clickForward()
        verify(exactly = 1) { mockPlayer.seekTo(BASE_POSITION_MS + STEP_MS) }

        // The player is still reporting the old position — after several release-effect poll
        // cycles (50 ms) the label must STILL hold the 0:35 target, not snap back to 0:30.
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        assertLabelPresent("0:35")
        assertLabelGone("0:30")
        // The remaining label holds the SAME target (2:45 = 200 s − 0:35), not the stale
        // position's 2:50 — both labels tick together on the held target.
        assertLabelPresent("2:45")
        assertLabelGone("2:50")

        // A second tap accumulates from the HELD target (0:35 → 0:40), not from the stale
        // player position (which would produce 0:35 again).
        clickForward()
        verify(exactly = 1) { mockPlayer.seekTo(BASE_POSITION_MS + 2 * STEP_MS) }
    }

    @Test
    fun `the held seek target releases once the player position converges`() {
        content()
        clickForward()
        verify(exactly = 1) { mockPlayer.seekTo(BASE_POSITION_MS + STEP_MS) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        assertLabelPresent("0:35")

        // The player commits the seek: the polled position converges on the held target —
        // the release effect clears the hold (500 ms tolerance, one poll cycle).
        fakePosition = BASE_POSITION_MS + STEP_MS
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.waitForIdle()

        // Proves the RELEASE, not a continued hold: a later position change is shown
        // directly. (If the hold were stuck on 35_000 the label would still read "0:35".)
        fakePosition = BASE_POSITION_MS + 2 * STEP_MS
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        assertLabelPresent("0:40")
        assertLabelGone("0:35")

        // And a fresh tap accumulates from the live position, not a ghost target.
        clickForward()
        verify(exactly = 1) { mockPlayer.seekTo(BASE_POSITION_MS + 3 * STEP_MS) }
    }

    @Test
    fun `a forward tap with an unset duration issues no seek while rewind still clamps`() {
        content(duration = C.TIME_UNSET)
        clickForward()
        // minOf(BASE + STEP, C.TIME_UNSET) is a garbage (negative) target → the
        // `newPosition < 0` guard vetoes it at the UI level; nothing is held either.
        verify(exactly = 0) { mockPlayer.seekTo(any()) }

        // Rewind clamps against 0 — a valid target, so its seek goes through.
        clickRewind()
        verify(exactly = 1) { mockPlayer.seekTo(BASE_POSITION_MS - STEP_MS) }
    }

    @Test
    fun `skip taps compute from the live player position when the composed one is frozen`() {
        // gh-881 Phase 3.1 field logs (2026-10-04): after a screen-off track change the UI's
        // polled position froze on the previous track (53 795 ms) and every skip tap re-seeked
        // to that stale value ± 5/30 s. The composed position stays frozen here while the
        // player has moved on to 100 s: the tap must land at 105 s, not 35 s.
        content()
        every { mockPlayer.currentPosition } returns LIVE_POSITION_MS

        clickForward()

        verify(exactly = 1) { mockPlayer.seekTo(LIVE_POSITION_MS + STEP_MS) }
        verify(exactly = 0) { mockPlayer.seekTo(BASE_POSITION_MS + STEP_MS) }
    }

    @Test
    fun `the hold releases from the live player position when the polled cache stays stale`() {
        // gh-881 Phase 3.2 (R2): the release effect used to converge on the gated poll cache
        // (position()); while the sheet content is inactive that cache freezes, so the hold
        // survived until the 10 s safety timeout and chained every skip tap from the stale
        // value (field logs 2026-10-04). Here the polled cache stays frozen at 0:30 while the
        // player's LIVE position converges on the 1:45 target — the hold must release on the
        // live convergence (the player's own state, RiMusic/Kreate model) and the label must
        // fall back to the cache, not keep holding the target.
        content()
        every { mockPlayer.currentPosition } returns LIVE_POSITION_MS

        clickForward()
        verify(exactly = 1) { mockPlayer.seekTo(LIVE_POSITION_MS + STEP_MS) }
        // Pump so the held target reaches the label. The hold cannot release yet: the live
        // position is still 5_000 ms away from the target (outside the 500 ms tolerance).
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        assertLabelPresent("1:45")

        // The player commits the seek (live position reaches the target) while the polled
        // cache (fakePosition) stays frozen on its stale value.
        every { mockPlayer.currentPosition } returns LIVE_POSITION_MS + STEP_MS
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.waitForIdle()

        // Released: the label fell back to the polled cache (0:30). With the old
        // cache-converged release it would still hold "1:45" until the 10 s timeout.
        // Harness note: the 0:30 fallback is an isolation artifact — in production the same
        // seek commit that releases the hold fires onPositionDiscontinuity(SEEK), which R1
        // uses to re-anchor the cache on the target, so the label shows the target, not the
        // stale cache. The assertion proves the RELEASE (the label no longer holds the
        // target), not the production display.
        assertLabelPresent("0:30")
        assertLabelGone("1:45")
    }

    @Test
    fun `elapsed and remaining labels are computed from the same whole second`() {
        // 30.5 s into a 200.441 s track: elapsed "0:30" and remaining 200 - 30 = "2:50". The
        // former `duration - position` (169 941 ms) read "2:49" — 441 ms out of phase.
        fakePosition = 30_500L
        content(duration = 200_441L)

        assertLabelPresent("0:30")
        assertLabelPresent("2:50")
        assertLabelGone("2:49")
    }

    @Test
    fun `the poll cache keeps ticking while the sheet is collapsed so pause between songs still fires`() {
        // Phase 3.3 (gh-881) review (P3): pin the gate removal at a real call site — the
        // collapsed sheet (progress below the hand-over threshold) used to gate
        // DurationIndicator's position cache; re-gating it would freeze the time remaining
        // and the pause-between-songs trigger (the cache's only consumer in GetSeekBar)
        // would never fire. pauseBetweenSongs is a SharedPreferences-backed enum preference
        // — seed it before composition.
        ApplicationProvider.getApplicationContext<Context>()
            .preferences
            .edit { putString(pauseBetweenSongsKey, PauseBetweenSongs.`5`.name) }
        content(duration = MEDIA_DURATION_MS, sheetProgress = 0.1f)

        // Baseline: 30 s into a 200 s track — 2:50 remaining, the trigger is dormant.
        assertLabelPresent("2:50")

        // The player approaches the end: the cache must tick (the poll is unconditional) so
        // the time remaining (200 ms) crosses under 500 ms and the trigger fires.
        fakePosition = MEDIA_DURATION_MS - 200L
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.waitForIdle()

        verify(exactly = 1) { mockPlayer.pause() }
        // pauseBetweenSongs = 5 s: the player resumes after the delay.
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        verify(exactly = 1) { mockPlayer.play() }
    }

    private companion object {
        const val STEP_MS = 5_000L
        const val BASE_POSITION_MS = 30_000L
        const val MEDIA_DURATION_MS = 200_000L
        const val LIVE_POSITION_MS = 100_000L
    }
}
