package app.n_zik.android.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppNavBarRouteTest {

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
            assertFalse(appNavBarPresentForRoute(route), "expected no bar for route=$route")
        }
        assertFalse(appNavBarPresentForRoute(null))
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
            assertTrue(appNavBarPresentForRoute(route), "expected bar for route=$route")
        }
    }

    @Test
    fun `artist and search prefixes do not leak into lookalike routes`() {
        assertTrue(appNavBarPresentForRoute("artist/{id}"))
        assertFalse(appNavBarPresentForRoute("artistInsights/{id}"))
        assertFalse(appNavBarPresentForRoute("artistAlbums/{id}?params={params}"))
        assertTrue(appNavBarPresentForRoute("search?text={text}"))
        assertTrue(appNavBarPresentForRoute("searchResults/{query}"))
        assertFalse(appNavBarPresentForRoute("searchFoo/x"))
    }
}
