package app.n_zik.android.components.ui.screens.rewind

import android.content.Intent
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.consumeRewindDeckExtras
import app.n_zik.android.consumeRewindPlaylistExtras
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.rewindDeckRoute
import app.n_zik.android.rewindDeckTargetFromIntent
import app.n_zik.android.rewindDeckTargetMarker
import app.n_zik.android.rewindPlaylistTargetFromIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract of the consumer side of the rewind notifications' content intents
 * (spec GH-275 + follow-up — the consumer side is unit-tested here):
 * - [rewindDeckTargetFromIntent]: the deck extras posted by [RewindReminderWorker] must
 *   decode to the finished `(year, month)` pair, and the year extra posted by
 *   [RewindYearlyReminderWorker] — without any month extra — must decode to the yearly
 *   target `(year, 0)`. Anything else (missing extras, out-of-range values) yields null,
 *   so the app starts without a forced deck open.
 * - [rewindPlaylistTargetFromIntent]: the playlist-ready notifications deep-link to the
 *   generated 'Rewind — <period>' playlist via its database id
 *   ([RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID]).
 *
 * The one-shot contract is layered (spec GH-275 follow-up): [consumeRewindDeckExtras] /
 * [consumeRewindPlaylistExtras] strip the extras off the intent the activity keeps
 * (singleTask relaunch), and the last-consumed marker arguments reject a re-delivered
 * intent carrying an already-consumed target — the task record re-sends the original
 * launch intent (extras intact) when the task is restored after a process death, a path
 * the in-memory strip cannot reach. A fresh notification tap still decodes to its
 * target: the tap is the user's intent (user decision 2026-10-01 — the tap always
 * re-opens the deck).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RewindDeckDeepLinkTest {

    private fun intentWith(year: Int, month: Int): Intent =
        Intent().apply {
            putExtra(RewindReminderWorker.EXTRA_DECK_YEAR, year)
            putExtra(RewindReminderWorker.EXTRA_DECK_MONTH, month)
        }

    private fun yearlyIntentWith(year: Int): Intent =
        Intent().apply {
            putExtra(RewindReminderWorker.EXTRA_DECK_YEAR, year)
        }

    @Test
    fun validFinishedMonthYieldsThePair() {
        assertEquals(2026 to 7, rewindDeckTargetFromIntent(intentWith(2026, 7)))
    }

    @Test
    fun nullIntentYieldsNull() {
        assertNull(rewindDeckTargetFromIntent(null))
    }

    @Test
    fun missingExtrasYieldNull() {
        assertNull(rewindDeckTargetFromIntent(Intent()))
    }

    @Test
    fun missingYearExtraYieldsNullEvenWithAValidMonth() {
        assertNull(
            rewindDeckTargetFromIntent(
                Intent().apply { putExtra(RewindReminderWorker.EXTRA_DECK_MONTH, 7) }
            )
        )
    }

    @Test
    fun outOfRangeYearOrMonthYieldsNull() {
        assertNull(rewindDeckTargetFromIntent(intentWith(1999, 7)))
        assertNull(rewindDeckTargetFromIntent(intentWith(2101, 7)))
        assertNull(rewindDeckTargetFromIntent(intentWith(2026, 13)))
        assertNull(rewindDeckTargetFromIntent(intentWith(2026, -1)))
    }

    @Test
    fun validYearWithMonthZeroYieldsTheYearlyTarget() {
        assertEquals(
            2026 to 0,
            rewindDeckTargetFromIntent(intentWith(2026, 0))
        )
    }

    @Test
    fun validYearWithoutMonthExtraYieldsTheYearlyTarget() {
        assertEquals(
            2026 to 0,
            rewindDeckTargetFromIntent(yearlyIntentWith(2026))
        )
    }

    @Test
    fun outOfRangeYearWithMonthZeroYieldsNull() {
        assertNull(rewindDeckTargetFromIntent(intentWith(1999, 0)))
        assertNull(rewindDeckTargetFromIntent(intentWith(2101, 0)))
    }

    @Test
    fun tapOnRestoredInstanceStillYieldsTheTarget() {
        // The tap is the user's intent: a notification tap landing on a restored process
        // instance (task restored after process death) must still open the deck. The
        // one-shot contract is enforced by consumeRewindDeckExtras (tests below), not by
        // suppressing the parse on restore — the old restore gate silently dropped the
        // tap whenever the process was dead, which is the common case for a monthly
        // reminder (user decision 2026-10-01: the tap always re-opens the deck).
        assertEquals(2026 to 7, rewindDeckTargetFromIntent(intentWith(2026, 7)))
        assertEquals(2026 to 0, rewindDeckTargetFromIntent(yearlyIntentWith(2026)))
    }

    // ---- Route building (rewindDeckRoute, spec GH-275) ----

    @Test
    fun yearlyTargetYieldsTheYearOnlyRouteWithoutAMonthArgument() {
        assertEquals("${NavRoutes.rewind.name}?year=2026", rewindDeckRoute(2026, 0))
    }

    @Test
    fun monthlyTargetYieldsTheRouteWithTheMonthArgument() {
        assertEquals("${NavRoutes.rewind.name}?year=2026&month=7", rewindDeckRoute(2026, 7))
    }

    // ---- One-shot consumption (consumeRewindDeckExtras) ----
    //
    // singleTask re-delivers the activity's current intent on every task relaunch, so the
    // deck extras must be consumed off the kept intent: a reparse after consumption must
    // yield null, or the deck re-opens on every app re-foreground.

    @Test
    fun monthlyIntentReparsesNullAfterConsumption() {
        val intent = intentWith(2026, 9)
        // Pin the before state: the intent decodes BEFORE consumption, so the null after
        // is the consumption's doing — not a parser that was already returning null.
        assertEquals(2026 to 9, rewindDeckTargetFromIntent(intent))
        consumeRewindDeckExtras(intent)
        assertNull(rewindDeckTargetFromIntent(intent))
    }

    @Test
    fun yearlyIntentReparsesNullAfterConsumption() {
        val intent = yearlyIntentWith(2026)
        assertEquals(2026 to 0, rewindDeckTargetFromIntent(intent))
        consumeRewindDeckExtras(intent)
        assertNull(rewindDeckTargetFromIntent(intent))
    }

    @Test
    fun consumptionIsIdempotent() {
        val intent = intentWith(2026, 9)
        assertEquals(2026 to 9, rewindDeckTargetFromIntent(intent))
        consumeRewindDeckExtras(intent)
        consumeRewindDeckExtras(intent)
        assertNull(rewindDeckTargetFromIntent(intent))
    }

    @Test
    fun consumingANullIntentDoesNotThrow() {
        consumeRewindDeckExtras(null)
    }

    @Test
    fun consumptionRemovesOnlyTheDeckExtras() {
        val intent = intentWith(2026, 9).apply { putExtra(Intent.EXTRA_TEXT, "artist - song") }
        consumeRewindDeckExtras(intent)
        assertFalse(intent.hasExtra(RewindReminderWorker.EXTRA_DECK_YEAR))
        assertFalse(intent.hasExtra(RewindReminderWorker.EXTRA_DECK_MONTH))
        assertEquals("artist - song", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun parserRejectedIntentStillHasItsExtrasConsumed() {
        // The strip is unconditional (not gated on a successful parse): an out-of-range year
        // must not leave stale extras in the retained intent either, or a later onNewIntent
        // re-delivery would keep them alive past the parse guard.
        val intent = intentWith(1999, 7)
        assertNull(rewindDeckTargetFromIntent(intent))
        consumeRewindDeckExtras(intent)
        assertFalse(intent.hasExtra(RewindReminderWorker.EXTRA_DECK_YEAR))
        assertFalse(intent.hasExtra(RewindReminderWorker.EXTRA_DECK_MONTH))
        assertNull(rewindDeckTargetFromIntent(intent))
    }

    // ---- Persistent last-consumed marker (spec GH-275 follow-up) ----
    //
    // The in-memory strip above cannot reach the task record, which re-sends the original
    // launch intent (extras intact) when the task is restored after a process death. The
    // marker encodes the last accepted target so a re-delivered intent carrying an
    // already-consumed target decodes to null — while a fresh cycle's target still opens.

    @Test
    fun deckTargetMarkerEncodesYearAndMonth() {
        assertEquals(202609, rewindDeckTargetMarker(2026, 9))
        // The yearly sentinel (month 0) is kept as is — it stays distinct from every
        // monthly encoding of the same year (202600 vs 202601..202612).
        assertEquals(202600, rewindDeckTargetMarker(2026, 0))
    }

    @Test
    fun alreadyConsumedMonthlyTargetReparsesNull() {
        val intent = intentWith(2026, 9)
        // Pin the before state: the intent decodes WITHOUT the marker, so the null after
        // is the marker's doing — not a parser that was already returning null.
        assertEquals(2026 to 9, rewindDeckTargetFromIntent(intent))
        assertNull(
            rewindDeckTargetFromIntent(intent, lastConsumedMarker = rewindDeckTargetMarker(2026, 9))
        )
    }

    @Test
    fun alreadyConsumedYearlyTargetReparsesNull() {
        val intent = yearlyIntentWith(2026)
        assertEquals(2026 to 0, rewindDeckTargetFromIntent(intent))
        assertNull(
            rewindDeckTargetFromIntent(intent, lastConsumedMarker = rewindDeckTargetMarker(2026, 0))
        )
    }

    @Test
    fun aSubsequentCycleTargetIsAcceptedDespiteThePreviousCycleMarker() {
        // Last month's task still carries last month's launch intent (its re-send must be
        // rejected); this month's notification must open despite that marker.
        val marker = rewindDeckTargetMarker(2026, 8)
        assertNull(rewindDeckTargetFromIntent(intentWith(2026, 8), lastConsumedMarker = marker))
        assertEquals(2026 to 9, rewindDeckTargetFromIntent(intentWith(2026, 9), lastConsumedMarker = marker))
    }

    @Test
    fun aYearlyTargetIsNotRejectedByAMonthlyMarkerAndViceVersa() {
        // January 1st posts both reminders: each must still open against the other's marker.
        val monthlyMarker = rewindDeckTargetMarker(2026, 12)
        val yearlyMarker = rewindDeckTargetMarker(2026, 0)
        assertEquals(
            2026 to 0,
            rewindDeckTargetFromIntent(yearlyIntentWith(2026), lastConsumedMarker = monthlyMarker)
        )
        assertEquals(
            2026 to 12,
            rewindDeckTargetFromIntent(intentWith(2026, 12), lastConsumedMarker = yearlyMarker)
        )
    }

    // ---- Playlist deep link (spec GH-275 follow-up) ----
    //
    // The playlist-ready notifications deep-link to the generated 'Rewind — <period>'
    // playlist: the content intent carries the playlist's database id, and the same
    // last-consumed guard applies as to the deck target (playlist ids are unique and
    // never reused, so the marker never needs resetting).

    private fun playlistIntentWith(playlistId: Long): Intent =
        Intent().apply { putExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID, playlistId) }

    @Test
    fun playlistDeepLinkParsesThePlaylistId() {
        assertEquals(42L, rewindPlaylistTargetFromIntent(playlistIntentWith(42L)))
    }

    @Test
    fun nullIntentYieldsNoPlaylistTarget() {
        assertNull(rewindPlaylistTargetFromIntent(null))
    }

    @Test
    fun missingPlaylistExtraYieldsNoPlaylistTarget() {
        // A notification posted before the deep link shipped carries no extra: the tap
        // still opens the app, just without a forced playlist open.
        assertNull(rewindPlaylistTargetFromIntent(Intent()))
    }

    @Test
    fun nonPositivePlaylistIdsYieldNoPlaylistTarget() {
        assertNull(rewindPlaylistTargetFromIntent(playlistIntentWith(0L)))
        assertNull(rewindPlaylistTargetFromIntent(playlistIntentWith(-1L)))
    }

    @Test
    fun alreadyOpenedPlaylistReparsesNull() {
        val intent = playlistIntentWith(42L)
        assertEquals(42L, rewindPlaylistTargetFromIntent(intent))
        assertNull(rewindPlaylistTargetFromIntent(intent, lastConsumedId = 42L))
    }

    @Test
    fun playlistExtrasAreConsumedAndReparsesNull() {
        val intent = playlistIntentWith(42L).apply { putExtra(Intent.EXTRA_TEXT, "artist - song") }
        assertEquals(42L, rewindPlaylistTargetFromIntent(intent))
        consumeRewindPlaylistExtras(intent)
        assertNull(rewindPlaylistTargetFromIntent(intent))
        assertEquals("artist - song", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun consumingANullPlaylistIntentDoesNotThrow() {
        consumeRewindPlaylistExtras(null)
    }

    @Test
    fun deckAndPlaylistConsumptionAreIndependent() {
        // Each strip removes only its own extras: consuming the deck extras must not drop
        // the playlist deep link, and vice versa.
        val intent = intentWith(2026, 9).apply { putExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID, 42L) }
        consumeRewindDeckExtras(intent)
        assertTrue(intent.hasExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID))
        consumeRewindPlaylistExtras(intent)
        assertFalse(intent.hasExtra(RewindReminderWorker.EXTRA_DECK_YEAR))
        assertFalse(intent.hasExtra(RewindReminderWorker.EXTRA_DECK_MONTH))
        assertFalse(intent.hasExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID))
    }
}
