package app.n_zik.android.components.ui.screens.rewind

import android.content.Intent
import app.n_zik.android.rewindDeckTargetFromIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract of the consumer side of the monthly reminder's content intent
 * ([rewindDeckTargetFromIntent], spec GH-275, re-review: consumer side untested):
 * the extras posted by [RewindReminderWorker] must decode to the finished
 * `(year, month)` pair — and only then. Missing extras, out-of-range values and
 * restored process instances all yield null, so the app starts without a forced
 * deck open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RewindDeckDeepLinkTest {

    private fun intentWith(year: Int, month: Int): Intent =
        Intent().apply {
            putExtra(RewindReminderWorker.EXTRA_DECK_YEAR, year)
            putExtra(RewindReminderWorker.EXTRA_DECK_MONTH, month)
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
    fun outOfRangeYearOrMonthYieldsNull() {
        assertNull(rewindDeckTargetFromIntent(intentWith(1999, 7), isRestoredInstance = false))
        assertNull(rewindDeckTargetFromIntent(intentWith(2101, 7), isRestoredInstance = false))
        assertNull(rewindDeckTargetFromIntent(intentWith(2026, 0), isRestoredInstance = false))
        assertNull(rewindDeckTargetFromIntent(intentWith(2026, 13), isRestoredInstance = false))
    }

    @Test
    fun restoredInstanceNeverYieldsATargetEvenWithValidExtras() {
        assertNull(rewindDeckTargetFromIntent(intentWith(2026, 7), isRestoredInstance = true))
    }
}
