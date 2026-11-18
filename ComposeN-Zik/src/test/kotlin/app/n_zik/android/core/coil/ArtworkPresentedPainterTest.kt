package app.n_zik.android.core.coil

import android.app.Application
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkPresentedPainterTest {
    @Test
    fun artworkIsReportedOnlyOnceAfterAnOpaqueDraw() {
        var reports = 0
        val painter = ArtworkPresentedPainter(ColorPainter(Color.Red)) { reports++ }
        val canvas = Canvas(ImageBitmap(4, 4))
        fun drawArtwork(alpha: Float) {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(4f, 4f)) {
                with(painter) { draw(size, alpha) }
            }
        }

        drawArtwork(0.2f)
        drawArtwork(0.75f)
        assertEquals(0, reports)
        drawArtwork(1f)
        assertEquals(1, reports)
        drawArtwork(1f)
        assertEquals(1, reports)
    }
}
