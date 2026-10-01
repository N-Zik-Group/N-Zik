package app.n_zik.android.core.coil

import android.app.Application
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.setProfileShares
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The lifted Coil disk-cache dir selection of [ImageCacheFactory.coilDiskCacheDir]
 * (spec-profile-data-separation): a NON-default profile resolves its own suffixed dir
 * under its own location setting, and the base dir when the images are shared. A
 * regression to the base dir for a separate profile would land here red. JUnit 4 +
 * [RobolectricTestRunner] through the junit-vintage-engine, with the plain [Application]
 * (no app init).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ImageCacheFactoryDiskCacheDirTest {

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
    fun `a separate profile resolves its suffixed coil dir`() {
        activate("work")

        assertEquals(
            "the separate coil cache lives in the profile's own suffixed folder",
            File(app.cacheDir, "coil_work").absolutePath,
            ImageCacheFactory.coilDiskCacheDir(app).absolutePath
        )
    }

    @Test
    fun `a shared profile resolves the base coil dir`() {
        activate("work")
        app.setProfileShares(ProfileDataItem.IMAGES, "work", true)

        assertEquals(
            "the shared images live in the base folder",
            File(app.cacheDir, "coil").absolutePath,
            ImageCacheFactory.coilDiskCacheDir(app).absolutePath
        )
    }

    @Test
    fun `a separate profile with a private location resolves under filesDir`() {
        activate("work")
        profileDataPrefs(app, "work").edit().putString(exoPlayerCacheLocationKey, "Private").commit()

        assertEquals(
            File(app.filesDir, "coil_work").absolutePath,
            ImageCacheFactory.coilDiskCacheDir(app).absolutePath
        )
    }

    @Test
    fun `the base profile keeps the unsuffixed coil dir`() {
        activate(DEFAULT_PROFILE_ID)

        assertEquals(File(app.cacheDir, "coil").absolutePath, ImageCacheFactory.coilDiskCacheDir(app).absolutePath)
    }
}
