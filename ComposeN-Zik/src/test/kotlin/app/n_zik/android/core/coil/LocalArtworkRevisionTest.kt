package app.n_zik.android.core.coil

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.n_zik.android.appContext
import app.n_zik.android.components.image.PosixAtomicFileShadow
import app.n_zik.android.components.image.saveOwnedImage
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import app.n_zik.android.utils.coroutines.NzikDispatchers
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.toBitmap
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, shadows = [PosixAtomicFileShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalArtworkRevisionTest {
    @get:Rule val compose = createComposeRule()
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var loader: ImageLoader
    private val requestRevisions = CopyOnWriteArrayList<Long>()

    @Before
    fun setup() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        loader = ImageLoader.Builder(context)
            .allowHardware(false)
            .diskCache(null)
            .memoryCache(MemoryCache.Builder().maxSizeBytes(1024 * 1024).build())
            .build()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        every { ImageCacheFactory.getDownloadDecision(any()) } returns
            ImageCacheFactory.DownloadDecision(false, ImageCacheFactory.NetworkQuality.HIGH)
        every { ImageCacheFactory.generateCacheKeySync(any(), any()) } answers {
            requestRevisions += ArtworkRevisions.current()
            callOriginal()
        }
    }

    @After
    fun cleanup() {
        loader.shutdown()
        unmockkAll()
    }

    @Test
    fun `repeated confirmed save refreshes mounted image pixels without changing permanent file identity`() {
        val source = File(context.cacheDir, "revision-input.png")
        val target = File(context.filesDir, "revision-test/cover.jpg")
        fun save(color: Int): Uri? {
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).let { bitmap ->
                bitmap.eraseColor(color)
                source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            // The synchronous JUnit entry point waits for the real off-main persistence boundary.
            return runBlocking {
                withContext(NzikDispatchers.DATA) {
                    saveOwnedImage(context, Uri.fromFile(source), "revision-test", target.name)
                }
            }
        }
        val firstUri = save(Color.RED)
        assertNotNull(firstUri)
        val successes = CopyOnWriteArrayList<Pair<String?, Int>>()
        val failures = CopyOnWriteArrayList<Throwable>()
        val loadingRequests = AtomicInteger()
        val lifecycle = AtomicReference("not composed")
        // Match Android's file:/// form; file:/C:/ lets Coil parse the host drive colon as a scheme.
        val stableModel = "file://${target.toURI().rawPath}"
        fun awaitPixels(label: String, matches: (Int) -> Boolean) {
            fun diagnostic() = "$label: model=$stableModel, loading=${loadingRequests.get()}, " +
                "pixels=${successes.map { it.first to Integer.toHexString(it.second) }}, " +
                "errors=${failures.map { it.stackTraceToString() }}, fileBytes=${target.length()}, " +
                "lifecycle=${lifecycle.get()}, currentRevision=${ArtworkRevisions.current()}, requestRevisions=$requestRevisions"
            try {
                compose.waitUntil(10_000) {
                    // Background StateFlow collection posts snapshot work to this separate paused looper.
                    shadowOf(Looper.getMainLooper()).idle()
                    failures.isNotEmpty() || successes.any { matches(it.second) }
                }
            } catch (timeout: ComposeTimeoutException) {
                throw AssertionError(diagnostic(), timeout)
            }
            assertTrue(diagnostic(), failures.isEmpty() && successes.any { matches(it.second) })
        }
        compose.setContent {
            val owner = LocalLifecycleOwner.current
            SideEffect { lifecycle.set(owner.lifecycle.currentState.toString()) }
            ImageCacheFactory.AsyncImage(
                thumbnailUrl = stableModel,
                shape = null,
                modifier = Modifier.size(48.dp),
                onLoading = { loadingRequests.incrementAndGet() },
                onError = { failures += it.result.throwable },
                onSuccess = { state ->
                    successes += state.result.request.memoryCacheKey to state.result.image.toBitmap().getPixel(8, 8)
                }
            )
        }
        awaitPixels("Initial red cover") { Color.red(it) > 240 && Color.blue(it) < 15 }
        val firstKey = successes.last().first
        assertEquals(firstUri, save(Color.BLUE))
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        awaitPixels("Replacement blue cover") { Color.blue(it) > 240 && Color.red(it) < 15 }
        assertNotEquals(firstKey, successes.last().first)
        assertEquals(listOf(target.name), target.parentFile?.listFiles()?.map(File::getName))
    }

    @Test
    fun `local revision changes file content and absolute-path keys while remote keys stay stable`() {
        val sources = listOf("file:///data/user/0/app/files/cover.jpg", "content://media/external/images/123", File(context.filesDir, "cover.jpg").absolutePath)
        val quality = ImageCacheFactory.NetworkQuality.HIGH
        val localKeys = sources.map { ImageCacheFactory.generateCacheKeySync(it, quality) }
        val remote = "https://example.com/cover.jpg"
        val remoteKey = ImageCacheFactory.generateCacheKeySync(remote, quality)
        ArtworkRevisions.invalidate()
        sources.forEachIndexed { index, source ->
            assertNotEquals(localKeys[index], ImageCacheFactory.generateCacheKeySync(source, quality))
        }
        assertEquals(remoteKey, ImageCacheFactory.generateCacheKeySync(remote, quality))
    }
}
