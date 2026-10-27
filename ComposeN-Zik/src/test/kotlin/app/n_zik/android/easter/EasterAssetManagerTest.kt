package app.n_zik.android.easter

import app.n_zik.android.BuildConfig
import app.n_zik.android.updater.models.UpdateEndpoints
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * JVM tests of the [EasterAssetManager] view-time sync against the
 * spec-remote-easter-eggs I/O matrix: temp dir + canned OkHttp responses
 * (the ArtworkRelayTest fake-client idiom, no network).
 *
 * JUnit 5 test methods cannot be suspend, so every test drives the suspend
 * [EasterAssetManager.sync] synchronously via `runBlocking` (same convention
 * as the Room DAO tests).
 */
class EasterAssetManagerTest {

    @TempDir
    lateinit var filesDir: File

    private val requests = mutableListOf<Request>()

    // Getters (not vals): filesDir is @TempDir-injected after construction
    private val easterDir: File get() = File(filesDir, "easter")
    private val image: File get() = File(easterDir, "easter1.jpg")
    private val etagSidecar: File get() = File(easterDir, "easter1.jpg.etag")

    private val oldBody = byteArrayOf(1, 2, 3)
    private val newBody = byteArrayOf(4, 5, 6)

    @BeforeEach
    fun setUp() {
        requests.clear()
    }

