package app.n_zik.android.components.dialog.song

import android.net.Uri
import app.n_zik.android.models.Song
import io.mockk.mockk
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataSaveRequestTest {
    private fun request() = MetadataSaveRequest(
        Song("local:123", "Original", "Artist", "1:00", null),
        "profile-one", 7, "original.mp3", emptyList(), null
    )

    @Test
    fun `completed write can only publish to its captured song profile and generation`() {
        val request = request()
        assertTrue(request.matches(true, 7, "local:123", "profile-one"))
        assertFalse(request.matches(true, 8, "local:123", "profile-one"))
        assertFalse(request.matches(true, 7, "local:456", "profile-one"))
        assertFalse(request.matches(true, 7, "local:123", "profile-two"))
        assertFalse(request.matches(false, 7, "local:123", "profile-one"))
    }

    @Test
    fun `dialog cancellation cannot delete a temporary file already owned by writing worker`() {
        val file = Files.createTempFile("metadata-write", ".mp3").toFile().apply { writeText("complete edited audio") }
        try {
            val store = PendingMetadataWriteStore()
            store.put(PendingMetadataWrite(request(), file, mockk<Uri>()))
            val worker = requireNotNull(store.take())
            store.discard()
            assertTrue(worker.file.exists())
            assertTrue(worker.file.readText() == "complete edited audio")
        } finally { file.delete() }
    }

    @Test
    fun `permission cancellation deletes the pending file not yet transferred to worker`() {
        val file = Files.createTempFile("metadata-pending", ".mp3").toFile()
        val store = PendingMetadataWriteStore()
        store.put(PendingMetadataWrite(request(), file, mockk<Uri>()))
        store.discard()
        assertFalse(file.exists())
        assertFalse(store.hasPending())
    }
}
