package app.n_zik.android.components.ui.screens.rewind

import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.Year as JavaYear
import java.time.format.TextStyle
import java.util.Locale

/**
 * The three Rewind periods: one month, one year, or the whole listening history
 * (global / all-time).
 *
 * [boundaries] returns an inclusive `[start, end]` window (epoch millis) in the
 * device's local timezone, matching the SQL `BETWEEN :from AND :to` windows of
 * [app.n_zik.android.core.database.RewindEventSource].
 */
sealed interface RewindPeriod {

    /** Inclusive `[start, end]` window (epoch millis) of this period. */
    fun boundaries(now: Long = System.currentTimeMillis()): Pair<Long, Long>

    /**
     * Calendar days spanned by the period; 0 for [Global] (an all-time window
     * has no fixed length — see [allTimeDaysInPeriod] for the actual span).
     */
    fun daysInPeriod(): Int

    /** Kicker label shown on the slides: "2026", "JANV. 2026" or "GLOBAL". */
    fun label(): String

    /** One month of [year] (1-based, 1 = January … 12 = December). */
    data class Month(
        val year: Int,
        val month: Int
    ) : RewindPeriod {

        override fun boundaries(now: Long): Pair<Long, Long> {
            val zone = ZoneId.systemDefault()
            val firstDay = LocalDate.of(year, month, 1)
            val start = firstDay.atStartOfDay()
            // The month window ends one millisecond before the next month starts
            val end = firstDay.plusMonths(1).atStartOfDay().minusNanos(1)
            return start.atZone(zone).toInstant().toEpochMilli() to
                end.atZone(zone).toInstant().toEpochMilli()
        }

        override fun daysInPeriod(): Int = JavaYear.of(year).atMonth(month).lengthOfMonth()

        override fun label(): String = periodMonthLabel(year, month)
    }

    /** One calendar year. */
    data class Year(
        val year: Int
    ) : RewindPeriod {

        override fun boundaries(now: Long): Pair<Long, Long> {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.of(year, 1, 1).atStartOfDay()
            val end = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX)
            return start.atZone(zone).toInstant().toEpochMilli() to
                end.atZone(zone).toInstant().toEpochMilli()
        }

        override fun daysInPeriod(): Int = JavaYear.of(year).length()

        override fun label(): String = year.toString()
    }

    /** The whole listening history (all-time); the window ends at [now]. */
    data object Global : RewindPeriod {

        override fun boundaries(now: Long): Pair<Long, Long> = 0L to now

        override fun daysInPeriod(): Int = 0

        override fun label(): String = "GLOBAL"
    }
}

/**
 * Kicker label of a month: the localized short month name uppercased to match
 * the deck's brand style (a dot is appended when the locale does not provide
 * one, so "JAN." in English stays "JANV." in French).
 */
internal fun periodMonthLabel(year: Int, month: Int): String {
    val short = Month.of(month)
        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
        .uppercase(Locale.getDefault())
    val withDot = if (short.endsWith('.')) short else "$short."
    return "$withDot $year"
}

/** File-name-safe token of the period: "2026", "2026_03" or "GLOBAL". */
internal fun RewindPeriod.fileNameToken(): String = when (this) {
    is RewindPeriod.Year -> year.toString()
    is RewindPeriod.Month -> String.format(Locale.ROOT, "%d_%02d", year, month)
    is RewindPeriod.Global -> "GLOBAL"
}

/**
 * Whether the month [month] (1-based) of [year] is fully over at [now]: the first
 * day of the following month, 00:00 in the device's local timezone, is at or before
 * [now]. The Rewind home shows unfinished months (the in-progress one and the future
 * ones) as locked cells instead of their stats, and their deck opens only from this
 * instant — the same moment the completion notification would fire, so access never
 * depends on whether that notification was actually delivered (user decision,
 * 2026-09-24: a period is not opened before it is finished).
 */
internal fun isMonthComplete(year: Int, month: Int, now: Long = System.currentTimeMillis()): Boolean {
    val end = LocalDate.of(year, month, 1).plusMonths(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    return end <= now
}

/**
 * Whether [year] is fully over at [now] (January 1st of the following year, 00:00 in
 * the device's local timezone, is at or before [now]). The Rewind home locks the
 * in-progress year's deck until it is over — the same user decision as
 * [isMonthComplete].
 */
internal fun isYearComplete(year: Int, now: Long = System.currentTimeMillis()): Boolean {
    val end = LocalDate.of(year + 1, 1, 1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    return end <= now
}
