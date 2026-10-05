package app.n_zik.android.bridge.library

import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.Page
import app.n_zik.android.bridge.PlaylistDto
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.TrackDownloadState
import app.n_zik.android.core.rewind.RewindPlaylists

/** `offset` / `limit` of a paginated route (contract §1), already validated. */
internal data class PageRequest(val offset: Int, val limit: Int)

/** `filter` of `/library/songs` (contract §10; the phone's `BuiltInPlaylist`, since 1.6). */
internal enum class SongFilter(val wire: String) {
    ALL("all"),
    LIKED("liked"),
    LOCAL("local"),
    DOWNLOADED("downloaded"),
    DISLIKED("disliked"),
    OFFLINE("offline"),
    TOP("top"),
}

/** `sort` of `/library/songs` (contract §10; the phone's `SongSortBy`, since 1.6). */
internal enum class SongSort(val wire: String) {
    TITLE("title"),
    ARTIST("artist"),
    ALBUM("album"),
    DURATION("duration"),
    PLAY_COUNT("playCount"),
    PLAY_TIME("playTime"),
    RELATIVE_PLAY_TIME("relativePlayTime"),
    DATE_ADDED("dateAdded"),
    DATE_PLAYED("datePlayed"),
    DATE_LIKED("dateLiked"),
    DOWNLOADED("downloaded"),
    CUSTOM("custom"),
}

/** `sort` of `/library/playlists/{id}/songs` (contract §10; the phone's `PlaylistSongSortBy`, since 1.6). */
internal enum class PlaylistSongSort(val wire: String) {
    TITLE("title"),
    ARTIST("artist"),
    ALBUM("album"),
    ARTIST_AND_ALBUM("artistAndAlbum"),
    DURATION("duration"),
    PLAY_COUNT("playCount"),
    PLAY_TIME("playTime"),
    RELATIVE_PLAY_TIME("relativePlayTime"),
    DATE_ADDED("dateAdded"),
    DATE_PLAYED("datePlayed"),
    DATE_LIKED("dateLiked"),
    ALBUM_YEAR("albumYear"),
    DOWNLOADED("downloaded"),
    CUSTOM("custom"),
}

/** `filter` of `/library/albums` and `/library/artists` (contract §10, since 1.1; `disliked` since 1.6). */
internal enum class CollectionFilter(val wire: String) {
    LIBRARY("library"),
    BOOKMARKED("bookmarked"),
    DISLIKED("disliked"),
}

/** `filter` of `/library/playlists` (contract §10, since 1.6; the phone's `PlaylistsType` chips). */
internal enum class PlaylistsFilter(val wire: String) {
    ALL("all"),
    PINNED("pinned"),
    REWIND("rewind"),
    YOUTUBE("youtube"),
}

/** `period` of `/library/songs?filter=top` (contract §10, since 1.6; the phone's `StatisticsType`). */
internal enum class TopPeriod(val wire: String) {
    TODAY("today"),
    WEEK("week"),
    MONTH("month"),
    THREE_MONTHS("3months"),
    SIX_MONTHS("6months"),
    YEAR("year"),
    ALL_TIME("all"),
}

/** `sort` of `/library/albums` (contract §10, since 1.6). */
internal enum class AlbumSort(val wire: String) {
    TITLE("title"),
    ARTIST("artist"),
    SONGS("songs"),
    DURATION("duration"),
    PLAY_COUNT("playCount"),
    LISTENING_TIME("listeningTime"),
    DATE_ADDED("dateAdded"),
    YEAR("year"),
    CUSTOM("custom"),
}

/** `sort` of `/library/artists` (contract §10, since 1.6). */
internal enum class ArtistSort(val wire: String) {
    NAME("name"),
    PLAY_COUNT("playCount"),
    LISTENING_TIME("listeningTime"),
    DATE_ADDED("dateAdded"),
    CUSTOM("custom"),
}

/** `sort` of `/library/playlists` (contract §10, since 1.6). */
internal enum class PlaylistSort(val wire: String) {
    NAME("name"),
    SONG_COUNT("songCount"),
    LISTENING_TIME("listeningTime"),
    PLAY_COUNT("playCount"),
    DATE_ADDED("dateAdded"),
    CUSTOM("custom"),
}

