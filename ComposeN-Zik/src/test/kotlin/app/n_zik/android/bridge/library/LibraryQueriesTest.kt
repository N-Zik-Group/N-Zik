package app.n_zik.android.bridge.library

import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.TrackSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal fun libSong(
    id: String,
    title: String,
    artists: String? = null,
    playTimeMs: Long = 1_000L,
    liked: Boolean = false,
    downloaded: Boolean = false,
): LibrarySong = LibrarySong(
    track = TrackDto(
        id = id,
        title = title,
        artists = artists,
        durationMs = null,
        source = if (id.startsWith("local:")) TrackSource.LOCAL else TrackSource.ONLINE,
        isDownloaded = downloaded,
        isLiked = liked,
        hasArtwork = true,
    ),
    totalPlayTimeMs = playTimeMs,
)

class LibraryQueriesTest {

    private val songs = listOf(
        libSong("id-b", "bravo", artists = "Zed", playTimeMs = 5_000L, liked = true),
        libSong("id-a", "Alpha", artists = "yann", playTimeMs = 9_000L, downloaded = true),
        libSong("local:1", "charlie", artists = "Abba", playTimeMs = 1_000L),
        libSong("id-d", "Delta", artists = null, playTimeMs = 7_000L, liked = true),
    )

    private fun ids(list: List<LibrarySong>) = list.map { it.track.id }

    @Test
    fun `pagination defaults to offset 0 and limit 50`() {
        assertEquals(PageRequest(0, 50), LibraryQueries.parsePage(null, null))
        assertEquals(PageRequest(10, 200), LibraryQueries.parsePage("10", "200"))
        assertEquals(PageRequest(0, 1), LibraryQueries.parsePage("0", "1"))
    }

    @Test
    fun `out of bounds or malformed pagination is rejected`() {
        assertNull(LibraryQueries.parsePage("-1", null))
        assertNull(LibraryQueries.parsePage(null, "0"))
        assertNull(LibraryQueries.parsePage(null, "201"))
        assertNull(LibraryQueries.parsePage("abc", null))
        assertNull(LibraryQueries.parsePage(null, ""))
    }

    @Test
    fun `numbers with a sign or spaces are rejected`() {
        assertNull(LibraryQueries.parsePage("+1", null))
        assertNull(LibraryQueries.parsePage(null, "+10"))
        assertNull(LibraryQueries.parsePage(" 1", null))
        assertNull(LibraryQueries.parsePage(null, "99999999999"))
        assertNull(LibraryQueries.parseArtworkSize("+300"))
        assertNull(LibraryQueries.parseArtworkSize(""))
    }

    @Test
    fun `the search text is trimmed and a blank one means no search`() {
        assertEquals("alp", LibraryQueries.songsText("  alp "))
        assertNull(LibraryQueries.songsText("   "))
        assertNull(LibraryQueries.songsText(null))
    }

    @Test
    fun `songs filter and sort default to all and title and reject unknown ones`() {
        assertEquals(SongFilter.ALL, LibraryQueries.parseSongFilter(null))
        assertEquals(SongFilter.LIKED, LibraryQueries.parseSongFilter("liked"))
        assertEquals(SongFilter.LOCAL, LibraryQueries.parseSongFilter("local"))
        assertEquals(SongFilter.DOWNLOADED, LibraryQueries.parseSongFilter("downloaded"))
        assertEquals(SongFilter.DISLIKED, LibraryQueries.parseSongFilter("disliked"))
        assertEquals(SongFilter.OFFLINE, LibraryQueries.parseSongFilter("offline"))
        assertEquals(SongFilter.TOP, LibraryQueries.parseSongFilter("top"))
        assertNull(LibraryQueries.parseSongFilter("favorites"))

        assertEquals(SongSort.TITLE, LibraryQueries.parseSongSort(null))
        assertEquals(SongSort.PLAY_TIME, LibraryQueries.parseSongSort("playTime"))
        assertEquals(SongSort.RELATIVE_PLAY_TIME, LibraryQueries.parseSongSort("relativePlayTime"))
        assertEquals(SongSort.DOWNLOADED, LibraryQueries.parseSongSort("downloaded"))
        assertEquals(SongSort.CUSTOM, LibraryQueries.parseSongSort("custom"))
        assertNull(LibraryQueries.parseSongSort("playcount"))
        assertNull(LibraryQueries.parseSongSort("albumName"))

        assertNull(LibraryQueries.parseCollectionFilter("all"))
        assertEquals(CollectionFilter.LIBRARY, LibraryQueries.parseCollectionFilter(null))
        assertEquals(CollectionFilter.BOOKMARKED, LibraryQueries.parseCollectionFilter("bookmarked"))
        assertEquals(CollectionFilter.DISLIKED, LibraryQueries.parseCollectionFilter("disliked"))
    }

