package app.n_zik.android.components.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop

/** Maps the stage into native preview coordinates, including all ancestor preview scaling. */
@Composable
internal fun rememberPreviewStageBackdrop(stage: Backdrop?): Backdrop? {
    if (stage !is LayerBackdrop) return stage
    return remember(stage) {
        object : Backdrop by stage {
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) {
                val sourceCoordinates = stage.layerCoordinates
                if (sourceCoordinates == null || coordinates == null ||
                    !sourceCoordinates.isAttached || !coordinates.isAttached) return
                val origin = sourceCoordinates.localPositionOf(coordinates)
                val coordinateScale = sourceCoordinates.localPositionOf(coordinates, Offset(1f, 1f)) - origin
                if (coordinateScale.x <= 0f || coordinateScale.y <= 0f) return
                // LayerBackdrop aligns origins only; native preview controls also need the inverse scale.
                withTransform({ scale(1f / coordinateScale.x, 1f / coordinateScale.y, Offset.Zero) }) {
                    with(stage) { drawBackdrop(density, coordinates, layerBlock) }
                }
            }
        }
    }
}
