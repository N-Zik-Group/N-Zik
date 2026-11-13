package app.n_zik.android.components.dialog.tab

import app.n_zik.android.models.Song
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class DeleteSongFilesTest {

    private fun song(id: String) = Song(id = id, title = "t", durationText = null, thumbnailUrl = null)

    @Test
    fun `local song file deletion runs off the calling thread`() = runBlocking {
        val callerThread = Thread.currentThread()
        val deleteThreads = CopyOnWriteArrayList<Thread>()

        deleteSongFiles(song("local:42")) { deleteThreads += Thread.currentThread() }

        assertEquals(1, deleteThreads.size, "delete must be called exactly once")
        assertNotEquals(callerThread, deleteThreads.single(), "delete must not run on the caller (Main) thread")
    }

    @Test
    fun `non local song never touches files`() = runBlocking {
        val calls = CopyOnWriteArrayList<Song>()

        deleteSongFiles(song("dQw4w9WgXcQ")) { calls += it }

        assertTrue(calls.isEmpty(), "delete must not be called for a non local song")
    }
}
