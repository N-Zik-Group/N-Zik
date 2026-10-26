package app.n_zik.android

import app.it.fast4x.rimusic.enums.NavRoutes
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The route predicate that hides the full-screen sheet (spec-lyrics-copy-loop-and-updater-miniplayer):
 * the Rewind deck (plain or with a query prefix) and the OTA updater screen hide the miniplayer,
 * every other route keeps it.
 */
class MiniplayerHiddenRouteTest {

    @Test
    fun `rewind route hides the miniplayer`() {
        assertTrue(isMiniplayerHiddenRoute(NavRoutes.rewind.name))
    }

    @Test
    fun `rewind route with a query prefix hides the miniplayer`() {
        assertTrue(isMiniplayerHiddenRoute("${NavRoutes.rewind.name}?key=value"))
    }

    @Test
    fun `updater route hides the miniplayer`() {
        assertTrue(isMiniplayerHiddenRoute(NavRoutes.updater.name))
    }

    @Test
    fun `other routes keep the miniplayer`() {
        assertFalse(isMiniplayerHiddenRoute(NavRoutes.home.name))
        assertFalse(isMiniplayerHiddenRoute("settings"))
        // Prefix look-alikes must not match: the updater is argless, the rewind check is
        // exact or a '?'-suffixed query.
        assertFalse(isMiniplayerHiddenRoute("${NavRoutes.updater.name}?key=value"))
        assertFalse(isMiniplayerHiddenRoute("${NavRoutes.rewind.name}X"))
        assertFalse(isMiniplayerHiddenRoute("rewindX?key=value"))
    }

    @Test
    fun `null route keeps the miniplayer`() {
        assertFalse(isMiniplayerHiddenRoute(null))
    }
}
