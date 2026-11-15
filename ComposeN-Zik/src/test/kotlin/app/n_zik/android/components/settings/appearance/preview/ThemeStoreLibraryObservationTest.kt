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
}
