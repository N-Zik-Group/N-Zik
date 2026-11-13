package app.n_zik.android.components.dialog.common

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.ExoPlayerCacheLocation
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.exoPlayerCacheLocationKey
import app.n_zik.android.utils.exoPlayerDiskCacheMaxSizeKey
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.writeProfileEntries
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.profiles.ProfileCacheLocationMigration
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.playback.services.mediaCacheLocation
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import java.io.File

/**
 * Test-only application: MainApplication.onCreate() touches AndroidKeyStore, absent on the
 * JVM; the dialog only needs [appContext] through [Dependencies] (the MediaItemUtilsTest
 * pattern). JUnit 4 + [RobolectricTestRunner] through the junit-vintage-engine, with the
 * plain [Application].
 */
class TestApplication : Application()

/**
 * The storage read/write + flag-write timing of [SamsungStorageAdviceDialog]
 * (spec-samsung-storage-advice-dialog, review loop 1): the active profile's OWN location
 * setting is what the gate reads, [SamsungStorageAdviceDialog.onConfirm] requests a verified transfer, and the shown flag is persisted ONLY when
 * the trigger decision passes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SamsungStorageAdviceStorageTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)

        app.profilePreferences.edit().clear().commit()
        check(app.writeProfileEntries(listOf("work" to "Work")))
        profileDataPrefs(app, "work").edit().clear().commit()
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit().clear().commit()
        DataStoreUtils.prefs(app).edit().clear().commit()
        SamsungStorageAdviceDialog.isActive = false
        RestartAppDialog.isActive = false
        SamsungStorageAdviceDialog.storageBytes = 0L
    }

    private fun activate(profile: String) {
        app.profilePreferences.edit().putString(activeProfileKey, profile).commit()
    }

    @Test
    fun `the active profile private setting is returned`() {
        activate("work")
        profileDataPrefs(app, "work").edit().putString(exoPlayerCacheLocationKey, "Private").commit()

        assertEquals(
            ExoPlayerCacheLocation.Private,
            SamsungStorageAdviceDialog.activeProfileCacheLocation(app)
        )
    }

    @Test
    fun `an unset active profile setting answers system`() {
        activate("work")

        assertEquals(
            ExoPlayerCacheLocation.System,
            SamsungStorageAdviceDialog.activeProfileCacheLocation(app)
        )
    }

    @Test
    fun `onConfirm queues private without changing the live service root`() {
        activate("work")
        val profile = checkNotNull(profileStorageIdentity(app, "work"))

        SamsungStorageAdviceDialog.onConfirm()

        assertEquals(ExoPlayerCacheLocation.Private, ProfileCacheLocationMigration.requested(app, profile))
        assertEquals(ExoPlayerCacheLocation.System, SamsungStorageAdviceDialog.activeProfileCacheLocation(app))
        assertEquals(File(app.cacheDir, "exoplayer_work").absolutePath, mediaCacheLocation(app).dir?.absolutePath)
    }

    @Test
    fun `the trigger persists the flag when the decision passes`() = runBlocking {
        // runBlocking: synchronous JUnit 4 entry point into the suspend trigger; no coroutine-test alternative under the vintage runner.
        activate("work")

        triggerSamsungStorageAdvice(app, samsungDetector = { true })

        assertTrue(
            "the shown flag is persisted when the dialog is shown",
            DataStoreUtils.getBoolean(app, DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN)
        )
        assertTrue("the dialog is shown", SamsungStorageAdviceDialog.isActive)
    }

    @Test
    fun `the trigger does not show the dialog when the advice was already shown`() = runBlocking {
        // runBlocking: synchronous JUnit 4 entry point into the suspend trigger; no coroutine-test alternative under the vintage runner.
        activate("work")
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN, true)

        triggerSamsungStorageAdvice(app, samsungDetector = { true })

        assertFalse("the advice is never shown twice", SamsungStorageAdviceDialog.isActive)
    }

    @Test
    fun `the trigger does not persist the flag when the location is private`() = runBlocking {
        // runBlocking: synchronous JUnit 4 entry point into the suspend trigger; no coroutine-test alternative under the vintage runner.
        activate("work")
        profileDataPrefs(app, "work").edit().putString(exoPlayerCacheLocationKey, "Private").commit()

        triggerSamsungStorageAdvice(app, samsungDetector = { true })

        assertFalse(
            "no flag is written for a private location",
            DataStoreUtils.getBoolean(app, DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN)
        )
        assertFalse("the dialog is not shown", SamsungStorageAdviceDialog.isActive)
    }

    @Test
    fun `the trigger does nothing on a non Samsung device`() = runBlocking {
        // runBlocking: synchronous JUnit 4 entry point into the suspend trigger; no coroutine-test alternative under the vintage runner.
        activate("work")

        triggerSamsungStorageAdvice(app, samsungDetector = { false })

        assertFalse(
            "no flag is written for non-Samsung devices",
            DataStoreUtils.getBoolean(app, DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN)
        )
        assertFalse("the dialog is not shown", SamsungStorageAdviceDialog.isActive)
    }

    @Test
    fun `isSamsungDevice answers true for a samsung manufacturer`() {
        ShadowBuild.setManufacturer("samsung")

        assertTrue(SamsungStorageAdviceDialog.isSamsungDevice())
    }

    @Test
    fun `isSamsungDevice answers false for a non samsung manufacturer`() {
        ShadowBuild.setManufacturer("google")

        assertFalse(SamsungStorageAdviceDialog.isSamsungDevice())
    }

    @Test
    fun `the trigger shows and persists the flag with the default samsung detector`() = runBlocking {
        // runBlocking: synchronous JUnit 4 entry point into the suspend trigger; no coroutine-test alternative under the vintage runner.
        activate("work")
        ShadowBuild.setManufacturer("SAMSUNG")

        triggerSamsungStorageAdvice(app)

        assertTrue(
            "the production default detector shows the dialog and persists the flag",
            DataStoreUtils.getBoolean(app, DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN)
        )
        assertTrue("the dialog is shown", SamsungStorageAdviceDialog.isActive)
    }

    @Test
    fun `onConfirm dismisses advice and opens the app restart prompt`() {
        activate("work")
        SamsungStorageAdviceDialog.showDialog()

        SamsungStorageAdviceDialog.onConfirm()

        assertTrue(RestartAppDialog.isActive)
        assertFalse(SamsungStorageAdviceDialog.isActive)
    }

    @Test
    fun `activeProfileStorageSize sums the written files of the active profile`() {
        activate("work")
        // Default location is System, so the separate dirs resolve under cacheDir.
        val downloads = app.cacheDir.resolve("exo_downloads_work")
        downloads.resolve("sub").mkdirs()
        downloads.resolve("track1.bin").writeBytes(ByteArray(300))
        downloads.resolve("sub").resolve("track2.bin").writeBytes(ByteArray(200))
        val mediaCache = app.cacheDir.resolve("exoplayer_work")
        mediaCache.mkdirs()
        mediaCache.resolve("segment.bin").writeBytes(ByteArray(100))

        assertEquals(
            "downloads and media cache sizes are summed",
            600L,
            SamsungStorageAdviceDialog.activeProfileStorageSize(app)
        )
    }

    @Test
    fun `activeProfileStorageSize answers zero when the directories are absent`() {
        activate("work")

        assertEquals(0L, SamsungStorageAdviceDialog.activeProfileStorageSize(app))
    }

    @Test
    fun `activeProfileStorageSize skips a disabled media cache`() {
        activate("work")
        val downloads = app.cacheDir.resolve("exo_downloads_work")
        downloads.mkdir()
        downloads.resolve("track1.bin").writeBytes(ByteArray(200))
        profileDataPrefs(app, "work").edit().putString(exoPlayerDiskCacheMaxSizeKey, "Disabled").commit()

        assertEquals(
            "a disabled media cache resolves to no directory, so only downloads count",
            200L,
            SamsungStorageAdviceDialog.activeProfileStorageSize(app)
        )
    }

    @Test
    fun `a back dismiss hides the dialog but keeps the shown flag`() = runBlocking {
        // runBlocking: synchronous JUnit 4 entry point into the suspend trigger; no coroutine-test alternative under the vintage runner.
        activate("work")

        triggerSamsungStorageAdvice(app, samsungDetector = { true })
        assertTrue("the dialog is shown", SamsungStorageAdviceDialog.isActive)

        SamsungStorageAdviceDialog.hideDialog()

        assertTrue(
            "the one-shot flag survives a back-dismiss",
            DataStoreUtils.getBoolean(app, DataStoreUtils.KEY_SAMSUNG_STORAGE_ADVICE_SHOWN)
        )
        assertFalse("the dialog is hidden after the back-dismiss", SamsungStorageAdviceDialog.isActive)
    }
}
