package app.n_zik.android.core.database.ext

/**
 * Play totals for one calendar year-month (local timezone), as returned by
 * [app.n_zik.android.core.database.RewindEventSource.rewindYearMonthTotals].
 *
 * @param month 1-based month (1 = January … 12 = December)
 */
data class RewindYearMonthStat(
    val year: Int,
    val month: Int,
    val plays: Int,
    val playTimeMs: Long
)
