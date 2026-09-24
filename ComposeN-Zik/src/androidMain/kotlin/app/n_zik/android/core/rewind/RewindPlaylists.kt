package app.n_zik.android.core.rewind

/**
 * Naming and visibility contract of the "Rewind" home category — the single
 * source of truth shared by spec 1 (category rename + Month/Year/All filter row) and
 * spec 2 (playlist generation worker and its notification).
 *
 * Naming rule (user decision, 2026-09-24): the name stored in the database is a stable,
 * language-neutral identifier (`rewind-monthly:YYYYMM`, `rewind-yearly:YYYY`). The worker
 * deduplicates by name and the Month/Year filter keys off the prefix, so a localized name
 * in the DB would create duplicates on a language change and break the filter. The display
 * name ("Rewind Août 2025", "Rewind 2025") is formatted at render time (spec 2).
 */
object RewindPlaylists {

    /** Prefix of the generated monthly rewind playlists: `rewind-monthly:YYYYMM`. */
    const val MONTHLY_PREFIX = "rewind-monthly:"

    /** Prefix of the generated yearly rewind playlists: `rewind-yearly:YYYY`. */
    const val YEARLY_PREFIX = "rewind-yearly:"

    /**
     * SharedPreferences key of the selected Month/Year/All chip in the Playlists tab —
     * stores the [Filter] enum name through `rememberPreference`, default [Filter.Month].
     */
    const val REWIND_PLAYLISTS_FILTER_KEY = "rewindPlaylistsFilter"

    /** Whether [name] is a generated monthly rewind playlist. */
    fun isMonthly(name: String): Boolean = name.startsWith(MONTHLY_PREFIX, ignoreCase = true)

    /** Whether [name] is a generated yearly rewind playlist. */
    fun isYearly(name: String): Boolean = name.startsWith(YEARLY_PREFIX, ignoreCase = true)

    /** Whether [name] belongs to the "Rewind" category (monthly or yearly). */
    fun isRewind(name: String): Boolean = isMonthly(name) || isYearly(name)

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

    /** Content filter per selected chip: [Filter.Month] -> monthly only, [Filter.Year] -> yearly only, [Filter.All] -> both. */
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
}
