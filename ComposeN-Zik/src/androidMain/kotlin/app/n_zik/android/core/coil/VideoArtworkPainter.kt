package app.n_zik.android.core.coil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.rememberPreference

/** Keeps foreground painters responsive when their Image caller uses a fixed Fit scale. */
@Composable
internal fun rememberVideoArtworkPainterTransform(url: String?, requested: ContentScale): (Painter) -> Painter {
    val crop = rememberPreference(cropVideoThumbnailsKey, true)
    return remember(url, requested, crop) {
        { painter: Painter ->
            if (isVideoArtwork(url) && requested == ContentScale.Crop) VideoArtworkPainter(painter, crop)
            else painter
        }
    }
}

/** Changes only drawing geometry; Coil's decoded image and cache entry remain intact. */
internal class VideoArtworkPainter(
    private val source: Painter,
    private val crop: State<Boolean>,
) : Painter() {
    private var alpha = 1f
    private var colorFilter: ColorFilter? = null

    override val intrinsicSize: Size
        get() {
            val original = source.intrinsicSize
            if (!crop.value || !original.isFinitePositive()) return original
            val side = minOf(original.width, original.height)
            return Size(side, side)
        }

    override fun DrawScope.onDraw() {
        val original = source.intrinsicSize
        val scale = if (crop.value && original.isFinitePositive())
            maxOf(size.width / original.width, size.height / original.height) else null
        val drawnSize = scale?.let { Size(original.width * it, original.height * it) } ?: size
        clipRect {
            translate((size.width - drawnSize.width) / 2f, (size.height - drawnSize.height) / 2f) {
                with(source) { draw(drawnSize, alpha, colorFilter) }
            }
        }
    }

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }
}

private fun Size.isFinitePositive(): Boolean = width.isFinite() && height.isFinite() && width > 0f && height > 0f
