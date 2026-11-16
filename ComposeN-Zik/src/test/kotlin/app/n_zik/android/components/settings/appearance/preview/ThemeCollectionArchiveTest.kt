package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.components.glass.glassBlurKey
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.lyricsSizeKey
import app.n_zik.android.utils.nowPlayingIndicatorKey
import app.n_zik.android.core.profiles.profileDataPrefs
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
import java.io.File
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
    fun legacyMinimumIsRaisedOnImportAndExportWithoutLosingTheTrustedStoreOrigin() = runBlocking {
        val current = theme("12345678-1111-2222-3333-123456789019", imported = false)
        val legacyTheme = requireNotNull(current.theme).copy(compatibility = StoreCompatibility(96))
        val legacy = current.copy(theme = legacyTheme)
        val archive = export(legacy)
        val manifests = zipFiles(archive).filterKeys { it.endsWith("manifest.json") }.values
        assertEquals(97, Json.decodeFromString<StoreTheme>(manifests.single().decodeToString()).compatibility.minVersionCode)
        val entry = readThemeCollectionIndex(context, archive.inputStream()).single()
        assertEquals(ThemeCollectionResult(1, 0), restoreThemeCollection(context, archive.inputStream(), library, setOf(entry.id)))
        assertEquals(97, library.download(entry.id)?.theme?.compatibility?.minVersionCode)
        assertEquals(1, bindRestoredStoreThemes(library, listOf(legacyTheme)))
        assertEquals(legacy, library.download(legacy.id))

        val singleFiles = mutableMapOf(
            "manifest.json" to Json.encodeToString(legacyTheme).encodeToByteArray(),
            "appearance.json" to legacy.document.encodeToByteArray(),
            "reviews.json" to """{"schemaVersion":1,"reviews":[]}""".encodeToByteArray(),
        )
        legacyTheme.previews.forEach { singleFiles[it.path] = preview }
        val single = ByteArrayOutputStream().also { writeThemeArchive(it, singleFiles) }.toByteArray()
        assertEquals(97, readThemeArchive(context, single.inputStream()).theme?.compatibility?.minVersionCode)
        val oldManifest = Json.encodeToString(legacyTheme)
        assertEquals(97, parseStoreTheme(oldManifest).compatibility.minVersionCode)
        assertEquals(105, parseStoreTheme(Json.encodeToString(legacyTheme.copy(compatibility = StoreCompatibility(105))))
            .compatibility.minVersionCode)
    }

    @Test
    fun collectionRoundTripsMoreThanSixtyFourThemes() = runBlocking {
        val themes = (1..65).map { number ->
            theme("12345678-1111-2222-3333-" + number.toString().padStart(12, '0'))
        }
        val bytes = export(*themes.toTypedArray())

        val entries = readThemeCollectionIndex(context, bytes.inputStream())
        assertEquals(themes.map { it.id }.toSet(), entries.map { it.id }.toSet())
        val restored = restoreThemeCollection(context, bytes.inputStream(), library, setOf(themes.last().id))
        assertEquals(ThemeCollectionResult(imported = 1, skipped = 0), restored)
    }

    @Test
    fun selectedCollectionRestoresIntoChosenProfileOnly() = runBlocking {
        val target = "theme-restore-test-profile"
        val active = getActiveProfile(context)
        val saved = theme("12345678-1111-2222-3333-123456789015")
        val archive = kotlin.io.path.createTempFile(context.cacheDir.toPath(), suffix = ".zip").toFile()
        try {
            archive.writeBytes(export(saved))
            val restored = restoreSelectedThemeCollection(context, target, ThemeCollectionImport(Uri.fromFile(archive), setOf(saved.id)))
            assertEquals(ThemeCollectionResult(1, 0), restored)
            assertEquals(saved.id, ThemeStoreLibrary(profileDataPrefs(context, target)).download(saved.id)?.id)
            assertNull(ThemeStoreLibrary(profileDataPrefs(context, active)).download(saved.id))
        } finally {
            archive.delete()
        }
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
    fun singleArchiveAcceptsCatalogMetadataSizesThroughBothImportPaths() = runBlocking {
        val saved = theme("12345678-1111-2222-3333-123456789018")
        val symbol = "\uD83C\uDFB5"
        val theme = requireNotNull(saved.theme).copy(title = symbol.repeat(100))
        val files = mutableMapOf(
            "manifest.json" to Json.encodeToString(theme).padEnd(ThemeDocumentLimit + 1).encodeToByteArray(),
            "appearance.json" to saved.document.encodeToByteArray(),
            "reviews.json" to """{"schemaVersion":1,"reviews":[]}""".padEnd(ThemeDocumentLimit + 1).encodeToByteArray(),
        )
        theme.previews.forEach { files[it.path] = preview }
        val archive = ByteArrayOutputStream().also { writeThemeArchive(it, files) }.toByteArray()
        val imported = readThemeArchive(context, archive.inputStream())
        assertEquals(theme.title, imported.name)
        assertEquals(imported.id, readThemeCollectionIndex(context, archive.inputStream()).single().id)
        assertEquals(ThemeCollectionResult(1, 0),
            restoreThemeCollection(context, archive.inputStream(), library, setOf(imported.id)))
        assertEquals(theme.title, library.download(imported.id)?.name)
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
    fun zipRoundTripKeepsExpandedAppearanceAndAppliesIt() = runBlocking {
        val plain = mapOf(
            Preference.SEARCH_RESULT_GRID_STATES.key to "1101010",
            Preference.HOME_LIBRARY_ITEM_SIZE.key to "BIG",
            lyricsSizeKey to 38f,
            nowPlayingIndicatorKey to "CrazyBars",
        )
        val document = encodeThemeV1(plain, mapOf(glassBlurKey to 7f))
        val original = theme("12345678-1111-2222-3333-123456789022")
        val manifest = requireNotNull(original.theme).let { current ->
            current.copy(appearance = current.appearance.copy(sha256 = document.encodeToByteArray().sha256()))
        }
        val saved = original.copy(document = document, theme = manifest)
        val backup = export(saved)
        assertEquals(ThemeCollectionResult(1, 0),
            restoreThemeCollection(context, backup.inputStream(), library, setOf(saved.id)))

        val restored = requireNotNull(library.download(saved.id))
        assertEquals(document, restored.document)
        val settings = parseThemeV1(restored.document).toStoreGroups().values
        val profile = context.getSharedPreferences("zip-customization-test", Context.MODE_PRIVATE)
        val glass = context.getSharedPreferences("zip-customization-glass-test", Context.MODE_PRIVATE)
        settings.forEach { it.applyTo(profile, glass) }
        assertEquals(plain, profile.all.filterKeys { it in plain })
        assertEquals(7f, glass.getFloat(glassBlurKey, -1f))
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

    @Test
    fun rescueAcceptsOrdinaryBackupIntoTheExplicitProfile() = runBlocking {
        val first = theme("12345678-1111-2222-3333-123456789012")
        val second = theme("12345678-1111-2222-3333-123456789013")
        val backup = export(first, second)
        val target = rescueLibrary("work")

        val index = readRescueThemeArchive(context, backup.inputStream(), ordinaryTarget = "work")
        assertEquals(setOf(first.id, second.id), index.getValue("work").map { it.id }.toSet())
        assertThrows(IllegalArgumentException::class.java) {
            readRescueThemeArchive(context, backup.inputStream())
        }
        assertEquals(
            ThemeCollectionResult(imported = 1, skipped = 0),
            restoreRescueThemeArchive(context, backup.inputStream(), mapOf("work" to target), mapOf("work" to setOf(second.id))),
        )
        assertEquals(second, target.download(second.id))
        assertNull(target.download(first.id))
        assertTrue(library.downloads().isEmpty())
    }

    @Test
    fun failedArchiveGenerationDoesNotReplaceAnExistingDestination() {
        val destination = File(context.cacheDir, "existing.zip").apply { writeText("previous backup") }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                writeStagedThemeArchive(context, Uri.fromFile(destination)) { output ->
                    output.write("partial archive".encodeToByteArray())
                    error("missing preview")
                }
            }
        }
        assertEquals("previous backup", destination.readText())
    }

    private fun rescueLibrary(name: String): ThemeStoreLibrary =
        ThemeStoreLibrary(context.getSharedPreferences("restored-themes-$name", Context.MODE_PRIVATE))

    private fun theme(id: String, imported: Boolean = true): SavedTheme {
        val document = defaultThemeAppearanceDocument()
        val theme = StoreTheme(
            1, id, 1, "Same name", "A portable theme", StoreAuthor(12, "alice"), emptyList(),
            StoreCompatibility(97), StoreFile("appearance.json", document.encodeToByteArray().sha256()),
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
