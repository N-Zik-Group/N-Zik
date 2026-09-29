package app.n_zik.android.components.ui.screens.profiles

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * Tests the avatar-save half of the profile face (spec-profiles-page-face):
 * [saveProfileAvatarFromUri] decodes the pick, downsizes it to [AVATAR_MAX_SIDE_PX] on its
 * longest side and re-encodes it as a JPEG at [AVATAR_JPEG_QUALITY] at exactly
 * `profiles/<id>/avatar.jpg` — the face travels base64 inside the profile state .txt export,
 * so a raw multi-megabyte pick (often a PNG) must not end up on disk. Robolectric is required
 * because the decode / scale / encode path needs real pixel backing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileAvatarSaveTest {

    private fun tempFiles(): File = Files.createTempDirectory("nzik-profile-avatar-save").toFile()

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun contextWith(files: File, resolver: ContentResolver): Context =
        mockk {
            every { filesDir } returns files
            every { contentResolver } returns resolver
        }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

    private fun decodedBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(file.readBytes(), 0, file.length().toInt(), options)
        return options.outWidth to options.outHeight
    }

    @Test
    fun saveReencodesThePickDownsizedAsAJpeg() = runTest {
        val files = tempFiles()
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(pngBytes(2000, 1000))
        val context = contextWith(files, resolver)

        assertTrue(saveProfileAvatarFromUri(context, "work", uri))

        val avatar = File(files, "profiles/work/avatar.jpg")
        assertTrue("the pick must materialize at profiles/<id>/avatar.jpg", avatar.exists())
        assertTrue("the pick is re-encoded as a JPEG, not copied as-is", isJpeg(avatar.readBytes()))
        val (width, height) = decodedBounds(avatar)
        assertEquals("the longest side is downsized to the cap", AVATAR_MAX_SIDE_PX.toLong(), width.toLong())
        assertEquals("the other side keeps the aspect ratio", (AVATAR_MAX_SIDE_PX / 2).toLong(), height.toLong())
        assertEquals(avatar.absolutePath, context.profileAvatarSource("work"))
    }

    @Test
    fun aPickSmallerThanTheCapIsReencodedWithoutUpscaling() = runTest {
        val files = tempFiles()
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(pngBytes(300, 200))
        val context = contextWith(files, resolver)

        assertTrue(saveProfileAvatarFromUri(context, "work", uri))

        val avatar = File(files, "profiles/work/avatar.jpg")
        assertTrue(isJpeg(avatar.readBytes()))
        val (width, height) = decodedBounds(avatar)
        assertEquals("a small pick is re-encoded at its own size, never upscaled", 300L, width.toLong())
        assertEquals(200L, height.toLong())
    }

    // Note: there is deliberately no "undecodable pick" test here. On a real device
    // BitmapFactory.decodeByteArray returns null for garbage bytes, but Robolectric
    // returns a 100x100 stub bitmap instead (even for an empty array), so the
    // production failure path cannot be expressed under Robolectric. The failure path
    // is covered by aFailedSaveReportsFalseAndLeavesNoFile (null stream) instead.

    @Test
    fun aFailedSaveReportsFalseAndLeavesNoFile() = runTest {
        val files = tempFiles()
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        val context = contextWith(files, resolver)

        assertFalse(saveProfileAvatarFromUri(context, "work", uri))
        assertFalse(File(files, "profiles/work/avatar.jpg").exists())
    }

    @Test
    fun removePhotoDeletesOnlyThePhotoFile() {
        val files = tempFiles()
        val avatar = File(files, "profiles/work/avatar.jpg").apply {
            parentFile?.mkdirs()
            writeText("photo")
        }
        val sibling = File(files, "profiles/work/notes.txt").apply { writeText("keep") }
        val context = contextWith(files, mockk())

        removeProfileAvatarFile(context, "work")

        assertFalse("the photo file must be gone", avatar.exists())
        assertTrue("the profile's other files must stay", sibling.exists())
    }
}
