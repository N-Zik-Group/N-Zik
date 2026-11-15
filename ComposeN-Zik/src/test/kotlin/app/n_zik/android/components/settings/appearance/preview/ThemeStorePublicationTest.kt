package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ThemeStorePublicationTest {
    @Test
    fun alertsOnlyOnceForNewThemesFromSubscribedCreators() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val library = ThemeStoreLibrary(context.getSharedPreferences("publication-test", Context.MODE_PRIVATE))
        val existing = StoreTheme(
            schemaVersion = 1,
            themeId = "99edc843-6a9b-5753-8e76-cc11fe9b3295",
            revision = 1,
            title = "Existing",
            description = "Existing theme",
            author = StoreAuthor(1, "creator"),
            tags = emptyList(),
            compatibility = StoreCompatibility(null),
            appearance = StoreFile("appearance.json", "a".repeat(64)),
            previews = emptyList(),
        )
        val new = existing.copy(themeId = "8f13dfe9-5dc9-5dc3-b563-aa9f11cf67b7")
        val other = new.copy(themeId = "8d06299d-a4c1-5dc7-bf21-8694af77ae36", author = StoreAuthor(2, "other"))
        library.setPublicationsEnabled(1, true, listOf(existing))
        val catalog = listOf(existing.copy(revision = 2), new, other)
        assertEquals(listOf(new), library.newPublications(catalog))
        assertEquals(emptyList<StoreTheme>(), library.newPublications(catalog))

        library.setPublicationsEnabled(1, false, catalog)
        assertEquals(emptyList<StoreTheme>(), library.newPublications(listOf(other.copy(author = existing.author))))
    }

    @Test
    fun downloadedRevisionsRespectUpdateModeAndNotificationHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("theme-update-test", Context.MODE_PRIVATE)
        val library = ThemeStoreLibrary(prefs)
        val original = StoreTheme(
            schemaVersion = 1,
            themeId = "99edc843-6a9b-5753-8e76-cc11fe9b3295",
            revision = 1,
            title = "Downloaded theme",
            description = "",
            author = StoreAuthor(1, "creator"),
            tags = emptyList(),
            compatibility = StoreCompatibility(null),
            appearance = StoreFile("appearance.json", "a".repeat(64)),
            previews = emptyList(),
        )
        val update = original.copy(revision = 2)
        val catalog = listOf(update)
        library.save(SavedTheme(original.title, "{}", original, imported = true))
        assertEquals(emptyList<StoreTheme>(), library.downloadedThemeUpdates(catalog, ThemeUpdateMode.Notify))

        library.save(SavedTheme(original.title, "{}", original))
        assertEquals(catalog, library.downloadedThemeUpdates(catalog, ThemeUpdateMode.Notify))
        library.markThemeUpdateNotified(update)
        assertEquals(emptyList<StoreTheme>(), library.downloadedThemeUpdates(catalog, ThemeUpdateMode.Notify))

        library.setUpdateMode(original.themeId, ThemeUpdateMode.Off)
        assertEquals(ThemeUpdateMode.Off, ThemeStoreLibrary(prefs).updateMode(original.themeId))
        assertEquals(emptyList<StoreTheme>(), library.downloadedThemeUpdates(catalog, ThemeUpdateMode.AutoDownload))
        assertEquals(emptyList<StoreTheme>(), library.downloadedThemeUpdates(catalog, ThemeUpdateMode.Notify))

        library.setUpdateMode(original.themeId, ThemeUpdateMode.AutoDownload)
        assertEquals(catalog, library.downloadedThemeUpdates(catalog, ThemeUpdateMode.AutoDownload))
        library.save(SavedTheme(update.title, "{}", update))
        assertEquals(emptyList<StoreTheme>(), library.downloadedThemeUpdates(catalog, ThemeUpdateMode.AutoDownload))
    }
}
