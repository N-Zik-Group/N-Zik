package app.n_zik.android.utils.toast

import kotlin.math.roundToInt

/** All coordinates are pixels in the same window, not screen or content coordinates. */
internal data class BottomNavigationGeometry(
    val barTop: Float,
    val windowBottom: Int,
    val systemBottomInset: Int,
    val standardBarContentHeight: Int,
    val minimumGap: Int = 0,
)

internal fun toastOffsetAboveNavigation(
    defaultOffset: Int,
    geometry: BottomNavigationGeometry?,
): Int {
    if (geometry == null || !geometry.barTop.isFinite()) return defaultOffset
    val usableBottom = geometry.windowBottom - geometry.systemBottomInset.coerceAtLeast(0)
    if (geometry.barTop < 0 || geometry.barTop >= usableBottom) return defaultOffset
    val normalGap = (defaultOffset - geometry.standardBarContentHeight).coerceAtLeast(geometry.minimumGap)
    return (usableBottom - geometry.barTop).roundToInt() + normalGap
}

/** Correct bottom gravity using the attached toast's real screen position, not app insets. */
internal fun correctedToastBottomOffset(
    currentOffset: Int,
    actualToastBottomOnScreen: Int,
    desiredToastBottomOnScreen: Int,
): Int = currentOffset + actualToastBottomOnScreen - desiredToastBottomOnScreen
