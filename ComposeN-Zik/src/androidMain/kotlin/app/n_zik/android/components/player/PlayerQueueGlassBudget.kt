package app.n_zik.android.components.player

/** Any visible queue reserves live optics for its foreground, including opening and closing. */
internal fun playerQueueUnderlayCovered(inheritedCovered: Boolean, queueVisible: Boolean): Boolean =
    inheritedCovered || queueVisible
