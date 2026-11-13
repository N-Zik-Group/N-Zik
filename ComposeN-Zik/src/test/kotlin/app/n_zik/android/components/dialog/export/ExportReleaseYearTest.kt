package app.n_zik.android.components.dialog.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportReleaseYearTest {
    @Test
    fun acceptsYearAndFullReleaseDates() {
        assertEquals("2025", exportReleaseYear("2025"))
        assertEquals("2025", exportReleaseYear(" 2025-11-06 "))
        assertEquals("2025", exportReleaseYear("2025-11-06T00:00:00Z"))
    }

    @Test
    fun ignoresBlankAndUnknownYears() {
        assertNull(exportReleaseYear(null))
        assertNull(exportReleaseYear("0000"))
        assertNull(exportReleaseYear("unknown"))
    }
}
