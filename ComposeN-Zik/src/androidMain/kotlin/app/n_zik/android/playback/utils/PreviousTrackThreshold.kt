package app.n_zik.android.playback.utils

/** A blank threshold and zero both mean to go to the previous item whenever one exists. */
internal fun parsePreviousTrackThreshold(raw: String): Int? {
    val value = raw.trim()
    return when {
        value.isEmpty() -> 0
        value.all(Char::isDigit) -> value.toIntOrNull()
        else -> null
    }
}

internal fun shouldRestartTrackOnPrevious(
    hasPreviousItem: Boolean,
    positionMs: Long,
    thresholdSeconds: Int,
): Boolean = !hasPreviousItem || (thresholdSeconds > 0 && positionMs > thresholdSeconds * 1_000L)
