package app.n_zik.android.core.ondevice

import app.n_zik.android.enums.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLibrarySortTest {
    private val rows = listOf("a-online", "b-local", "c-unavailable", "d-local", "e-online")

    @Test
    fun `direction only reverses device priority and keeps the secondary order`() {
        val available = setOf("b-local", "d-local")
        assertEquals(
            listOf("b-local", "d-local", "a-online", "c-unavailable", "e-online"),
            rows.groupByDeviceAvailability(SortOrder.Ascending) { it in available },
        )
        assertEquals(
            listOf("a-online", "c-unavailable", "e-online", "b-local", "d-local"),
            rows.groupByDeviceAvailability(SortOrder.Descending) { it in available },
        )
    }

    @Test
    fun `replaced device snapshots change the partition without dropping removed files`() {
        val refreshed = setOf("d-local")
        assertEquals(
            listOf("d-local", "a-online", "b-local", "c-unavailable", "e-online"),
            rows.groupByDeviceAvailability(SortOrder.Ascending) { it in refreshed },
        )
        assertEquals(rows, rows.groupByDeviceAvailability(SortOrder.Ascending) { false })
        assertEquals(rows, rows.groupByDeviceAvailability(SortOrder.Descending) { true })
        assertEquals(emptyList<String>(), emptyList<String>().groupByDeviceAvailability(SortOrder.Ascending) { true })
    }
}
