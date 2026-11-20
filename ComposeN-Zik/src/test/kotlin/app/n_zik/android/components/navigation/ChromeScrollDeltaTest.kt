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

    @Test fun measuredShortPageCanScrollWithoutMovingTheHeader() {
        val travel = HeaderScrollTravel()
        val room = hasHeaderScrollRoom(contentSize = 1000, viewportSize = 970, collapseDistance = 64f)
        assertEquals(false, room)
        assertEquals(0f, travel.delta(-30f, -120f, 0f, 64f, room), 0f)
    }

    @Test fun measuredLongPageMovesImmediatelyAndPaddingCountsTowardScrollRoom() {
        val travel = HeaderScrollTravel()
        val room = hasHeaderScrollRoom(contentSize = 1080, viewportSize = 970, collapseDistance = 64f)
        assertEquals(true, room)
        assertEquals(-30f, travel.delta(-30f, -120f, 0f, 64f, room), 0f)
    }

    @Test fun unknownShortRouteDoesNotFoldFromPaddingOrOverscroll() {
        val travel = HeaderScrollTravel()
        assertEquals(0f, travel.delta(-40f, 0f, 0f, 64f, null), 0f)
        assertEquals(0f, travel.delta(0f, -100f, 0f, 64f, null), 0f)
        assertEquals(40f, travel.delta(40f, 0f, 0f, 64f, null), 0f)
        assertEquals(0f, travel.delta(-40f, 0f, 0f, 64f, null), 0f)
        assertEquals(-6f, travel.delta(-30f, 0f, 0f, 64f, null), 0f)
        travel.reset()
        assertEquals(0f, travel.delta(-30f, 0f, 0f, 64f, null), 0f)
    }
}
