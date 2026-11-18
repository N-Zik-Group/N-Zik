package app.n_zik.android.core.coil

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/** Reports the incoming image once Coil has drawn it at full opacity. */
internal class ArtworkPresentedPainter(
    private val source: Painter,
    private val onPresented: () -> Unit,
) : Painter() {
    private var alpha = 1f
    private var filter: ColorFilter? = null
    private var reported = false
    override val intrinsicSize get() = source.intrinsicSize

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        filter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        with(source) { draw(size, alpha, filter) }
        if (!reported && alpha >= 1f && size.width > 0f && size.height > 0f) {
            reported = true
            onPresented()
        }
    }
}
