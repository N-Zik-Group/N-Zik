package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.writeProfileEntries
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
class ThemeStoreDownloadWorkerTest {
    @get:Rule val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val library get() = ThemeStoreLibrary(profileDataPrefs(context, DEFAULT_PROFILE_ID))
    private val document = """{"schemaVersion":1,"categories":{"colors":{"palette":"dynamic","mode":"dark"}}}"""
    private val theme = StoreTheme(
        1, "99edc843-6a9b-5753-8e76-cc11fe9b3295", 2, "Theme", "Theme description",
        StoreAuthor(1, "creator"), emptyList(), StoreCompatibility(BuildConfig.VERSION_CODE),
        StoreFile("appearance.json", document.encodeToByteArray().sha256()), emptyList(),
    )
    private val id = "${theme.themeId}:${theme.revision}"

    @Before fun resetProfile() {
        setActiveProfile(DEFAULT_PROFILE_ID, context)
        profileDataPrefs(context, DEFAULT_PROFILE_ID).edit().clear().commit()
        shadowOf(checkNotNull(context.getSystemService(NotificationManager::class.java))).setNotificationsEnabled(false)
    }

    @Test fun persistsWithoutAnOpenScreenAndDoesNotRedownloadAfterRestart() = runTest {
        val result = worker(catalog()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(document, library.download(id)?.document)

        val restarted = worker(ThemeStoreCatalog(folder.newFolder()) { _, _ -> error("No network expected") }).doWork()
        assertTrue(restarted is ListenableWorker.Result.Success)
    }

    @Test fun switchingProfilesBeforeCommitLeavesTheDownloadQueuedForItsOwner() = runTest {
        val result = worker(catalog {
            setActiveProfile("other", context)
            document.encodeToByteArray()
        }).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertNull(library.download(id))
        assertNull(ThemeStoreLibrary(profileDataPrefs(context, "other")).download(id))
    }

    @Test fun themeDownloadAndNotificationBelongToTheRequestingProfile() = runTest {
        assertTrue(context.writeProfileEntries(listOf("work" to "Work")))
        setActiveProfile("work", context)
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        shadowOf(manager).setNotificationsEnabled(true)

        assertTrue(worker(catalog()).doWork() is ListenableWorker.Result.Success)

        assertEquals(document, ThemeStoreLibrary(profileDataPrefs(context, "work")).download(id)?.document)
        assertNull(library.download(id))
        val channel = manager.activeNotifications.single().notification.channelId
        assertEquals("theme_store_downloads_work", channel)
        assertEquals("nzik_theme_store_work", manager.getNotificationChannel(channel).group)
    }

    @Test fun invalidThemeHashDoesNotSaveADownload() = runTest {
        val result = worker(catalog { "tampered".encodeToByteArray() }).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertNull(library.download(id))
    }

    private fun worker(catalog: ThemeStoreCatalog): ThemeStoreDownloadWorker {
        val owner = checkNotNull(ProfileWorkOwner.capture(context))
        val input = Data.Builder().putAll(ProfileWorkOwner.input(owner))
            .putString(ThemeStoreDownloadWorker.ThemeId, theme.themeId)
            .putInt(ThemeStoreDownloadWorker.Revision, theme.revision).build()
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.inputData } returns input
        return ThemeStoreDownloadWorker(context, parameters, catalog)
    }

    private fun catalog(readAppearance: () -> ByteArray = { document.encodeToByteArray() }): ThemeStoreCatalog {
        val manifest = Json.encodeToString(StoreTheme.serializer(), theme).encodeToByteArray()
        val index = """{"schemaVersion":1,"themes":[{"themeId":"${theme.themeId}","revision":${theme.revision},"manifestSha256":"${manifest.sha256()}"}]}"""
        return ThemeStoreCatalog(folder.newFolder()) { path, _ ->
            when (path.substringBefore('?')) {
                ThemeCatalogV1Path -> index.encodeToByteArray()
                theme.directory + "manifest.json" -> manifest
                theme.directory + "appearance.json" -> readAppearance()
                else -> error("Unexpected path $path")
            }
        }
    }
}
