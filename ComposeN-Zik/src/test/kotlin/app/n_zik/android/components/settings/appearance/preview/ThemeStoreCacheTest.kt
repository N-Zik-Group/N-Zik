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
    fun v2UsesTrustedPreviewLimitAndRetainsItOffline() = runTest {
        val theme = StoreTheme(
            2, "99edc843-6a9b-5753-8e76-cc11fe9b3295", 3, "Theme", "Theme", StoreAuthor(1, "Creator"),
            emptyList(), StoreCompatibility(99), StoreFile("appearance.json", "a".repeat(64)),
            List(80) { slot -> StorePreview("previews/player-$slot.webp", "other", "b".repeat(64)) },
        )
        val manifest = Json.encodeToString(theme).encodeToByteArray()
        val index = """{"schemaVersion":1,"previewLimit":80,"legacyMaxVersionCode":98,"themes":[
            {"themeId":"${theme.themeId}","revision":3,"manifestSha256":"${manifest.sha256()}"}
        ]}"""
        val bundled = """{"schemaVersion":1,"previewLimit":64,"legacyMaxVersionCode":98,"themes":[]}""".encodeToByteArray()
        var remoteIndex = index
        var offline = true
        val store = ThemeStoreCatalog(folder.root, appVersionCode = 99, bundledPolicy = { bundled }) { path, _ ->
            if (offline) throw IOException("Offline")
            when (path.substringBefore('?')) {
                ThemeCatalogV2Path -> remoteIndex.encodeToByteArray()
                "${theme.directory}manifest.json" -> manifest
                else -> error("Unexpected path $path")
            }
        }

        assertEquals(64, store.submissionPreviewLimit())
        offline = false
        assertEquals(80, store.submissionPreviewLimit())
        assertEquals(listOf(theme), store.refresh())
        remoteIndex = index.replace("\"previewLimit\":80", "\"previewLimit\":79")
        assertTrue(runCatching { store.refresh() }.exceptionOrNull() is IllegalArgumentException)
        remoteIndex = index.replace("\"previewLimit\":80", "\"previewLimit\":0")
        assertTrue(runCatching { store.submissionPreviewLimit() }.exceptionOrNull() is IllegalArgumentException)
        offline = true
        assertEquals(80, store.submissionPreviewLimit())
        assertEquals(listOf(theme), store.cached())
    }

    @Test
    fun missingV2IndexFallsBackToV1Only() = runTest {
        val paths = mutableListOf<String>()
        val store = ThemeStoreCatalog(folder.root, appVersionCode = 99) { path, _ ->
            paths += path
            when (path) {
                ThemeCatalogV2Path -> throw StoreFileNotFound()
                ThemeCatalogV1Path -> """{"schemaVersion":1,"themes":[]}""".encodeToByteArray()
                else -> error("Unexpected path $path")
            }
        }
        assertEquals(emptyList<StoreTheme>(), store.refresh())
        assertEquals(listOf(ThemeCatalogV2Path, ThemeCatalogV1Path), paths)
        assertEquals(emptyList<StoreTheme>(), store.cached())
    }

    @Test
    fun v3MergesCompatibleRevisionsAndFallsBackWhenItsIndexDisappears() = runTest {
        val legacy = StoreTheme(
            1, "99edc843-6a9b-5753-8e76-cc11fe9b3295", 1, "Legacy", "Theme", StoreAuthor(1, "Creator"),
            emptyList(), StoreCompatibility(98), StoreFile("appearance.json", "a".repeat(64)), emptyList(),
        )
        val v2 = legacy.copy(schemaVersion = 2, revision = 2, compatibility = StoreCompatibility(99))
        val future = v2.copy(schemaVersion = 3, revision = 3, compatibility = StoreCompatibility(102))
        val v3 = future.copy(
            themeId = "99999999-6a9b-5753-8e76-cc11fe9b3295",
            revision = 1,
            compatibility = StoreCompatibility(101),
        )
        val manifests = (listOf(legacy, v2, future, v3)).associateWith { Json.encodeToString(it).encodeToByteArray() }
        fun index(themes: List<StoreTheme>, modern: Boolean): ByteArray {
            val entries = themes.joinToString(",") { theme ->
                """{"themeId":"${theme.themeId}","revision":${theme.revision},"manifestSha256":"${manifests.getValue(theme).sha256()}"}"""
            }
            val policy = if (modern) ""","previewLimit":80,"legacyMaxVersionCode":98""" else ""
            return """{"schemaVersion":1,"themes":[$entries]$policy}""".encodeToByteArray()
        }
        var legacyAvailable = true
        var v3Available = true
        val catalog = ThemeStoreCatalog(folder.root, appVersionCode = 101) { path, _ ->
            when (val canonical = path.substringBefore('?')) {
                ThemeCatalogV1Path -> if (legacyAvailable) index(listOf(legacy), modern = false) else throw StoreFileNotFound()
                ThemeCatalogV2Path -> index(listOf(v2), modern = true)
                ThemeCatalogV3Path -> if (v3Available) index(listOf(future, v3), modern = true) else throw StoreFileNotFound()
                else -> manifests.getValue(manifests.keys.single { "${it.directory}manifest.json" == canonical })
            }
        }

        assertEquals(listOf(v2, v3), catalog.refresh())
        assertEquals(listOf(v2, v3), catalog.cached())
        legacyAvailable = false
        assertEquals(listOf(v2, v3), catalog.refresh())
        assertEquals(listOf(v2, v3), catalog.cached())
        v3Available = false
        assertEquals(listOf(v2), catalog.refresh())
        assertEquals(listOf(v2), catalog.cached())
    }

    @Test
    fun newProfileHasNoDownloadsOrFollows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val library = ThemeStoreLibrary(context.getSharedPreferences("emptyStore", Context.MODE_PRIVATE))
        assertTrue(library.downloads().isEmpty())
        assertTrue(library.follows().isEmpty())
    }
}
