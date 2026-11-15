package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ThemeStoreCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun creatorMetadataUsesTheSchemaUnicodeLimits() = runTest {
        val symbol = "\uD83C\uDFB5"
        var profile = CreatorProfile(1, 1, "Creator", symbol.repeat(60), symbol.repeat(280),
            CreatorMedia("github"), CreatorMedia("generated"))
        val store = ThemeStoreCatalog(folder.root) { path, _ ->
            val bytes = Json.encodeToString(profile).encodeToByteArray()
            if (path == "catalog/creators/index.json") {
                """{"schemaVersion":1,"creators":[{"githubId":1,"profileSha256":"${bytes.sha256()}"}]}""".encodeToByteArray()
            } else bytes
        }
        assertEquals(listOf(profile), store.creators())
        val valid = profile
        profile = valid.copy(displayName = valid.displayName + symbol)
        assertTrue(runCatching { store.creators() }.exceptionOrNull() is IllegalArgumentException)
        profile = valid.copy(bio = valid.bio + symbol)
        assertTrue(runCatching { store.creators() }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun rejectedRefreshDoesNotReplaceValidatedCatalog() = runTest {
        val theme = StoreTheme(
            1, "99edc843-6a9b-5753-8e76-cc11fe9b3295", 1, "Blurred Cover", "Theme",
            StoreAuthor(1, "N-Zik-Group"), listOf("artwork"),
            StoreCompatibility(null), StoreFile("appearance.json", "a".repeat(64)), emptyList(),
        )
        val manifest = Json.encodeToString(theme).encodeToByteArray()
        val index = """{"schemaVersion":1,"themes":[{"themeId":"${theme.themeId}","revision":1,"manifestSha256":"${manifest.sha256()}"}]}"""
        var response = manifest
        var offline = false
        val store = ThemeStoreCatalog(folder.root) { path, _ ->
            if (offline) throw IOException("Offline")
            if (path == "catalog/themes/index.json") index.encodeToByteArray() else response
        }
        assertNull(store.cached())
        val synced = store.refreshState()
        assertEquals(listOf(theme), synced.themes)
        assertTrue((synced.syncedAt ?: 0) > 0)

        response = Json.encodeToString(theme.copy(title = "Unverified")).encodeToByteArray()
        assertTrue(runCatching { store.refresh() }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(synced, store.cachedState())
        offline = true
        assertTrue(runCatching { store.refresh() }.exceptionOrNull() is IOException)
        assertEquals(synced, store.cachedState())
    }

    @Test
    fun refreshReadsTheNewManifestWhenPublicationMetadataChangesInPlace() = runTest {
        var theme = StoreTheme(
            1, "99edc843-6a9b-5753-8e76-cc11fe9b3295", 1, "Blurred Cover", "Theme",
            StoreAuthor(1, "N-Zik-Group"), emptyList(),
            StoreCompatibility(null), StoreFile("appearance.json", "a".repeat(64)), emptyList(),
        )
        val manifestCache = mutableMapOf<String, ByteArray>()
        val store = ThemeStoreCatalog(folder.root) { path, _ ->
            val bytes = Json.encodeToString(theme).encodeToByteArray()
            if (path == "catalog/themes/index.json") {
                """{"schemaVersion":1,"themes":[{"themeId":"${theme.themeId}","revision":1,"manifestSha256":"${bytes.sha256()}"}]}""".encodeToByteArray()
            } else manifestCache.getOrPut(path) { bytes }
        }

        assertEquals(listOf(theme), store.refresh())
        theme = theme.copy(publishedOn = "2026-11-15")
        assertEquals(listOf(theme), store.refresh())
        assertEquals(2, manifestCache.size)
    }

    @Test
    fun newProfileHasNoDownloadsOrFollows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val library = ThemeStoreLibrary(context.getSharedPreferences("emptyStore", Context.MODE_PRIVATE))
        assertTrue(library.downloads().isEmpty())
        assertTrue(library.follows().isEmpty())
    }
}
