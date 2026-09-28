package app.n_zik.android.components.ui.header

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import app.kreate.android.drawable.APP_ICON_IMAGE_BITMAP
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.components.themed.Button
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

/**
 * Collapse animation duration (ms) for the header title's sliding extras, in both
 * directions. Under the 300ms app animation rule; a single spec keeps the width
 * retraction and the slide/fade perfectly in sync.
 */
private const val APP_TITLE_COLLAPSE_ANIMATION_MS = 250

/**
 * Boundary jitter tolerated before the header title collapses: without it, subpixel
 * differences between the available and ideal widths could flip the collapsed state
 * on and off between frames.
 */
private val APP_TITLE_COLLAPSE_TOLERANCE = 2.dp

/**
 * Collapse decision for the header title row: the sliding extras (title text + badges)
 * must slide behind the logo when the width actually available to the row is below its
 * ideal (full) width by more than [tolerancePx].
 *
 * @param availablePx Width available to the row from the parent, in px (very large when
 * unconstrained)
 * @param idealPx Ideal (full) width of the row — logo + sliding extras — in px; 0 = not
 * measured yet
 * @param tolerancePx Boundary jitter that must NOT trigger a collapse, in px
 * @return true when the sliding extras should collapse behind the logo
 */
internal fun shouldCollapseTitle(availablePx: Int, idealPx: Int, tolerancePx: Int): Boolean {
    if (idealPx <= 0) return false
    return availablePx < idealPx - tolerancePx
}

/**
 * The app header's title row — the always-visible app logo plus the sliding extras
 * ("N-ZIK" text, version badge, parental-control shield, debug badge) that collapse
 * behind the logo when the header runs out of horizontal space.
 *
 * Drop-in replacement for the legacy fixed `AppTitle` row: unlike that row, this one is
 * measured against the width actually available in the parent — callers pass
 * `Modifier.weight(1f)`, so the header row gives it exactly the remaining space. When
 * that space is below the row's ideal width, the extras slide to the left,
 * behind the logo, while fading out and their layout width retracts to zero, so the
 * action bar (search + burger) is never compressed. When the space returns, the reverse
 * animation re-expands them from the logo. The logo itself always keeps its full size.
 *
 * @param navController Navigation controller (home navigation from the title text, logo
 * easter eggs)
 * @param context Context for the logo easter-egg toasts
 * @param modifier Outer modifier — pass `weight(1f)` so the parent row gives this row
 * the full remaining width (fill), which is what the collapse decision measures
 */
