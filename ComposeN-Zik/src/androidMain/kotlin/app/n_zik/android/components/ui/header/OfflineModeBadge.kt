package app.n_zik.android.components.ui.header

import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Icon size in dp, identical to the other header badges. */
private const val ICON_SIZE_DP = 18

/** Gap between the badge and the previous item — the header's own 5dp badge spacing. */
private const val START_PADDING_DP = 5

// User-requested slower travel (see spec change log): deliberate exception to the
// app's sub-300ms animation rule.
private const val ENTER_MS = 400

private const val ENTER_FADE_MS = 150

private const val EXIT_MS = 300

/** Distance the badge flies to the right on exit, in dp. */
private const val EXIT_TRAVEL_DP = 48

/**
 * Layout slot of the badge while it is present in the header row: icon + start padding.
 * The parent uses it to count the badge in its collapse decision.
 */
internal val OFFLINE_BADGE_TOTAL_WIDTH_DP: Dp = (ICON_SIZE_DP + START_PADDING_DP).dp

/**
 * Airplane badge of the app header, visible only while full offline mode is on.
 *
 * Rendered as a sibling of the logo — OUTSIDE the header's clipped "extras" box — so
 * the plane stays visible through its whole travel: on enable it emerges from under
 * the logo, glides above the title, and lands at its slot after the last badge; on
 * disable it flies out to the right with a progressive fade, then leaves the tree
 * (0 space in the row, no reserved slot).
 *
 * Motion is draw-level only: a graphicsLayer translationX + alpha driven by
 * [Animatable]s with pre-computed px targets — no per-frame layout, no per-frame
 * recomposition.
 *
 * @param offlineMode live state of the full offline mode flag
 * @param enterTravelPx distance in px between the badge's rest position and its entry
 * start under the logo, pre-computed by the parent from its measurements
 * @param onPresenceChange called when the badge enters or leaves the composition,
 * so the parent can count its layout slot while the exit animation is still running
 */
@Composable
fun OfflineModeBadge(
    offlineMode: Boolean,
    enterTravelPx: Int,
    onPresenceChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Entry can only run once the parent has measured (the travel is known).
    val ready = enterTravelPx > 0

    // Composition gate: the badge leaves the tree only after the exit animation
    // finished — while the mode is off it contributes 0 space to the row.
    var visible by remember { mutableStateOf(false) }
    // True once the badge is at rest in its final position.
    var atRest by remember { mutableStateOf(false) }

    val offsetX = remember { Animatable(0f) }
    // "Anim" suffix: the bare name would shadow GraphicsLayerScope.alpha in the
    // graphicsLayer block below
    val alphaAnim = remember { Animatable(0f) }
    val exitTravelPx = with(LocalDensity.current) { EXIT_TRAVEL_DP.dp.toPx().roundToInt() }

    LaunchedEffect(offlineMode, ready) {
        if (!ready) return@LaunchedEffect
        if (offlineMode) {
            if (atRest) return@LaunchedEffect
            visible = true
            onPresenceChange(true)
            // Every entry starts from under the logo, even one that interrupts an exit.
            // The 5dp start padding places the badge's rest position at logo + extras +
            // gap, so subtracting the travel lands it exactly 5dp from the row's left
            // edge — under the 36dp logo.
            offsetX.snapTo(-enterTravelPx.toFloat())
            alphaAnim.snapTo(0f)
            coroutineScope {
                launch {
                    alphaAnim.animateTo(1f, tween(ENTER_FADE_MS, easing = FastOutSlowInEasing))
                }
                // User-requested slower travel (see spec change log).
                offsetX.animateTo(0f, tween(ENTER_MS, easing = FastOutSlowInEasing))
            }
            atRest = true
        } else if (visible) {
            // Reset atRest before the animation: an entry that interrupts this exit
            // must re-snap from under the logo instead of hitting the atRest guard.
            atRest = false
            coroutineScope {
                launch {
                    alphaAnim.animateTo(0f, tween(EXIT_MS, easing = FastOutSlowInEasing))
                }
                offsetX.animateTo(exitTravelPx.toFloat(), tween(EXIT_MS, easing = FastOutSlowInEasing))
            }
            visible = false
            onPresenceChange(false)
        }
    }

    if (visible) {
        Image(
            painter = painterResource(R.drawable.airplane),
            contentDescription = stringResource(R.string.offline_mode),
            modifier = modifier
                .padding(start = START_PADDING_DP.dp)
                .size(ICON_SIZE_DP.dp)
                .graphicsLayer {
                    translationX = offsetX.value
                    alpha = alphaAnim.value
                },
            colorFilter = ColorFilter.tint(colorPalette().text)
        )
    }
}
