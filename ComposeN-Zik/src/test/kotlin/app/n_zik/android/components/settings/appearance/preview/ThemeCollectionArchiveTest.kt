package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeCollectionArchiveTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val library = ThemeStoreLibrary(context.getSharedPreferences("restored-themes", Context.MODE_PRIVATE))
    private val preview = ByteArrayOutputStream().use { buffer ->
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer))
        } finally {
            bitmap.recycle()
        }
        buffer.toByteArray()
    }

    @Test
    fun backupRestoresSelectedLocalAndStoreThemesWithoutOverwritingTheSameId() = runBlocking {
        val first = theme("12345678-1111-2222-3333-123456789012")
        val second = theme("12345678-1111-2222-3333-123456789013")
        val online = theme("12345678-1111-2222-3333-123456789014", imported = false)
        val backup = export(first, second, online)
        val entries = readThemeCollectionIndex(context, backup.inputStream())
        val onlineCopyId = entries.single { it.id !in setOf(first.id, second.id) }.id
        val onlineTheme = requireNotNull(online.theme)
        assertEquals(3, entries.size)

        library.save(first.copy(name = "Edited locally"))
        assertEquals(
            ThemeCollectionResult(imported = 1, skipped = 1),
            restoreThemeCollection(context, backup.inputStream(), library, setOf(first.id, onlineCopyId)),
        )
        assertEquals("Edited locally", library.download(first.id)?.name)
        assertNull(library.download(second.id))
        assertNull(library.download(online.id))
        val restored = requireNotNull(library.download(onlineCopyId))
        assertTrue(restored.imported)
        assertEquals(ThemeStoreOrigin(onlineTheme.themeId, 1), restored.storeOrigin)
        assertEquals(onlineTheme.previews, restored.theme?.previews)
        assertArrayEquals(preview, themeArchivePreviewFile(context, onlineTheme.previews.first()).readBytes())

        assertEquals(0, bindRestoredStoreThemes(library, listOf(onlineTheme.copy(title = "Different"))))
        assertEquals(1, bindRestoredStoreThemes(library, listOf(onlineTheme)))
        assertEquals(online, library.download(online.id))
        assertNull(library.download(onlineCopyId))
    }

    @Test
    fun invalidSelectedFileDoesNotRestoreOtherThemes() = runBlocking {
        val first = theme("12345678-1111-2222-3333-123456789012")
        val second = theme("12345678-1111-2222-3333-123456789013")
        val files = zipFiles(export(first, second))
        val path = files.keys.single { it.contains(requireNotNull(second.theme).themeId) && it.endsWith("appearance.json") }
        files[path] = "corrupt".encodeToByteArray()
        val invalid = ByteArrayOutputStream().also { writeThemeArchive(it, files) }.toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            restoreThemeCollection(context, invalid.inputStream(), library, setOf(first.id, second.id))
        }
        assertTrue(library.downloads().isEmpty())
    }

    @Test
    fun singleArchiveUsesTheSameLocalIdentityThroughEitherImportPath() = runBlocking {
        val saved = theme("12345678-1111-2222-3333-123456789012")
        val theme = requireNotNull(saved.theme)
        val files = mutableMapOf(
            "manifest.json" to Json.encodeToString(theme).encodeToByteArray(),
            "appearance.json" to saved.document.encodeToByteArray(),
            "reviews.json" to """{"schemaVersion":1,"reviews":[]}""".encodeToByteArray(),
        )
        theme.previews.forEach { files[it.path] = preview }
        val backup = ByteArrayOutputStream().also { writeThemeArchive(it, files) }.toByteArray()
        val single = readThemeArchive(context, backup.inputStream())
        val index = readThemeCollectionIndex(context, backup.inputStream())
        assertEquals(single.id, index.single().id)
        assertEquals(
            ThemeCollectionResult(imported = 1, skipped = 0),
            restoreThemeCollection(context, backup.inputStream(), library, setOf(single.id)),
        )
        assertEquals(single, library.download(single.id))
    }

    @Test
    fun rescueBackupKeepsSelectionsScopedToTheirOriginalProfileAndExcludesStock() = runBlocking {
        val first = theme("12345678-1111-2222-3333-123456789012")
        val second = theme("12345678-1111-2222-3333-123456789013")
        val stock = first.copy(theme = requireNotNull(first.theme).copy(themeId = BundledThemeId))
        val other = rescueLibrary("other")
        val backup = ByteArrayOutputStream().also {
            writeRescueThemeArchive(context, it, mapOf("default" to listOf(first, stock), "other" to listOf(second)))
        }.toByteArray()
        val index = readRescueThemeArchive(context, backup.inputStream())

        assertEquals(listOf(first.id), index.getValue("default").map { it.id })
        assertEquals(listOf(second.id), index.getValue("other").map { it.id })
        assertEquals(
            ThemeCollectionResult(imported = 2, skipped = 0),
            restoreRescueThemeArchive(
                context, backup.inputStream(), mapOf("default" to library, "other" to other),
                mapOf("default" to setOf(first.id), "other" to setOf(second.id)),
            ),
        )
        assertEquals(first, library.download(first.id))
        assertEquals(second, other.download(second.id))
        assertNull(library.download(second.id))
        assertNull(other.download(first.id))
    }

    @Test
    fun rescueBackupRejectsCrossProfileSelectionBeforeRestoringAnything() = runBlocking {
        val first = theme("12345678-1111-2222-3333-123456789012")
        val second = theme("12345678-1111-2222-3333-123456789013")
        val other = rescueLibrary("other")
        val backup = ByteArrayOutputStream().also {
            writeRescueThemeArchive(context, it, mapOf("default" to listOf(first), "other" to listOf(second)))
        }.toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            restoreRescueThemeArchive(
                context, backup.inputStream(), mapOf("default" to library, "other" to other),
                mapOf("default" to setOf(first.id), "other" to setOf(first.id)),
            )
        }
        assertTrue(library.downloads().isEmpty())
        assertTrue(other.downloads().isEmpty())
    }

    private fun rescueLibrary(name: String): ThemeStoreLibrary =
        ThemeStoreLibrary(context.getSharedPreferences("restored-themes-$name", Context.MODE_PRIVATE))

    private fun theme(id: String, imported: Boolean = true): SavedTheme {
        val document = defaultThemeAppearanceDocument()
        val theme = StoreTheme(
            1, id, 1, "Same name", "A portable theme", StoreAuthor(12, "alice"), emptyList(),
            StoreCompatibility(1), StoreFile("appearance.json", document.encodeToByteArray().sha256()),
            requiredPreviews.map { slot -> StorePreview("previews/$slot.png", "other", preview.sha256()) },
        )
        cacheThemeArchivePreviews(context, theme) { preview }
        return SavedTheme(theme.title, document, theme, imported)
    }

    private suspend fun export(vararg themes: SavedTheme): ByteArray =
        ByteArrayOutputStream().also {
            writeThemeCollectionArchive(context, it, themes.toList())
        }.toByteArray()

    private fun zipFiles(bytes: ByteArray): MutableMap<String, ByteArray> =
        ZipInputStream(bytes.inputStream()).use { zip ->
            buildMap {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes())
                }
            }.toMutableMap()
        }
}