    /** OkHttp client whose every call is answered by [answer], without any network. */
    private fun buildManager(answer: (Request) -> Response): EasterAssetManager {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                answer(request)
            }
            .build()
        return EasterAssetManager(filesDir) { client }
    }

    private fun respond(request: Request, code: Int, body: ByteArray? = null, etag: String? = null): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("canned")
        etag?.let { builder.header("ETag", it) }
        if (code != 304) {
            builder.body((body ?: ByteArray(0)).toResponseBody("image/jpeg".toMediaType()))
        }
        return builder.build()
    }

    private fun seed(body: ByteArray, etag: String? = null) {
        image.parentFile?.mkdirs()
        image.writeBytes(body)
        etag?.let { etagSidecar.writeText(it) }
    }

    @Test
    fun `first open online downloads the image with its ETag sidecar`() = runBlocking {
        val manager = buildManager { req -> respond(req, 200, oldBody, etag = "etag-A") }

        val result = manager.sync(EasterEgg.PACMAN)

        assertEquals(image, result)
        assertArrayEquals(oldBody, image.readBytes())
        assertEquals("etag-A", etagSidecar.readText().trim())
        assertEquals(
            "https://raw.githubusercontent.com/N-Zik-Group/${BuildConfig.APP_NAME}/main/assets/easter/easter1.jpg",
            requests.single().url.toString()
        )
        // No temp-file residue
        assertTrue(easterDir.listFiles().orEmpty().none { it.name.endsWith(".download") })
    }

    @Test
    fun `first open offline writes nothing and returns null`() = runBlocking {
        val manager = buildManager { throw IOException("offline") }

        val result = manager.sync(EasterEgg.PACMAN)

        assertNull(result)
        assertFalse(image.exists())
        assertFalse(etagSidecar.exists())
    }

    @Test
    fun `the offline gate (503) with no local file returns null and writes nothing`() = runBlocking {
        val manager = buildManager { req -> respond(req, 503, "no network".encodeToByteArray()) }

        val result = manager.sync(EasterEgg.PACMAN)

        assertNull(result)
        assertFalse(image.exists())
        assertFalse(etagSidecar.exists())
    }

    @Test
    fun `the offline gate (503) leaves an existing file usable and untouched`() = runBlocking {
        seed(oldBody, etag = "etag-A")
        val manager = buildManager { req -> respond(req, 503, "no network".encodeToByteArray()) }

        val result = manager.sync(EasterEgg.PACMAN)

        assertEquals(image, result)
        assertArrayEquals(oldBody, image.readBytes())
        assertEquals("etag-A", etagSidecar.readText().trim())
    }

    @Test
    fun `a genuinely unexpected status with no local file returns null and writes nothing`() = runBlocking {
        val manager = buildManager { req -> respond(req, 404, "gone".encodeToByteArray()) }

        val result = manager.sync(EasterEgg.PACMAN)

        assertNull(result)
        assertFalse(image.exists())
        assertFalse(etagSidecar.exists())
    }

    @Test
    fun `a saved ETag triggers a conditional request and a 304 rewrites nothing`() = runBlocking {
        seed(oldBody, etag = "etag-A")
        val manager = buildManager { req ->
            assertEquals("etag-A", req.header("If-None-Match"))
            respond(req, 304)
        }

        val result = manager.sync(EasterEgg.PACMAN)

        assertEquals(image, result)
        assertArrayEquals(oldBody, image.readBytes())
        assertEquals("etag-A", etagSidecar.readText().trim())
        assertEquals(1, requests.size)
    }

    @Test
    fun `a remote change replaces the file and the sidecar`() = runBlocking {
        seed(oldBody, etag = "etag-A")
        val manager = buildManager { req ->
            assertEquals("etag-A", req.header("If-None-Match"))
            respond(req, 200, newBody, etag = "etag-B")
        }

        val result = manager.sync(EasterEgg.PACMAN)

        assertEquals(image, result)
        assertArrayEquals(newBody, image.readBytes())
        assertEquals("etag-B", etagSidecar.readText().trim())
    }

    @Test
    fun `a 200 with the saved ETag is treated as unchanged`() = runBlocking {
        // OkHttp response cache re-serve: a 200 carrying the SAME ETag within the freshness window
        seed(oldBody, etag = "etag-A")
        val manager = buildManager { req -> respond(req, 200, newBody, etag = "etag-A") }

        val result = manager.sync(EasterEgg.PACMAN)

        assertEquals(image, result)
        assertArrayEquals(oldBody, image.readBytes())
        assertEquals("etag-A", etagSidecar.readText().trim())
    }

    @Test
    fun `a 200 without an ETag header rewrites the file but not the sidecar`() = runBlocking {
        seed(oldBody, etag = "etag-A")
        val manager = buildManager { req -> respond(req, 200, newBody) }

        manager.sync(EasterEgg.PACMAN)

        assertArrayEquals(newBody, image.readBytes())
        assertEquals("etag-A", etagSidecar.readText().trim())
    }

    @Test
    fun `a first open 200 without an ETag header writes the file and no sidecar`() = runBlocking {
        val manager = buildManager { req -> respond(req, 200, oldBody) }

        val result = manager.sync(EasterEgg.PACMAN)

        assertEquals(image, result)
        assertArrayEquals(oldBody, image.readBytes())
        assertFalse(etagSidecar.exists())
    }

    @Test
    fun `a failed download leaves the existing file usable`() = runBlocking {
        seed(oldBody)
        val manager = buildManager { throw IOException("offline") }

        val result = manager.sync(EasterEgg.PACMAN)

        assertEquals(image, result)
        assertArrayEquals(oldBody, image.readBytes())
    }

    // Suspend-based latch wait: a blocking CountDownLatch.await() would stall the
    // runBlocking event loop and starve the very coroutines it waits for
    private suspend fun CountDownLatch.awaitSuspended() {
        while (count > 0) delay(10)
    }

    @Test
    fun `concurrent opens deduplicate to a single transfer`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val manager = buildManager { req ->
            started.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
            respond(req, 200, oldBody, etag = "etag-A")
        }

        val first = async { manager.sync(EasterEgg.PACMAN) }
        withTimeout(10_000) { started.awaitSuspended() }
        val second = async { manager.sync(EasterEgg.PACMAN) }
        release.countDown()

        assertEquals(image, first.await())
        assertEquals(image, second.await())
        assertEquals(1, requests.size)
    }

    @Test
    fun `re-entering the screen during a download does not start a second transfer`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = buildManager { req ->
            started.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
            respond(req, 200, oldBody, etag = "etag-A")
        }
        // A fresh manager stands in for the re-entered screen (new composition)
        val second = buildManager { req -> respond(req, 200, oldBody, etag = "etag-A") }

        val firstOpen = async { first.sync(EasterEgg.PACMAN) }
        withTimeout(10_000) { started.awaitSuspended() }
        val secondOpen = async { second.sync(EasterEgg.PACMAN) }
        release.countDown()

        assertEquals(image, firstOpen.await())
        assertEquals(image, secondOpen.await())
        // Both managers shared the single in-flight transfer
        assertEquals(1, requests.size)
    }

    @Test
    fun `leaving the screen mid download leaves the transfer intact`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val manager = buildManager { req ->
            started.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
            respond(req, 200, oldBody, etag = "etag-A")
        }

        // The awaiting coroutine is cancelled (screen closed) while the transfer is in flight
        val awaiter = launch { manager.sync(EasterEgg.PACMAN) }
        withTimeout(10_000) { started.awaitSuspended() }
        awaiter.cancel()

        release.countDown()
        val deadline = System.currentTimeMillis() + 10_000
        while (!image.exists()) {
            assertTrue(System.currentTimeMillis() < deadline, "file did not land after the transfer was released")
            yield()
        }

        assertArrayEquals(oldBody, image.readBytes())
        assertEquals("etag-A", etagSidecar.readText().trim())
    }

    @Test
    fun `a deleted image with a surviving ETag sidecar self-heals via a full download`() = runBlocking {
        // Only the sidecar survives (a cleanup removed the image but missed the .etag file):
        // the request must NOT be conditional, else a 304 would leave the image forever absent.
        etagSidecar.parentFile?.mkdirs()
        etagSidecar.writeText("etag-A")
        val manager = buildManager { req ->
            assertNull(req.header("If-None-Match"))
            respond(req, 200, oldBody, etag = "etag-A")
        }

        val result = manager.sync(EasterEgg.PACMAN)

        assertEquals(image, result)
        assertArrayEquals(oldBody, image.readBytes())
        assertEquals("etag-A", etagSidecar.readText().trim())
    }

    @Test
    fun `two different eggs sync independently (no cross-egg deduplication)`() = runBlocking {
        val manager = buildManager { req -> respond(req, 200, oldBody, etag = "etag-A") }
        val pacmanImage = File(easterDir, "easter1.jpg")
        val snakeImage = File(easterDir, "easter2.jpg")

        val pacman = async { manager.sync(EasterEgg.PACMAN) }
        val snake = async { manager.sync(EasterEgg.SNAKE) }

        assertEquals(pacmanImage, pacman.await())
        assertEquals(snakeImage, snake.await())
        // The dedup key is the image path, so two distinct eggs never merge into one transfer.
        assertEquals(2, requests.size)
        assertTrue(pacmanImage.exists())
        assertTrue(snakeImage.exists())
    }

    @Test
    fun `each egg maps to its own raw asset under the shared repo`() {
        // Derived from UpdateEndpoints.REPO (the single source of owner/repo), so a rename
        // there cannot silently desync an egg's download URL.
        assertEquals(
            "https://raw.githubusercontent.com/${UpdateEndpoints.REPO}/main/assets/easter/easter1.jpg",
            EasterEgg.PACMAN.rawUrl
        )
        assertEquals(
            "https://raw.githubusercontent.com/${UpdateEndpoints.REPO}/main/assets/easter/easter2.jpg",
            EasterEgg.SNAKE.rawUrl
        )
        assertEquals("easter1.jpg", EasterEgg.PACMAN.fileName)
        assertEquals("easter2.jpg", EasterEgg.SNAKE.fileName)
        // The two eggs must never share an asset (a swap would serve the wrong image).
        assertNotEquals(EasterEgg.PACMAN.fileName, EasterEgg.SNAKE.fileName)
    }
}
