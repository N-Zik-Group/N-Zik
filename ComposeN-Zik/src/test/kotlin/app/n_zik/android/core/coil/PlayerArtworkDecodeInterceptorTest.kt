package app.n_zik.android.core.coil

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Size
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerArtworkDecodeInterceptorTest {
    // The synchronous JUnit methods drive suspend interceptor calls in a single coroutine scope.
    @Test fun `matching player surfaces share pixels but each result keeps its request`() = runBlocking {
        val interceptor = PlayerArtworkDecodeInterceptor()
        val foreground = request()
        val background = request()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImage()
        val release = CompletableDeferred<Unit>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            interceptor.intercept(TestChain(foreground) {
                release.await()
                SuccessResult(bitmap, foreground, DataSource.DISK)
            })
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            interceptor.intercept(TestChain(background) { error("Duplicate decode") })
        }
        assertFalse(second.isCompleted)
        release.complete(Unit)

        assertSame(foreground, first.await().request)
        val backgroundResult = second.await() as SuccessResult
        assertSame(background, backgroundResult.request)
        assertSame(bitmap, backgroundResult.image)
        assertEquals(DataSource.MEMORY_CACHE, backgroundResult.dataSource)
    }

    // The synchronous JUnit method bridges to Coil's suspend intercept API.
    @Test fun `different profiles and requested sizes can load independently`() = runBlocking {
        val interceptor = PlayerArtworkDecodeInterceptor()
        val foreground = request()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImage()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            interceptor.intercept(TestChain(foreground) { awaitCancellation() })
        }
        try {
            val otherProfile = request(profile = "another")
            val otherSize = request()
            val otherProfileLoad = async(start = CoroutineStart.UNDISPATCHED) {
                interceptor.intercept(TestChain(otherProfile) { SuccessResult(bitmap, otherProfile) })
            }
            val otherSizeLoad = async(start = CoroutineStart.UNDISPATCHED) {
                interceptor.intercept(TestChain(otherSize, Size(200, 200)) { SuccessResult(bitmap, otherSize) })
            }
            assertTrue(otherProfileLoad.isCompleted)
            assertTrue(otherSizeLoad.isCompleted)
            assertEquals(DataSource.MEMORY, (otherProfileLoad.await() as SuccessResult).dataSource)
            assertEquals(DataSource.MEMORY, (otherSizeLoad.await() as SuccessResult).dataSource)
        } finally {
            first.cancelAndJoin()
        }
    }

    // The synchronous JUnit method bridges to Coil's suspend intercept API.
    @Test fun `a remaining waiter can decode after the owner is cancelled`() = runBlocking {
        val interceptor = PlayerArtworkDecodeInterceptor()
        val background = request()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImage()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            interceptor.intercept(TestChain(request()) { awaitCancellation() })
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            interceptor.intercept(TestChain(background) { SuccessResult(bitmap, background, DataSource.DISK) })
        }
        first.cancelAndJoin()

        val result = second.await() as SuccessResult
        assertSame(background, result.request)
        assertEquals(DataSource.DISK, result.dataSource)
    }

    private fun request(profile: String = "default") = ImageRequest.Builder(ApplicationProvider.getApplicationContext<Application>())
        .data("https://example.com/cover.png")
        .memoryCacheKey("thumbnail")
        .diskCacheKey("thumbnail")
        .apply { extras[PlayerArtworkDecodeScope.extra] = PlayerArtworkDecodeScope(profile, cacheEpoch = 0) }
        .build()

    private data class TestChain(
        override val request: ImageRequest,
        override val size: Size = Size(100, 100),
        val decode: suspend () -> ImageResult,
    ) : Interceptor.Chain {
        override fun withRequest(request: ImageRequest): Interceptor.Chain = copy(request = request)
        override fun withSize(size: Size): Interceptor.Chain = copy(size = size)
        override suspend fun proceed(): ImageResult = decode()
    }
}