/**
 * The per-track download and cache states of a library read (contract §1.1, since 1.7.1): what the
 * phone's rows show beside each song.
 */
internal data class TrackSupport(
    /** The ids of the online songs fully downloaded on the phone. */
    val downloaded: Set<String> = emptySet(),
    /** The ids of the songs in the phone's streaming cache (the phone's "Cached" tab). */
    val cached: Set<String> = emptySet(),
    /** The active download state of each downloading / queued song (the settled ones stay absent). */
    val states: Map<String, TrackDownloadState> = emptyMap(),
    /** The download progress (0..1) of each active download. */
    val progresses: Map<String, Float> = emptyMap(),
)

/** The listening stats behind a collection id (contract §1.1, since 1.7.1): the phone's grid overlays. */
internal data class CollectionListening(val playCount: Int = 0, val totalPlayTimeMs: Long = 0L)

/**
 * Pure part of the library routes (contract §1, §10): parameter validation, search and
 * pagination. The sorting belongs to the provider (it needs the phone's database, since 1.6).
 * Every parser returns `null` for an invalid value, which the server answers with
 * `400 BAD_REQUEST`.
 */
internal object LibraryQueries {

    private val DECIMAL_ID = Regex("^[0-9]{1,18}$")

    /** Plain digits only: no sign, no blank, at most 9 digits (fits an `Int`). */
    private val DIGITS = Regex("^[0-9]{1,9}$")

    fun parsePage(offset: String?, limit: String?): PageRequest? {
        val parsedOffset = if (offset == null) BridgeContract.PAGE_OFFSET_DEFAULT else offset.digitsOrNull() ?: return null
        val parsedLimit = if (limit == null) BridgeContract.PAGE_LIMIT_DEFAULT else limit.digitsOrNull() ?: return null
        if (parsedLimit !in BridgeContract.PAGE_LIMIT_MIN..BridgeContract.PAGE_LIMIT_MAX) return null
        return PageRequest(parsedOffset, parsedLimit)
    }

    /** `filter` of `/library/songs`; absent means `all`. */
    fun parseSongFilter(filter: String?): SongFilter? =
        if (filter == null) SongFilter.ALL else SongFilter.entries.firstOrNull { it.wire == filter }

    /** `sort` of `/library/songs` (since 1.6); absent means the first value. */
    fun parseSongSort(sort: String?): SongSort? =
        if (sort == null) SongSort.TITLE else SongSort.entries.firstOrNull { it.wire == sort }

    /** `sort` of `/library/playlists/{id}/songs` (since 1.6); absent keeps the phone's position order. */
    fun parsePlaylistSongSort(sort: String?): PlaylistSongSort? =
        if (sort == null) PlaylistSongSort.CUSTOM else PlaylistSongSort.entries.firstOrNull { it.wire == sort }

    /** `query` of `/library/songs` (contract §10): trimmed, `null` when blank. */
    fun songsText(query: String?): String? = query?.trim()?.takeIf { it.isNotEmpty() }

    /** `text` of `/library/playlists` and of the playlist songs (contract §10, since 1.7.2): trimmed, `null` when blank. */
    fun playlistText(query: String?): String? = query?.trim()?.takeIf { it.isNotEmpty() }

    fun parseCollectionFilter(filter: String?): CollectionFilter? =
        if (filter == null) CollectionFilter.LIBRARY else CollectionFilter.entries.firstOrNull { it.wire == filter }

    /** `filter` of `/library/playlists` (since 1.6); absent means `all`. */
    fun parsePlaylistsFilter(filter: String?): PlaylistsFilter? =
        if (filter == null) PlaylistsFilter.ALL else PlaylistsFilter.entries.firstOrNull { it.wire == filter }

    /**
     * `rewind` of `/library/playlists` (contract §10, since 1.7.2): the phone's Month/Year/All
     * filter; absent means the phone's own current setting.
     */
    fun parseRewindFilter(raw: String?): RewindPlaylists.Filter? = when (raw) {
        null -> null
        "month" -> RewindPlaylists.Filter.Month
        "year" -> RewindPlaylists.Filter.Year
        "all" -> RewindPlaylists.Filter.All
        else -> null
    }

