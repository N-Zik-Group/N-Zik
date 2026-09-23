package app.n_zik.android.core.database.ext

/**
 * Play totals for one calendar year (local timezone), as returned by
 * [app.n_zik.android.core.database.RewindEventSource.rewindYearTotals].
 */
data class RewindYearStat(
    val year: Int,
    val plays: Int,
    val playTimeMs: Long
)
