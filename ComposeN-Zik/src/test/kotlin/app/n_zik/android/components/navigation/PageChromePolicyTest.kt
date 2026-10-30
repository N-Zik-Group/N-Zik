package app.n_zik.android.components.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageChromePolicyTest {
    @Test fun migratedPagesOwnInsetsAndScrollWithOptionalArguments() {
        for (route in listOf("home", "settings", "bridgeServer", "listenTogether", "profiles",
            "artistInsights/{id}", "albumInsights/album?source=home",
            "album/{id}", "artist/{id}", "playlist/{id}", "localPlaylist/{id}",
            "podcast/{id}", "mood/{id}", "moodsPage", "newAlbums", "artistAlbums/{id}",
            "artistPlaylists/{id}", "artistVideos/{id}", "search", "searchResults/{query}",
            "history", "statistics", "rewindHome", "updater")) {
            assertTrue(route, pageOwnsChromeInsets(route))
            assertTrue(route, pageSupportsChromeScroll(route))
        }
    }

    @Test fun fullScreenAndUnmigratedPagesKeepTheirOwnViewport() {
        for (route in listOf(null, "queue", "rewind?year=2026", "easterOne")) {
            assertFalse(route, pageOwnsChromeInsets(route))
        }
        for (route in listOf(null, "queue", "rewind", "easterOne")) {
            assertFalse(route, pageSupportsChromeScroll(route))
        }
    }
}