    @Test
    fun `playlists filter defaults to all and accepts pinned, rewind and youtube`() {
        assertEquals(PlaylistsFilter.ALL, LibraryQueries.parsePlaylistsFilter(null))
        assertEquals(PlaylistsFilter.ALL, LibraryQueries.parsePlaylistsFilter("all"))
        assertEquals(PlaylistsFilter.PINNED, LibraryQueries.parsePlaylistsFilter("pinned"))
        assertEquals(PlaylistsFilter.REWIND, LibraryQueries.parsePlaylistsFilter("rewind"))
        assertEquals(PlaylistsFilter.YOUTUBE, LibraryQueries.parsePlaylistsFilter("youtube"))
        assertNull(LibraryQueries.parsePlaylistsFilter("favorites"))
    }

    @Test
    fun `top period is absent by default and rejects unknown ones`() {
        assertNull(LibraryQueries.parseTopPeriod(null))
        assertEquals(TopPeriod.TODAY, LibraryQueries.parseTopPeriod("today"))
        assertEquals(TopPeriod.WEEK, LibraryQueries.parseTopPeriod("week"))
        assertEquals(TopPeriod.MONTH, LibraryQueries.parseTopPeriod("month"))
        assertEquals(TopPeriod.THREE_MONTHS, LibraryQueries.parseTopPeriod("3months"))
        assertEquals(TopPeriod.SIX_MONTHS, LibraryQueries.parseTopPeriod("6months"))
        assertEquals(TopPeriod.YEAR, LibraryQueries.parseTopPeriod("year"))
        assertEquals(TopPeriod.ALL_TIME, LibraryQueries.parseTopPeriod("all"))
        assertNull(LibraryQueries.parseTopPeriod("weekly"))
    }

    @Test
    fun `playlist songs sort defaults to the position order and rejects unknown ones`() {
        assertEquals(PlaylistSongSort.CUSTOM, LibraryQueries.parsePlaylistSongSort(null))
        assertEquals(PlaylistSongSort.TITLE, LibraryQueries.parsePlaylistSongSort("title"))
        assertEquals(PlaylistSongSort.ARTIST_AND_ALBUM, LibraryQueries.parsePlaylistSongSort("artistAndAlbum"))
        assertEquals(PlaylistSongSort.ALBUM_YEAR, LibraryQueries.parsePlaylistSongSort("albumYear"))
        assertNull(LibraryQueries.parsePlaylistSongSort("rewindTop"))
        assertNull(LibraryQueries.parsePlaylistSongSort("name"))
    }

    @Test
    fun `artwork size defaults to 544 and must stay within 64-1200`() {
        assertEquals(544, LibraryQueries.parseArtworkSize(null))
        assertEquals(64, LibraryQueries.parseArtworkSize("64"))
        assertEquals(1200, LibraryQueries.parseArtworkSize("1200"))
        assertNull(LibraryQueries.parseArtworkSize("63"))
        assertNull(LibraryQueries.parseArtworkSize("1201"))
        assertNull(LibraryQueries.parseArtworkSize("big"))
    }

    @Test
    fun `playlist id must be decimal`() {
        assertEquals(12L, LibraryQueries.parsePlaylistId("12"))
        assertNull(LibraryQueries.parsePlaylistId("-1"))
        assertNull(LibraryQueries.parsePlaylistId("+1"))
        assertNull(LibraryQueries.parsePlaylistId("1a"))
        assertNull(LibraryQueries.parsePlaylistId(""))
        assertNull(LibraryQueries.parsePlaylistId(null))
    }

