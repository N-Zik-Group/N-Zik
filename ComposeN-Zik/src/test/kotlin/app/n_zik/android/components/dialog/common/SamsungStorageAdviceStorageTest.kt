package app.n_zik.android.components.dialog.common

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.profiles.profileCacheBaseDir
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.mediaCacheLocation
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * setting is what the gate reads, [SamsungStorageAdviceDialog.onConfirm] writes Private
 * to the store the player service reads from, and the shown flag is persisted ONLY when
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
        profileDataPrefs(app, "work").edit().clear().commit()
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit().clear().commit()
        DataStoreUtils.prefs(app).edit().clear().commit()
        SamsungStorageAdviceDialog.isActive = false
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
    fun `onConfirm writes private and the service read resolves under filesDir`() {
        activate("work")

        SamsungStorageAdviceDialog.onConfirm()

        assertEquals(
            "the active profile's own store holds Private",
            ExoPlayerCacheLocation.Private,
            profileDataPrefs(app, "work").getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)
        )
        val location = mediaCacheLocation(app)
        assertNotNull("the media cache is enabled by default, so the dir resolves", location.dir)
        assertEquals(
            "the service-side media cache read follows the private location",
            File(app.filesDir, "exoplayer_work").absolutePath,
            location.dir?.absolutePath
        )
        assertEquals(
            "profileCacheBaseDir follows the active profile's own setting",
            app.filesDir.absolutePath,
            profileCacheBaseDir(app, "work").absolutePath
        )
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
    fun `onConfirm with a bound binder restarts the service and dismisses`() {
        activate("work")
        val binder = mockk<PlayerServiceModern.Binder>()
        every { binder.restartForegroundOrStop() } returns Unit
        SamsungStorageAdviceDialog.showDialog()

        SamsungStorageAdviceDialog.onConfirm(binder)

        verify(exactly = 1) { binder.restartForegroundOrStop() }
        assertEquals(
            "the active profile's own store holds Private",
            ExoPlayerCacheLocation.Private,
            profileDataPrefs(app, "work").getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)
        )
        assertFalse("the dialog is dismissed after the primary action", SamsungStorageAdviceDialog.isActive)
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
