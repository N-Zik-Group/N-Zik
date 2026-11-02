package app.n_zik.android.components.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCarouselGeometryTest {
    @Test fun `Small landscape at minimum spacing retains a bounded overlapping page budget`() {
        // Landscape spacing is -50% of the viewport minus three Small (90dp) insets.
        val geometry = playerCarouselGeometry(400.dp, 300.dp, 90.dp, (-420).dp, true)
        assertEquals(300.dp, geometry.pageSize)
        assertEquals(40.dp, geometry.coverPadding)
        assertTrue((geometry.pageSize + geometry.pageSpacing).value in 48f..49f)
        assertEquals(220.dp, geometry.pageSize - geometry.coverPadding * 2)
    }

    @Test fun `extreme compact spacing remains bounded in tall short and nearly padded viewports`() {
        for (height in listOf(100, 160, 300, 400, 800)) {
            for (padding in listOf(20, 30, 55, 90, 180)) {
                val spacing = -height * .5f - 3 * padding
                val geometry = playerCarouselGeometry(400.dp, height.dp, padding.dp, spacing.dp, true)
                assertTrue("Page budget at height=$height padding=$padding: $geometry",
                    geometry.pageSize + geometry.pageSpacing >= geometry.pageSize / 8)
            }
        }
    }

    @Test fun `short stack compresses upper and lower decoration before visible cover`() {
        val tall = playerCarouselGeometry(400.dp, 500.dp, 100.dp, (-300).dp, true)
        val short = playerCarouselGeometry(400.dp, 300.dp, 100.dp, (-300).dp, true)
        assertEquals(400.dp, tall.pageSize)
        assertEquals(300.dp, short.pageSize)
        assertEquals(100.dp, tall.coverPadding)
        assertEquals(50.dp, short.coverPadding)
        assertEquals(tall.pageSize - tall.coverPadding * 2, short.pageSize - short.coverPadding * 2)
        assertEquals(100.dp, tall.pageSize + tall.pageSpacing)
        assertEquals(60.dp, short.pageSize + short.pageSpacing)
    }

    @Test fun `below native visible cover size stride scales with artwork instead of collapsing`() {
        val short = playerCarouselGeometry(400.dp, 160.dp, 100.dp, (-300).dp, true)
        assertEquals(0.dp, short.coverPadding)
        assertEquals(160.dp, short.pageSize)
        assertEquals(40.dp, short.pageSize + short.pageSpacing)
    }

    @Test fun `compact stack keeps proportional stride as decoration is exhausted`() {
        for (height in listOf(300, 240, 200, 160, 100)) {
            val geometry = playerCarouselGeometry(400.dp, height.dp, 100.dp, (-300).dp, true)
            val visibleCover = geometry.pageSize - geometry.coverPadding * 2
            assertTrue("Stride must remain proportional at height $height: $geometry",
                geometry.pageSize + geometry.pageSpacing >= visibleCover / 4)
            assertEquals(minOf(200, height).dp, visibleCover)
        }
    }

    @Test fun `nonexpanded stack and nonstack options keep their original spacing and padding`() {
        val native = playerCarouselGeometry(400.dp, 300.dp, 100.dp, (-300).dp, false)
        assertEquals(100.dp, native.coverPadding)
        assertEquals((-300).dp, native.pageSpacing)
    }

    @Test fun `every spacing step remains responsive in portrait and landscape including below minus27`() {
        // Portrait spacing uses screen height; landscape uses the artwork slot height.
        for ((height, referenceHeight) in listOf(160 to 160, 300 to 300, 500 to 800)) {
            for (padding in listOf(0, 20, 30, 55, 90)) {
                val strides = (-50..50).map { setting ->
                    val geometry = playerCarouselGeometry(400.dp, height.dp, padding.dp,
                        (setting * .01f * referenceHeight - 3 * padding).dp, true)
                    (geometry.pageSize + geometry.pageSpacing).value
                }
                strides.zipWithNext().forEachIndexed { index, (before, after) ->
                    assertTrue("Spacing ${index - 50} must change at height=$height padding=$padding",
                        after > before)
                }
                if (padding == 90 && height >= 300) {
                    assertTrue("The formerly flat -50 to -27 range must visibly change: $strides",
                        strides[23] - strides[0] > 4f)
                }
            }
        }
    }

    @Test fun `spacing compression has no jump at its transition and preserves wider strides`() {
        val below = playerCarouselBoundedStride(99.99.dp, 400.dp)
        val at = playerCarouselBoundedStride(100.dp, 400.dp)
        val above = playerCarouselBoundedStride(100.01.dp, 400.dp)
        assertTrue(below < at && at < above)
        assertEquals((at - below).value, (above - at).value, .0001f)
        assertEquals(180.dp, playerCarouselBoundedStride(180.dp, 400.dp))
    }
}
