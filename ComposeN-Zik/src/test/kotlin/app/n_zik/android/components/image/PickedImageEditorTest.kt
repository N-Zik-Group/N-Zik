package app.n_zik.android.components.image

import java.io.File
import java.io.IOException
import java.util.UUID
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.Timeout
import okio.buffer

@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PickedImageEditorTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test
    fun `editor opens only its own activity without exposing image URIs`() {
        val id = UUID.randomUUID().toString()
        val intent = PickedImageEditor.editIntent(context, id)
        assertEquals(PickedImageEditorActivity::class.java.name, intent.component?.className)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(id, intent.getStringExtra(PickedImageEditor.SESSION_EXTRA))
        assertEquals(null, intent.action)
        assertEquals(null, intent.data)
        assertEquals(null, intent.clipData)
        assertEquals(0, intent.flags)
    }

    @Test
    fun `cancelling an isolated copy removes temporary files and preserves gallery image`() {
        // The synchronous JUnit entry point waits for the actual DATA dispatcher.
        runBlocking {
            val original = imageFile("gallery-original.png", Color.RED)
            val originalBytes = original.readBytes()
            val folder = editDirectory()
            withContext(NzikDispatchers.DATA) {
                val copied = PickedImageEditor.copyImage(context, Uri.fromFile(original), folder, "input")
                val session = PickedImageEditor.EditSession(folder.name, context, copied.first, copied.second)
                assertNotEquals(original.canonicalFile, session.source.canonicalFile)
                assertArrayEquals(originalBytes, session.source.readBytes())
                assertFalse(session.result.isCompleted)
                session.result.complete(null)
                PickedImageEditor.discard(context, folder.name)
            }
            assertFalse(folder.exists())
            assertArrayEquals(originalBytes, original.readBytes())
            original.delete()
        }
    }

    @Test
    fun `confirmed crop is staged separately without modifying original or input`() {
        // The synchronous JUnit entry point waits for image work on the MEDIA dispatcher.
        runBlocking {
            val folder = editDirectory()
            val original = imageFile("gallery-source.png", Color.RED)
            val originalBytes = original.readBytes()
            try {
                val source = withContext(NzikDispatchers.DATA) {
                    PickedImageEditor.copyImage(context, Uri.fromFile(original), folder, "input")
                }
                val session = PickedImageEditor.EditSession(folder.name, context, source.first, source.second)
                // Simulate uCrop's completed output; the adapter validates and stages it.
                val crop = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                session.output.outputStream().use { assertTrue(crop.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                crop.recycle()
                val result = PickedImageEditor.prepareResult(session, Uri.fromFile(session.output))
                val actual = requireNotNull(BitmapFactory.decodeFile(result.file.absolutePath))
                try {
                    assertEquals(8, actual.width)
                    assertEquals(8, actual.height)
                    assertEquals(Color.RED, actual.getPixel(4, 4))
                } finally {
                    actual.recycle()
                }
                assertArrayEquals(originalBytes, original.readBytes())
                assertArrayEquals(originalBytes, source.first.readBytes())
                assertEquals(folder.canonicalFile, result.file.parentFile?.canonicalFile)
                assertNotEquals(original.canonicalFile, result.file.canonicalFile)
                assertNotEquals(source.first.canonicalFile, result.file.canonicalFile)
            } finally {
                PickedImageEditor.discard(context, folder.name)
                original.delete()
            }
        }
    }

    @Test
    fun `cancellation waits for library callback before removing its staging directory`() {
        // JUnit waits for the non-cancellable library export and its callback barrier.
        runBlocking {
            val folder = editDirectory()
            val input = File(folder, "input.png").apply { writeText("isolated") }
            val session = PickedImageEditor.EditSession(folder.name, context, input, "image/png")
            val entered = CompletableDeferred<Unit>()
            var exportFinished = false
            val release = CompletableDeferred<Unit>()
            session.exportFinished = CompletableDeferred()
            val exporter = launch(NzikDispatchers.MEDIA) {
                entered.complete(Unit)
                release.await()
                File(folder, "prepared.pending").writeText("last exporter write")
                exportFinished = true
                requireNotNull(session.exportFinished).complete(Unit)
            }
            entered.await()
            val cleanup = launch { PickedImageEditor.cancelAndDiscard(session) }
            delay(25)
            assertTrue(folder.exists())
            assertFalse(cleanup.isCompleted)
            release.complete(Unit)
            cleanup.join()
            exporter.join()
            assertTrue(exportFinished)
            assertFalse(folder.exists())
        }
    }

    @Test
    fun `library result cannot substitute original input or unrelated URI`() {
        // JUnit waits for URI validation on the named DATA dispatcher.
        runBlocking {
            val folder = editDirectory()
            val original = imageFile("foreign-result.png", Color.GREEN)
            val bytes = original.readBytes()
            try {
                val source = PickedImageEditor.copyImage(context, Uri.fromFile(original), folder, "input")
                val session = PickedImageEditor.EditSession(folder.name, context, source.first, source.second)
                for (uri in listOf(Uri.fromFile(original), Uri.fromFile(source.first), Uri.parse("content://foreign/image"))) {
                    var rejected = false
                    try { PickedImageEditor.prepareResult(session, uri) } catch (_: IllegalArgumentException) { rejected = true }
                    assertTrue(rejected)
                }
                assertFalse(File(folder, "prepared.png").exists())
                assertArrayEquals(bytes, original.readBytes())
            } finally {
                PickedImageEditor.discard(context, folder.name)
                original.delete()
            }
        }
    }

    @Test
    fun `invalid image data never produces a prepared image or leaves a partial file`() {
        // The synchronous JUnit entry point waits for validation on the DATA dispatcher.
        runBlocking {
            val folder = editDirectory()
            val original = File(context.cacheDir, "invalid-image.bin").apply { writeText("not an image") }
            try {
                var rejected = false
                withContext(NzikDispatchers.DATA) {
                    try {
                        PickedImageEditor.copyImage(context, Uri.fromFile(original), folder, "input")
                    } catch (_: IOException) {
                        rejected = true
                    }
                }
                assertTrue(rejected)
                assertTrue(folder.listFiles().orEmpty().isEmpty())
                assertEquals("not an image", original.readText())
            } finally {
                PickedImageEditor.discard(context, folder.name)
                original.delete()
            }
        }
    }

    @Test
    fun `remote source uses the shared client and validates the actual image type`() {
        val source = imageFile("remote-source.png", Color.RED)
        val bytes = source.readBytes()
        val folder = editDirectory()
        val uri = Uri.parse("https://example.com/cover")
        mockkObject(NetworkClientFactory)
        mockEmptyArtworkCache()
        try {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                assertEquals(uri.toString(), chain.request().url.toString())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bytes.toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            }.build()
            every { NetworkClientFactory.getClient() } returns client
            // JUnit waits for the same source copy used by the crop editor.
            runBlocking {
                val (file, mime) = withContext(NzikDispatchers.DATA) {
                    PickedImageEditor.copyImage(context, uri, folder, "input")
                }
                assertEquals("image/png", mime)
                assertArrayEquals(bytes, file.readBytes())
            }
        } finally {
            unmockkObject(NetworkClientFactory)
            unmockkObject(ImageCacheFactory)
            PickedImageEditor.discard(context, folder.name)
            source.delete()
        }
    }

    @Test
    fun `cached fallback cover preserves encoded bytes without transport`() {
        val source = imageFile("cached-cover.png", Color.GREEN)
        val bytes = source.readBytes()
        val cacheDir = File(context.cacheDir, UUID.randomUUID().toString())
        val cache = DiskCache.Builder().directory(cacheDir.toOkioPath()).maxSizeBytes(1024 * 1024).build()
        val folder = editDirectory()
        val uri = Uri.parse("https://i.ytimg.com/vi/example/maxresdefault.jpg")
        val fallback = "https://i.ytimg.com/vi/example/hqdefault.jpg"
        val key = ImageCacheFactory.generateCacheKeySync(fallback, ImageCacheFactory.NetworkQuality.LOW)
        val entry = requireNotNull(cache.openEditor(key))
        cache.fileSystem.write(entry.data) { write(bytes) }
        cache.fileSystem.write(entry.metadata) { writeUtf8("fixture") }
        entry.commit()
        mockkObject(ImageCacheFactory, NetworkClientFactory)
        every { ImageCacheFactory.getDiskCache() } returns cache
        every { NetworkClientFactory.getClient() } throws AssertionError("Cache hit must not fetch")
        try {
            // The synchronous JUnit entry point waits for the DATA dispatcher.
            runBlocking {
                val (file, mimeType) = withContext(NzikDispatchers.DATA) {
                    PickedImageEditor.copyImage(context, uri, folder, "input")
                }
                assertEquals("image/png", mimeType)
                assertArrayEquals(bytes, file.readBytes())
            }
        } finally {
            unmockkObject(NetworkClientFactory, ImageCacheFactory)
            PickedImageEditor.discard(context, folder.name)
            cache.shutdown()
            cacheDir.deleteRecursively()
            source.delete()
        }
    }

    @Test
    fun `cancelling a remote copy cancels its blocked response body`() {
        val folder = editDirectory()
        val reading = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Boolean>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val call = chain.call()
            val body = object : ResponseBody() {
                private val input = object : Source {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        reading.complete(Unit)
                        val deadline = System.nanoTime() + 5_000_000_000L
                        while (!call.isCanceled() && System.nanoTime() < deadline) Thread.sleep(5)
                        cancelled.complete(call.isCanceled())
                        throw IOException("Stopped test body")
                    }

                    override fun timeout(): Timeout = Timeout.NONE
                    override fun close() = Unit
                }.buffer()

                override fun contentType() = "image/png".toMediaType()
                override fun contentLength() = -1L
                override fun source() = input
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build()
        }.build()
        mockkObject(NetworkClientFactory)
        mockEmptyArtworkCache()
        every { NetworkClientFactory.getClient() } returns client
        try {
            // JUnit waits for the suspended copy to stop before inspecting its output directory.
            runBlocking {
                val copying = launch(NzikDispatchers.DATA) {
                    PickedImageEditor.copyImage(context, Uri.parse("https://example.com/blocked"), folder, "input")
                }
                withTimeout(10_000) {
                    reading.await()
                    copying.cancelAndJoin()
                    assertTrue(cancelled.await())
                }
                assertTrue(folder.listFiles().orEmpty().isEmpty())
            }
        } finally {
            unmockkObject(NetworkClientFactory, ImageCacheFactory)
            PickedImageEditor.discard(context, folder.name)
        }
    }

    private fun mockEmptyArtworkCache() {
        mockkObject(ImageCacheFactory)
        val cache = mockk<DiskCache>()
        every { cache.openSnapshot(any()) } returns null
        every { ImageCacheFactory.getDiskCache() } returns cache
    }

    @Test
    fun `startup sweep removes previous process images and preserves current editors and drafts`() {
        // The synchronous JUnit entry point waits for startup cleanup on the DATA dispatcher.
        runBlocking {
            fun scopedDirectory() = PickedImageEditor.directory(context, UUID.randomUUID().toString()).apply { mkdirs() }
            val abandonedEditor = scopedDirectory()
            val abandonedDraft = scopedDirectory()
            val liveEditor = scopedDirectory()
            val liveDraft = scopedDirectory()
            val legacy = editDirectory()
            val legacyInput = File(legacy, "input.png").apply { writeText("unattributed") }
            val oldInput = File(abandonedEditor, "input.png").apply { writeText("old editor") }
            val oldPrepared = File(abandonedDraft, "prepared.png").apply { writeText("old draft") }
            val currentInput = File(liveEditor, "input.png").apply { writeText("current editor") }
            val currentPrepared = File(liveDraft, "prepared.png").apply { writeText("current draft") }
            val session = PickedImageEditor.EditSession(liveEditor.name, context, currentInput, "image/png")
            val prepared = PreparedImage(currentPrepared, context)
            try {
                PickedImageEditor.cleanupOrphans(context)
                assertFalse(oldInput.exists())
                assertFalse(oldPrepared.exists())
                assertFalse(abandonedEditor.exists())
                assertFalse(abandonedDraft.exists())
                assertTrue(legacyInput.isFile)
                assertEquals("current editor", session.source.readText())
                assertEquals("current draft", prepared.file.readText())
            } finally {
                listOf(abandonedEditor, abandonedDraft, liveEditor, liveDraft, legacy).forEach {
                    PickedImageEditor.discard(context, it.name)
                }
            }
        }
    }

    @Test
    fun `discard rejects paths outside an editor session`() {
        val unrelated = File(context.cacheDir, "keep-image.png").apply { writeText("keep") }
        try {
            PickedImageEditor.discard(context, "../keep-image.png")
            assertEquals("keep", unrelated.readText())
        } finally {
            unrelated.delete()
        }
    }

    private fun editDirectory(): File = File(File(context.cacheDir, "image_edit"), UUID.randomUUID().toString()).apply { mkdirs() }

    private fun imageFile(name: String, color: Int): File {
        val bitmap = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return File(context.cacheDir, name).also { file ->
            try {
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
