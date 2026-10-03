package app.n_zik.android.components.player

/**
 * Remaining time shown on the timeline label, in ms, aligned on whole seconds.
 *
 * The elapsed label floors the position to the second (`formatAsDuration` drops the ms); a
 * remaining label computed as `duration - position` then floors at a DIFFERENT boundary —
 * offset by the duration's ms part (247 441 ms → the two labels tick 441 ms apart). Flooring
 * both values first makes the two labels tick on the same frame and keeps
 * elapsed + remaining == the displayed duration.
 *
 * @return -1 ("unknown", displayed as "--:--") when the duration is not positive (still
 *   loading: `C.TIME_UNSET`), otherwise the clamped whole-second remaining time.
 */
internal fun displayedTimeRemainingOf(durationMs: Long, positionMs: Long): Long =
    if (durationMs <= 0) -1L
    else ((durationMs / 1000) - (positionMs / 1000)).coerceAtLeast(0) * 1000
