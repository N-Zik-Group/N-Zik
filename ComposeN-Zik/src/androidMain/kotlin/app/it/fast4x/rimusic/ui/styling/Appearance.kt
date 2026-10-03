package app.it.fast4x.rimusic.ui.styling

import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.ui.saveable.reportSaveableMismatch
import app.n_zik.android.ui.saveable.reportTruncatedSaveable

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CircleShape

class BoundedCornerSize(val dp: Dp, val maxFraction: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val requestedPx = with(density) { dp.toPx() }
        val maxPx = shapeSize.minDimension * maxFraction
        return kotlin.math.min(requestedPx, maxPx)
    }
}

data class Appearance(
    val colorPalette: ColorPalette,
    val typography: Typography,
    val thumbnailShape: Shape,
    val uiRoundnessShape: Shape,
    val artistThumbnailShape: Shape
) {
    companion object : Saver<Appearance, Any> {
        /**
         * Issue #881 (gh-881): tolerant restore (spec M5). The saved payload is a `List<Any>`
         * (an `ArrayList` after the Bundle round-trip) — but a positional saveable shift can
         * deliver a FOREIGN payload (any type — the Saver generics are erased at runtime) or a
         * truncated list. A non-List payload falls back to [fallback] (the site passes the same
         * preference-derived default its `init` lambda computes) instead of crashing; a List
         * payload is parsed with safe casts, each missing element falling back to its default
         * (a valid saved payload restores exactly the same value as before).
         */
        override fun restore(value: Any): Appearance = restore(value, ::processDefaultAppearance)

        fun restore(value: Any?, fallback: () -> Appearance): Appearance {
            val list = value as? List<*>
            if (list == null) {
                reportSaveableMismatch<List<*>>("appearance", value)
                return fallback()
            }
            // A valid appearance payload always has 5 elements — a shorter List is a truncated
            // (corrupted) save: report it, then parse what is present (missing elements fall back).
            if (list.size < 5) reportTruncatedSaveable("appearance.truncated", list.size, 5)
            val thumbRadius = (list.getOrNull(2) as? Float) ?: (list.getOrNull(2) as? Int)?.toFloat() ?: 12f
            val uiRadius = (list.getOrNull(3) as? Float) ?: (list.getOrNull(3) as? Int)?.toFloat() ?: 20f
            val artistThumbRadius = (list.getOrNull(4) as? Float) ?: (list.getOrNull(4) as? Int)?.toFloat() ?: 48f
            return Appearance(
                // A null element (truncated list) means "missing" → the element fallback; a present
                // foreign element is logged + fallen back to by the element saver itself.
                colorPalette = list.getOrNull(0)?.let { ColorPalette.restore(it) } ?: ColorPalette.fallback,
                typography = list.getOrNull(1)?.let { Typography.restore(it) } ?: Typography.fallback,
                thumbnailShape = if (thumbRadius >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbRadius.dp, 0.25f)),
                uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRadius.dp, 0.4f)),
                artistThumbnailShape = if (artistThumbRadius >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbRadius.dp, 0.25f))
            )
        }

        /**
         * Process-wide last-resort appearance (dark default palette + default typography and
         * roundness) used only when a foreign payload reaches [restore] without a site-provided
         * fallback (issue #881, gh-881).
         */
        private fun processDefaultAppearance(): Appearance = Appearance(
            colorPalette = ColorPalette.fallback,
            typography = Typography.fallback,
            thumbnailShape = RoundedCornerShape(BoundedCornerSize(12f.dp, 0.25f)),
            uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(20f.dp, 0.4f)),
            artistThumbnailShape = CircleShape
        )

        override fun SaverScope.save(value: Appearance): List<Any> {
            val thumbRadius = when (val shape = value.thumbnailShape) {
                is RoundedCornerShape -> {
                    val size = shape.topStart
                    if (size is BoundedCornerSize) size.dp.value else 12f
                }
                else -> 48f // For CircleShape
            }

            val uiRadius = when (val shape = value.uiRoundnessShape) {
                is RoundedCornerShape -> {
                    val size = shape.topStart
                    if (size is BoundedCornerSize) size.dp.value else 20f
                }
                else -> 20f
            }

            val artistThumbRadius = when (val shape = value.artistThumbnailShape) {
                is RoundedCornerShape -> {
                    val size = shape.topStart
                    if (size is BoundedCornerSize) size.dp.value else 12f
                }
                else -> 48f // For CircleShape
            }

            return listOf(
                with(ColorPalette.Companion) { save(value.colorPalette) },
                with(Typography.Companion) { save(value.typography) },
                thumbRadius,
                uiRadius,
                artistThumbRadius
            )
        }
    }
}

val LocalAppearance = staticCompositionLocalOf<Appearance> { error("No Appearance provided") }