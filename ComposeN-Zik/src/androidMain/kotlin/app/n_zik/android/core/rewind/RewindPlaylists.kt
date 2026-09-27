package app.n_zik.android.core.rewind

import android.content.Context
import app.n_zik.android.R
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Naming and visibility contract of the "Rewind" home category — the single
 * source of truth shared by spec 1 (category rename + Month/Year/All filter row) and
 * spec 2 (playlist generation worker and its notification).
 *
 * Naming rule (user decision, 2026-09-24): the name stored in the database is a stable,
 * language-neutral identifier (`rewind-monthly:YYYYMM`, `rewind-yearly:YYYY`,
 * `rewind-alltime`). The worker deduplicates by name and the Month/Year filter keys off
 * the prefix, so a localized name in the DB would create duplicates on a language change
 * and break the filter. The display name ("Rewind Août 2025", "Rewind 2025",
 * "Rewind All-time") is formatted at render time (spec 2).
 */
object RewindPlaylists {

    /** Prefix of the generated monthly rewind playlists: `rewind-monthly:YYYYMM`. */
    const val MONTHLY_PREFIX = "rewind-monthly:"

    /** Prefix of the generated yearly rewind playlists: `rewind-yearly:YYYY`. */
    const val YEARLY_PREFIX = "rewind-yearly:"

    /**
     * The unique all-time snapshot playlist (spec 2). Produced only by the deck's
     * "regenerate all-time" pill — there is no worker and no creation toggle for it:
     * the target keeps moving, so there is exactly one name, never a pile.
     */
    const val ALLTIME_NAME = "rewind-alltime"

    /**
     * SharedPreferences key of the selected Month/Year/All chip in the Playlists tab —
     * stores the [Filter] enum name through `rememberPreference`, default [Filter.Month].
     */
    const val REWIND_PLAYLISTS_FILTER_KEY = "rewindPlaylistsFilter"

    /** Whether [name] is a generated monthly rewind playlist. */
    fun isMonthly(name: String): Boolean = name.startsWith(MONTHLY_PREFIX, ignoreCase = true)

    /** Whether [name] is a generated yearly rewind playlist. */
    fun isYearly(name: String): Boolean = name.startsWith(YEARLY_PREFIX, ignoreCase = true)

    /** Whether [name] is the generated all-time snapshot playlist. */
    fun isAlltime(name: String): Boolean = name.equals(ALLTIME_NAME, ignoreCase = true)

    /** Whether [name] belongs to the "Rewind" category (monthly, yearly or all-time). */
    fun isRewind(name: String): Boolean = isMonthly(name) || isYearly(name) || isAlltime(name)

    /**
     * Builds the database name of a monthly rewind playlist: `rewind-monthly:YYYYMM`
     * (month zero-padded — spec 2 normalizes the legacy unpadded `monthValue`).
     */
    fun monthlyName(year: Int, month: Int): String =
        String.format(Locale.ROOT, "%s%04d%02d", MONTHLY_PREFIX, year, month)

    /** Builds the database name of a yearly rewind playlist: `rewind-yearly:YYYY`. */
    fun yearlyName(year: Int): String = "$YEARLY_PREFIX$year"

    /**
     * The Month/Year/All row only exists when BOTH creation toggles are on: with a single
     * active type there is nothing to filter (the tab shows that type directly), and with
     * no active type the list is empty.
     */
    fun rowVisible(monthlyEnabled: Boolean, yearlyEnabled: Boolean): Boolean =
        monthlyEnabled && yearlyEnabled

    /**
     * Content predicate of the "Rewind" tab for every creation-toggle
     * combination: both on -> filtered by the selected [filter] chip, only monthly on ->
     * monthly playlists only, only yearly on -> yearly playlists only, both off ->
     * nothing. A disabled toggle never hides the other type.
     */
    fun isShown(name: String, monthlyEnabled: Boolean, yearlyEnabled: Boolean, filter: Filter): Boolean =
        when {
            rowVisible(monthlyEnabled, yearlyEnabled) -> matches(filter, name)
            monthlyEnabled -> isMonthly(name)
            yearlyEnabled -> isYearly(name)
            else -> false
        }

