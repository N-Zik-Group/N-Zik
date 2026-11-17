package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.image.PosixAtomicFileShadow
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, shadows = [PosixAtomicFileShadow::class])
class BundledThemePreviewTest {
    @get:Rule val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun stock(): StoreTheme = checkNotNull(bundledDefaultTheme(context).theme)
    private fun index(previews: List<StorePreview>, version: Int = 1): ByteArray =
        """{"schemaVersion":$version,"previews":${Json.encodeToString(previews)}}""".encodeToByteArray()

    @Test
    fun stockThumbnailFollowsAppPaletteAndGlassUsingTheSameMetadata() {
        val theme = stock()
        for (dark in listOf(false, true)) {
            for (glass in listOf(false, true)) {
                val expected = "previews/player-${if (dark) "dark" else "light"}-${if (glass) "glass" else "normal"}.webp"
                assertEquals(expected, theme.playerPreview(glass, dark)?.path)
            }
        }
    }

    @Test
    fun refreshCachesValidatedMetadataAndRetriesWithoutLosingLastValidIndex() = runTest {
        val original = stock().previews
        val updated = original.map { it.copy(sha256 = "b".repeat(64), caption = "Updated scene") }
        var response = index(original)
        var offline = false
        val loader = BundledThemePreviewIndex(File(folder.root, "nested")) { path, limit ->
            assertEquals("demo/stock/index.json", path)
            assertTrue(limit <= 64 * 1024)
            if (offline) throw IOException("Offline")
            response
        }
        assertNull(loader.cached())
        assertEquals(original, loader.refresh())
        val cache = File(folder.root, "nested/stock-previews.json")
        val valid = cache.readBytes()
        val invalid = listOf(
            index(original, version = 2),
            index(original.dropLast(1)),
            index(original.dropLast(1) + original.first()),
            index(original.mapIndexed { i, p -> if (i == 0) p.copy(path = "previews/../../bad.webp") else p }),
            index(original.mapIndexed { i, p -> if (i == 0) p.copy(sha256 = "invalid") else p }),
            index(original.mapIndexed { i, p -> if (i == 0) p.copy(role = "invalid") else p }),
            index(original.mapIndexed { i, p -> if (i == 0) p.copy(caption = "x".repeat(161)) else p }),
            index(original.mapIndexed { i, p -> if (i == 0) p.copy(caption = "") else p }),
            ByteArray(64 * 1024 + 1),
        )
        for (bytes in invalid) {
            response = bytes
            assertTrue(runCatching { loader.refresh() }.isFailure)
            assertTrue(valid.contentEquals(cache.readBytes()))
            assertEquals(original, loader.cached())
        }
        offline = true
        assertTrue(runCatching { loader.refresh() }.exceptionOrNull() is IOException)
        assertEquals(original, loader.cached())
        offline = false
        response = index(updated)
        assertEquals(updated, loader.refresh())
        val restarted = BundledThemePreviewIndex(checkNotNull(cache.parentFile)) { _, _ -> throw IOException("Offline") }
        assertEquals(updated, restarted.cached())
    }

    @Test
    fun stockUsesRemoteImagesAndVerifiedCacheWithoutReusingObsoleteHashes() = runTest {
        val theme = stock()
        val bytes = "verified image".encodeToByteArray()
        val preview = theme.previews.first().copy(sha256 = bytes.sha256())
        val selected = theme.copy(previews = listOf(preview))
        var calls = 0
        var response = bytes
        var expectedHash = preview.sha256
        val catalog = ThemeStoreCatalog(folder.root) { path, limit ->
            calls++
            assertEquals("demo/stock/${preview.path}?sha=$expectedHash", path)
            assertTrue(limit <= 4 * 1024 * 1024)
            response
        }
        val installed = catalog.preview(selected, preview)
        assertNotNull(installed)
        assertTrue(bytes.contentEquals(checkNotNull(installed).readBytes()))
        assertEquals(1, calls)
        val offline = ThemeStoreCatalog(folder.root) { _, _ -> throw IOException("Offline") }
        assertEquals(installed, offline.preview(selected, preview))
        val changedBytes = "replacement image".encodeToByteArray()
        val changed = preview.copy(sha256 = changedBytes.sha256())
        val refreshed = theme.copy(previews = listOf(changed))
        assertNull(offline.preview(refreshed, changed))
        expectedHash = changed.sha256
        assertNull(catalog.preview(refreshed, changed))
        assertFalse(File(folder.root, "${changed.sha256}.webp").exists())
        response = changedBytes
        assertTrue(changedBytes.contentEquals(checkNotNull(catalog.preview(refreshed, changed)).readBytes()))
        assertEquals(3, calls)
        checkNotNull(installed).writeText("corrupt")
        assertNull(offline.preview(selected, preview))
    }

    @Test
    fun oversizedStockImageCacheFailsClosedOfflineAndRepairsOnRetry() = runTest {
        val bytes = "verified replacement".encodeToByteArray()
        val original = stock()
        val preview = original.previews.first().copy(sha256 = bytes.sha256())
        val theme = original.copy(previews = listOf(preview))
        val file = File(folder.root, "${preview.sha256}.webp")
        file.writeBytes(ByteArray(4 * 1024 * 1024 + 1))
        val offline = ThemeStoreCatalog(folder.root) { _, _ -> throw IOException("Offline") }
        assertNull(offline.preview(theme, preview))
        assertEquals(4 * 1024 * 1024 + 1, file.length().toInt())
        val online = ThemeStoreCatalog(folder.root) { path, _ ->
            assertEquals("demo/stock/${preview.path}?sha=${preview.sha256}", path)
            bytes
        }
        assertEquals(file, online.preview(theme, preview))
        assertTrue(bytes.contentEquals(file.readBytes()))
        assertEquals(file, offline.preview(theme, preview))
    }

    @Test
    fun stockDefaultsRemainAvailableWithoutAnyPackagedPreviewImages() = runTest {
        val saved = bundledDefaultTheme(context)
        val theme = checkNotNull(saved.theme)
        assertEquals(BundledThemeId, theme.themeId)
        assertEquals(2, theme.revision)
        assertEquals(64, theme.previews.size)
        assertEquals(defaultThemeAppearanceDocument(), saved.document)
        assertTrue(parseThemeAppearance(saved.document).values.isNotEmpty())
        assertTrue(context.assets.list("theme-store-default/previews").isNullOrEmpty())
        assertFalse(context.assets.list("theme-store-default").isNullOrEmpty())
        val catalog = ThemeStoreCatalog(folder.root) { _, _ -> throw IOException("Offline") }
        assertNull(catalog.preview(theme, theme.previews.first()))
    }
}
