package app.n_zik.android.legacyoffmain.ui.screens.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source-level wiring test for the burger menu's deep links (spec-customizable-burger-menu,
 * goal B): every long-press deep link emitted by the burger items must target an existing
 * settings tab whose screen carries a focus guard for that link's focus value - so a link
 * can never scroll a screen that ignores its focus. The screens' focus guards are read
 * from their source (a Compose render of all nine tabs is out of scope for a unit test),
 * so the test parses the three files directly; if the deep-link format changes, the
 * extraction below fails loud.
 */
class BurgerMenuDeepLinkMappingTest {

    private fun source(relativePath: String): String {
        // Unit tests run from the ComposeN-Zik project dir; fall back to the repo root
        // when the working dir is the one above.
        for (prefix in listOf("src/androidMain/kotlin/", "../src/androidMain/kotlin/")) {
            val file = File(prefix + relativePath)
            if (file.isFile) return file.readText()
        }
        throw AssertionError("source file not found: $relativePath")
    }

    /** The (tab, focus) pairs the burger long-press handlers emit. */
    private fun deepLinks(): List<Pair<String, String>> {
        val action = source("app/it/fast4x/rimusic/ui/components/navigation/header/ActionBar.kt")
        return Regex(
            "navController\\.navigate\\(\"\\$\\{NavRoutes\\.settings\\.name\\}\\?tab=(\\d+)&focus=([a-z_]+)\"\\)"
        ).findAll(action).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    /** The SettingsScreen tab map: tab number -> screen composable name. */
    private fun tabToScreen(): Map<String, String> {
        val settingsScreen = source("app/it/fast4x/rimusic/ui/screens/settings/SettingsScreen.kt")
        return Regex("(\\d+) -> (\\w+)\\(").findAll(settingsScreen)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun everyDeepLinkTargetsTheTabWhoseFocusGuardMatches() {
        val links = deepLinks()
        assertEquals(
            13,
            links.size,
            "the burger menu carries 13 deep-link items (a new/removed item must be reflected here)"
        )

        val tabToScreen = tabToScreen()
        val focusGuard = Regex("focus\\s*(?:==|!=)\\s*\"([a-z_]+)\"")

        for ((tab, focus) in links) {
            val screen = tabToScreen.getValue(tab)
            val screenFile = source("app/it/fast4x/rimusic/ui/screens/settings/$screen.kt")
            val guardedValues = focusGuard.findAll(screenFile).map { it.groupValues[1] }.toSet()
            assertTrue(
                focus in guardedValues,
                "deep link tab=$tab focus=$focus targets $screen, which has no matching focus guard (guards: $guardedValues)"
            )
        }
    }
}
