package app.n_zik.android.bridge.library

import app.n_zik.android.bridge.AlbumDto
import app.n_zik.android.bridge.PlaylistDto
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

    private fun query(
        filter: String? = null,
        sort: String? = null,
        search: String? = null,
    ): SongsQuery = requireNotNull(LibraryQueries.parseSongsQuery(null, null, search, filter, sort))

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
    fun `query is trimmed and a blank one means no search`() {
        assertEquals("alp", LibraryQueries.parseSongsQuery(null, null, "  alp ", null, null)?.query)
        val blank = requireNotNull(LibraryQueries.parseSongsQuery(null, null, "   ", null, null))
        assertNull(blank.query)
        assertEquals(listOf("id-a"), ids(LibraryQueries.selectSongs(songs, query(search = " alp"))))
        // Surrounding spaces do not count towards the 100-character limit
        assertEquals("x".repeat(100), LibraryQueries.parseSongsQuery(null, null, " ${"x".repeat(100)} ", null, null)?.query)
    }

    @Test
    fun `unknown enum values and a query over 100 characters are rejected`() {
        assertNull(LibraryQueries.parseSongsQuery(null, null, null, "favorites", null))
        assertNull(LibraryQueries.parseSongsQuery(null, null, null, null, "duration"))
        assertNull(LibraryQueries.parseSongsQuery(null, null, "x".repeat(101), null, null))
        assertEquals("x".repeat(100), LibraryQueries.parseSongsQuery(null, null, "x".repeat(100), null, null)?.query)
        assertNull(LibraryQueries.parseCollectionFilter("all"))
        assertEquals(CollectionFilter.LIBRARY, LibraryQueries.parseCollectionFilter(null))
        assertEquals(CollectionFilter.BOOKMARKED, LibraryQueries.parseCollectionFilter("bookmarked"))
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
    fun `default sort is by title A to Z ignoring case`() {
        assertEquals(listOf("id-a", "id-b", "local:1", "id-d"), ids(LibraryQueries.selectSongs(songs, query())))
    }

    @Test
    fun `artist sort is A to Z and play time sort is descending`() {
        assertEquals(listOf("id-d", "local:1", "id-a", "id-b"), ids(LibraryQueries.selectSongs(songs, query(sort = "artist"))))
        assertEquals(listOf("id-a", "id-d", "id-b", "local:1"), ids(LibraryQueries.selectSongs(songs, query(sort = "playTime"))))
    }

    @Test
    fun `filters keep liked, local or downloaded tracks only`() {
        assertEquals(listOf("id-b", "id-d"), ids(LibraryQueries.selectSongs(songs, query(filter = "liked"))))
        assertEquals(listOf("local:1"), ids(LibraryQueries.selectSongs(songs, query(filter = "local"))))
        assertEquals(listOf("id-a"), ids(LibraryQueries.selectSongs(songs, query(filter = "downloaded"))))
        assertEquals(4, LibraryQueries.selectSongs(songs, query(filter = "all")).size)
    }

    @Test
    fun `query matches title or artists case-insensitively`() {
        assertEquals(listOf("id-a"), ids(LibraryQueries.selectSongs(songs, query(search = "ALP"))))
        assertEquals(listOf("local:1"), ids(LibraryQueries.selectSongs(songs, query(search = "abb"))))
        assertEquals(listOf("id-b"), ids(LibraryQueries.selectSongs(songs, query(search = "zed", filter = "liked"))))
        assertEquals(emptyList<String>(), ids(LibraryQueries.selectSongs(songs, query(search = "nothing"))))
    }

    @Test
    fun `equal titles are ordered by id so pages are stable`() {
        val twins = listOf(libSong("z", "Same"), libSong("a", "same"), libSong("m", "SAME"))
        assertEquals(listOf("a", "m", "z"), ids(LibraryQueries.selectSongs(twins, query())))
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
    fun `playlists and albums are sorted A to Z ignoring case`() {
        val playlists = listOf(
            PlaylistDto("2", "beta", 1, null),
            PlaylistDto("1", "Alpha", 3, "x"),
            PlaylistDto("3", "Rewind 2025", 0, null),
        )
        assertEquals(listOf("1", "2", "3"), LibraryQueries.sortPlaylists(playlists).map { it.id })
        val albums = listOf(
            AlbumDto("b", "zulu", null, null, 1, false),
            AlbumDto("a", "Echo", "X", "2021", 2, true),
        )
        assertEquals(listOf("a", "b"), LibraryQueries.sortAlbums(albums).map { it.id })
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
