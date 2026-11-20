package app.n_zik.android.components.navigation

/** Follow actual page travel; a downward edge drag may reveal chrome already hidden. */
internal fun chromeScrollDelta(consumedY: Float, availableY: Float, chromeHidden: Boolean): Float =
    if (consumedY != 0f) consumedY
    else if (chromeHidden && availableY > 0f) availableY
    else 0f

/** For scroll owners without measured geometry, require one header's worth of actual travel. */
internal class HeaderScrollTravel {
    private var upwardTravel = 0f

    fun reset() {
        upwardTravel = 0f
    }

    fun delta(
        consumedY: Float,
        availableY: Float,
        headerOffset: Float,
        headerHeight: Float,
        hasRoom: Boolean?,
    ): Float {
        val delta = chromeScrollDelta(consumedY, availableY, headerOffset < 0f)
        if (hasRoom != null) {
            reset()
            return if (hasRoom) delta else 0f
        }
        if (headerHeight <= 0f) return 0f
        if (delta >= 0f) {
            upwardTravel = (upwardTravel - delta).coerceAtLeast(0f)
            return delta
        }
        if (headerOffset < 0f) return delta

        val distanceNeeded = (headerHeight - upwardTravel).coerceAtLeast(0f)
        upwardTravel = (upwardTravel - delta).coerceAtMost(headerHeight)
        return (delta + distanceNeeded).coerceAtMost(0f)
    }
}

/** The measured extent includes the padding already reserved for the active miniplayer. */
internal fun hasHeaderScrollRoom(contentSize: Int, viewportSize: Int, collapseDistance: Float): Boolean? =
    if (viewportSize <= 0 || viewportSize == Int.MAX_VALUE || contentSize == Int.MAX_VALUE || collapseDistance <= 0f) null
    else contentSize.toLong() - viewportSize >= collapseDistance
