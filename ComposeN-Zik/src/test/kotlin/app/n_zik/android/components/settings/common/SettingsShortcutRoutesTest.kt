package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsShortcutRoutesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `header shortcuts open their modern category and focus the registered card`() {
        val shortcuts = mapOf(
            (4 to "history") to "privacy",
            (3 to "statistics") to "ai",
            (3 to "rewind") to "rewind",
            (6 to "offline") to "network",
            (5 to "listentogether") to "services",
            (6 to "pcserver") to "network",
            (5 to "profiles") to "profiles",
            (0 to "pip") to "player",
            (1 to "burger") to "interface",
            (7 to "extensions") to "maintenance",
            (7 to "maintenance") to "maintenance",
            (7 to "rescue") to "maintenance",
        )

        for ((shortcut, category) in shortcuts) {
            val (tab, focus) = shortcut
            val card = cardsForCategory(category).single { it.tab == tab && it.focusId == focus }
            val route = Uri.parse(requireNotNull(settingsShortcutRoute(context, tab, focus)))
            assertEquals("settingsCategory/$category", route.path)
            assertEquals(settingsCardTargetId(context, tab, card.titleRes), route.getQueryParameter("target"))
        }
    }

    @Test
    fun `debug shortcut focuses the logging switch within maintenance`() {
        val route = Uri.parse(requireNotNull(settingsShortcutRoute(context, 7, "debug")))

        assertEquals("settingsCategory/maintenance", route.path)
        assertEquals("embedded.maintenance.enable_log_debug", route.getQueryParameter("target"))
        assertEquals("card.7.maintenance", route.getQueryParameter("parent"))
    }

    @Test
    fun `glass aliases keep their destination and other legacy links keep their fallback`() {
        for (alias in listOf("glass", "liquid_glass", "card.1.liquid_glass")) {
            assertEquals(SETTINGS_GLASS_ROUTE, settingsShortcutRoute(context, 1, alias))
        }
        assertNull(settingsShortcutRoute(context, 6, ""))
        assertNull(settingsShortcutRoute(context, 4, "offline"))
        assertNull(settingsShortcutRoute(context, 1, "unknown"))
    }
}
