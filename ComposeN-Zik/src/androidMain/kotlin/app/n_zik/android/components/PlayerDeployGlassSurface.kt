package app.n_zik.android.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMiniPlayerBackground
import kotlin.math.roundToInt

/** Samples the moving card live, with reduced blur capture cost during its transition. */
@Composable
internal fun PlayerDeployGlassSurface(
    progress: () -> Float,
    collapsedHeight: Dp,
    startInset: Dp,
    endInset: Dp,
    baseShape: Shape,
    settings: GlassSettings,
    backdrop: Backdrop?,
    filmColor: Color,
    exportedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    val position = progress().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
    if (!isPlayerDeployGlassActive(position)) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val geometry = with(density) {
            computeCardGeometry(position, viewport, collapsedHeight.toPx(), horizontalPadding.toPx(),
                collapsedCardCornerPx(baseShape, viewport, collapsedHeight.toPx(), horizontalPadding.toPx(),
                    startInset.toPx(), endInset.toPx(), this),
                28.dp.toPx(), startInset.toPx(), endInset.toPx())
        }
        val shape = RoundedCornerShape(with(density) { geometry.cornerPx.toDp() })
        Box(Modifier
            .testTag("player_deploy_glass")
            .offset { IntOffset(geometry.left.roundToInt(), 0) }
            .size(with(density) { geometry.width.toDp() }, with(density) { geometry.height.toDp() })
            .glassMiniPlayerBackground(settings.copy(rim = 0f), shape,
                backdrop = backdrop, originalColor = filmColor, exportedBackdrop = exportedBackdrop,
                motionOptimized = true)
            .glassAreaContour(settings, GlassArea.MiniPlayer, shape))
    }
}

/** The transition's optical layers must not survive at either settled endpoint. */
internal fun isPlayerDeployGlassActive(progress: Float): Boolean =
    progress.isFinite() && progress > 0f && progress < 1f
