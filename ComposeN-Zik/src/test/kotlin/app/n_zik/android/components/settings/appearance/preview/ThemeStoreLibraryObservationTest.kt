package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeStoreLibraryObservationTest {
    @Test
    fun openDetailReceivesDownloadsAndRemovalsWrittenByAnotherLibraryInstance() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("observed-theme-downloads", Context.MODE_PRIVATE)
        val detailLibrary = ThemeStoreLibrary(preferences)
        val workerLibrary = ThemeStoreLibrary(preferences)
        val states = mutableListOf<List<SavedTheme>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            detailLibrary.observeDownloads().collect { states += it }
        }
        assertEquals(emptyList<SavedTheme>(), states.last())

        val saved = SavedTheme("Downloaded by worker", "{}")
        workerLibrary.save(saved)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(saved), states.last())

        workerLibrary.removeDownload(saved.id)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<SavedTheme>(), states.last())
    }

    @Test
    fun downloadObservationRemainsBoundToItsOriginalProfile() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = ThemeStoreLibrary(context.getSharedPreferences("observed-profile-first", Context.MODE_PRIVATE))
        val second = ThemeStoreLibrary(context.getSharedPreferences("observed-profile-second", Context.MODE_PRIVATE))
        val states = mutableListOf<List<SavedTheme>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            first.observeDownloads().collect { states += it }
        }

        second.save(SavedTheme("Other profile", "{\"other\":true}"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(emptyList<SavedTheme>()), states)
    }

    @Test
    fun openRestoredDetailResolvesTheStoreRevisionBoundByAnotherLibraryInstance() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("observed-restored-theme", Context.MODE_PRIVATE)
        val detailLibrary = ThemeStoreLibrary(preferences)
        val workerLibrary = ThemeStoreLibrary(preferences)
        val published = publishedTheme()
        val local = restoredTheme(published)
        detailLibrary.restoreImported(listOf(local))
        val states = mutableListOf<List<SavedTheme>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            detailLibrary.observeDownloads().collect { states += it }
        }
        assertEquals(listOf(local), states.last())
        assertNull(local.restoredStoreDownload(states.last()))

        assertEquals(1, workerLibrary.bindTrustedStoreOrigins(listOf(checkNotNull(published.theme))))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(published), states.last())
        assertEquals(published, local.restoredStoreDownload(states.last()))
        assertNull(detailLibrary.download(local.id))
    }

    @Test
    fun aLocalDetailNeverRedirectsToAnUnverifiedOrDifferentDownload() {
        val published = publishedTheme()
        val local = restoredTheme(published)
        assertNull(local.restoredStoreDownload(listOf(local, published)))
        assertNull(local.restoredStoreDownload(emptyList()))
        assertNull(local.copy(storeOrigin = null).restoredStoreDownload(listOf(published)))
        val theme = checkNotNull(published.theme)
        val mismatches = listOf(
            published.copy(imported = true),
            published.copy(document = published.document + " "),
            published.copy(theme = theme.copy(revision = 2)),
            published.copy(theme = theme.copy(title = "Another theme")),
        )
        mismatches.forEach { assertNull(local.restoredStoreDownload(listOf(it))) }
    }

    private fun publishedTheme(): SavedTheme {
        val document = defaultThemeAppearanceDocument()
        val theme = StoreTheme(
            1, "12345678-1111-2222-3333-123456789014", 1, "Restored theme", "A portable theme",
            StoreAuthor(12, "alice"), emptyList(), StoreCompatibility(1),
            StoreFile("appearance.json", document.encodeToByteArray().sha256()), emptyList(),
            publishedOn = "2026-11-15",
        )
        return SavedTheme(theme.title, document, theme)
    }

    private fun restoredTheme(published: SavedTheme): SavedTheme {
        val theme = checkNotNull(published.theme)
        return published.copy(
            theme = theme.copy(themeId = "12345678-1111-2222-3333-123456789015", publishedOn = null),
            imported = true,
            storeOrigin = ThemeStoreOrigin(theme.themeId, theme.revision),
        )
    }
}
