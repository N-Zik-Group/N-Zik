package app.n_zik.android.utils.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.n_zik.android.utils.excludeMediaItems
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MenuQueueEditsTest {
    @Test
    fun `play next preserves the current occurrence and replaces only queued duplicates`() {
        val plan = planMenuPlayNext(
            currentIndex = 1,
            queueIds = listOf("previous", "current", "next", "current", "tail"),
            nextIds = listOf("current", "previous", "new"),
            shuffledIndices = emptyList(),
        )

        assertEquals(listOf(3, 0), plan.removals)
        assertEquals(1, plan.insertionIndex)
    }

    @Test
    fun `play next schedules the requested order immediately after current in shuffle`() {
        val plan = planMenuPlayNext(
            currentIndex = 1,
            queueIds = listOf("previous", "current", "next", "current", "tail"),
            nextIds = listOf("current", "previous", "new"),
            shuffledIndices = listOf(4, 1, 0, 3, 2),
        )

        assertArrayEquals(intArrayOf(5, 0, 1, 2, 3, 4), plan.shuffleOrder)
    }

    @Test
    fun `clear during filtering invalidates an already captured menu request`() = runBlocking {
        val player = mockk<Player>(relaxed = true)
        val context = mockk<Context>()
        val songs = listOf(MediaItem.Builder().setMediaId("track").build())
        val filterStarted = CompletableDeferred<Unit>()
        val filterCanFinish = CompletableDeferred<Unit>()
        val edits = MenuQueueEdits({ player }, setShuffleOrder = {})
        mockkStatic("app.n_zik.android.utils.PlayerKt")
        try {
            every { player.excludeMediaItems(songs, context) } answers {
                filterStarted.complete(Unit)
                runBlocking { withTimeout(5_000) { filterCanFinish.await() } }
                songs
            }
            val request = checkNotNull(edits.capture())
            val result = async { request.queue(songs, context, playNext = true) }
            withTimeout(5_000) { filterStarted.await() }
            edits.invalidate()
            filterCanFinish.complete(Unit)

            assertFalse(result.await())
            verify(exactly = 0) { player.setMediaItems(any()) }
        } finally {
            filterCanFinish.complete(Unit)
            unmockkStatic("app.n_zik.android.utils.PlayerKt")
        }
    }
}
