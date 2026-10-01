package app.n_zik.android.download.utils

import android.app.Application
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.setProfileShares
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The lifted download-cache resolution of [MyDownloadHelper.downloadCacheLocation]
 * (spec-profile-data-separation): a NON-default profile resolves its own suffixed dir +
 * suffixed index when separate, the base dir + base index when shared, and a null dir
 * (temp dir) when the effective download cache is Disabled — the (dir, indexDbName) that
 * [MyDownloadHelper.initDownloadCache] used to compute inline. A regression to the base
 * dir or to [androidx.media3.database.StandaloneDatabaseProvider] would land here red.
 * JUnit 4 + [RobolectricTestRunner] through the junit-vintage-engine, with the plain
 * [Application] (no app init).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MyDownloadHelperCacheLocationTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.profilePreferences.edit().clear().commit()
        profileDataPrefs(app, "work").edit().clear().commit()
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit().clear().commit()
    }

    private fun activate(profile: String) {
        app.profilePreferences.edit().putString(activeProfileKey, profile).commit()
    }

    @Test
    fun `a separate profile resolves its suffixed dir and suffixed index`() {
        activate("work")

        val location = MyDownloadHelper.downloadCacheLocation(app)

        assertEquals(
            "the separate downloads live in the profile's own suffixed folder",
            File(app.cacheDir, "exo_downloads_work").absolutePath,
            location.dir!!.absolutePath
        )
        assertEquals("exoplayer_internal_work.db", location.indexDbName)
    }

    @Test
    fun `a shared profile resolves the base dir and base index`() {
        activate("work")
        app.setProfileShares(ProfileDataItem.DOWNLOADS, "work", true)

        val location = MyDownloadHelper.downloadCacheLocation(app)

        assertEquals(
            "the shared downloads live in the base folder",
            File(app.cacheDir, "exo_downloads").absolutePath,
            location.dir!!.absolutePath
        )
        assertEquals("exoplayer_internal.db", location.indexDbName)
    }

    @Test
    fun `a separate profile with a private location resolves under filesDir`() {
        activate("work")
        profileDataPrefs(app, "work").edit().putString(exoPlayerCacheLocationKey, "Private").commit()

        val location = MyDownloadHelper.downloadCacheLocation(app)

        assertEquals(
            File(app.filesDir, "exo_downloads_work").absolutePath,
            location.dir!!.absolutePath
        )
    }

    @Test
    fun `a disabled profile download cache resolves to a null dir with its own temp name`() {
        activate("work")
        profileDataPrefs(app, "work").edit().putString(exoPlayerDiskDownloadCacheMaxSizeKey, "Disabled").commit()

        val location = MyDownloadHelper.downloadCacheLocation(app)

        assertNull("a Disabled cache lives in a temp dir, not a persistent one", location.dir)
        assertEquals("exo_downloads_work", location.tempDirName)
        assertEquals("exoplayer_internal_work.db", location.indexDbName)
    }

    @Test
    fun `a shared item is disabled by the base setting with the base temp name`() {
        activate("work")
        app.setProfileShares(ProfileDataItem.DOWNLOADS, "work", true)
        profileDataPrefs(app, DEFAULT_PROFILE_ID).edit()
            .putString(exoPlayerDiskDownloadCacheMaxSizeKey, "Disabled")
            .commit()

        val location = MyDownloadHelper.downloadCacheLocation(app)

        assertNull("the base's Disabled governs the shared item", location.dir)
        assertEquals("exo_downloads", location.tempDirName)
        assertEquals("exoplayer_internal.db", location.indexDbName)
    }

    @Test
    fun `the base profile keeps the unsuffixed dir and the base index`() {
        activate(DEFAULT_PROFILE_ID)

        val location = MyDownloadHelper.downloadCacheLocation(app)

        assertEquals(File(app.cacheDir, "exo_downloads").absolutePath, location.dir!!.absolutePath)
        assertEquals("exoplayer_internal.db", location.indexDbName)
    }
}