    @Test
    fun `the search matches title or artists case-insensitively and null keeps every song`() {
        assertEquals(listOf("id-a"), ids(LibraryQueries.searchSongs(songs, "ALP")))
        assertEquals(listOf("local:1"), ids(LibraryQueries.searchSongs(songs, "abb")))
        assertEquals(listOf("id-b"), ids(LibraryQueries.searchSongs(songs, "zed")))
        assertEquals(emptyList<String>(), ids(LibraryQueries.searchSongs(songs, "nothing")))
        assertEquals(4, LibraryQueries.searchSongs(songs, null).size)
    }

    @Test
    fun `paging to the end returns every item exactly once`() {
        val items = (1..437).map { "item-$it" }
        val collected = mutableListOf<String>()
        var offset = 0
        while (true) {
            val page = LibraryQueries.paginate(items, PageRequest(offset, 50))
            assertEquals(437, page.total)
            assertEquals(offset, page.offset)
            assertEquals(50, page.limit)
            if (page.items.isEmpty()) break
            collected += page.items
            offset += page.items.size
        }
        assertEquals(items, collected)
    }

    @Test
    fun `offset past the end gives an empty page with the total`() {
        val page = LibraryQueries.paginate(listOf(1, 2, 3), PageRequest(10, 50))
        assertEquals(emptyList<Int>(), page.items)
        assertEquals(3, page.total)
    }

    @Test
    fun `collection sorts default to the first value and reject unknown ones`() {
        assertEquals(AlbumSort.TITLE, LibraryQueries.parseAlbumSort(null))
        assertEquals(AlbumSort.YEAR, LibraryQueries.parseAlbumSort("year"))
        assertEquals(AlbumSort.PLAY_COUNT, LibraryQueries.parseAlbumSort("playCount"))
        assertNull(LibraryQueries.parseAlbumSort("name"))
        assertNull(LibraryQueries.parseAlbumSort("playcount"))

        assertEquals(ArtistSort.NAME, LibraryQueries.parseArtistSort(null))
        assertEquals(ArtistSort.CUSTOM, LibraryQueries.parseArtistSort("custom"))
        assertNull(LibraryQueries.parseArtistSort("title"))

        assertEquals(PlaylistSort.NAME, LibraryQueries.parsePlaylistSort(null))
        assertEquals(PlaylistSort.SONG_COUNT, LibraryQueries.parsePlaylistSort("songCount"))
        assertNull(LibraryQueries.parsePlaylistSort("songs"))
    }

    @Test
    fun `reverse defaults to false and only true or false are accepted`() {
        assertEquals(false, LibraryQueries.parseReverse(null))
        assertEquals(false, LibraryQueries.parseReverse("false"))
        assertEquals(true, LibraryQueries.parseReverse("true"))
        assertNull(LibraryQueries.parseReverse("True"))
        assertNull(LibraryQueries.parseReverse("1"))
        assertNull(LibraryQueries.parseReverse(""))
    }

    @Test
    fun `image type is sniffed from the bytes before the declared type`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.US_ASCII)
        assertEquals("image/jpeg", ImageTypes.contentTypeOf(jpeg, "image/png"))
        assertEquals("image/png", ImageTypes.contentTypeOf(png, null))
        assertEquals("image/webp", ImageTypes.contentTypeOf(webp, null))
        assertEquals("image/webp", ImageTypes.contentTypeOf(byteArrayOf(1, 2, 3), "image/WEBP; q=1"))
    }

    @Test
    fun `only jpeg, png and webp are ever served`() {
        assertNull(ImageTypes.contentTypeOf(byteArrayOf(1, 2, 3), "image/gif"))
        assertNull(ImageTypes.contentTypeOf(byteArrayOf(1, 2, 3), "image/svg+xml"))
        assertNull(ImageTypes.contentTypeOf(byteArrayOf(1, 2, 3), "text/html"))
        assertNull(ImageTypes.contentTypeOf(byteArrayOf(1, 2, 3), null))
    }
}
