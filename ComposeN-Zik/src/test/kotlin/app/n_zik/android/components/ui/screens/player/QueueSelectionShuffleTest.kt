package app.n_zik.android.components.ui.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(UnstableApi::class)
class QueueSelectionShuffleTest {
    @Test
    fun `shuffling selected queue entries keeps all other entries in their slots`() {
        val queue = ('a'..'h').toMutableList()
        val selectedIndices = listOf(1, 2, 5, 7)

        shuffleQueueSelection(selectedIndices, listOf(7, 1, 2, 5)) { from, to ->
            queue.add(to, queue.removeAt(from))
        }

        assertEquals(listOf('a', 'h', 'b', 'd', 'e', 'c', 'g', 'f'), queue)
    }

    @Test
    fun `queue mutations resolve checked occurrences after reorder without touching a duplicate`() {
        val sharedTrack = MediaItem.Builder().setMediaId("repeat").build()
        val differentTrack = MediaItem.Builder().setMediaId("different").build()
        val windows = listOf(
            window("unchecked-occurrence", sharedTrack),
            window("different-occurrence", differentTrack),
            window("checked-occurrence", sharedTrack),
        )
        val timeline = mockk<Timeline> {
            every { windowCount } returns windows.size
            every { getWindow(any(), any()) } answers { windows[firstArg()] }
        }

        val selected = selectedQueueIndices(
            timeline,
            setOf("checked-occurrence", "different-occurrence", "removed-occurrence"),
        )
        assertEquals(listOf(1, 2), selected)

        val shuffled = windows.map { it.uid }.toMutableList()
        shuffleQueueSelection(selected, selected.reversed()) { from, to ->
            shuffled.add(to, shuffled.removeAt(from))
        }
        assertEquals(listOf("unchecked-occurrence", "checked-occurrence", "different-occurrence"), shuffled)

        val remaining = windows.map { it.uid }.toMutableList()
        selected.asReversed().forEach { remaining.removeAt(it) }
        assertEquals(listOf("unchecked-occurrence"), remaining)
    }

    private fun window(uid: String, item: MediaItem) = Timeline.Window().apply {
        this.uid = uid
        mediaItem = item
    }
}