@Composable
fun CollapsingAppTitle(
    navController: NavController,
    context: Context,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val idealExtrasPx = remember { mutableIntStateOf(0) }
    val logoPx = remember { mutableIntStateOf(0) }
    // Width actually available to this row from the parent. Deliberately NOT the measured
    // row width: while the extras animate, the measured width would track the animation
    // itself and deadlock the re-expansion — the available space is what decides.
    val availablePx = remember { mutableIntStateOf(0) }

    val collapsed = shouldCollapseTitle(
        availablePx = availablePx.intValue,
        idealPx = logoPx.intValue + idealExtrasPx.intValue,
        tolerancePx = with(density) { APP_TITLE_COLLAPSE_TOLERANCE.toPx().toInt() }
    )

    // Sliding extras' layout width (ideal <-> 0): retracts while the content slides
    // behind the logo, so the action bar slides back into its space.
    val extrasWidth by animateDpAsState(
        targetValue = if (collapsed) 0.dp else with(density) { idealExtrasPx.intValue.toDp() },
        animationSpec = tween(APP_TITLE_COLLAPSE_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "appTitleExtrasWidth"
    )
    // Slide + fade fraction (0f = fully visible, 1f = fully behind the logo). The fade is
    // quadratic so the content is nearly gone before it reaches the logo's far edge.
    val slide by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(APP_TITLE_COLLAPSE_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "appTitleSlide"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.layout { measurable, constraints ->
            availablePx.intValue = constraints.maxWidth
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }
    ) {
        AppLogo(
            navController = navController,
            context = context,
            modifier = Modifier
                .zIndex(1f) // draw the logo above the sliding extras while they pass behind
                .onSizeChanged { logoPx.intValue = it.width }
                .testTag("appTitleLogo")
        )

        Box(
            modifier = Modifier
                .width(extrasWidth)
                .graphicsLayer {
                    translationX = -slide * logoPx.intValue
                    alpha = (1f - slide) * (1f - slide)
                }
                .clip(RectangleShape) // cuts the retracted overflow and moves with the slide
                .testTag("appTitleExtras")
        ) {
            AppTitleExtras(
                navController = navController,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        // Always measure at natural width: the animated Box width above
                        // only clips and slides this content. Measuring inside the clipped
                        // width would report the animation itself as the ideal width and
                        // deadlock the re-expansion (0 -> 0 -> 0).
                        val placeable = measurable.measure(
                            constraints.copy(maxWidth = Int.MAX_VALUE)
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .onSizeChanged { idealExtrasPx.intValue = it.width }
            )
        }
    }
}

/**
 * The sliding part of the header title row — everything after the logo: the "N-ZIK"
 * text, the version badge, the parental-control shield (when active) and the debug
 * badge. Composed at its ideal (natural) width at all times; the parent [Box] clips
 * and slides it.
 */
@Composable
private fun AppTitleExtras(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        AppLogoText(navController)

        // Version badge: width capped at the "DEBUG" badge width, marquee on overflow
        HeaderVersionBadge()

        // Parental-control shield (same conditional as the legacy AppTitle)
        if (parentalControlEnabled())
            Button(
                iconId = R.drawable.shield_checkmark,
                color = colorPalette().text,
                padding = 0.dp,
                size = 20.dp
            ).Draw()

        // Reactive debug badge: live preference state, tap toggles the debug logs
        DebugLogsBadge()
    }
}

@Composable
private fun parentalControlEnabled(): Boolean =
    rememberPreference(parentalControlEnabledKey, false).value

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppLogo(
    navController: NavController,
    context: Context,
    modifier: Modifier
) {
    val countToReveal = remember { mutableIntStateOf(0) }

    Image(
        bitmap = APP_ICON_IMAGE_BITMAP,
        contentDescription = stringResource(R.string.cd_app_s_icon),
        modifier = modifier
            .clip(uiRoundnessShape())
            .combinedClickable(
                onClick = { appIconClickAction(navController, countToReveal, context) },
                onLongClick = { appIconLongClickAction(navController, context) }
            )
            .size(36.dp)
    )
}

@Composable
private fun AppLogoText(navController: NavController) {
    val iconTextClick: () -> Unit = {
        if (NavRoutes.home.isNotHere(navController))
            navController.navigate(NavRoutes.home.name)
    }

    BasicText(
        text = "N-ZIK",
        style = TextStyle(
            fontSize = typography().xl.semiBold.fontSize,
            fontWeight = typography().xl.semiBold.fontWeight,
            fontFamily = typography().xl.semiBold.fontFamily,
            color = colorPalette().text
        ),
        modifier = Modifier
            .clip(uiRoundnessShape())
            .clickable { iconTextClick() }
            .padding(horizontal = 8.dp)
    )
}

// App icon easter eggs, behavior-identical to the legacy AppTitle: 10 clicks open the
// Pacman game, 3/6/9 clicks toast progress; long click opens the Snake game.
private fun appIconClickAction(
    navController: NavController,
    countToReveal: MutableIntState,
    context: Context
) {
    countToReveal.intValue++

    val message: String =
        when (countToReveal.intValue) {
            10 -> {
                countToReveal.intValue = 0
                navController.navigate(NavRoutes.gamePacman.name)
                ""
            }

            3 -> context.getString(R.string.easter_egg_click_message)
            6 -> context.getString(R.string.easter_egg_keep_going)
            9 -> context.getString(R.string.easter_egg_number_one)
            else -> ""
        }
    if (message.isNotEmpty())
        Toaster.n(message, Toast.LENGTH_LONG)
}

private fun appIconLongClickAction(
    navController: NavController,
    context: Context
) {
    Toaster.n(context.getString(R.string.easter_egg_last), Toast.LENGTH_LONG)
    navController.navigate(NavRoutes.gameSnake.name)
}
