package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.models.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoQueueDurationEnricherTest {
    // runBlocking bridges the synchronous JUnit entry point to the suspending DATA lookup.
    @Test fun `missing duration is filled without changing item identity order or existing lengths`() = runBlocking {
        val stale = item("same")
        val known = item("same", 125_000L)
        val text = item("text", text = "4:00")
        val calls = mutableListOf<List<String>>()
        val result = enrichAutoQueueDurations(listOf(stale, known, text, stale)) { ids ->
            calls += ids
            listOf(Song("same", "Changed database title", durationText = "3:35", thumbnailUrl = null))
        }
        assertEquals(listOf(listOf("same")), calls)
        val expected = stale.buildUpon().setMediaMetadata(stale.mediaMetadata.buildUpon().setDurationMs(215_000L).build()).build()
        assertEquals(listOf(expected, known, text, expected), result)
        assertSame(known, result[1])
        assertSame(text, result[2])
    }

    // runBlocking bridges the synchronous JUnit entry point to the suspending DATA lookup.
    @Test fun `absent or malformed stored lengths stay unknown`() = runBlocking {
        val items = listOf(item("missing"), item("invalid"))
        val result = enrichAutoQueueDurations(items) {
            listOf(Song("invalid", "Invalid", durationText = "LIVE", thumbnailUrl = null))
        }
        assertSame(items[0], result[0])
        assertSame(items[1], result[1])
    }

    // runBlocking bridges the synchronous JUnit entry point to the suspending DATA lookup.
    @Test fun `long queues use bounded batches of distinct IDs`() = runBlocking {
        val items = (0 until 1801).map { item("track-$it") }
        val sizes = mutableListOf<Int>()
        val result = enrichAutoQueueDurations(items + items.first()) { ids ->
            sizes += ids.size
            ids.map { Song(it, it, durationText = "3:00", thumbnailUrl = null) }
        }
        assertEquals(listOf(900, 900, 1), sizes)
        assertEquals((items + items.first()).map { it.mediaId }, result.map { it.mediaId })
        assertEquals(listOf(180_000L), result.map { it.mediaMetadata.durationMs }.distinct())
    }

    // runBlocking bridges the synchronous JUnit entry point to the suspending DATA lookup.
    @Test fun `optional database failure preserves the playable queue`() = runBlocking {
        val items = listOf(item("track"))
        assertSame(items, enrichAutoQueueDurations(items) { throw IllegalStateException("database unavailable") })
    }

    @Test fun `cancellation is never converted into successful metadata fallback`() {
        assertThrows(CancellationException::class.java) {
            // runBlocking is required by Assert.assertThrows' synchronous callback.
            runBlocking { enrichAutoQueueDurations(listOf(item("track"))) { throw CancellationException("superseded") } }
        }
    }

    private fun item(id: String, duration: Long? = null, text: String? = null) = MediaItem.Builder()
        .setMediaId(id).setUri("https://example.invalid/$id").setCustomCacheKey(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Browsed title").setArtist("Browsed artist")
            .setDurationMs(duration).setExtras(Bundle().apply { putString("durationText", text); putString("kept", "value") })
            .setIsPlayable(true).build()).build()
}
