package app.n_zik.android.playback.services

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import app.n_zik.android.core.profiles.invalidateProfileStorageIdentity
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.writeProfileEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBinder
import java.io.File
import java.io.FileOutputStream

/** The provider resolves custom covers within the active profile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkContentProviderProfileTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.profilePreferences.edit().clear().commit()
        ShadowBinder.reset()
        assertTrue(app.writeProfileEntries(listOf("work" to "Work")))
    }

    /** A minimal real JPEG so the provider's decode/EXIF/center-crop pipeline runs. */
    private fun seedCover(dirName: String, songId: String, color: Int = Color.MAGENTA) {
        val file = File(app.filesDir, "$dirName/cover_$songId.jpg")
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        try {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `untrusted UID receives the public placeholder instead of a private cover`() {
        seedCover("app_covers", "private")
        val content = provider()
        val uri = ArtworkContentProvider.uriFor("private")
        val scoped = requireNotNull(ArtworkContentProvider.bindLocalRequestUri(app, "default", uri))
        val privateBytes = servedBytes(requireNotNull(content.openFile(uri, "r")))
        ShadowBinder.setCallingUid(Process.myUid() + 1)
        try {
            val publicBytes = servedBytes(requireNotNull(content.openFile(uri, "r")))
            assertFalse(privateBytes.contentEquals(publicBytes))
            assertTrue(app.profilePreferences.edit().putString(activeProfileKey, "work").commit())
            assertTrue(publicBytes.contentEquals(servedBytes(requireNotNull(content.openFile(scoped, "r")))))
        } finally {
            ShadowBinder.reset()
        }
    }

    private fun provider(): ArtworkContentProvider = ArtworkContentProvider().also { it.attachInfo(app, null) }

    @Test
    fun `deleting an initially unversioned cover revises its canonical song URI`() {
        val songId = "deleted-cover"
        seedCover("app_covers", songId)
        val cover = requireNotNull(ArtworkContentProvider.customCoverFile(app, "default", songId))
        val initialUri = ArtworkContentProvider.uriFor(songId)
        val untouchedUri = ArtworkContentProvider.uriFor("never-edited-cover")

        ArtworkRevisions.invalidate()
        assertTrue(cover.delete())
        ArtworkRevisions.invalidateFile(cover)

        val expected = ArtworkContentProvider.scopedUriFor(app, "default", songId, ArtworkRevisions.current(cover.path, "default"))
        assertNotEquals(initialUri, expected)
        assertEquals(expected, ArtworkContentProvider.refreshLocalUri(app, "default", initialUri))
        assertEquals(ArtworkContentProvider.scopedUriFor(app, "default", "never-edited-cover"),
            ArtworkContentProvider.refreshLocalUri(app, "default", untouchedUri))
    }

    @Test
    fun `same song and revision serve scoped pixels for each profile`() {
        val songId = "shared-song"
        seedCover("app_covers", songId, Color.RED)
        seedCover("app_covers_work", songId, Color.BLUE)
        val content = provider()
        val legacy = ArtworkContentProvider.uriFor(songId, revision = 17L)
        val initial = requireNotNull(ArtworkContentProvider.bindLocalRequestUri(app, "default", legacy))
        val defaultColor = servedColor(requireNotNull(content.openFile(initial, "r")))
        assertTrue(Color.red(defaultColor) > 230 && Color.blue(defaultColor) < 25)

        assertTrue(app.profilePreferences.edit().putString(activeProfileKey, "work").commit())
        assertNull(ArtworkContentProvider.bindLocalRequestUri(app, "work", initial))
        assertNull(content.openFile(initial, "r"))
        val next = requireNotNull(ArtworkContentProvider.bindLocalRequestUri(app, "work", legacy))
        assertNotEquals(initial, next)
        assertEquals("17", next.getQueryParameter("r"))
        val workColor = servedColor(requireNotNull(content.openFile(next, "r")))
        assertTrue(Color.blue(workColor) > 230 && Color.red(workColor) < 25)
        assertEquals(workColor, servedColor(requireNotNull(content.openFile(legacy, "r"))))
    }

    @Test
    fun `replaced profile storage invalidates a retained scoped URI`() {
        val songId = "retained-song"
        assertTrue(app.profilePreferences.edit().putString(activeProfileKey, "work").commit())
        seedCover("app_covers_work", songId)
        val original = requireNotNull(ArtworkContentProvider.scopedUriFor(app, "work", songId))

        invalidateProfileStorageIdentity(app, "work")

        assertNull(ArtworkContentProvider.bindLocalRequestUri(app, "work", original))
        assertEquals(original, ArtworkContentProvider.refreshLocalUri(app, "work", original))
        assertNull(provider().openFile(original, "r"))
        val replacement = requireNotNull(ArtworkContentProvider.scopedUriFor(app, "work", songId))
        assertNotEquals(original, replacement)
        requireNotNull(provider().openFile(replacement, "r")).close()
    }

    @Test
    fun `song artwork recognition accepts only provider paths and known query keys`() {
        val songId = "local:42"
        val legacy = ArtworkContentProvider.uriFor(songId, revision = 17L)
        val otherProvider = legacy.buildUpon().authority("other.artwork").build()
        val unknownParameter = legacy.buildUpon().appendQueryParameter("unexpected", "value").build()
        val duplicateRevision = legacy.buildUpon().appendQueryParameter("r", "18").build()
        val partialOwner = legacy.buildUpon().appendQueryParameter("p", "default").build()
        val genericSource = Uri.parse("content://${ArtworkContentProvider.AUTHORITY}/source/token")
        val remote = ArtworkContentProvider.uriFor("remote")
        val scoped = requireNotNull(ArtworkContentProvider.bindLocalRequestUri(app, "default", legacy))

        assertTrue(ArtworkContentProvider.isSongArtworkUri(legacy, songId))
        assertTrue(ArtworkContentProvider.isSongArtworkUri(scoped, songId))
        for (uri in listOf(otherProvider, genericSource, remote)) {
            assertFalse(ArtworkContentProvider.isSongArtworkUri(uri, uri.lastPathSegment.orEmpty()))
            assertEquals(uri, ArtworkContentProvider.bindLocalRequestUri(app, "default", uri))
        }
        for (uri in listOf(unknownParameter, duplicateRevision, partialOwner)) {
            assertFalse(ArtworkContentProvider.isSongArtworkUri(uri, songId))
            assertNull(ArtworkContentProvider.bindLocalRequestUri(app, "default", uri))
        }
    }

    @Test
    fun `openFile serves the active profile's own cover`() {
        seedCover("app_covers_work", "1")
        app.profilePreferences.edit().putString(activeProfileKey, "work").commit()

        val content = provider()
        val pfd = content.openFile(ArtworkContentProvider.uriFor("1"), "r")
        assertNotNull("the profile's own cover must be served from app_covers_<id>", pfd)
        val actual = servedBytes(requireNotNull(pfd))
        val placeholder = servedBytes(requireNotNull(content.openFile(ArtworkContentProvider.uriFor("missing"), "r")))
        assertFalse("the verified profile cover must differ from the public placeholder", actual.contentEquals(placeholder))
    }

    /** Reads the served JPEG through the descriptor (the temp file is unlinked, the FD stays open). */
    private fun servedBytes(pfd: ParcelFileDescriptor): ByteArray =
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }

    private fun servedColor(pfd: ParcelFileDescriptor): Int = ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
        val bitmap = requireNotNull(BitmapFactory.decodeStream(input))
        try {
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `openFile does not serve a base-only cover for a non-default profile`() {
        seedCover("app_covers", "1") // base only
        val baseCoverBytes = File(app.filesDir, "app_covers/cover_1.jpg").readBytes()
        app.profilePreferences.edit().putString(activeProfileKey, "work").commit()

        // The provider now falls back to the app placeholder instead of refusing -
        // but the base profile's cover must still never leak across profiles.
        val pfd = provider().openFile(ArtworkContentProvider.uriFor("1"), "r")
        assertNotNull("the provider serves a fallback placeholder, never nothing", pfd)
        assertNotEquals(
            "a base-only cover must not leak into a non-default profile",
            baseCoverBytes,
            servedBytes(requireNotNull(pfd))
        )

        // ...and the base cover IS served again once the base profile is active.
        app.profilePreferences.edit().remove(activeProfileKey).commit()
        assertNotNull(
            "the base profile serves its own cover from the unsuffixed folder",
            provider().openFile(ArtworkContentProvider.uriFor("1"), "r")
        )
    }
}
