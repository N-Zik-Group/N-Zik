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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import app.n_zik.android.utils.coroutines.NzikDispatchers

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
    fun `startup sweep removes previous process images and preserves current editors and drafts`() {
        // The synchronous JUnit entry point waits for startup cleanup on the DATA dispatcher.
        runBlocking {
            val abandonedEditor = editDirectory()
            val abandonedDraft = editDirectory()
            val liveEditor = editDirectory()
            val liveDraft = editDirectory()
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
                assertEquals("current editor", session.source.readText())
                assertEquals("current draft", prepared.file.readText())
            } finally {
                listOf(abandonedEditor, abandonedDraft, liveEditor, liveDraft).forEach {
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
