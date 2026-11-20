package app.n_zik.android.playback.utils

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionBitmapLoaderTest {
    @Test fun `file fallback is bounded and oriented after a single shared request`() {
        val file = File.createTempFile("session-cover", ".jpg")
        val uri = Uri.fromFile(file)
        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        mockkObject(ImageCacheFactory)
        try {
            val source = Bitmap.createBitmap(960, 480, Bitmap.Config.ARGB_8888)
            try {
                source.eraseColor(Color.RED)
                file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            } finally {
                source.recycle()
            }
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            coEvery {
                ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 250, cacheDecodedBitmap = false)
            } returns null

            val loader = SessionBitmapLoader(RuntimeEnvironment.getApplication(), scope, 250)
            val artwork = loader.loadBitmap(uri).get(5, TimeUnit.SECONDS)

            assertEquals(125, artwork.width)
            assertEquals(250, artwork.height)
            assertTrue(Color.red(artwork.getPixel(0, 0)) > 240)
            coVerify(exactly = 1) {
                ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 250, cacheDecodedBitmap = false)
            }
            artwork.recycle()
        } finally {
            scope.cancel()
            unmockkObject(ImageCacheFactory)
            file.delete()
        }
    }

    @Test fun `missing local artwork completes with bounded launcher image without retry`() {
        val uri = Uri.fromFile(File(RuntimeEnvironment.getApplication().cacheDir, "missing-session-cover.jpg"))
        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        mockkObject(ImageCacheFactory)
        try {
            coEvery {
                ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 250, cacheDecodedBitmap = false)
            } returns null
            val loader = SessionBitmapLoader(RuntimeEnvironment.getApplication(), scope, 250)
            val artwork = loader.loadBitmap(uri).get(5, TimeUnit.SECONDS)

            assertNotEquals(0, minOf(artwork.width, artwork.height))
            assertTrue(maxOf(artwork.width, artwork.height) <= 250)
            coVerify(exactly = 1) {
                ImageCacheFactory.loadBitmap(uri.toString(), requestSize = 250, cacheDecodedBitmap = false)
            }
        } finally {
            scope.cancel()
            unmockkObject(ImageCacheFactory)
        }
    }
}
