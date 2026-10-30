package app.n_zik.android.components.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Dimensions

/** Height of the app header row, without the status bar (see AppHeader). */
internal val APP_HEADER_HEIGHT: Dp = 64.dp

/** Breathing room between the header (or top navigation bar) and a top-anchored mini-player. */
internal val MINI_PLAYER_TOP_GAP: Dp = 8.dp

/** Breathing room kept between a top-anchored mini-player and the content that starts under it. */
internal val MINI_PLAYER_CONTENT_GAP: Dp = 8.dp

/** Height of the top navigation bar: the bar's 64dp minus the 10dp trim, plus the 5dp bottom padding it gets at the top (see HorizontalNavigationBar). */
internal val TOP_NAV_BAR_HEIGHT: Dp
    get() = Dimensions.navigationBarHeight - 5.dp

/**
 * Distance from the top of the screen to the mini-player when it is anchored at the top:
 * right below the app header, and below the navigation bar when that one is at the top too,
 * with a small [gap] in between.
 *
 * @param barsHidden True when the app header and navigation bar are put away (landscape phone
 *        on the bar-less screens); the mini-player then only stays clear of the status bar.
 * @param hasTopNavBar True when the navigation bar sits at the top of the screen.
 */
internal fun miniPlayerTopInset(
    statusBarTop: Dp,
    barsHidden: Boolean,
    hasTopNavBar: Boolean,
    headerHeight: Dp = APP_HEADER_HEIGHT,
    topNavBarHeight: Dp = TOP_NAV_BAR_HEIGHT,
    gap: Dp = MINI_PLAYER_TOP_GAP,
): Dp {
    if (barsHidden) return statusBarTop
    return statusBarTop + headerHeight + (if (hasTopNavBar) topNavBarHeight else 0.dp) + gap
}

/**
 * Extra room the collapsed mini-player leaves at one side so it stays clear of whatever sits
 * there: the system bar or cutout itself (status bar at the left in landscape, side navigation
 * bar, display cutout) and, after it, a navigation rail. The card already keeps
 * [standardPadding] from the safe content edge. System clearance is additional;
 * a rail also keeps at least this same margin from the capsule.
 *
 * @param safeInset Cutout or system inset on that side, which the rail is placed after.
 * @param railWidth Width of the rail; 0 when there is no rail on that side.
 */
internal fun miniPlayerSideInset(
    railWidth: Dp,
    safeInset: Dp,
    standardPadding: Dp = 16.dp,
    gap: Dp = MINI_PLAYER_TOP_GAP,
): Dp {
    // System clearance is additional to the capsule's usual margin, at either vertical anchor.
    if (railWidth <= 0.dp) return safeInset.coerceAtLeast(0.dp)
    return (safeInset + railWidth + maxOf(gap, standardPadding) - standardPadding).coerceAtLeast(0.dp)
}

/**
 * Room a screen keeps under a top-anchored mini-player so its content stays reachable.
 * It shrinks as the header, the top nav bar and the mini-player slide out together, which
 * leaves no empty band behind and keeps the content edge glued to the mini-player's bottom.
 *
 * @param reservePx Mini-player height plus the gaps around it, when it is fully shown.
 * @param scrollOffsetPx Header scroll-hide offset: 0 when shown, [hideRangePx] below 0 when
 *        entirely slid out.
 */
internal fun miniPlayerContentReservePx(
    reservePx: Float,
    scrollOffsetPx: Float,
    hideRangePx: Float,
): Float {
    if (hideRangePx <= 0f) return reservePx
    val hidden = (-scrollOffsetPx / hideRangePx).coerceIn(0f, 1f)
    return reservePx * (1f - hidden)
}

/**
 * Distance from the top of the screen to the mini-player while the header scrolls out.
 * With a retained top navigation row it settles immediately below that row. Otherwise it
 * leaves with the header and ends fully above the screen.
 *
 * @param scrollOffsetPx Header scroll-hide offset: 0 when shown, [hideRangePx] below 0 when
 *        entirely slid out.
 * @param hideRangePx How far the header travels to be entirely hidden.
 * @param retainedTopInsetPx Visible anchor below a pinned top navigation row, when present.
 * @param barsHidden The inset already excludes the hidden landscape header and navbar.
 */
internal fun miniPlayerTopPaddingPx(
    insetPx: Float,
    scrollOffsetPx: Float,
    hideRangePx: Float,
    collapsedHeightPx: Float,
    retainedTopInsetPx: Float? = null,
    barsHidden: Boolean = false,
): Float {
    if (barsHidden || hideRangePx <= 0f) return insetPx
    val hidden = (-scrollOffsetPx / hideRangePx).coerceIn(0f, 1f)
    val hiddenInsetPx = retainedTopInsetPx ?: -collapsedHeightPx
    return insetPx + (hiddenInsetPx - insetPx) * hidden
}

/** The mini joins the pinned top row while its header scrolls away independently. */
internal fun miniPlayerTopDockPaddingPx(
    insetPx: Float,
    scrollOffsetPx: Float,
    headerHeightPx: Float,
    navHeightPx: Float,
    miniHeightPx: Float,
    gapPx: Float,
    fold: Float,
): Float {
    val p = fold.coerceIn(0f, 1f)
    val headerOffset = scrollOffsetPx.coerceIn(-headerHeightPx.coerceAtLeast(0f), 0f)
    val rowShift = navHeightPx + gapPx - (navHeightPx - miniHeightPx) / 2f
    return insetPx + headerOffset - rowShift * p
}
