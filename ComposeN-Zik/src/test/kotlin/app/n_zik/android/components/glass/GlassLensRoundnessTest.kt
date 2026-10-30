package app.n_zik.android.components.glass

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.n_zik.android.components.glass.backdrop.effects.lensCornerRadii
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class GlassLensRoundnessTest {
    @Test fun `bounded and percent corners match the outline at every capture resolution`() {
        val density = Density(2f)
        val fullSize = Size(240f, 120f)
        val corners = listOf(CornerSize(0.dp), CornerSize(8.dp), CornerSize(50), BoundedCornerSize(96.dp, .4f))
        corners.forEach { corner ->
            listOf(.3f, .5f, 1f).forEach { scale ->
                val radius = corner.toPx(fullSize, density).coerceAtMost(60f) * scale
                val actual = lensCornerRadii(RoundedCornerShape(corner), fullSize * scale, scale, density, LayoutDirection.Ltr)
                assertArrayEquals(FloatArray(4) { radius }, actual, .0001f)
            }
        }
    }

    @Test fun `directional and absolute corners retain their ordering after downsampling`() {
        val logical = RoundedCornerShape(2.dp, 4.dp, 6.dp, 8.dp)
        val absolute = AbsoluteRoundedCornerShape(2.dp, 4.dp, 6.dp, 8.dp)
        val density = Density(2f)
        assertArrayEquals(floatArrayOf(4f, 2f, 8f, 6f),
            lensCornerRadii(logical, Size(100f, 50f), .5f, density, LayoutDirection.Rtl), .0001f)
        assertArrayEquals(floatArrayOf(2f, 4f, 6f, 8f),
            lensCornerRadii(absolute, Size(100f, 50f), .5f, density, LayoutDirection.Rtl), .0001f)
    }
}
