package app.n_zik.android.components.dialog.song

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Issue #606 M1 — `CoverArtField` used to decode the cover art inside a `remember`, i.e.
 * `BitmapFactory.decodeByteArray` ran on Main during composition. The decode is now
 * [EditMetadataDialog.decodeCoverBitmap], dispatched on `NzikDispatchers.MEDIA` from a
 * `produceState`. The dispatch must not change the result: same bytes in, same bitmap (or null
 * for an undecodable image) out.
 */
class EditMetadataDialogDecodeCoverBitmapOffMainTest {

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `decodeCoverBitmap decodes the given bytes on a nzik-media thread, not the caller`() = runBlocking {
        val callerThread = Thread.currentThread()
        val bytes = byteArrayOf(9, 8, 7, 6)
        val decoded = mockk<Bitmap> {
            every { width } returns 400
            every { height } returns 300
        }
        val threads = CopyOnWriteArrayList<Thread>()
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, any()) } answers {
            val options = arg<BitmapFactory.Options>(3)
            if (options.inJustDecodeBounds) {
                options.outWidth = 4000
                options.outHeight = 3000
                return@answers null
            }
            threads += Thread.currentThread()
            assertTrue(options.inSampleSize > 1)
            decoded
        }

        val result = EditMetadataDialog.decodeCoverBitmap(bytes)

        assertSame(decoded, result)
        assertEquals(1, threads.size)
        assertNotEquals(callerThread, threads.single())
        assertTrue(threads.single().name.startsWith("nzik-media"), "decoded on ${threads.single().name}")
    }

    @Test
    fun `decodeCoverBitmap returns null when the bytes are not an image`() = runBlocking {
        val bytes = byteArrayOf(0, 0, 0)
        mockkStatic(BitmapFactory::class)
        every { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, any()) } returns null

        assertNull(EditMetadataDialog.decodeCoverBitmap(bytes))
    }
}
