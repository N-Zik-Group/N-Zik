package app.n_zik.android.components.ui.screens.home.quickpicks

import app.n_zik.android.models.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QuickPicksRecommendationSlotsTest {
    private val portraitSlots = visibleQuickPicksSlots(400f, 280f)
    private val history = List(9) { song("history-$it") }
    private val online = List(9) { song("online-$it") }

    @Test
    fun `placeholder count follows the viewport and the shared row count`() {
        assertEquals(6, portraitSlots)
        assertEquals(3, visibleQuickPicksSlots(270f, 280f))
        assertEquals(9, visibleQuickPicksSlots(900f, 337.5f))
    }

    @Test
    fun `history first leaves online slots open and preserves revealed titles`() {
        val first = slots(history = history, onlinePending = true)
        assertEquals(history.first(), first[0])
        assertNull(first[1])
        assertNull(first[3])
        assertNull(first[5])

        val completed = slots(first, history, online)
        (0 until portraitSlots step 2).forEach { assertEquals(first[it], completed[it]) }
        assertEquals(first.drop(portraitSlots), completed.drop(portraitSlots).take(first.size - portraitSlots))
        assertEquals(listOf(true, false, true, false, true, false), completed.take(6).map { it in history })
    }

    @Test
    fun `online first holds the ranked first slot for history`() {
        val first = slots(online = online, historyPending = true)
        assertNull(first[0])
        assertNull(first[2])
        assertNull(first[4])

        val completed = slots(first, history, online)
        assertEquals(history.first(), completed[0])
        (1 until portraitSlots step 2).forEach { assertEquals(first[it], completed[it]) }
    }

    @Test
    fun `duplicate suggestions update metadata without moving the visible title`() {
        val duplicate = online.first()
        val first = slots(online = listOf(duplicate), historyPending = true)
        val updated = duplicate.copy(title = "From listening history")
        val completed = slots(first, history + updated, online)
        assertEquals(updated, completed[1])
        assertEquals(1, completed.count { it?.id == duplicate.id })
    }

    @Test
    fun `empty or failed sources do not leave loading slots`() {
        val first = slots(online = online, historyPending = true)
        val noHistory = slots(first, online = online)
        assertEquals(online.toSet(), noHistory.toSet())
        assertFalse(null in noHistory)
        assertEquals(emptyList<Song>(), slots())
    }

    @Test
    fun `random mode mixes the sources without pinning the history song`() {
        val completed = slots(history = history, online = online, keepFirstHistory = false)
        assertEquals(listOf(false, true, false, true, false, true), completed.take(6).map { it in history })
    }

    @Test
    fun `expanding the viewport preserves the content already displayed`() {
        val first = slots(history = history, online = online)
        val expanded = slots(first, history, online, initialSlots = visibleQuickPicksSlots(900f, 337.5f))
        assertEquals(first, expanded)
    }

    private fun slots(
        previous: List<Song?> = emptyList(),
        history: List<Song> = emptyList(),
        online: List<Song> = emptyList(),
        historyPending: Boolean = false,
        onlinePending: Boolean = false,
        keepFirstHistory: Boolean = true,
        initialSlots: Int = portraitSlots,
    ) = fillQuickPicksRecommendationSlots(
        previous, history, online, historyPending, onlinePending,
        keepFirstHistorySong = keepFirstHistory,
        seed = 17,
        initialSlots = initialSlots,
    )

    private fun song(id: String) = Song(
        id = id,
        title = id,
        durationText = null,
        thumbnailUrl = null,
    )
}
