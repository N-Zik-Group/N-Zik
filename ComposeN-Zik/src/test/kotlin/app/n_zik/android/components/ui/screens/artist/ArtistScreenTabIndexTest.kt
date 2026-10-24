package app.n_zik.android.components.ui.screens.artist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Artist screen offline tab index coercion (spec-full-offline-mode): mode on
 * forces the local library tab (index 1); mode off is a passthrough.
 */
class ArtistScreenTabIndexTest {

    @Test
    fun `offline mode forces the library tab regardless of the prior index`() {
        assertEquals(1, artistScreenTabIndex(offlineMode = true, tabIndex = 0))
        assertEquals(1, artistScreenTabIndex(offlineMode = true, tabIndex = 1))
        assertEquals(1, artistScreenTabIndex(offlineMode = true, tabIndex = 2))
    }

    @Test
    fun `online mode passes the tab index through unchanged`() {
        assertEquals(0, artistScreenTabIndex(offlineMode = false, tabIndex = 0))
        assertEquals(1, artistScreenTabIndex(offlineMode = false, tabIndex = 1))
        assertEquals(2, artistScreenTabIndex(offlineMode = false, tabIndex = 2))
    }
}
