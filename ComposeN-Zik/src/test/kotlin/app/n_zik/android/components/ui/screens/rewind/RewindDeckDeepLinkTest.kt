package app.n_zik.android.components.ui.screens.rewind

import android.content.Intent
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.rewindDeckRoute
import app.n_zik.android.rewindDeckTargetFromIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract of the consumer side of the rewind reminders' content intent
 * ([rewindDeckTargetFromIntent], spec GH-275, re-review: consumer side untested):
 * the extras posted by [RewindReminderWorker] must decode to the finished
 * `(year, month)` pair, and the year extra posted by [RewindYearlyReminderWorker] —
 * without any month extra — must decode to the yearly target `(year, 0)`. Anything
 * else (missing extras, out-of-range values, restored process instance) yields
 * null, so the app starts without a forced deck open.
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
        assertEquals(2026 to 7, rewindDeckTargetFromIntent(intentWith(2026, 7), isRestoredInstance = false))
    }

    @Test
    fun nullIntentYieldsNull() {
        assertNull(rewindDeckTargetFromIntent(null, isRestoredInstance = false))
    }

    @Test
    fun missingExtrasYieldNull() {
        assertNull(rewindDeckTargetFromIntent(Intent(), isRestoredInstance = false))
    }

    @Test
    fun missingYearExtraYieldsNullEvenWithAValidMonth() {
        assertNull(
            rewindDeckTargetFromIntent(
                Intent().apply { putExtra(RewindReminderWorker.EXTRA_DECK_MONTH, 7) },
                isRestoredInstance = false
            )
        )
    }

    @Test
    fun outOfRangeYearOrMonthYieldsNull() {
        assertNull(rewindDeckTargetFromIntent(intentWith(1999, 7), isRestoredInstance = false))
        assertNull(rewindDeckTargetFromIntent(intentWith(2101, 7), isRestoredInstance = false))
        assertNull(rewindDeckTargetFromIntent(intentWith(2026, 13), isRestoredInstance = false))
        assertNull(rewindDeckTargetFromIntent(intentWith(2026, -1), isRestoredInstance = false))
    }

    @Test
    fun validYearWithMonthZeroYieldsTheYearlyTarget() {
        assertEquals(
            2026 to 0,
            rewindDeckTargetFromIntent(intentWith(2026, 0), isRestoredInstance = false)
        )
    }

    @Test
    fun validYearWithoutMonthExtraYieldsTheYearlyTarget() {
        assertEquals(
            2026 to 0,
            rewindDeckTargetFromIntent(yearlyIntentWith(2026), isRestoredInstance = false)
        )
    }

    @Test
    fun outOfRangeYearWithMonthZeroYieldsNull() {
        assertNull(rewindDeckTargetFromIntent(intentWith(1999, 0), isRestoredInstance = false))
        assertNull(rewindDeckTargetFromIntent(intentWith(2101, 0), isRestoredInstance = false))
    }

    @Test
    fun restoredInstanceNeverYieldsATargetEvenWithValidExtras() {
        assertNull(rewindDeckTargetFromIntent(intentWith(2026, 7), isRestoredInstance = true))
        assertNull(rewindDeckTargetFromIntent(yearlyIntentWith(2026), isRestoredInstance = true))
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
}
