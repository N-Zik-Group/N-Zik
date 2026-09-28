package app.n_zik.android.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppNavBarRouteTest {

    // The home tab count is irrelevant for every non-home route; 5 is the realistic full set.
    private val fullHomeTabCount = 5

    @Test
    fun `routes without a navigation bar report no bar`() {
        val routes = listOf(
            "album/{id}",
            "artistInsights/{id}",
            "albumInsights/{id}",
            "playlist/{id}?params={params}",
            "localPlaylist/{id}",
            "podcast/{id}",
            "mood",
            "moodsPage",
            "newAlbums",
            "history",
            "artistAlbums/{id}?params={params}",
            "artistVideos/{id}?params={params}",
            "artistPlaylists/{id}?params={params}",
            "queue",
            "rewind?year={year}&month={month}&scope={scope}",
            "rewindHome",
            "gamePacman",
            "gameSnake",
            "updater",
            "unknownRoute",
            // bare prefixes and declared-but-unregistered routes pin the else-branch default
            "search",
            "artist",
            "settings",
            "rewind",
            "games",
            "videoOrSongInfo",
        )
        routes.forEach { route ->
            assertFalse(appNavBarPresentForRoute(route, fullHomeTabCount), "expected no bar for route=$route")
        }
        assertFalse(appNavBarPresentForRoute(null, fullHomeTabCount))
    }

    @Test
    fun `routes with a navigation bar report the bar`() {
        val routes = listOf(
            "home",
            "artist/{id}",
            "settings?tab={tab}&focus={focus}",
            "search?text={text}",
            "searchResults/{query}",
            "statistics",
        )
        routes.forEach { route ->
            assertTrue(appNavBarPresentForRoute(route, fullHomeTabCount), "expected bar for route=$route")
        }
    }

    @Test
    fun `artist and search prefixes do not leak into lookalike routes`() {
        assertTrue(appNavBarPresentForRoute("artist/{id}", fullHomeTabCount))
        assertFalse(appNavBarPresentForRoute("artistInsights/{id}", fullHomeTabCount))
        assertFalse(appNavBarPresentForRoute("artistAlbums/{id}?params={params}", fullHomeTabCount))
        assertTrue(appNavBarPresentForRoute("search?text={text}", fullHomeTabCount))
        assertTrue(appNavBarPresentForRoute("searchResults/{query}", fullHomeTabCount))
        assertFalse(appNavBarPresentForRoute("searchFoo/x", fullHomeTabCount))
    }

    @Test
    fun `home only reports the bar with two or more active tabs`() {
        // The bar is only drawn with two or more tab buttons, so a single remaining
        // tab (incl. the all-disabled quickpicks fallback) must not reserve its space.
        assertFalse(appNavBarPresentForRoute("home", 0))
        assertFalse(appNavBarPresentForRoute("home", 1))
        assertTrue(appNavBarPresentForRoute("home", 2))
        assertTrue(appNavBarPresentForRoute("home", 5))
    }
}
