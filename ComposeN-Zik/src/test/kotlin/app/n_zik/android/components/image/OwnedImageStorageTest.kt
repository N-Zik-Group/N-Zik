package app.n_zik.android.components.image

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import app.n_zik.android.components.dialog.common.isTemporaryImageUri
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.n_zik.android.core.profiles.profileDataPrefs
import org.junit.Assert.assertEquals
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, shadows = [PosixAtomicFileShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OwnedImageStorageTest {
    @Test
    fun `original keeps source bytes while default limits the longest side`() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "large-source.png")
        val bitmap = Bitmap.createBitmap(4800, 180, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }

        val optimized = requireNotNull(saveOwnedImage(context, Uri.fromFile(source), "artwork", "cover_test.jpg"))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(optimized.path, bounds)
        assertEquals(1000, bounds.outWidth)

        profileDataPrefs(context, "default").edit().putBoolean(ORIGINAL_ARTWORK_QUALITY_KEY, true).commit()
        val original = requireNotNull(saveOwnedImage(context, Uri.fromFile(source), "artwork", "cover_test.png"))
        val saved = File(requireNotNull(original.path))
        assertArrayEquals(source.readBytes(), saved.readBytes())
        BitmapFactory.decodeFile(saved.path, bounds)
        assertEquals(4800, bounds.outWidth)
    }

    @Test
    fun `failed atomic publication reports error and removes unfinished staging`() {
        val context = RuntimeEnvironment.getApplication()
        val target = File(context.filesDir, "blocked-destination").apply { mkdirs() }
        val existing = File(target, "keep").apply { writeText("original") }
        try {
            writeOwnedImageAtomically(target) { it.write("new cover".toByteArray()) }
            fail("A failed rename must not report successful publication")
        } catch (_: IOException) {
            assertTrue(existing.readText() == "original")
            assertFalse(File(target.path + ".new").exists())
        }
    }

    @Test
    fun `rollback restores previous bytes and invalidates artwork read during failed commit`() {
        val context = RuntimeEnvironment.getApplication()
        val target = File(context.filesDir, "rolled-back-cover.jpg").apply { writeText("confirmed") }
        val previous = target.readBytes()
        target.writeText("cancelled")
        val beforeRollback = ArtworkRevisions.current()
        restoreOwnedCover(target, previous)
        assertArrayEquals(previous, target.readBytes())
        assertTrue(ArtworkRevisions.current() > beforeRollback)
        restoreOwnedCover(target, null)
        assertFalse(target.exists())
    }

    @Test
    fun `failed replacement restores original bytes`() {
        val context = RuntimeEnvironment.getApplication()
        val target = File(context.filesDir, "atomic-cover.jpg").apply { writeText("previous cover") }
        val original = target.readBytes()
        try {
            writeOwnedImageAtomically(target) {
                it.write("partial new image".toByteArray())
                throw IOException("disk failure")
            }
        } catch (_: IOException) {
            // Simulates a failed encode or write after the output stream was opened.
        }
        assertArrayEquals(original, target.readBytes())
    }

    @Test
    fun `cache drafts and provider grants cannot become typed database covers`() {
        // Android URI paths are POSIX paths; host Windows drive-letter URIs are not Android inputs.
        val context = mockk<Context> {
            every { packageName } returns "app.n_zik.android"
            every { cacheDir } returns File("/data/user/0/app.n_zik.android/cache")
            every { codeCacheDir } returns File("/data/user/0/app.n_zik.android/code_cache")
        }
        assertTrue(isTemporaryImageUri(context, "file:///data/user/0/app.n_zik.android/cache/image_edit/session/input.jpg"))
        assertTrue(isTemporaryImageUri(context, "content://${context.packageName}.fileprovider/image_edit/session/input.jpg"))
        assertFalse(isTemporaryImageUri(context, "file:///data/user/0/app.n_zik.android/files/app_covers/cover_song.jpg"))
        assertFalse(isTemporaryImageUri(context, "file:///data/user/0/app.n_zik.android/cache-archive/cover.jpg"))
        assertFalse(isTemporaryImageUri(context, "https://example.com/cover.jpg"))
    }
}
