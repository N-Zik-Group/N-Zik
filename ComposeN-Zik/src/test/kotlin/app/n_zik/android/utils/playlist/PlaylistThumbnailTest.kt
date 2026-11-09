package app.n_zik.android.utils.playlist

import android.app.Application
import android.content.Context
import java.io.File
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import app.n_zik.android.components.image.PosixAtomicFileShadow
import org.robolectric.annotation.Config
import io.mockk.just
import io.mockk.Runs
import android.net.Uri
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.components.image.PreparedImage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.Before
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, shadows = [PosixAtomicFileShadow::class])
class PlaylistThumbnailTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val picked = mockk<Uri>()
    private val editedUri = mockk<Uri>()
    private val edited = mockk<PreparedImage>(relaxed = true)

    @Before
    fun prepare() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(PickedImageEditor, ImageCacheFactory)
        every { ImageCacheFactory.clearCacheForKey(any(), any(), any()) } just Runs
        mockkStatic("app.it.fast4x.rimusic.utils.ProfilePreferencesKt")
        every { getActiveProfile(context) } returns "default"
        every { edited.uri } returns editedUri
        coEvery { PickedImageEditor.edit(context, picked) } returns edited
    }

    @After
    fun cleanup() { unmockkAll() }

    @Test
    fun `confirmed edited copy is saved off caller thread and closed`() = runBlocking {
        val caller = Thread.currentThread()
        val permanent = mockk<Uri>()
        val result = savePlaylistThumbnail(context, picked, "playlist_42") { _, uri, directory, name ->
            assertSame(editedUri, uri)
            assertEquals("thumbnail", directory)
            assertEquals("playlist_42", name)
            assertNotEquals(caller, Thread.currentThread())
            permanent
        }
        assertSame(permanent, result)
        verify(exactly = 1) { edited.close() }
    }

    @Test
    fun `editor cancellation never writes a thumbnail`() = runBlocking {
        coEvery { PickedImageEditor.edit(context, picked) } returns null
        assertNull(savePlaylistThumbnail(context, picked, "playlist_1") { _, _, _, _ -> error("must not save") })
    }

    @Test
    fun `profile change while editing discards result without writing`() = runBlocking {
        coEvery { PickedImageEditor.edit(context, picked) } answers {
            every { getActiveProfile(context) } returns "other"
            edited
        }
        assertNull(savePlaylistThumbnail(context, picked, "playlist_1") { _, _, _, _ -> error("must not save") })
        verify(exactly = 1) { edited.close() }
    }

    @Test
    fun `changed playlist while editor open cannot replace its thumbnail`() = runBlocking {
        var current = true
        coEvery { PickedImageEditor.edit(context, picked) } answers { current = false; edited }
        assertNull(savePlaylistThumbnail(context, picked, "playlist_1", isCurrentTarget = { current }) { _, _, _, _ -> error("wrong playlist") })
        verify(exactly = 1) { edited.close() }
    }

    @Test
    fun `failed save closes the temporary copy`() = runBlocking {
        assertNull(savePlaylistThumbnail(context, picked, "playlist_1") { _, _, _, _ -> null })
        verify(exactly = 1) { edited.close() }
    }

    @Test
    fun `target change during blocking save restores previous thumbnail and invalidates cached pixels`() = runBlocking {
        val target = File(context.filesDir, "thumbnail/playlist_1").apply { parentFile?.mkdirs(); writeText("old") }
        val previousRevision = ArtworkRevisions.current()
        var current = true
        val result = savePlaylistThumbnail(context, picked, "playlist_1", isCurrentTarget = { current }) { _, _, _, _ ->
            target.writeText("stale new")
            current = false
            Uri.fromFile(target)
        }
        assertNull(result)
        assertEquals("old", target.readText())
        assertTrue(ArtworkRevisions.current() > previousRevision)
        verify { ImageCacheFactory.clearCacheForKey(target.absolutePath, any(), any()) }
        verify(exactly = 1) { edited.close() }
    }

    @Test
    fun `profile change during save restores shared global thumbnail`() = runBlocking {
        val target = File(context.filesDir, "thumbnail/playlist_1").apply { parentFile?.mkdirs(); writeText("old") }
        assertNull(savePlaylistThumbnail(context, picked, "playlist_1") { _, _, _, _ ->
            target.writeText("wrong profile")
            every { getActiveProfile(context) } returns "other"
            Uri.fromFile(target)
        })
        assertEquals("old", target.readText())
    }

    @Test
    fun `cancellation inside blocking save rolls back before cleanup completes`() = runBlocking {
        val target = File(context.filesDir, "thumbnail/playlist_1").apply { parentFile?.mkdirs(); writeText("old") }
        val worker = launch {
            val job = requireNotNull(currentCoroutineContext()[Job])
            savePlaylistThumbnail(context, picked, "playlist_1") { _, _, _, _ ->
                target.writeText("cancelled new")
                job.cancel(CancellationException("screen disposed"))
                Uri.fromFile(target)
            }
        }
        worker.join()
        assertEquals("old", target.readText())
        assertTrue(worker.isCancelled)
        verify(exactly = 1) { edited.close() }
    }

    @Test
    fun `failed first save removes uncommitted thumbnail`() = runBlocking {
        val target = File(context.filesDir, "thumbnail/playlist_17")
        target.delete()
        assertNull(savePlaylistThumbnail(context, picked, "playlist_17") { _, _, _, _ ->
            target.parentFile?.mkdirs()
            target.writeText("partial")
            null
        })
        assertFalse(target.exists())
    }

    @Test
    fun `missing playlist identity cannot write shared null cover`() = runBlocking {
        assertNull(savePlaylistThumbnail(context, picked, "playlist_null") { _, _, _, _ -> error("must not save") })
    }
}
