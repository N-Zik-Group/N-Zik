package app.n_zik.android

import app.n_zik.android.enums.NavRoutes
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
        assertTrue(isMiniplayerHiddenRoute("${NavRoutes.updater.name}?settingsTarget=options"))
        assertTrue(isMiniplayerHiddenRoute(NavRoutes.notifications.name))
        assertTrue(isMiniplayerHiddenRoute(NavRoutes.themeStore.name))
        assertTrue(isMiniplayerHiddenRoute("${NavRoutes.themeStore.name}?tab=creator"))
    }

    @Test
    fun `other routes keep the miniplayer`() {
        assertFalse(isMiniplayerHiddenRoute(NavRoutes.home.name))
        assertFalse(isMiniplayerHiddenRoute("settings"))
        assertFalse(isMiniplayerHiddenRoute("${NavRoutes.updater.name}X?settingsTarget=options"))
        assertFalse(isMiniplayerHiddenRoute("${NavRoutes.rewind.name}X"))
        assertFalse(isMiniplayerHiddenRoute("rewindX?key=value"))
    }

    @Test
    fun `null route keeps the miniplayer`() {
        assertFalse(isMiniplayerHiddenRoute(null))
    }
}
