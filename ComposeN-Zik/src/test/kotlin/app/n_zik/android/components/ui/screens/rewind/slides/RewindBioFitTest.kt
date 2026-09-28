package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-arithmetic tests for [rewindBioMaxLines] — the hard area budget of the
 * artist spotlight's bio clamp (the slide shows [BIO_PREVIEW_LINES] of a bio that
 * overflows its area, capped by this budget, with the "SEE MORE" button below).
 *
 * Deliberately free of any text measurement: Robolectric text metrics are
 * nondeterministic, and the overflow DECISION itself is measurement-based on device
 * (the hidden natural-height copy vs. the area box — see
 * RewindBioDriftMeasurementTest). These tests pin only the math: floor division,
 * the SEE MORE reservation, and the one-line floor.
 */
class RewindBioFitTest {

    @Test
    fun previewIsCappedByTheAreaBudget() {
        // The slide wants 3 preview lines (BIO_PREVIEW_LINES), but the area only
        // holds 2 at the line height: the budget wins, the pill stays on-slide.
        val area = rewindBioMaxLines(areaHeightDp = 25.dp, lineHeightDp = 10.dp, reservedDp = 28.dp)
        assertEquals(1, minOf(BIO_PREVIEW_LINES, area))
    }

    @Test
    fun budgetDividesTheAreaByTheLineHeight() {
        // 100dp area, 10dp lines, nothing reserved: exactly 10 lines fit.
        assertEquals(10, rewindBioMaxLines(areaHeightDp = 100.dp, lineHeightDp = 10.dp, reservedDp = 0.dp))
    }

    @Test
    fun budgetFloorsToTheLastFullLine() {
        // 95dp area, 10dp lines: 9 full lines + 5dp of a tenth — the tenth line must
        // NOT be admitted (a half-visible last line is exactly what the clamp exists
        // to avoid).
        assertEquals(9, rewindBioMaxLines(areaHeightDp = 95.dp, lineHeightDp = 10.dp, reservedDp = 0.dp))
    }

    @Test
    fun seeMoreReservationIsDeductedFromTheBudget() {
        // 100dp area with the pill reserved (28dp): the budget is 72dp = 7 lines at
        // 10dp — reserving the pill first keeps the pill and the last clamped line
        // from colliding.
        assertEquals(7, rewindBioMaxLines(areaHeightDp = 100.dp, lineHeightDp = 10.dp, reservedDp = 28.dp))
    }

    @Test
    fun reservationLargerThanTheAreaStillYieldsOneLine() {
        // A degenerate area (smaller than the pill reservation) must never collapse
        // to zero lines — one clamped line keeps the block readable and the pill
        // reachable.
        assertEquals(1, rewindBioMaxLines(areaHeightDp = 20.dp, lineHeightDp = 10.dp, reservedDp = 28.dp))
    }

    @Test
    fun zeroAreaStillYieldsOneLine() {
        // The first composition frame reports a 0px area (measurements not delivered
        // yet): coerceAtLeast(0.dp) on the budget, then the one-line floor — no
        // maxLines = 0 (which would render nothing) on that frame.
        assertEquals(1, rewindBioMaxLines(areaHeightDp = 0.dp, lineHeightDp = 13.dp, reservedDp = 28.dp))
    }
}
