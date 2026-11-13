package app.n_zik.android.components.menu.playlist

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import app.n_zik.android.models.Song
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PlaylistMenuThumbnailsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `open playlist switches between custom cover and current collage after artwork revisions`() = runBlocking {
        val file = directory.resolve("playlist_7").toFile()
        val song = Song("song", "Title", durationText = "1:00", thumbnailUrl = "first")
        val rows = MutableStateFlow(listOf(song))
        val revisions = MutableStateFlow(0L)
        val ownsProfile = AtomicBoolean(true)
        val lookupThread = AtomicLong()
        val collectorThread = Thread.currentThread().id
        val values = Channel<List<String>>(Channel.UNLIMITED)
        val job = launch {
            playlistMenuThumbnails(rows, revisions, ownsProfile::get) {
                lookupThread.set(Thread.currentThread().id)
                file.takeIf { it.exists() }?.toURI()?.toString()
            }.collect { values.send(it) }
        }
        try {
            assertEquals(listOf("first"), withTimeout(5000) { values.receive() })
            assertNotEquals(collectorThread, lookupThread.get())
            file.writeText("custom")
            revisions.value++
            assertEquals(listOf(file.toURI().toString()), withTimeout(5000) { values.receive() })
            file.delete()
            revisions.value++
            assertEquals(listOf("first"), withTimeout(5000) { values.receive() })
            rows.value = listOf(song.copy(thumbnailUrl = "new"))
            assertEquals(listOf("new"), withTimeout(5000) { values.receive() })
            ownsProfile.set(false)
            revisions.value++
            assertEquals(emptyList<String>(), withTimeout(5000) { values.receive() })
        } finally {
            job.cancelAndJoin()
            values.close()
        }
    }
}
