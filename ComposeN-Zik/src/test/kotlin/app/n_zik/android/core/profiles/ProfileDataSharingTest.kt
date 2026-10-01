package app.n_zik.android.core.profiles

import android.app.Application
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.profilePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The opt-in sharing flags of spec-profile-data-separation: one boolean per
 * [ProfileDataItem], per profile, in the global `profile_preferences` store. Separate is
 * the DEFAULT (a missing key = separate); a `false` flag is REMOVED, so the store only
 * ever carries the opt-ins. JUnit 4 + [RobolectricTestRunner] through the
 * junit-vintage-engine, with the plain [Application].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ProfileDataSharingTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.profilePreferences.edit().clear().commit()
        // The per-profile settings stores the dir resolvers read (location + size).
        profileDataPrefs(app, "work").edit().clear().commit()
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit().clear().commit()
    }

    @Test
    fun `the share key is scoped by item and profile`() {
        assertEquals("share_downloads_work", profileShareKey(ProfileDataItem.DOWNLOADS, "work"))
        assertEquals("share_media_cache_work", profileShareKey(ProfileDataItem.MEDIA_CACHE, "work"))
        assertEquals("share_images_home", profileShareKey(ProfileDataItem.IMAGES, "home"))
        assertEquals("share_waveforms_home", profileShareKey(ProfileDataItem.WAVEFORMS, "home"))
    }

    @Test
    fun `a missing flag means separate`() {
        ProfileDataItem.ALL.forEach { item ->
            assertFalse("a fresh profile shares nothing", app.profileShares(item, "work"))
        }
        assertTrue(app.profileSharedItems("work").isEmpty())
    }

    @Test
    fun `the share flag round trips per item`() {
        app.setProfileShares(ProfileDataItem.WAVEFORMS, "work", true)

        assertTrue(app.profileShares(ProfileDataItem.WAVEFORMS, "work"))
        assertEquals(setOf(ProfileDataItem.WAVEFORMS), app.profileSharedItems("work"))
        // the other items of the same profile stay separate
        ProfileDataItem.ALL.filter { it != ProfileDataItem.WAVEFORMS }.forEach { item ->
            assertFalse(app.profileShares(item, "work"))
        }
        // and the flag never leaks to another profile
        ProfileDataItem.ALL.forEach { item ->
            assertFalse(app.profileShares(item, "home"))
        }
    }

    @Test
    fun `sharing every item is read back as every item`() {
        ProfileDataItem.ALL.forEach { item -> app.setProfileShares(item, "work", true) }

        assertEquals(ProfileDataItem.ALL.toSet(), app.profileSharedItems("work"))
    }

    @Test
    fun `a false flag is removed from the store`() {
        app.setProfileShares(ProfileDataItem.DOWNLOADS, "work", true)
        app.setProfileShares(ProfileDataItem.DOWNLOADS, "work", false)

        assertFalse(app.profileShares(ProfileDataItem.DOWNLOADS, "work"))
        assertFalse(
            "the default (separate) is the absence of the key",
            app.profilePreferences.contains(profileShareKey(ProfileDataItem.DOWNLOADS, "work"))
        )
    }

    @Test
    fun `clearing forgets every flag of the profile and only those`() {
        ProfileDataItem.ALL.forEach { item -> app.setProfileShares(item, "work", true) }
        app.setProfileShares(ProfileDataItem.DOWNLOADS, "home", true)
        assertEquals(ProfileDataItem.ALL.toSet(), app.profileSharedItems("work"))

        app.clearProfileShareFlags("work")

        ProfileDataItem.ALL.forEach { item ->
            assertFalse(app.profileShares(item, "work"))
            assertFalse(
                "the key of $item is gone",
                app.profilePreferences.contains(profileShareKey(item, "work"))
            )
        }
        // the other profiles' flags survive
        assertTrue(app.profileShares(ProfileDataItem.DOWNLOADS, "home"))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Dir resolvers: a SHARED item is governed by the BASE profile's settings
    // (PARTAGE_ON — the base's shared folder + the base's index), a SEPARATE item
    // by the profile's own. (spec-profile-data-separation)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a shared item lives in the base folder under the base location setting`() {
        app.setProfileShares(ProfileDataItem.DOWNLOADS, "work", true)
        // Base caches in filesDir; the profile's own setting is System (cacheDir) —
        // it must be IGNORED for a shared item.
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit()
            .putString(exoPlayerCacheLocationKey, "Private")
            .commit()
        profileDataPrefs(app, "work").edit()
            .putString(exoPlayerCacheLocationKey, "System")
            .commit()

        assertEquals(
            "a shared item follows the BASE's location setting",
            File(app.filesDir, "exo_downloads").absolutePath,
            downloadsDir(app, "work")!!.absolutePath
        )
    }

    @Test
    fun `a separate item lives in the profile folder under its own location setting`() {
        // The profile's own setting is Private (filesDir); the base's is System —
        // the base setting must be IGNORED for a separate item.
        profileDataPrefs(app, "work").edit()
            .putString(exoPlayerCacheLocationKey, "Private")
            .commit()
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit()
            .putString(exoPlayerCacheLocationKey, "System")
            .commit()

        assertEquals(
            "a separate item follows the PROFILE's own location setting",
            File(app.filesDir, "exo_downloads_work").absolutePath,
            downloadsDir(app, "work")!!.absolutePath
        )
    }

    @Test
    fun `a shared item is disabled by the base size setting not the profile own`() {
        app.setProfileShares(ProfileDataItem.DOWNLOADS, "work", true)
        // Base disabled; the profile's own 2GB must be IGNORED for a shared item.
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit()
            .putString(exoPlayerDiskDownloadCacheMaxSizeKey, "Disabled")
            .commit()
        profileDataPrefs(app, "work").edit()
            .putString(exoPlayerDiskDownloadCacheMaxSizeKey, "2GB")
            .commit()

        assertNull("a shared item is disabled by the BASE's size setting", downloadsDir(app, "work"))
    }

    @Test
    fun `a separate item is disabled by its own size setting`() {
        // The profile's own cache is Disabled; the base's default 2GB must not save it.
        profileDataPrefs(app, "work").edit()
            .putString(exoPlayerDiskDownloadCacheMaxSizeKey, "Disabled")
            .commit()

        assertNull("a separate item is disabled by the PROFILE's own size setting", downloadsDir(app, "work"))
    }

    @Test
    fun `a shared media cache follows the base settings`() {
        app.setProfileShares(ProfileDataItem.MEDIA_CACHE, "work", true)
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit()
            .putString(exoPlayerCacheLocationKey, "Private")
            .commit()
        profileDataPrefs(app, "work").edit()
            .putString(exoPlayerCacheLocationKey, "System")
            .putString(exoPlayerDiskCacheMaxSizeKey, "Disabled") // must be ignored
            .commit()

        assertEquals(
            "a shared media cache follows the BASE's location setting",
            File(app.filesDir, "exoplayer").absolutePath,
            mediaCacheDir(app, "work")!!.absolutePath
        )
    }

    @Test
    fun `a separate media cache follows the profile own settings`() {
        profileDataPrefs(app, "work").edit()
            .putString(exoPlayerCacheLocationKey, "Private")
            .commit()

        assertEquals(
            "a separate media cache follows the PROFILE's own location setting",
            File(app.filesDir, "exoplayer_work").absolutePath,
            mediaCacheDir(app, "work")!!.absolutePath
        )
    }

    @Test
    fun `a shared coil cache lives in the base base dir`() {
        app.setProfileShares(ProfileDataItem.IMAGES, "work", true)
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit()
            .putString(exoPlayerCacheLocationKey, "Private")
            .commit()
        profileDataPrefs(app, "work").edit()
            .putString(exoPlayerCacheLocationKey, "System")
            .commit()

        assertEquals(
            "a shared coil cache follows the BASE's location setting",
            File(app.filesDir, "coil").absolutePath,
            coilImageCacheDir(app, "work").absolutePath
        )
    }

    @Test
    fun `a separate coil cache lives in the profile own base dir`() {
        profileDataPrefs(app, "work").edit()
            .putString(exoPlayerCacheLocationKey, "Private")
            .commit()

        assertEquals(
            "a separate coil cache follows the PROFILE's own location setting",
            File(app.filesDir, "coil_work").absolutePath,
            coilImageCacheDir(app, "work").absolutePath
        )
    }

    @Test
    fun `the save-side covers name is the active profile folder`() {
        // A non-default active profile saves into its own suffixed folder.
        app.profilePreferences.edit().putString(activeProfileKey, "work").commit()
        assertEquals("app_covers_work", activeCoversDirName(app))
        // The base keeps the unsuffixed folder.
        app.profilePreferences.edit().putString(activeProfileKey, DEFAULT_PROFILE_ID).commit()
        assertEquals("app_covers", activeCoversDirName(app))
    }
}