    /** `period` of `/library/songs?filter=top` (since 1.6); absent means the phone's own Top period. */
    fun parseTopPeriod(period: String?): TopPeriod? =
        if (period == null) null else TopPeriod.entries.firstOrNull { it.wire == period }

    /** `sort` of the collections (contract §10, since 1.6); absent means the first value. */
    fun parseAlbumSort(sort: String?): AlbumSort? =
        if (sort == null) AlbumSort.TITLE else AlbumSort.entries.firstOrNull { it.wire == sort }

    fun parseArtistSort(sort: String?): ArtistSort? =
        if (sort == null) ArtistSort.NAME else ArtistSort.entries.firstOrNull { it.wire == sort }

    fun parsePlaylistSort(sort: String?): PlaylistSort? =
        if (sort == null) PlaylistSort.NAME else PlaylistSort.entries.firstOrNull { it.wire == sort }

    /** `reverse` of the collections (contract §10, since 1.6); absent or `false` keeps the ascending order. */
    fun parseReverse(reverse: String?): Boolean? = when (reverse) {
        null -> false
        "true" -> true
        "false" -> false
        else -> null
    }

    fun parseArtworkSize(size: String?): Int? {
        if (size == null) return BridgeContract.ARTWORK_SIZE_DEFAULT
        return size.digitsOrNull()?.takeIf { it in BridgeContract.ARTWORK_SIZE_MIN..BridgeContract.ARTWORK_SIZE_MAX }
    }

    private fun String.digitsOrNull(): Int? = takeIf { DIGITS.matches(it) }?.toIntOrNull()

    /** Contract §1: a `playlistId` is decimal; anything else designates no playlist (`404`). */
    fun parsePlaylistId(id: String?): Long? = id?.takeIf { DECIMAL_ID.matches(it) }?.toLongOrNull()

    /** Case-insensitive search on `title` and `artists` (contract §10); [query] `null` keeps every song. */
    fun searchSongs(songs: List<TrackDto>, query: String?): List<TrackDto> =
        songs.filter { song ->
            query == null || song.matches(query)
        }

    private fun TrackDto.matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) || artists?.contains(query, ignoreCase = true) == true

    /**
     * Case-insensitive name search of `/library/playlists` (contract §10, since 1.7.2); [query]
     * `null` keeps every playlist.
     */
    fun searchPlaylists(playlists: List<PlaylistDto>, query: String?): List<PlaylistDto> =
        playlists.filter { playlist ->
            query == null || playlist.name.contains(query, ignoreCase = true)
        }

    /** Contract §1: `total` is the full size; an offset past the end gives an empty page. */
    fun <T> paginate(items: List<T>, page: PageRequest, sortMenu: List<String>? = null): Page<T> =
        paginate(items, page, items.size, sortMenu)

    /**
     * Contract §1 (since 1.7.2): [total] given apart from the [items] size — the phone's
     * counters keep their pre-search total while the `text` filter narrows the page.
     * Since 1.7.3: [sortMenu] rides on the page (the songs route only, contract §10.1).
     */
    fun <T> paginate(items: List<T>, page: PageRequest, total: Int, sortMenu: List<String>? = null): Page<T> {
        val from = page.offset.coerceAtMost(items.size)
        val to = (from.toLong() + page.limit).coerceAtMost(items.size.toLong()).toInt()
        return Page(
            items = items.subList(from, to).toList(),
            total = total,
            offset = page.offset,
            limit = page.limit,
            sortMenu = sortMenu,
        )
    }

    /**
     * The phone's local playlist header duration (contract §10, since 1.7.2): the sum of the
     * whole list's durations, unknown ones counted as 0.
     */
    fun totalDurationMsOf(tracks: List<TrackDto>): Long =
        tracks.sumOf { it.durationMs ?: 0L }
}

/** The wire value of the phone's rewind filter (contract §10, since 1.7.2). */
internal val RewindPlaylists.Filter.wire: String get() = name.lowercase()
