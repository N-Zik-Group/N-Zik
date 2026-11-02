package app.n_zik.android.components.player

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import app.it.fast4x.rimusic.enums.PlayerType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Expanded Essential still needs weighted lower space when its controls retain weighted gaps. */
fun playerPortraitUsesWeightedInformation(
    expandedPlayer: Boolean,
    standaloneLyrics: Boolean,
    playerType: PlayerType,
    carousel: Boolean,
    lyricsOnCover: Boolean,
): Boolean = !standaloneLyrics && !(carousel && playerType == PlayerType.Modern) &&
    (!expandedPlayer || (playerType == PlayerType.Essential && lyricsOnCover))

/** The Modern player always owns a pager, independently of its optional carousel transform. */
fun playerPortraitPrioritizesArtwork(artworkVisible: Boolean, playerType: PlayerType): Boolean =
    artworkVisible && playerType == PlayerType.Essential

/** Reserve lower content before portrait artwork, including the native expanded carousel. */
@Composable
fun PlayerGlassBudgetLayout(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    expandedCarousel: Boolean = false,
    // Keep the carousel's lower slot stable through expansion toggles.
    reserveLowerContent: Boolean = false,
    prioritizeArtwork: Boolean = false,
    expandInformation: Boolean = prioritizeArtwork,
    artworkVerticalPadding: Dp = 0.dp,
    informationGap: Dp = 0.dp,
    header: @Composable ColumnScope.() -> Unit,
    artwork: @Composable ColumnScope.() -> Unit,
    information: @Composable ColumnScope.() -> Unit,
) {
    // Keep the overlay hosts and their animations in the same composition when a
    // standalone lyric/visualizer replaces the cover. Only measurement changes.
    PlayerPortraitArtworkLayout(modifier = modifier,
        prioritizeArtwork = prioritizeArtwork && !expandedCarousel && !reserveLowerContent,
        expandInformation = expandInformation && !expandedCarousel && !reserveLowerContent,
        fillOrdinaryArtwork = !enabled && !expandedCarousel && !reserveLowerContent,
        artworkVerticalPadding = artworkVerticalPadding, informationGap = informationGap,
        header = header, artwork = artwork, information = information)
}

/** Scroll only excess lower content; ordinary layouts retain their native visual overflow. */
@Composable
internal fun PlayerLowerContentViewport(
    expandToViewport: Boolean = false,
    naturalHeight: () -> Int = { Int.MAX_VALUE },
    content: @Composable ColumnScope.() -> Unit,
) {
    val geometry = LocalPlayerTransportGeometry.current
    DisposableEffect(geometry) {
        onDispose {
            geometry?.lowerViewport = null
            geometry?.lowerScrollOffset = 0
        }
    }
    var offset by remember { mutableFloatStateOf(0f) }
    var maxScroll by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollableState { delta ->
        val previous = offset
        val requested = previous + delta
        offset = requested.coerceIn(0f, maxScroll.toFloat())
        // Relocation cancels on partial consumption; subtraction rounding is not a boundary.
        if (offset == requested) delta else offset - previous
    }
    val modifier = Modifier
        .scrollable(scroll, Orientation.Vertical, enabled = maxScroll > 0, reverseDirection = true)
        .semantics {
            if (maxScroll > 0) {
                verticalScrollAxisRange = ScrollAxisRange({ offset }, { maxScroll.toFloat() })
                scrollBy { _, y ->
                    scope.launch { scroll.scroll { scrollBy(y) } }
                    true
                }
            }
        }
        // A layer clip excludes hidden controls from hit testing as well as drawing.
        .graphicsLayer { clip = maxScroll > 0 }
        // Match the layout ancestor seen by descendants, inside the graphics-layer coordinator.
        .onPlaced { geometry?.lowerViewport = it }
    Layout(modifier = modifier, content = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }) { children, constraints ->
        val fits = expandToViewport && !isLookingAhead && constraints.hasBoundedHeight &&
            naturalHeight() <= constraints.maxHeight
        val lower = children.single().measure(if (fits) constraints.copy(minHeight = constraints.maxHeight)
            else constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val height = constraints.constrainHeight(lower.height)
        if (!isLookingAhead) {
            maxScroll = (lower.height - height).coerceAtLeast(0)
            offset = offset.coerceIn(0f, maxScroll.toFloat())
        }
        val placementOffset = if (isLookingAhead) 0 else offset.roundToInt()
        layout(lower.width, height) {
            if (!isLookingAhead) geometry?.lowerScrollOffset = placementOffset
            lower.placeRelative(0, -placementOffset)
        }
    }
}
