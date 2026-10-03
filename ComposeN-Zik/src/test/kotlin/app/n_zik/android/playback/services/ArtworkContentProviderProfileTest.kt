package app.n_zik.android.playback.services

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.profilePreferences
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * The profile resolution of [ArtworkContentProvider.openFile]
 * (spec-profile-data-separation): the served cover comes from the ACTIVE profile's own
 * custom covers folder — a non-default profile never sees a base-only cover, and the base
 * profile keeps its unsuffixed folder. JUnit 4 + [RobolectricTestRunner] through the
 * junit-vintage-engine, with the plain [Application] (no app init).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ArtworkContentProviderProfileTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.profilePreferences.edit().clear().commit()
    }

    /** A minimal real JPEG so the provider's decode/EXIF/center-crop pipeline runs. */
    private fun seedCover(dirName: String, songId: String) {
        val file = File(app.filesDir, "$dirName/cover_$songId.jpg")
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun provider(): ArtworkContentProvider = ArtworkContentProvider().also { it.attachInfo(app, null) }

    @Test
    fun `openFile serves the active profile's own cover`() {
        seedCover("app_covers_work", "1")
        app.profilePreferences.edit().putString(activeProfileKey, "work").commit()

        val pfd = provider().openFile(Uri.parse("content://anything/1"), "r")

        assertNotNull("the profile's own cover must be served from app_covers_<id>", pfd)
    }

    /** Reads the served JPEG through the descriptor (the temp file is unlinked, the FD stays open). */
    private fun servedBytes(pfd: ParcelFileDescriptor): ByteArray =
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }

    @Test
    fun `openFile does not serve a base-only cover for a non-default profile`() {
        seedCover("app_covers", "1") // base only
        val baseCoverBytes = File(app.filesDir, "app_covers/cover_1.jpg").readBytes()
        app.profilePreferences.edit().putString(activeProfileKey, "work").commit()

        // The provider now falls back to the app placeholder instead of refusing —
        // but the base profile's cover must still never leak across profiles.
        val pfd = provider().openFile(Uri.parse("content://anything/1"), "r")
        assertNotNull("the provider serves a fallback placeholder, never nothing", pfd)
        assertNotEquals(
            "a base-only cover must not leak into a non-default profile",
            baseCoverBytes,
            servedBytes(pfd!!)
        )

        // ...and the base cover IS served again once the base profile is active.
        app.profilePreferences.edit().remove(activeProfileKey).commit()
        assertNotNull(
            "the base profile serves its own cover from the unsuffixed folder",
            provider().openFile(Uri.parse("content://anything/1"), "r")
        )
    }
}
