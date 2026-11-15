package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.setActiveProfile
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ThemeStoreAlertWorkerTest {
    @get:Rule val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val preferences get() = profileDataPrefs(context, DEFAULT_PROFILE_ID)
    private val library get() = ThemeStoreLibrary(preferences)
    private val document = """{"schemaVersion":1,"categories":{"colors":{"palette":"dynamic","mode":"dark"}}}"""
    private val update = StoreTheme(
        1, "99edc843-6a9b-5753-8e76-cc11fe9b3295", 2, "Theme", "Theme update",
        StoreAuthor(1, "creator"), emptyList(), StoreCompatibility(BuildConfig.VERSION_CODE),
        StoreFile("appearance.json", document.encodeToByteArray().sha256()), emptyList(),
    )

    @Before
    fun setup() {
        setActiveProfile(DEFAULT_PROFILE_ID, context)
        preferences.edit().clear().commit()
        context.getSharedPreferences("github_account", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(checkNotNull(context.getSystemService(NotificationManager::class.java))).setNotificationsEnabled(false)
        library.save(SavedTheme(update.title, document, update.copy(revision = 1)))
        library.setUpdateMode(update.themeId, ThemeUpdateMode.AutoDownload)
    }

    @Test
    fun scheduledCheckDownloadsWithoutNotificationPermissionOrCreatorAccount() = runTest {
        val result = worker(catalog()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(document, library.download("${update.themeId}:2")?.document)
        assertTrue(preferences.getLong("themeStore.alerts.lastCheck", 0L) > 0L)
        assertTrue(checkNotNull(context.getSystemService(NotificationManager::class.java)).activeNotifications.isEmpty())
    }

    @Test
    fun failedDownloadRetriesWithoutRecordingASuccessfulCheck() = runTest {
        val result = worker(catalog { "corrupt".encodeToByteArray() }).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertNull(library.download("${update.themeId}:2"))
        assertFalse(preferences.contains("themeStore.alerts.lastCheck"))
    }

    @Test
    fun disablingAutomaticUpdatesDuringDownloadPreventsSaving() = runTest {
        val result = worker(catalog {
            library.setUpdateMode(update.themeId, ThemeUpdateMode.Off)
            document.encodeToByteArray()
        }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(library.download("${update.themeId}:2"))
    }

    @Test
    fun profileChangeDuringDownloadDoesNotSaveToThePreviousProfile() = runTest {
        val result = worker(catalog {
            setActiveProfile("other", context)
            document.encodeToByteArray()
        }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(library.download("${update.themeId}:2"))
        assertFalse(preferences.contains("themeStore.alerts.lastCheck"))
    }

    @Test
    fun concurrentScreenAndWorkerDownloadsFetchEachUpdateOnce() = runTest {
        var reads = 0
        val catalog = catalog {
            reads++
            document.encodeToByteArray()
        }
        val results = List(2) {
            async { catalog.downloadAutomaticUpdates(context, library, listOf(update)) { true } }
        }.awaitAll()

        assertEquals(listOf(true, true), results)
        assertEquals(1, reads)
        assertEquals(document, library.download("${update.themeId}:2")?.document)
    }

    @Test
    fun restoredStoreDownloadResumesAutomaticUpdatesWithoutOpeningTheStore() = runTest {
        val restored = restoreOriginal()

        val result = worker(catalog()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(library.download(restored.id))
        assertEquals(false, library.download("${update.themeId}:1")?.imported)
        assertEquals(document, library.download("${update.themeId}:2")?.document)
    }

    @Test
    fun restoredLatestRevisionIsBoundWithoutDownloadingItAgain() = runTest {
        val restored = restoreOriginal(update)

        val result = worker(catalog { error("The restored appearance is already available") }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(library.download(restored.id))
        assertEquals(false, library.download("${update.themeId}:2")?.imported)
        assertEquals(document, library.download("${update.themeId}:2")?.document)
    }

    @Test
    fun restoredStoreDownloadResumesUpdateNotifications() = runTest {
        val restored = restoreOriginal()
        library.setUpdateMode(update.themeId, ThemeUpdateMode.Notify)
        val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        shadowOf(manager).setNotificationsEnabled(true)

        val result = worker(catalog()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(library.download(restored.id))
        assertEquals(false, library.download("${update.themeId}:1")?.imported)
        assertNull(library.download("${update.themeId}:2"))
        assertEquals(2, preferences.getInt("themeStore.updateSeen.${update.themeId}", 0))
        assertTrue(manager.activeNotifications.any { it.tag == "theme-store-update:${update.themeId}" })
    }

    @Test
    fun modifiedRestoredThemeCannotAcquireAStoreOriginOrAutomaticUpdates() = runTest {
        val restored = restoreOriginal(update.copy(revision = 1, title = "Edited after export"))

        val result = worker(catalog()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(restored, library.download(restored.id))
        assertNull(library.download("${update.themeId}:1"))
        assertNull(library.download("${update.themeId}:2"))
    }

    @Test
    fun failedRestoredRevisionVerificationRetriesWithoutBinding() = runTest {
        val restored = restoreOriginal()

        val result = worker(catalog(readOriginal = { error("Network unavailable") })).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(restored, library.download(restored.id))
        assertNull(library.download("${update.themeId}:2"))
        assertFalse(preferences.contains("themeStore.alerts.lastCheck"))
    }

    @Test
    fun profileChangeWhileVerifyingRestoredRevisionPreventsBinding() = runTest {
        val restored = restoreOriginal()

        val result = worker(catalog(readOriginal = {
            setActiveProfile("other", context)
            Json.encodeToString(update.copy(revision = 1)).encodeToByteArray()
        })).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(restored, library.download(restored.id))
        assertNull(library.download("${update.themeId}:1"))
        assertNull(library.download("${update.themeId}:2"))
        assertFalse(preferences.contains("themeStore.alerts.lastCheck"))
    }

    @Test
    fun restoredThemesWithUpdatesDisabledDoNotStartNetworkChecks() = runTest {
        val restored = restoreOriginal()
        library.setUpdateMode(update.themeId, ThemeUpdateMode.Off)

        val result = worker(ThemeStoreCatalog(folder.newFolder()) { _, _ -> error("No network expected") }).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(restored, library.download(restored.id))
        assertFalse(preferences.contains("themeStore.alerts.lastCheck"))
    }

    private fun restoreOriginal(original: StoreTheme = update.copy(revision = 1)): SavedTheme {
        library.removeDownload("${update.themeId}:1")
        val local = original.copy(themeId = localThemeArchiveId(Json.encodeToString(original).encodeToByteArray()))
        val restored = SavedTheme(
            original.title, document, local, imported = true,
            storeOrigin = ThemeStoreOrigin(original.themeId, original.revision),
        )
        assertEquals(1, library.restoreImported(listOf(restored)))
        return restored
    }

    private fun worker(catalog: ThemeStoreCatalog) =
        ThemeStoreAlertWorker(context, mockk<WorkerParameters>(relaxed = true), catalog)

    private fun catalog(
        readOriginal: () -> ByteArray = { Json.encodeToString(update.copy(revision = 1)).encodeToByteArray() },
        readAppearance: () -> ByteArray = { document.encodeToByteArray() },
    ): ThemeStoreCatalog {
        val manifest = Json.encodeToString(StoreTheme.serializer(), update).encodeToByteArray()
        val index = """{"schemaVersion":1,"themes":[{"themeId":"${update.themeId}","revision":2,"manifestSha256":"${manifest.sha256()}"}]}"""
        return ThemeStoreCatalog(folder.newFolder()) { path, _ ->
            when (path.substringBefore('?')) {
                "catalog/themes/index.json" -> index.encodeToByteArray()
                update.directory + "manifest.json" -> manifest
                update.directory + "appearance.json" -> readAppearance()
                update.copy(revision = 1).directory + "manifest.json" -> readOriginal()
                else -> error("Unexpected path $path")
            }
        }
    }
}
