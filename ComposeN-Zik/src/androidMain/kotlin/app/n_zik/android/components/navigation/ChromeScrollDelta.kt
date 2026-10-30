package app.n_zik.android.components.navigation

/** Follow actual page travel; a downward edge drag may reveal chrome already hidden. */
internal fun chromeScrollDelta(consumedY: Float, availableY: Float, chromeHidden: Boolean): Float =
    if (consumedY != 0f) consumedY
    else if (chromeHidden && availableY > 0f) availableY
    else 0f
