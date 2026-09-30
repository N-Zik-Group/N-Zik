package app.n_zik.android.bridge.library

import app.n_zik.android.bridge.AlbumDto
import app.n_zik.android.bridge.ArtistDto
import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.Page
import app.n_zik.android.bridge.PlaylistDto
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.TrackSource

/** `offset` / `limit` of a paginated route (contract §1), already validated. */
internal data class PageRequest(val offset: Int, val limit: Int)

/** `filter` of `/library/songs` (contract §10). */
internal enum class SongFilter(val wire: String) {
    ALL("all"),
    LIKED("liked"),
    LOCAL("local"),
    DOWNLOADED("downloaded"),
}

/** `sort` of `/library/songs` (contract §10). */
internal enum class SongSort(val wire: String) {
    TITLE("title"),
    ARTIST("artist"),
    PLAY_TIME("playTime"),
}

/** `filter` of `/library/albums` and `/library/artists` (contract §10, since 1.1). */
internal enum class CollectionFilter(val wire: String) {
    LIBRARY("library"),
    BOOKMARKED("bookmarked"),
}

/** Validated parameters of `/library/songs`; [query] is `null` when absent or blank. */
internal data class SongsQuery(
    val page: PageRequest,
    val query: String?,
    val filter: SongFilter,
    val sort: SongSort,
)

/** One song of the library: its wire [track] plus what the sort needs and the wire does not carry. */
internal data class LibrarySong(val track: TrackDto, val totalPlayTimeMs: Long)

/**
 * Pure part of the library routes (contract §1, §10): parameter validation, filtering,
 * search, sorting and pagination. Every parser returns `null` for an invalid value, which
 * the server answers with `400 BAD_REQUEST`.
 */
internal object LibraryQueries {

    private val DECIMAL_ID = Regex("^[0-9]{1,18}$")

    /** Plain digits only: no sign, no blank, at most 9 digits (fits an `Int`). */
    private val DIGITS = Regex("^[0-9]{1,9}$")

    /**
     * Case-insensitive A→Z order. Every sort below ends with the id as last tie-breaker,
     * so pages never overlap nor skip.
     */
    private val TEXT_ORDER: Comparator<String> = String.CASE_INSENSITIVE_ORDER

    fun parsePage(offset: String?, limit: String?): PageRequest? {
        val parsedOffset = if (offset == null) BridgeContract.PAGE_OFFSET_DEFAULT else offset.digitsOrNull() ?: return null
        val parsedLimit = if (limit == null) BridgeContract.PAGE_LIMIT_DEFAULT else limit.digitsOrNull() ?: return null
        if (parsedLimit !in BridgeContract.PAGE_LIMIT_MIN..BridgeContract.PAGE_LIMIT_MAX) return null
        return PageRequest(parsedOffset, parsedLimit)
    }

    fun parseSongsQuery(offset: String?, limit: String?, query: String?, filter: String?, sort: String?): SongsQuery? {
        val page = parsePage(offset, limit) ?: return null
        val trimmedQuery = query?.trim()
        if (trimmedQuery != null && trimmedQuery.length > BridgeContract.LIBRARY_QUERY_MAX_LENGTH) return null
        val parsedFilter = if (filter == null) SongFilter.ALL else SongFilter.entries.firstOrNull { it.wire == filter } ?: return null
        val parsedSort = if (sort == null) SongSort.TITLE else SongSort.entries.firstOrNull { it.wire == sort } ?: return null
        return SongsQuery(page, trimmedQuery?.takeIf { it.isNotEmpty() }, parsedFilter, parsedSort)
    }

    fun parseCollectionFilter(filter: String?): CollectionFilter? =
        if (filter == null) CollectionFilter.LIBRARY else CollectionFilter.entries.firstOrNull { it.wire == filter }

    fun parseArtworkSize(size: String?): Int? {
        if (size == null) return BridgeContract.ARTWORK_SIZE_DEFAULT
        return size.digitsOrNull()?.takeIf { it in BridgeContract.ARTWORK_SIZE_MIN..BridgeContract.ARTWORK_SIZE_MAX }
    }

    private fun String.digitsOrNull(): Int? = takeIf { DIGITS.matches(it) }?.toIntOrNull()

    /** Contract §1: a `playlistId` is decimal; anything else designates no playlist (`404`). */
    fun parsePlaylistId(id: String?): Long? = id?.takeIf { DECIMAL_ID.matches(it) }?.toLongOrNull()

    /** Filter, then case-insensitive search on `title` and `artists`, then sort (contract §10). */
    fun selectSongs(songs: List<LibrarySong>, query: SongsQuery): List<LibrarySong> {
        val filtered = songs.filter { song ->
            val track = song.track
            val kept = when (query.filter) {
                SongFilter.ALL -> true
                SongFilter.LIKED -> track.isLiked
                SongFilter.LOCAL -> track.source == TrackSource.LOCAL
                SongFilter.DOWNLOADED -> track.isDownloaded
            }
            kept && (query.query == null || track.matches(query.query))
        }
        val byId = compareBy<LibrarySong> { it.track.id }
        val order: Comparator<LibrarySong> = when (query.sort) {
            SongSort.TITLE -> compareBy<LibrarySong, String>(TEXT_ORDER) { it.track.title }
                .thenBy(TEXT_ORDER) { it.track.artists.orEmpty() }
            SongSort.ARTIST -> compareBy<LibrarySong, String>(TEXT_ORDER) { it.track.artists.orEmpty() }
                .thenBy(TEXT_ORDER) { it.track.title }
            SongSort.PLAY_TIME -> compareByDescending<LibrarySong> { it.totalPlayTimeMs }
                .thenBy(TEXT_ORDER) { it.track.title }
        }
        return filtered.sortedWith(order.then(byId))
    }

    private fun TrackDto.matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) || artists?.contains(query, ignoreCase = true) == true

    fun sortPlaylists(playlists: List<PlaylistDto>): List<PlaylistDto> =
        playlists.sortedWith(compareBy<PlaylistDto, String>(TEXT_ORDER) { it.name }.thenBy { it.id })

    fun sortAlbums(albums: List<AlbumDto>): List<AlbumDto> =
        albums.sortedWith(compareBy<AlbumDto, String>(TEXT_ORDER) { it.title }.thenBy { it.id })

    fun sortArtists(artists: List<ArtistDto>): List<ArtistDto> =
        artists.sortedWith(compareBy<ArtistDto, String>(TEXT_ORDER) { it.name }.thenBy { it.id })

    /** Contract §1: `total` is the full size; an offset past the end gives an empty page. */
    fun <T> paginate(items: List<T>, page: PageRequest): Page<T> {
        val from = page.offset.coerceAtMost(items.size)
        val to = (from.toLong() + page.limit).coerceAtMost(items.size.toLong()).toInt()
        return Page(items = items.subList(from, to).toList(), total = items.size, offset = page.offset, limit = page.limit)
    }
}
