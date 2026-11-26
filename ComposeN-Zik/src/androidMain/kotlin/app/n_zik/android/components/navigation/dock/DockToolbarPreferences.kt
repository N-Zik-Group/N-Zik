package app.n_zik.android.components.navigation.dock

import app.n_zik.android.R
import org.json.JSONArray

/** Dock order uses the drawable names already stored in user preferences. */
internal data class DockToolbarTab(val id: String, val labelRes: Int, val iconRes: Int)

internal val searchDockTabs = listOf(
    DockToolbarTab("globe", R.string.search_online, R.drawable.globe),
    DockToolbarTab("library", R.string.library_title, R.drawable.library),
    DockToolbarTab("link", R.string.search_youtube_action_open_url, R.drawable.link),
)

internal val searchResultDockTabs = listOf(
    DockToolbarTab("musical_notes", R.string.library_songs_title, R.drawable.musical_notes),
    DockToolbarTab("album", R.string.library_albums_title, R.drawable.album),
    DockToolbarTab("artist", R.string.library_artists_title, R.drawable.artist),
    DockToolbarTab("video", R.string.search_videos_title, R.drawable.video),
    DockToolbarTab("playlist", R.string.library_playlists_title, R.drawable.playlist),
    DockToolbarTab("featured_playlist", R.string.search_featured_title, R.drawable.featured_playlist),
    DockToolbarTab("podcast", R.string.podcast_title, R.drawable.podcast),
)

internal val statisticsDockTabs = listOf(
    DockToolbarTab("stat_today", R.string.calendar_today, R.drawable.stat_today),
    DockToolbarTab("stat_week", R.string.calendar_period_one_week, R.drawable.stat_week),
    DockToolbarTab("stat_month", R.string.calendar_period_one_month, R.drawable.stat_month),
    DockToolbarTab("stat_3months", R.string.calendar_period_three_months, R.drawable.stat_3months),
    DockToolbarTab("stat_6months", R.string.calendar_period_six_months, R.drawable.stat_6months),
    DockToolbarTab("stat_year", R.string.calendar_period_one_year, R.drawable.stat_year),
    DockToolbarTab("calendar_clear", R.string.common_option_all, R.drawable.calendar_clear),
)

internal val artistDockTabs = listOf(
    DockToolbarTab("artist", R.string.artist_overview_title, R.drawable.artist),
    DockToolbarTab("library", R.string.library_title, R.drawable.library),
)

/** Dock-only ordering; native navigation preferences and compact side actions are untouched. */
object DockToolbarPreferences {
    const val orderKey = "floatingDockToolbarOrder"

    fun parseOrder(serialized: String): List<String> = runCatching {
        val array = JSONArray(serialized)
        List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }.distinct()
    }.getOrDefault(emptyList())

    fun orderedIds(ids: List<String>, serialized: String): List<String> {
        val available = ids.distinct()
        val selected = parseOrder(serialized).filter { it in available }
        return selected + available.filterNot { it in selected }
    }

    fun serializeOrder(ids: List<String>): String = JSONArray(ids.distinct()).toString()
}
