package app.it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import java.time.ZoneId
import java.time.LocalDate

enum class StatisticsType(
    val duration: Duration,
    @field:DrawableRes override val iconId: Int,
    @field:StringRes override val textId: Int
): Drawable, TextView {

    Today( 1.days, R.drawable.stat_today, R.string.calendar_today ),

    OneWeek( 7.days, R.drawable.stat_week, R.string.calendar_period_one_week ),

    OneMonth( 30.days, R.drawable.stat_month, R.string.calendar_period_one_month ),

    ThreeMonths( 90.days, R.drawable.stat_3months, R.string.calendar_period_three_months ),

    SixMonths( 180.days, R.drawable.stat_6months, R.string.calendar_period_six_months ),

    OneYear( 365.days, R.drawable.stat_year, R.string.calendar_period_one_year ),

    All( Duration.INFINITE, R.drawable.calendar_clear, R.string.common_option_all );

    /**
     * For example:
     * - March 10th 2025 at 14:30 has timestamp of `1,741,617,000,000`.
     * [PastDay.timeStampInMillis] returns `1,741,530,600,000` instead of
     * [Duration.inWholeMilliseconds] which is `86,400,000`.
     *
     * @return real timestamp in millis.
     */
    fun timeStampInMillis(): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return when (this) {
            Today -> today.atStartOfDay(zone).toInstant().toEpochMilli()
            OneWeek -> today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli() // 7 days including today
            OneMonth -> today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli() // 30 days including today
            ThreeMonths -> today.minusDays(89).atStartOfDay(zone).toInstant().toEpochMilli() // 90 days including today
            SixMonths -> today.minusDays(179).atStartOfDay(zone).toInstant().toEpochMilli() // 180 days including today
            OneYear -> today.minusDays(364).atStartOfDay(zone).toInstant().toEpochMilli() // 365 days including today
            All -> 0L
        }
    }
}

enum class StatisticsCategory(
    @field:StringRes override val textId: Int
): TextView {

    Songs( R.string.library_songs_title ),

    Artists( R.string.library_artists_title ),

    Albums( R.string.library_albums_title ),

    Playlists( R.string.library_playlists_title );
}



