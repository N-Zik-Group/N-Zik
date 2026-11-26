package app.n_zik.android.components.menu

import app.n_zik.android.enums.NavRoutes
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenEntityMenuActionTest {
    @Test
    fun `opening the current exact album or artist leaves navigation unchanged`() {
        assertTrue(isCurrentMenuEntity(NavRoutes.album, "LOCAL_ALBUM_DEVICE_7", "album/{id}", "LOCAL_ALBUM_DEVICE_7"))
        assertTrue(isCurrentMenuEntity(NavRoutes.artist, "modified:UCExact", "artist/{id}", "modified:UCExact"))
    }

    @Test
    fun `opening another entity from its detail screen keeps the previous screen reachable`() {
        assertFalse(isCurrentMenuEntity(NavRoutes.artist, "UCNext", "artist/{id}", "UCCurrent"))
        assertFalse(isCurrentMenuEntity(NavRoutes.album, "PageId", "playlist/{id}?params={params}", "PageId"))
        assertFalse(isCurrentMenuEntity(NavRoutes.artist, "UCExact", "artistInsights/{id}", "UCExact"))
    }
}
