package app.n_zik.android.legacyoffmain.ui.screens.settings

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies emitted burger links against the actual Settings host and receiving focus guards.
 */
class BurgerMenuDeepLinkMappingTest {

    private fun source(relativePath: String): String {
        val candidates = listOf("src/androidMain/kotlin/", "ComposeN-Zik/src/androidMain/kotlin/")
            .map { File(it + relativePath) }
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: throw AssertionError("source file not found; tried: ${candidates.map { it.absolutePath }}")
    }

    /** The (tab, focus) pairs the burger long-press handlers emit. */
    private fun deepLinks(): List<Pair<String, String>> {
        val action = source("app/it/fast4x/rimusic/ui/components/navigation/header/ActionBar.kt")
        return Regex(
            "navController\\.navigate\\(\"\\$\\{NavRoutes\\.settings\\.name\\}\\?tab=(\\d+)&focus=([a-z_]+)\"\\)"
        ).findAll(action).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    private val settingsScreen by lazy {
        source("app/n_zik/android/components/ui/screens/settings/SettingsScreen.kt")
    }

    /** Keeps the actual numeric host dispatch and arguments available for validation. */
    private fun tabBranches(): Map<String, MatchResult> {
        val branches = Regex("""(?m)^\s*(\d+) -> (\w+)\(([^\r\n]*)\)\s*$""")
            .findAll(settingsScreen).toList()
        assertEquals(9, branches.size, "the Settings host must dispatch all nine legacy tabs")
        assertEquals(9, branches.map { it.groupValues[1] }.distinct().size, "duplicate Settings tab branch")
        return branches.associateBy { it.groupValues[1] }
    }

    private fun screenPath(screen: String): String {
        val imports = Regex("""(?m)^import ([\w.]+\.${Regex.escape(screen)})\s*$""")
            .findAll(settingsScreen).toList()
        assertEquals(1, imports.size, "expected one unambiguous Settings host import for $screen")
        return imports.single().groupValues[1].replace('.', '/') + ".kt"
    }

    private fun guardedValues(screenFile: String, path: String): Set<String> {
        val operands = Regex("""\bfocus\s*(?:==|!=)\s*("[^"]*"|[A-Za-z_]\w*(?:\.\w+)*)""")
            .findAll(screenFile).map { it.groupValues[1] }
        return operands.map { operand ->
            when {
                operand.startsWith('"') -> operand.removeSurrounding("\"")
                operand == "DataSettingTargets.HISTORY_FOCUS" -> {
                    val targets = source(path.substringBeforeLast('/') + "/DataSettingTargets.kt")
                    val declarations = Regex("""\bconst\s+val\s+HISTORY_FOCUS\s*=\s*"([a-z_]+)"""")
                        .findAll(targets).toList()
                    assertEquals(1, declarations.size, "expected one literal HISTORY_FOCUS declaration")
                    declarations.single().groupValues[1]
                }
                else -> throw AssertionError("unsupported focus guard operand $operand in $path")
            }
        }.toSet()
    }

    @Test
    fun everyDeepLinkTargetsTheTabWhoseFocusGuardMatches() {
        val links = deepLinks()
        assertEquals(
            13,
            links.size,
            "the burger menu carries 13 deep-link items (a new/removed item must be reflected here)"
        )

        val expectedLinks = listOf(
            "4" to "history", "3" to "statistics", "3" to "rewind", "6" to "offline",
            "5" to "listentogether", "6" to "pcserver", "5" to "profiles", "0" to "pip",
            "1" to "burger", "7" to "extensions", "7" to "debug", "7" to "maintenance", "7" to "rescue",
        )
        assertEquals(expectedLinks.groupingBy { it }.eachCount(), links.groupingBy { it }.eachCount())

        val branches = tabBranches()

        for ((tab, focus) in links) {
            val branch = branches.getValue(tab)
            val screen = branch.groupValues[2]
            assertTrue(
                Regex("""\bfocus\s*=\s*tabFocus\b""").containsMatchIn(branch.groupValues[3]),
                "tab=$tab must forward the deep-link focus to $screen",
            )
            val path = screenPath(screen)
            val guardedValues = guardedValues(source(path), path)
            assertTrue(
                focus in guardedValues,
                "deep link tab=$tab focus=$focus targets $screen, which has no matching focus guard (guards: $guardedValues)"
            )
        }
    }
}
