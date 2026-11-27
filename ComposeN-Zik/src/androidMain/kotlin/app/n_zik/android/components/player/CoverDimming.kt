package app.n_zik.android.components.player

/** Clamp persisted opacity and recover malformed values with the overlay's default. */
internal fun clampCoverDimmingOpacity(opacity: Float, fallback: Float): Float =
    if (opacity.isNaN()) fallback else opacity.coerceIn(0f, 1f)

/** Resolve the active alpha without changing the saved intensity. */
internal fun coverDimmingAlpha(eligible: Boolean, enabled: Boolean, opacity: Float, fallback: Float): Float =
    if (eligible && enabled) clampCoverDimmingOpacity(opacity, fallback) else 0f
