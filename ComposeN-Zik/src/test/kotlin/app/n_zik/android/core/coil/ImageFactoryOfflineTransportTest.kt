package app.n_zik.android.core.coil

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.ConnectivityChecker
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

import app.n_zik.android.appContext

/** Verifies the factory's cache-only request against real Coil and OkHttp transport behavior. */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageFactoryOfflineTransportTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val transportRequests = AtomicInteger()
    private val serverExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "artwork-test-http").apply { isDaemon = true }
    }
    private lateinit var server: HttpServer
    private lateinit var client: OkHttpClient
    private lateinit var loader: ImageLoader
    private lateinit var diskCache: DiskCache
    private val loadedBitmaps = mutableListOf<Bitmap>()
    private val loadedUrls = mutableListOf<String>()

    @Before fun setUp() {
        val source = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        val png = try {
            source.eraseColor(Color.MAGENTA)
            ByteArrayOutputStream().use { output ->
                check(source.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { source.recycle() }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = serverExecutor
            createContext("/") { exchange ->
                transportRequests.incrementAndGet()
                exchange.responseHeaders.set("Content-Type", "image/png")
                exchange.responseHeaders.set("Cache-Control", "public, max-age=3600")
                exchange.sendResponseHeaders(200, png.size.toLong())
                exchange.responseBody.use { it.write(png) }
                exchange.close()
            }
            start()
        }
        client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
        diskCache = DiskCache.Builder()
            .directory(temporaryFolder.newFolder("coil").toOkioPath())
            .maxSizeBytes(1024 * 1024)
            .build()
        loader = ImageLoader.Builder(context)
            // Every warm hit must read actual encoded pixels from Coil's disk cache.
            .memoryCache(null)
            .diskCache(diskCache)
            .components {
                add(OkHttpNetworkFetcherFactory(
                    callFactory = { client },
                    // Keep connectivity online: only the real request's cacheOnly policy may block I/O.
                    connectivityChecker = { ConnectivityChecker.ONLINE },
                ))
            }
            .build()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        every { ImageCacheFactory.getDownloadDecision(any()) } returns ImageCacheFactory.DownloadDecision(
            useNetwork = true,
            quality = ImageCacheFactory.NetworkQuality.HIGH,
        )
    }

    @After fun tearDown() {
        if (::loader.isInitialized) loader.shutdown()
        if (::diskCache.isInitialized) diskCache.shutdown()
        if (::client.isInitialized) {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
        if (::server.isInitialized) server.stop(0)
        serverExecutor.shutdownNow()
        loadedBitmaps.distinct().forEach { if (!it.isRecycled) it.recycle() }
        context.getSharedPreferences("image_cache_metadata", 0).edit().apply {
            loadedUrls.forEach { remove(it.hashCode().toString()) }
        }.commit()
        unmockkAll()
    }

    @Test fun `warm encoded artwork remains readable offline without another transport request`() {
        // JUnit is synchronous; bridge to the real suspend factory without replacing its request.
        runBlocking {
            val url = url("warm")
            assertPixels(load(url, cacheOnly = false))
            assertEquals(1, transportRequests.get())
            assertPixels(load(url, cacheOnly = true))
            assertEquals("Warm offline lookup must not reach HTTP transport", 1, transportRequests.get())
        }
    }

    @Test fun `cold offline artwork miss issues zero transport requests while online can fetch it`() {
        // JUnit is synchronous; bridge to the real suspend factory without replacing its request.
        runBlocking {
            val url = url("cold")
            val offline = load(url, cacheOnly = true)
            assertEquals("Cold offline lookup must not reach HTTP transport", 0, transportRequests.get())
            assertNull(offline)
            assertPixels(load(url, cacheOnly = false))
            assertEquals("The same cold source must be fetchable online", 1, transportRequests.get())
        }
    }

    private fun url(name: String): String = "http://127.0.0.1:${server.address.port}/$name.png".also {
        loadedUrls += it
    }

    private suspend fun load(url: String, cacheOnly: Boolean): Bitmap? =
        ImageCacheFactory.loadBitmap(
            url,
            requestSize = 512,
            qualityOverride = ImageCacheFactory.NetworkQuality.HIGH,
            cacheOnly = cacheOnly,
        )?.also { loadedBitmaps += it }

    private fun assertPixels(bitmap: Bitmap?) {
        val pixels = requireNotNull(bitmap) { "Expected actual decoded artwork pixels" }
        // The unchanged production request decodes toward 512px; Coil may upscale the fixture.
        assertEquals(512, pixels.width)
        assertTrue("The decoded source keeps its 3:2 ratio", abs(pixels.height - 512f * 16 / 24) <= 1f)
        assertEquals(Color.MAGENTA, pixels.getPixel(pixels.width / 2, pixels.height / 2))
    }
}
