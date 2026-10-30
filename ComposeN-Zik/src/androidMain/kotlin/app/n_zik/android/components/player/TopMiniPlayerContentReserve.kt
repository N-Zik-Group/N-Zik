package app.n_zik.android.components.player

/** Include only the part of the top navigation anchor not already reserved by the page. */
internal fun topMiniPlayerStackReservePx(
    miniReservePx: Float,
    fullMiniReservePx: Float,
    expectedTopNavPx: Float,
    reservedTopNavPx: Float,
    scrollOffsetPx: Float = 0f,
    hideRangePx: Float = 0f,
    keepVisible: Boolean = false,
    attachedMiniProgress: Float = 0f,
): Float {
    if (miniReservePx <= 0f || fullMiniReservePx <= 0f) return 0f
    val fraction = (miniReservePx / fullMiniReservePx).coerceIn(0f, 1f)
    val missingNav = (expectedTopNavPx - reservedTopNavPx).coerceAtLeast(0f)
    // The navigation row keeps its own reserve; only the mini's extra room joins that row.
    val reserve = miniReservePx * (1f - attachedMiniProgress.coerceIn(0f, 1f)) + missingNav * fraction
    return if (keepVisible) reserve else miniPlayerContentReservePx(reserve, scrollOffsetPx, hideRangePx)
}
