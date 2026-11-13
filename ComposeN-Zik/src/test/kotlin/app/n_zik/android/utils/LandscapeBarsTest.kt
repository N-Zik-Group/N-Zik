package app.n_zik.android.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LandscapeBarsTest {

    @AfterEach
    fun resetState() {
        LandscapeBars.hide()
    }

    private fun hides(
        isLandscape: Boolean = true,
        smallestScreenWidthDp: Int = 411,
        route: String? = "home",
        homeTab: String? = "songs"
    ) = hidesBarsInLandscape(isLandscape, smallestScreenWidthDp, route, homeTab)

    @Test
    fun `bars are hidden on the four list tabs of a landscape phone`() {
        listOf("songs", "artists", "albums", "playlists").forEach { tab ->
            assertTrue(hides(homeTab = tab), "tab $tab should hide the bars")
        }
    }

    @Test
    fun `quick picks keeps its bars`() {
        assertFalse(hides(homeTab = "quickpicks"))
    }

    @Test
    fun `unknown or missing tab keeps the bars`() {
        assertFalse(hides(homeTab = null))
        assertFalse(hides(homeTab = "somethingelse"))
    }

    @Test
    fun `portrait keeps the bars`() {
        assertFalse(hides(isLandscape = false))
    }

    @Test
    fun `tablet in landscape keeps the bars`() {
        assertFalse(hides(smallestScreenWidthDp = 600))
        assertFalse(hides(smallestScreenWidthDp = 800))
    }

    @Test
    fun `widest phone still hides the bars`() {
        assertTrue(hides(smallestScreenWidthDp = 599))
    }

    @Test
    fun `other routes keep the bars even if the last home tab was a list`() {
        // currentHomeTab is stale once the user navigates away from Home
        assertFalse(hides(route = "album/abc", homeTab = "albums"))
        assertFalse(hides(route = "settings", homeTab = "songs"))
        assertFalse(hides(route = null))
    }

    @Test
    fun `toggle reveals then puts the bars away again`() {
        assertFalse(LandscapeBars.revealed)

        LandscapeBars.toggle()
        assertTrue(LandscapeBars.revealed)

        LandscapeBars.toggle()
        assertFalse(LandscapeBars.revealed)
    }

    @Test
    fun `hide puts the bars away and is harmless when they are already away`() {
        LandscapeBars.toggle()

        LandscapeBars.hide()
        assertFalse(LandscapeBars.revealed)

        LandscapeBars.hide()
        assertFalse(LandscapeBars.revealed)
    }
}
