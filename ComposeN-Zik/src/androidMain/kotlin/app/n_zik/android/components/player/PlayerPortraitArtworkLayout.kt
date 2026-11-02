package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial

/** The preference is an inset, so its maximum value leaves the requested square at full width. */
fun playerThumbnailPadding(size: Float, landscape: Boolean = false) =
    ((100f - size.coerceIn(0f, 100f)) * if (landscape) .5f else 1.5f).dp

/** Keep the legacy square inside its slot even when its vertical decoration cannot fit. */
@Composable
fun PlayerPortraitThumbnailFrame(
    enabled: Boolean,
    verticalPadding: Dp,
    contentPadding: Dp,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, (maxHeight - verticalPadding).coerceAtLeast(0.dp))
        // aspectRatio falls back to a constraint-breaking square when neither axis fits.
        // Do not compose that image once its configured insets consume the entire square.
        if (width > contentPadding * 2) {
            Box(Modifier.width(width), contentAlignment = Alignment.Center) { content() }
        }
    }
}

internal data class PlayerPortraitArtworkBudget(val artwork: Int, val lower: Int)

internal fun playerPortraitArtworkBudget(available: Int, requested: Int, minimumLower: Int): PlayerPortraitArtworkBudget {
    val height = available.coerceAtLeast(0)
    val artwork = requested.coerceIn(0, (height - minimumLower.coerceAtLeast(0)).coerceAtLeast(0))
    return PlayerPortraitArtworkBudget(artwork, height - artwork)
}

/** A small external separation belongs only to the visible information pane. */
@Composable
fun playerPortraitInformationGap(): Dp {
    val settings = rememberGlassSettings(GlassScope.PlayerHeader)
    return if (settings.usesGlassMaterial(GlassArea.ActionButtons) ||
        settings.usesGlassContour(GlassArea.ActionButtons)) 8.dp else 0.dp
}

/** Measure the controller itself at its natural height before giving it flexible space. */
@Composable
fun PlayerPortraitInformationLayout(
    artworkPrioritized: Boolean,
    weightedControls: Boolean,
    above: @Composable ColumnScope.() -> Unit,
    controls: @Composable () -> Unit,
    below: @Composable ColumnScope.() -> Unit,
) {
    // An unbounded Column gives a weighted controller zero height. Use explicit
    // slots so the natural lookahead pass sees metadata, timeline and transport;
    // the bounded pass still reserves the bottom actions before expanding gaps.
    Layout(content = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = above)
        Box { controls() }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = below)
    }) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val top = children[0].measure(loose)
        val bottom = children[2].measure(loose.copy(maxHeight =
            if (constraints.hasBoundedHeight) (constraints.maxHeight - top.height).coerceAtLeast(0)
            else Constraints.Infinity))
        val remaining = if (constraints.hasBoundedHeight)
            (constraints.maxHeight - top.height - bottom.height).coerceAtLeast(0) else Constraints.Infinity
        val expand = constraints.hasBoundedHeight && (artworkPrioritized || weightedControls)
        val body = children[1].measure(loose.copy(
            minHeight = if (expand) remaining else 0,
            maxHeight = remaining))
        val width = constraints.constrainWidth(maxOf(top.width, body.width, bottom.width))
        val height = if (expand) constraints.maxHeight
            else constraints.constrainHeight(top.height + body.height + bottom.height)
        layout(width, height) {
            top.placeRelative((width - top.width) / 2, 0)
            body.placeRelative((width - body.width) / 2, top.height)
            bottom.placeRelative((width - bottom.width) / 2, height - bottom.height)
        }
    }
}

private class PortraitMinimum {
    var constraints: Constraints? = null
    var lower: Int = 0
}

/** Measure real content before assigning spare height to the original weighted lower groups. */
@Composable
internal fun PlayerPortraitArtworkLayout(
    modifier: Modifier,
    prioritizeArtwork: Boolean,
    expandInformation: Boolean,
    fillOrdinaryArtwork: Boolean,
    artworkVerticalPadding: Dp,
    informationGap: Dp,
    header: @Composable ColumnScope.() -> Unit,
    artwork: @Composable ColumnScope.() -> Unit,
    information: @Composable ColumnScope.() -> Unit,
) {
    val minimum = remember { PortraitMinimum() }
    // Lookahead performs two supported measure passes on one composition. The first
    // collapses weighted gaps; the second restores only the surplus after the cover.
    LookaheadScope {
        Layout(modifier = modifier, content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, content = header)
            Column(horizontalAlignment = Alignment.CenterHorizontally, content = artwork)
            PlayerLowerContentViewport(expandToViewport = expandInformation,
                naturalHeight = { minimum.lower }, content = information)
        }) { children, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val top = children[0].measure(loose)
            val available = if (constraints.hasBoundedHeight)
                (constraints.maxHeight - top.height).coerceAtLeast(0) else Constraints.Infinity
            val naturalPass = isLookingAhead || minimum.constraints != constraints || !constraints.hasBoundedHeight
            val lower = if (naturalPass)
                children[2].measure(loose.copy(maxHeight = Constraints.Infinity)) else null
            if (isLookingAhead) {
                minimum.constraints = constraints
                minimum.lower = requireNotNull(lower).height
            }
            val natural = lower?.height ?: minimum.lower
            val requested = constraints.maxWidth + artworkVerticalPadding.roundToPx()
            // The expanded compact controller belongs at the bottom; its surplus
            // remains in the centered artwork slot. Collapsed controllers instead
            // own the space below their square, including their weighted gaps.
            val budget = if (!constraints.hasBoundedHeight) PlayerPortraitArtworkBudget(requested, natural)
                else if (expandInformation) playerPortraitArtworkBudget(available, requested, natural)
                else playerPortraitArtworkBudget(available, available, natural)
            // Spend only surplus lower whitespace on the Glass separation. A full
            // requested cover and all real controls take priority over this gap.
            val gap = if (prioritizeArtwork && constraints.hasBoundedHeight)
                minOf(informationGap.roundToPx().coerceAtLeast(0),
                    (budget.lower - natural).coerceAtLeast(0)) else 0
            val cover = children[1].measure(loose.copy(
                minHeight = if (prioritizeArtwork || fillOrdinaryArtwork) budget.artwork else 0,
                maxHeight = budget.artwork))
            val lowerHeight = (budget.lower - gap).coerceAtLeast(0)
            val finalLower = lower ?: children[2].measure(loose.copy(minHeight = lowerHeight, maxHeight = lowerHeight))
            val width = constraints.constrainWidth(maxOf(top.width, cover.width, finalLower.width))
            val height = if (constraints.hasBoundedHeight) constraints.maxHeight
                else constraints.constrainHeight(top.height + cover.height + finalLower.height)
            layout(width, height) {
                top.placeRelative((width - top.width) / 2, 0)
                cover.placeRelative((width - cover.width) / 2, top.height)
                finalLower.placeRelative((width - finalLower.width) / 2, height - finalLower.height)
            }
        }
    }
}