    /** Content filter per selected chip: [Filter.Month] -> monthly only, [Filter.Year] -> yearly only, [Filter.All] -> both + all-time. */
    fun matches(filter: Filter, name: String): Boolean = when (filter) {
        Filter.Month -> isMonthly(name)
        Filter.Year -> isYearly(name)
        Filter.All -> isRewind(name)
    }

    /** Filter chips of the "Rewind" category row (default selection: [Month]). */
    enum class Filter {
        Month,
        Year,
        All,
    }

    /**
     * The localized display name of a generated rewind playlist (spec 2), formatted at
     * render time from the language-neutral database name:
     * - `rewind-monthly:202508` -> "Rewind August 2025" (localized month, existing
     *   `month_*_s` keys)
     * - `rewind-yearly:2025` -> "Rewind 2025"
     * - `rewind-alltime` -> "Rewind All-time"
     * - anything else -> [name] unchanged (safe no-op, so it can be called
     *   unconditionally on every display surface).
     */
    fun Context.rewindDisplayName(name: String): String = when {
        isMonthly(name) -> {
            val tail = name.substringAfter(MONTHLY_PREFIX, "")
            val month = if (tail.length == 6) tail.substring(4, 6).toIntOrNull() else null
            if (tail.length == 6 && month != null && month in 1..12) {
                getString(R.string.rewind) + " " + getString(monthNameKeys[month - 1], tail.substring(0, 4))
            } else {
                name
            }
        }
        isYearly(name) -> getString(R.string.rewind) + " " + name.substringAfter(YEARLY_PREFIX, "")
        isAlltime(name) -> getString(R.string.rewind) + " " + getString(R.string.rewind_alltime_name)
        else -> name
    }

    /**
     * The `[from, to]` listening window (epoch millis, device local zone, `BETWEEN`-inclusive
     * semantics of [app.n_zik.android.core.database.EventTable.findSongsMostPlayedBetween])
     * derived from a generated playlist's database name — the single source of the
     * regeneration window shared by the deck pill and the post-import job (spec 2):
     * - `rewind-monthly:YYYYMM` -> that month (`[startOfMonth, startOfNextMonth)`)
     * - `rewind-yearly:YYYY` -> that year (`[Jan 1, Jan 1 of the next year)`)
     * - `rewind-alltime` -> the whole history (`[0, now]`)
     * - anything else -> `null`.
     *
     * The end bound is the start of the *next* period: exactly 1 ms past the last
     * millisecond of the period, which is acceptable for the inclusive `BETWEEN`
     * (frozen spec 2 edge).
     */
    fun windowFor(name: String, now: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        val zone = ZoneId.systemDefault()
        return when {
            isMonthly(name) -> {
                val tail = name.substringAfter(MONTHLY_PREFIX, "")
                if (tail.length != 6) return null
                // toIntOrNull: a user-created playlist can carry a prefix-shaped but
                // non-numeric tail (e.g. "rewind-monthly:2026ab") — that is "anything
                // else", which must resolve to null, never an exception
                val year = tail.substring(0, 4).toIntOrNull() ?: return null
                val month = tail.substring(4, 6).toIntOrNull() ?: return null
                if (month !in 1..12) return null
                val start = LocalDate.of(year, month, 1).atStartOfDay(zone)
                start.toInstant().toEpochMilli() to start.plusMonths(1).toInstant().toEpochMilli()
            }
            isYearly(name) -> {
                val tail = name.substringAfter(YEARLY_PREFIX, "")
                if (tail.length != 4) return null
                val year = tail.toIntOrNull() ?: return null
                val start = LocalDate.of(year, 1, 1).atStartOfDay(zone)
                start.toInstant().toEpochMilli() to start.plusYears(1).toInstant().toEpochMilli()
            }
            isAlltime(name) -> 0L to now
            else -> null
        }
    }

    /** `month_*_s` string keys in calendar order (1 = January … 12 = December). */
    private val monthNameKeys = intArrayOf(
        R.string.month_january_s,
        R.string.month_february_s,
        R.string.month_march_s,
        R.string.month_april_s,
        R.string.month_may_s,
        R.string.month_june_s,
        R.string.month_july_s,
        R.string.month_august_s,
        R.string.month_september_s,
        R.string.month_october_s,
        R.string.month_november_s,
        R.string.month_december_s
    )
}
