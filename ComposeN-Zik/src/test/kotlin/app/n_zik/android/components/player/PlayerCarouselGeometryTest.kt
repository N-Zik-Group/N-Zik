package app.n_zik.android.components.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCarouselGeometryTest {
    @Test fun `short stack compresses upper and lower decoration before visible cover`() {
        val tall = playerCarouselGeometry(400.dp, 500.dp, 100.dp, (-300).dp, true)
        val short = playerCarouselGeometry(400.dp, 300.dp, 100.dp, (-300).dp, true)
        assertEquals(400.dp, tall.pageSize)
        assertEquals(300.dp, short.pageSize)
        assertEquals(100.dp, tall.coverPadding)
        assertEquals(50.dp, short.coverPadding)
        assertEquals(tall.pageSize - tall.coverPadding * 2, short.pageSize - short.coverPadding * 2)
        assertEquals(100.dp, tall.pageSize + tall.pageSpacing)
        assertEquals(50.dp, short.pageSize + short.pageSpacing)
    }

    @Test fun `below native visible cover size decoration collapses and pager stride remains valid`() {
        val short = playerCarouselGeometry(400.dp, 160.dp, 100.dp, (-300).dp, true)
        assertEquals(0.dp, short.coverPadding)
        assertEquals(160.dp, short.pageSize)
        assertTrue(short.pageSize + short.pageSpacing > 0.dp)
    }

    @Test fun `nonexpanded stack and nonstack options keep their original spacing and padding`() {
        val native = playerCarouselGeometry(400.dp, 300.dp, 100.dp, (-300).dp, false)
        assertEquals(100.dp, native.coverPadding)
        assertEquals((-300).dp, native.pageSpacing)
    }
}
