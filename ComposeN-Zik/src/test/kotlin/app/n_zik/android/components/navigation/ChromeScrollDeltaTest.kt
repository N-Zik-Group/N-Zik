package app.n_zik.android.components.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ChromeScrollDeltaTest {
    @Test fun shortPageDragCannotHideChrome() {
        assertEquals(0f, chromeScrollDelta(consumedY = 0f, availableY = -120f, chromeHidden = false), 0f)
        assertEquals(0f, chromeScrollDelta(consumedY = 0f, availableY = 120f, chromeHidden = false), 0f)
    }

    @Test fun movingListStillMovesChromeWithConsumedDistance() {
        assertEquals(-30f, chromeScrollDelta(consumedY = -30f, availableY = -90f, chromeHidden = false), 0f)
        assertEquals(30f, chromeScrollDelta(consumedY = 30f, availableY = 90f, chromeHidden = true), 0f)
    }

    @Test fun downwardEdgeDragCanRevealHiddenChrome() {
        assertEquals(70f, chromeScrollDelta(consumedY = 0f, availableY = 70f, chromeHidden = true), 0f)
        assertEquals(0f, chromeScrollDelta(consumedY = 0f, availableY = -70f, chromeHidden = true), 0f)
    }
}
