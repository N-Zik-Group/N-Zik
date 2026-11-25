package app.n_zik.android.components.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ItemSelectorTargetsTest {
    private data class Row(val id: Int, val title: String)

    private val first = Row(1, "First")
    private val second = Row(2, "Second")
    private val refreshed = listOf(first, second)

    @Test
    fun `browsing returns the original collection regardless of residual selection`() {
        assertSame(refreshed, selectionActionTargets(false, listOf(second), refreshed, Row::id, Row::id))
    }

    @Test
    fun `empty or ineligible selection does not fall back to the collection`() {
        assertEquals(emptyList<Row>(), selectionActionTargets(true, emptyList<Row>(), refreshed, Row::id, Row::id))
        assertEquals(emptyList<Row>(), selectionActionTargets(true, listOf(Row(3, "Hidden")), refreshed, Row::id, Row::id))
    }

    @Test
    fun `selected keys match eligible refreshed rows in the caller's order`() {
        val selected = listOf(Row(2, "Old title"), Row(3, "Hidden"), first)

        assertEquals(refreshed, selectionActionTargets(true, selected, refreshed, Row::id, Row::id))
        assertEquals(
            listOf(second, first),
            selectionActionTargets(true, selected, refreshed, Row::id, Row::id, selectionOrder = true),
        )
    }
}
