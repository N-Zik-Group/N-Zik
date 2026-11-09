package app.n_zik.android.components.dialog.song

import java.io.File
import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.components.image.PreparedImage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EditMetadataCoverConfirmationTest {
    @Test
    fun `a confirmed link and subsequent crop reach the outer save without prefilling a link`() {
        val context = RuntimeEnvironment.getApplication()
        val remote = Uri.parse("https://example.com/cover.png")
        val original = byteArrayOf(1, 2, 3)
        val cropped = byteArrayOf(4, 5, 6)
        val remoteFile = File(context.cacheDir, "metadata-import.png").apply { writeBytes(original) }
        val cropFile = File(context.cacheDir, "metadata-crop.png").apply { writeBytes(cropped) }
        val imported = mockk<PreparedImage>(relaxed = true)
        val edited = mockk<PreparedImage>(relaxed = true)
        every { imported.uri } returns Uri.fromFile(remoteFile)
        every { edited.uri } returns Uri.fromFile(cropFile)

        mockkObject(PickedImageEditor)
        try {
            coEvery { PickedImageEditor.prepareSource(context, remote) } returns imported
            coEvery { PickedImageEditor.edit(context, match<ByteArray> { it.contentEquals(original) }) } returns edited

            // JUnit waits for the dialog jobs and their DATA dispatcher reads.
            runBlocking {
                val song = Song("local:123", "Title", "Artist", "1:00", null)
                val dialog = EditMetadataDialog(mutableStateOf(false), { song }, context, this)
                dialog.showDialog()
                val cover = dialog.coverDialog
                cover.showDialog()
                cover.value = TextFieldValue(remote.toString())
                assertNull(dialog.captureSaveRequest("song.mp3"))
                cover.onSet(cover.value.text)
                requireNotNull(coroutineContext[Job]).children.toList().joinAll()
                assertArrayEquals(original, dialog.captureSaveRequest("song.mp3")?.coverBytes)

                cover.showDialog()
                assertEquals("", cover.value.text)
                cover.editCurrentCover()
                requireNotNull(coroutineContext[Job]).children.toList().joinAll()
                cover.onSet(cover.value.text)
                requireNotNull(coroutineContext[Job]).children.toList().joinAll()
                assertArrayEquals(cropped, dialog.captureSaveRequest("song.mp3")?.coverBytes)
            }
        } finally {
            unmockkObject(PickedImageEditor)
            remoteFile.delete()
            cropFile.delete()
        }
    }
}
