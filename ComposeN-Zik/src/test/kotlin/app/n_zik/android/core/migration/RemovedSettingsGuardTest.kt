package app.n_zik.android.core.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the removal of the "Advanced" notification type and of the wallpaper feature (issue
 * #606): no code may name their preference keys again, and the deleted symbols must stay gone.
 * [RemovedSettingsMigration] is the single place allowed to mention the keys.
 */
class RemovedSettingsGuardTest {

    private val sourceRoot: File = listOf(
        File("src/androidMain/kotlin"),
        File("ComposeN-Zik/src/androidMain/kotlin"),
    ).firstOrNull(File::isDirectory)
        ?: error("androidMain sources not found from ${File(".").absolutePath}")

    private val removedSymbols = listOf(
        "app.it.fast4x.rimusic.enums.NotificationType",
        "app.it.fast4x.rimusic.enums.WallpaperType",
        "app.it.fast4x.rimusic.enums.UiType",
        "app.it.fast4x.rimusic.enums.MessageType",
    )

    @Test
    fun `removed preference keys are named only by the migration`() {
        val keyLiterals = RemovedSettingsMigration.REMOVED_KEYS.map { "\"$it\"" }

        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "RemovedSettingsMigration.kt" }
            .filter { file ->
                val text = file.readText()
                keyLiterals.any(text::contains)
            }
            .map { it.name }
            .toList()

        assertEquals("Files still naming a removed preference key", emptyList<String>(), offenders)
    }

    @Test
    fun `removed enums and builders no longer exist`() {
        removedSymbols.forEach { name ->
            val exists = runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess
            assertFalse("$name must stay deleted", exists)
        }

        val service = Class.forName(
            "app.n_zik.android.playback.services.PlayerServiceModern",
            false,
            javaClass.classLoader,
        )
        val methodNames = service.declaredMethods.map { it.name }
        listOf("updateCustomNotification", "updateWallpaper").forEach { removed ->
            assertTrue(
                "PlayerServiceModern must not define $removed again",
                methodNames.none { it.startsWith(removed) },
            )
        }
    }
}
