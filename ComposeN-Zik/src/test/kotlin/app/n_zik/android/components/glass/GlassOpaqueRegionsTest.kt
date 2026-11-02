package app.n_zik.android.components.glass

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassOpaqueRegionsTest {
    private val viewport = Rect(10f, 24f, 370f, 800f)

    @Test fun `only complete paint or adjacent opaque bands cover the viewport`() {
        assertTrue(opaqueRegionsCover(viewport, listOf(viewport)))
        assertTrue(opaqueRegionsCover(viewport, listOf(Rect(0f, 0f, 400f, 200f), Rect(0f, 200f, 400f, 900f))))
        assertFalse(opaqueRegionsCover(viewport, emptyList()))
        assertFalse(opaqueRegionsCover(viewport, listOf(Rect(10f, 24.1f, 370f, 800f))))
        assertFalse(opaqueRegionsCover(viewport, listOf(Rect(10.1f, 24f, 370f, 800f))))
        assertFalse(opaqueRegionsCover(viewport, listOf(Rect(0f, 0f, 400f, 200f), Rect(0f, 201f, 400f, 900f))))
        assertFalse(opaqueRegionsCover(Rect.Zero, listOf(viewport)))
    }

    @Test fun `opacity and overlay ownership remain independent of modal registration`() {
        val visibility = GlassOverlayVisibility()
        val lower = Any()
        val upper = Any()
        val lowerPaint = Any()
        val upperPaint = Any()
        visibility.regions[lowerPaint] = GlassOpaqueRegion(lower, viewport, .999f)
        assertFalse(visibility.covers(viewport, listOf(lower)))
        visibility.regions[upperPaint] = GlassOpaqueRegion(upper, viewport, 1f)
        assertTrue(visibility.covers(viewport, listOf(lower, upper)))
        assertFalse(visibility.covers(viewport, listOf(lower)))
        visibility.removeOverlay(upper)
        assertFalse(visibility.covers(viewport, listOf(lower, upper)))
        visibility.regions[lowerPaint] = GlassOpaqueRegion(lower, viewport, 1f)
        assertTrue(visibility.covers(viewport, listOf(lower)))
        visibility.removeOverlay(lower)
        assertFalse(visibility.covers(viewport, listOf(lower)))
    }
}
