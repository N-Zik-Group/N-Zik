package app.n_zik.android.bridge.library

import app.n_zik.android.bridge.AlbumDto
import app.n_zik.android.bridge.AlbumLike
import app.n_zik.android.bridge.ArtistDto
import app.n_zik.android.bridge.ArtistFollow
import app.n_zik.android.bridge.AuthResult
import app.n_zik.android.bridge.BridgeJson
import app.n_zik.android.bridge.BridgeServerCore
import app.n_zik.android.bridge.CacheSpaceDto
import app.n_zik.android.bridge.DislikeModeDto
import app.n_zik.android.bridge.DeviceAuthenticator
import app.n_zik.android.bridge.LibraryCacheDto
import app.n_zik.android.bridge.PlaylistDto
import app.n_zik.android.bridge.PlaylistOrigin
import app.n_zik.android.bridge.RewindStateDto
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.TrackLike
import app.n_zik.android.bridge.state.TrackSource
import app.n_zik.android.core.rewind.RewindPlaylists
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val TOKEN = "valid-token"

/** The full request of `GET /library/songs`: filter, sort, direction and the Top period. */
private data class SongQuery(val filter: SongFilter, val sort: SongSort, val reverse: Boolean, val period: TopPeriod?)

/** In-memory library standing in for the phone's database: it sorts like the phone does. */
private class FakeLibrary : LibraryProvider {
    val calls = mutableListOf<String>()
    var albumFilter: CollectionFilter? = null
    var artworkSize: Int? = null
    var playlistRequest: Triple<PlaylistsFilter, PlaylistSort, Boolean>? = null
    var playlistRewind: RewindPlaylists.Filter? = RewindPlaylists.Filter.Month
    var playlistCalls = 0
    var albumRequest: Triple<CollectionFilter, AlbumSort, Boolean>? = null
    var artistRequest: Triple<CollectionFilter, ArtistSort, Boolean>? = null
    var songRequest: SongQuery? = null
    var playlistSongsRequest: Pair<PlaylistSongSort, Boolean>? = null

    val songList = listOf(
        libSong("id-b", "Bravo", liked = true),
        libSong("id-a", "alpha"),
        libSong("local:1", "Charlie"),
    )

    override suspend fun songs(filter: SongFilter, sort: SongSort, reverse: Boolean, period: TopPeriod?): List<TrackDto> {
        calls += "songs"
        songRequest = SongQuery(filter, sort, reverse, period)
        val kept = when (filter) {
            SongFilter.ALL -> songList
            SongFilter.LIKED -> songList.filter { it.isLiked }
            SongFilter.LOCAL -> songList.filter { it.source == TrackSource.LOCAL }
            SongFilter.DOWNLOADED -> songList.filter { it.isDownloaded }
            // The fake keeps no disliked songs, no cache and no play events
            SongFilter.DISLIKED -> emptyList()
            SongFilter.OFFLINE -> emptyList()
            SongFilter.TOP -> songList.reversed()
        }
        return kept.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }).let { if (reverse) it.reversed() else it }
    }

    var sortMenuFilter: SongFilter? = null
    var sortMenuResult: List<String> = listOf("playCount", "title", "dateAdded")

    override suspend fun songsSortMenu(filter: SongFilter): List<String> {
        calls += "songsSortMenu"
        sortMenuFilter = filter
        return sortMenuResult
    }

    override suspend fun playlists(filter: PlaylistsFilter, sort: PlaylistSort, reverse: Boolean, rewindFilter: RewindPlaylists.Filter?): List<PlaylistDto> {
        calls += "playlists"
        playlistCalls++
        playlistRequest = Triple(filter, sort, reverse)
        playlistRewind = rewindFilter
        return listOf(
            PlaylistDto("7", "Zebra", 2, "id-a", origin = PlaylistOrigin.YTMUSIC, isPinned = true, isBookmarked = true, browseId = "VLCOMPLEX"),
            PlaylistDto("3", "apple", 0, null),
        ).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).let { if (reverse) it.reversed() else it }
    }

    override suspend fun playlistSongs(playlistId: Long, sort: PlaylistSongSort, reverse: Boolean): List<TrackDto>? {
        calls += "playlistSongs"
        playlistSongsRequest = sort to reverse
        // The fake's playlist 7 holds two known tracks with known durations (the header sum)
        return if (playlistId == 7L) listOf(
            libSong("local:1", "Charlie", durationMs = 120_000L),
            libSong("id-b", "Bravo", durationMs = 200_000L, liked = true),
        ) else null
    }

    override suspend fun albums(filter: CollectionFilter, sort: AlbumSort, reverse: Boolean): List<AlbumDto> {
        calls += "albums"
        albumFilter = filter
        albumRequest = Triple(filter, sort, reverse)
        return listOf(
            AlbumDto("MPREb_2", "Zulu", "B", "2020", 3, false),
            AlbumDto("MPREb_1", "echo", null, null, 1, true),
        ).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }).let { if (reverse) it.reversed() else it }
    }

    override suspend fun albumSongs(albumId: String): List<TrackDto>? =
        calls.add("albumSongs").let { if (albumId == "MPREb_1") listOf(songList[1]) else null }

    override suspend fun artists(filter: CollectionFilter, sort: ArtistSort, reverse: Boolean): List<ArtistDto> {
        calls += "artists"
        artistRequest = Triple(filter, sort, reverse)
        return listOf(ArtistDto("UC2", "yann", 4, true), ArtistDto("UC1", "Abba", 1, false))
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).let { if (reverse) it.reversed() else it }
    }

    override suspend fun artistSongs(artistId: String): List<TrackDto>? =
        calls.add("artistSongs").let { if (artistId == "UC1") listOf(songList[0]) else null }

    override suspend fun trackArtwork(trackId: String, size: Int): ArtworkResult {
        calls += "trackArtwork"
        artworkSize = size
        return when (trackId) {
            "id-a" -> ArtworkResult.Image(PNG, "image/png")
            "local:1" -> ArtworkResult.Image(JPEG, "image/jpeg")
            "id-b" -> ArtworkResult.UpstreamFailed
            else -> ArtworkResult.NotFound
        }
    }

    override suspend fun albumArtwork(albumId: String, size: Int): ArtworkResult =
        calls.add("albumArtwork").let { if (albumId == "MPREb_1") ArtworkResult.Image(JPEG, "image/jpeg") else ArtworkResult.NotFound }

    override suspend fun artistArtwork(artistId: String, size: Int): ArtworkResult =
        calls.add("artistArtwork").let { if (artistId == "UC2") ArtworkResult.UpstreamFailed else ArtworkResult.NotFound }

    override suspend fun playlistArtwork(playlistId: Long): ArtworkResult =
        calls.add("playlistArtwork").let { if (playlistId == 7L) ArtworkResult.Image(PNG, "image/png") else ArtworkResult.NotFound }

    var cacheSpaceRequest = 0

    override suspend fun cacheSpace(): LibraryCacheDto {
        cacheSpaceRequest++
        return LibraryCacheDto(
            cached = CacheSpaceDto(usedBytes = 1_000L, maxBytes = 2_000L, maxText = "2GB"),
            // The download cap is unlimited on this fake (the phone hides its bar then)
            downloaded = CacheSpaceDto(usedBytes = 3_000L, maxBytes = null, maxText = null),
        )
    }

    override suspend fun rewindState(): RewindStateDto =
        calls.add("rewindState").let { RewindStateDto(monthlyEnabled = true, yearlyEnabled = false, filter = "year") }

    override suspend fun dislikeMode(): DislikeModeDto =
        calls.add("dislikeMode").let { DislikeModeDto(songs = true, albums = false, artists = true) }

    // Writes (contract §10.2, since 1.7): `null` when the target is unknown, like the reads
    val songLikes = linkedMapOf("id-a" to TrackLike.NEUTRAL, "id-b" to TrackLike.LIKED, "local:1" to TrackLike.NEUTRAL)
    val albumBookmarks = linkedMapOf("MPREb_1" to false, "MPREb_2" to false)
    val albumLikes = linkedMapOf("MPREb_1" to AlbumLike.NEUTRAL, "MPREb_2" to AlbumLike.BOOKMARKED)
    val artistFollows = linkedMapOf("UC1" to ArtistFollow.NEUTRAL, "UC2" to ArtistFollow.NEUTRAL)
    val playlistPins = linkedMapOf(3L to false, 7L to false)
    val playlistBookmarks = linkedMapOf(3L to false, 7L to true)

    override suspend fun setSongLike(songId: String, state: TrackLike): TrackLike? {
        calls += "setSongLike"
        return if (songLikes.containsKey(songId)) { songLikes[songId] = state; state } else null
    }

    override suspend fun setAlbumBookmark(albumId: String, bookmarked: Boolean): Boolean? {
        calls += "setAlbumBookmark"
        return if (albumBookmarks.containsKey(albumId)) { albumBookmarks[albumId] = bookmarked; bookmarked } else null
    }

    override suspend fun setArtistFollow(artistId: String, state: ArtistFollow): ArtistFollow? {
        calls += "setArtistFollow"
        return if (artistFollows.containsKey(artistId)) { artistFollows[artistId] = state; state } else null
    }

    override suspend fun setAlbumLike(albumId: String, state: AlbumLike): AlbumLike? {
        calls += "setAlbumLike"
        return if (albumLikes.containsKey(albumId)) { albumLikes[albumId] = state; state } else null
    }

    override suspend fun setPlaylistPin(playlistId: Long, pinned: Boolean): Boolean? {
        calls += "setPlaylistPin"
        return if (playlistPins.containsKey(playlistId)) { playlistPins[playlistId] = pinned; pinned } else null
    }

    override suspend fun setPlaylistBookmark(playlistId: Long, bookmarked: Boolean): Boolean? {
        calls += "setPlaylistBookmark"
        return if (playlistBookmarks.containsKey(playlistId)) { playlistBookmarks[playlistId] = bookmarked; bookmarked } else null
    }

    companion object {
        val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    }
}

class LibraryRoutesTest {

    private val acceptOnly = DeviceAuthenticator { token ->
        if (token == TOKEN) AuthResult.Accepted(deviceId = "test-device") else AuthResult.Revoked
    }

    private fun ApplicationTestBuilder.mount(library: LibraryProvider) {
        application { BridgeServerCore(serverName = "Pixel test", authenticator = acceptOnly, libraryProvider = library).install(this) }
    }

    private suspend fun ApplicationTestBuilder.getAuthed(path: String): HttpResponse =
        client.get(path) { bearerAuth(TOKEN) }

    private suspend fun HttpResponse.json(): JsonObject = BridgeJson.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.errorCode(): String? = json()["code"]?.jsonPrimitive?.content

    private fun JsonObject.itemIds(key: String = "id"): List<String> =
        this.getValue("items").jsonArray.map { it.jsonObject.getValue(key).jsonPrimitive.content }

    @Test
    fun `songs filter, sort and reverse pass through to the provider`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        getAuthed("/api/v1/library/songs")
        assertEquals(SongQuery(SongFilter.ALL, SongSort.TITLE, false, null), library.songRequest)
        getAuthed("/api/v1/library/songs?filter=liked&sort=playCount&reverse=true")
        assertEquals(SongQuery(SongFilter.LIKED, SongSort.PLAY_COUNT, true, null), library.songRequest)
        getAuthed("/api/v1/library/songs?filter=downloaded&sort=downloaded")
        assertEquals(SongQuery(SongFilter.DOWNLOADED, SongSort.DOWNLOADED, false, null), library.songRequest)
        getAuthed("/api/v1/library/songs?filter=disliked")
        assertEquals(SongQuery(SongFilter.DISLIKED, SongSort.TITLE, false, null), library.songRequest)
        getAuthed("/api/v1/library/songs?filter=offline&sort=duration")
        assertEquals(SongQuery(SongFilter.OFFLINE, SongSort.DURATION, false, null), library.songRequest)
        getAuthed("/api/v1/library/songs?filter=top")
        assertEquals(SongQuery(SongFilter.TOP, SongSort.TITLE, false, null), library.songRequest)
        // The period selector of the phone's Top tab overrides the phone's own period
        getAuthed("/api/v1/library/songs?filter=top&period=week")
        assertEquals(SongQuery(SongFilter.TOP, SongSort.TITLE, false, TopPeriod.WEEK), library.songRequest)
        getAuthed("/api/v1/library/songs?filter=top&period=3months")
        assertEquals(SongQuery(SongFilter.TOP, SongSort.TITLE, false, TopPeriod.THREE_MONTHS), library.songRequest)

        getAuthed("/api/v1/library/playlists/7/songs")
        assertEquals(PlaylistSongSort.CUSTOM to false, library.playlistSongsRequest)
        getAuthed("/api/v1/library/playlists/7/songs?sort=albumYear&reverse=true")
        assertEquals(PlaylistSongSort.ALBUM_YEAR to true, library.playlistSongsRequest)
    }

    @Test
    fun `songs are paginated and sorted by title with the total after filters`() = testApplication {
        mount(FakeLibrary())

        val first = getAuthed("/api/v1/library/songs?offset=0&limit=2")
        assertEquals(HttpStatusCode.OK, first.status)
        val page = first.json()
        assertEquals(listOf("id-a", "id-b"), page.itemIds())
        assertEquals(3, page.getValue("total").jsonPrimitive.int)
        assertEquals(0, page.getValue("offset").jsonPrimitive.int)
        assertEquals(2, page.getValue("limit").jsonPrimitive.int)

        val second = getAuthed("/api/v1/library/songs?offset=2&limit=2").json()
        assertEquals(listOf("local:1"), second.itemIds())

        val liked = getAuthed("/api/v1/library/songs?filter=liked").json()
        assertEquals(listOf("id-b"), liked.itemIds())
        assertEquals(1, liked.getValue("total").jsonPrimitive.int)
        assertTrue(liked.getValue("items").jsonArray[0].jsonObject.getValue("isLiked").jsonPrimitive.boolean)

        val searched = getAuthed("/api/v1/library/songs?query=CHAR&filter=local&sort=artist").json()
        assertEquals(listOf("local:1"), searched.itemIds())
    }

    @Test
    fun `invalid library parameters are BAD_REQUEST and never read the library`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        for (path in listOf(
            "/api/v1/library/songs?limit=0",
            "/api/v1/library/songs?limit=201",
            "/api/v1/library/songs?offset=-1",
            "/api/v1/library/songs?filter=unknown",
            "/api/v1/library/songs?sort=unknown",
            "/api/v1/library/songs?sort=title&reverse=nope",
            "/api/v1/library/songs?query=${"a".repeat(101)}",
            "/api/v1/library/songs?period=weekly",
            "/api/v1/library/playlists?limit=abc",
            "/api/v1/library/playlists?filter=favorites",
            "/api/v1/library/albums?filter=all",
            "/api/v1/library/artists?filter=liked",
            "/api/v1/library/albums?sort=name",
            "/api/v1/library/albums?sort=playTime",
            "/api/v1/library/artists?sort=title",
            "/api/v1/library/playlists?sort=songs",
            "/api/v1/library/albums?reverse=1",
            "/api/v1/library/playlists?reverse=maybe",
            "/api/v1/artwork/id-a?size=10",
            "/api/v1/library/albums/MPREb_1/artwork?size=5000",
            "/api/v1/library/songs?offset=%2B1",
            "/api/v1/library/playlists/7/songs?limit=0",
            "/api/v1/library/playlists/7/songs?sort=rewindTop",
            "/api/v1/library/playlists/7/songs?reverse=yes",
            "/api/v1/library/albums/MPREb_1/songs?offset=-2",
            "/api/v1/library/artists/UC1/songs?limit=500",
            "/api/v1/library/artists/UC2/artwork?size=abc",
        )) {
            val response = getAuthed(path)
            assertEquals(HttpStatusCode.BadRequest, response.status, path)
            assertEquals("BAD_REQUEST", response.errorCode(), path)
        }
        assertTrue(library.calls.isEmpty())
    }

    @Test
    fun `playlists are sorted by name and their songs keep the playlist order`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val playlists = getAuthed("/api/v1/library/playlists").json()
        assertEquals(listOf("3", "7"), playlists.itemIds())
        val zebra = playlists.getValue("items").jsonArray[1].jsonObject
        assertEquals(2, zebra.getValue("trackCount").jsonPrimitive.int)
        assertEquals("id-a", zebra.getValue("artworkTrackId").jsonPrimitive.content)

        val songs = getAuthed("/api/v1/library/playlists/7/songs").json()
        assertEquals(listOf("local:1", "id-b"), songs.itemIds())
        assertEquals(PlaylistSongSort.CUSTOM to false, library.playlistSongsRequest)
    }

    @Test
    fun `unknown or non-decimal playlist, album or artist is NOT_FOUND`() = testApplication {
        mount(FakeLibrary())

        for (path in listOf(
            "/api/v1/library/playlists/99/songs",
            "/api/v1/library/playlists/abc/songs",
            "/api/v1/library/playlists/-7/songs",
            "/api/v1/library/albums/unknown/songs",
            "/api/v1/library/artists/unknown/songs",
        )) {
            val response = getAuthed(path)
            assertEquals(HttpStatusCode.NotFound, response.status, path)
            assertEquals("NOT_FOUND", response.errorCode(), path)
        }
    }

    @Test
    fun `collection sort and reverse default to ascending name and pass through to the provider`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        getAuthed("/api/v1/library/playlists")
        assertEquals(Triple(PlaylistsFilter.ALL, PlaylistSort.NAME, false), library.playlistRequest)
        getAuthed("/api/v1/library/albums")
        assertEquals(Triple(CollectionFilter.LIBRARY, AlbumSort.TITLE, false), library.albumRequest)
        getAuthed("/api/v1/library/artists")
        assertEquals(Triple(CollectionFilter.LIBRARY, ArtistSort.NAME, false), library.artistRequest)

        getAuthed("/api/v1/library/playlists?sort=playCount&reverse=true")
        assertEquals(Triple(PlaylistsFilter.ALL, PlaylistSort.PLAY_COUNT, true), library.playlistRequest)
        getAuthed("/api/v1/library/albums?filter=bookmarked&sort=year&reverse=true")
        assertEquals(Triple(CollectionFilter.BOOKMARKED, AlbumSort.YEAR, true), library.albumRequest)
        getAuthed("/api/v1/library/artists?sort=custom&reverse=false")
        assertEquals(Triple(CollectionFilter.LIBRARY, ArtistSort.CUSTOM, false), library.artistRequest)

        // The home-tab chips of the phone: the filter travels to the provider
        getAuthed("/api/v1/library/playlists?filter=pinned")
        assertEquals(Triple(PlaylistsFilter.PINNED, PlaylistSort.NAME, false), library.playlistRequest)
        getAuthed("/api/v1/library/playlists?filter=rewind&sort=songCount")
        assertEquals(Triple(PlaylistsFilter.REWIND, PlaylistSort.SONG_COUNT, false), library.playlistRequest)
        getAuthed("/api/v1/library/playlists?filter=youtube&reverse=true")
        assertEquals(Triple(PlaylistsFilter.YOUTUBE, PlaylistSort.NAME, true), library.playlistRequest)
        getAuthed("/api/v1/library/albums?filter=disliked")
        assertEquals(Triple(CollectionFilter.DISLIKED, AlbumSort.TITLE, false), library.albumRequest)
        getAuthed("/api/v1/library/artists?filter=disliked&sort=custom")
        assertEquals(Triple(CollectionFilter.DISLIKED, ArtistSort.CUSTOM, false), library.artistRequest)

        // The provider answers already sorted; reverse=true reverses its order
        assertEquals(listOf("7", "3"), getAuthed("/api/v1/library/playlists?reverse=true").json().itemIds())
        assertEquals(listOf("MPREb_2", "MPREb_1"), getAuthed("/api/v1/library/albums?reverse=true").json().itemIds())
        assertEquals(listOf("UC2", "UC1"), getAuthed("/api/v1/library/artists?reverse=true").json().itemIds())
    }

    @Test
    fun `playlists accept a text search that keeps the pre-text total`() = testApplication {
        mount(FakeLibrary())

        val all = getAuthed("/api/v1/library/playlists").json()
        assertEquals(2, all.getValue("total").jsonPrimitive.int)

        val searched = getAuthed("/api/v1/library/playlists?text=zebra").json()
        // total keeps the pre-search count; items hold the matched listing
        assertEquals(2, searched.getValue("total").jsonPrimitive.int)
        assertEquals(listOf("7"), searched.itemIds())

        val blank = getAuthed("/api/v1/library/playlists?text=   ").json()
        assertEquals(2, blank.getValue("total").jsonPrimitive.int)
    }

    @Test
    fun `the rewind filter is applied to the provider and an unknown one is rejected`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        getAuthed("/api/v1/library/playlists?filter=rewind&rewind=year")
        assertEquals(RewindPlaylists.Filter.Year, library.playlistRewind)
        getAuthed("/api/v1/library/playlists?filter=rewind&rewind=all")
        assertEquals(RewindPlaylists.Filter.All, library.playlistRewind)
        // Absent `rewind` reaches the provider as `null` (the phone keeps its own setting)
        getAuthed("/api/v1/library/playlists?filter=rewind")
        assertNull(library.playlistRewind)

        val bad = getAuthed("/api/v1/library/playlists?rewind=week")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals("BAD_REQUEST", bad.errorCode())
        // The rejected call never reaches the provider: 3 valid calls
        assertEquals(3, library.playlistCalls)
    }

    @Test
    fun `the rewind state and dislike mode routes answer the phone settings`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val rewind = getAuthed("/api/v1/library/rewind").json()
        assertEquals(true, rewind.getValue("monthlyEnabled").jsonPrimitive.boolean)
        assertEquals(false, rewind.getValue("yearlyEnabled").jsonPrimitive.boolean)
        assertEquals("year", rewind.getValue("filter").jsonPrimitive.content)

        val mode = getAuthed("/api/v1/library/dislikeMode").json()
        assertEquals(true, mode.getValue("songs").jsonPrimitive.boolean)
        assertEquals(false, mode.getValue("albums").jsonPrimitive.boolean)
        assertEquals(true, mode.getValue("artists").jsonPrimitive.boolean)

        assertTrue("rewindState" in library.calls)
        assertTrue("dislikeMode" in library.calls)
    }

    @Test
    fun `playlist songs carry the full-list duration and keep the pre-text total`() = testApplication {
        mount(FakeLibrary())

        val all = getAuthed("/api/v1/library/playlists/7/songs").json()
        assertEquals(2, all.getValue("total").jsonPrimitive.int)
        assertEquals(320_000L, all.getValue("totalDurationMs").jsonPrimitive.long)

        val searched = getAuthed("/api/v1/library/playlists/7/songs?text=charlie").json()
        assertEquals(2, searched.getValue("total").jsonPrimitive.int)
        assertEquals(listOf("local:1"), searched.itemIds())
        // The duration is the whole list's, apart from the text filter
        assertEquals(320_000L, searched.getValue("totalDurationMs").jsonPrimitive.long)

        val bad = getAuthed("/api/v1/library/playlists/7/songs?text=${"x".repeat(101)}")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
    }

    @Test
    fun `the playlist custom artwork route serves the cover bytes or 404s without one`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val cover = getAuthed("/api/v1/library/playlists/7/artwork")
        assertEquals(HttpStatusCode.OK, cover.status)
        assertEquals("image/png", cover.headers[HttpHeaders.ContentType])
        assertEquals("private, max-age=86400", cover.headers[HttpHeaders.CacheControl])
        assertArrayEquals(FakeLibrary.PNG, cover.readRawBytes())

        assertEquals(HttpStatusCode.NotFound, getAuthed("/api/v1/library/playlists/3/artwork").status)
        assertEquals(HttpStatusCode.NotFound, getAuthed("/api/v1/library/playlists/abc/artwork").status)
    }

    @Test
    fun `album like writes set the explicit tri-state on the phone`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val disliked = client.post("/api/v1/library/albums/MPREb_1/like") {
            bearerAuth(TOKEN); setBody("""{"state":"disliked"}""")
        }
        assertEquals(HttpStatusCode.OK, disliked.status)
        assertEquals("disliked", disliked.json().getValue("state").jsonPrimitive.content)
        assertEquals(AlbumLike.DISLIKED, library.albumLikes["MPREb_1"])

        val bookmarked = client.post("/api/v1/library/albums/MPREb_2/like") {
            bearerAuth(TOKEN); setBody("""{"state":"bookmarked"}""")
        }
        assertEquals(HttpStatusCode.OK, bookmarked.status)
        assertEquals("bookmarked", bookmarked.json().getValue("state").jsonPrimitive.content)

        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/library/albums/MPREb_9/like") {
            bearerAuth(TOKEN); setBody("""{"state":"neutral"}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/library/albums/MPREb_1/like") {
            bearerAuth(TOKEN); setBody("""{"state":"bogus"}""")
        }.status)
        // The rejected writes left the states untouched
        assertEquals(AlbumLike.DISLIKED, library.albumLikes["MPREb_1"])
        assertEquals(AlbumLike.BOOKMARKED, library.albumLikes["MPREb_2"])
    }

    @Test
    fun `playlist bookmark writes set the phone isYoutubePlaylist column`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val off = client.post("/api/v1/library/playlists/7/bookmark") {
            bearerAuth(TOKEN); setBody("""{"bookmarked":false}""")
        }
        assertEquals(HttpStatusCode.OK, off.status)
        assertEquals(false, off.json().getValue("bookmarked").jsonPrimitive.boolean)
        assertEquals(false, library.playlistBookmarks[7L])

        val on = client.post("/api/v1/library/playlists/3/bookmark") {
            bearerAuth(TOKEN); setBody("""{"bookmarked":true}""")
        }
        assertEquals(HttpStatusCode.OK, on.status)
        assertEquals(true, on.json().getValue("bookmarked").jsonPrimitive.boolean)

        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/library/playlists/99/bookmark") {
            bearerAuth(TOKEN); setBody("""{"bookmarked":true}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/library/playlists/abc/bookmark") {
            bearerAuth(TOKEN); setBody("""{"bookmarked":true}""")
        }.status)
    }

    @Test
    fun `albums and artists are sorted A to Z with their songs and the library filter by default`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val albums = getAuthed("/api/v1/library/albums").json()
        assertEquals(listOf("MPREb_1", "MPREb_2"), albums.itemIds())
        assertEquals(CollectionFilter.LIBRARY, library.albumFilter)
        getAuthed("/api/v1/library/albums?filter=bookmarked")
        assertEquals(CollectionFilter.BOOKMARKED, library.albumFilter)

        val artists = getAuthed("/api/v1/library/artists?filter=library").json()
        assertEquals(listOf("UC1", "UC2"), artists.itemIds())

        assertEquals(listOf("id-a"), getAuthed("/api/v1/library/albums/MPREb_1/songs").json().itemIds())
        assertEquals(listOf("id-b"), getAuthed("/api/v1/library/artists/UC1/songs").json().itemIds())
    }

    @Test
    fun `artwork returns the image bytes, its real type and the cache header`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val online = getAuthed("/api/v1/artwork/id-a?size=300")
        assertEquals(HttpStatusCode.OK, online.status)
        assertEquals("image/png", online.headers[HttpHeaders.ContentType])
        assertEquals("private, max-age=86400", online.headers[HttpHeaders.CacheControl])
        assertArrayEquals(FakeLibrary.PNG, online.readRawBytes())
        assertEquals(300, library.artworkSize)

        val local = getAuthed("/api/v1/artwork/local%3A1")
        assertEquals(HttpStatusCode.OK, local.status)
        assertEquals("image/jpeg", local.headers[HttpHeaders.ContentType])
        assertEquals(544, library.artworkSize)

        val album = getAuthed("/api/v1/library/albums/MPREb_1/artwork")
        assertEquals(HttpStatusCode.OK, album.status)
        assertEquals("private, max-age=86400", album.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `missing artwork is NOT_FOUND and an unreachable upstream is AUDIO_UPSTREAM_FAILED`() = testApplication {
        mount(FakeLibrary())

        val missing = getAuthed("/api/v1/artwork/unknown")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("NOT_FOUND", missing.errorCode())

        val upstream = getAuthed("/api/v1/artwork/id-b")
        assertEquals(HttpStatusCode.BadGateway, upstream.status)
        assertEquals("AUDIO_UPSTREAM_FAILED", upstream.errorCode())

        assertEquals(HttpStatusCode.NotFound, getAuthed("/api/v1/library/artists/UC1/artwork").status)
        assertEquals(HttpStatusCode.BadGateway, getAuthed("/api/v1/library/artists/UC2/artwork").status)
    }

    @Test
    fun `library and artwork routes check the Bearer token before anything else`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        for (path in listOf(
            "/api/v1/library/songs",
            "/api/v1/library/playlists",
            "/api/v1/library/playlists/7/songs",
            "/api/v1/library/albums",
            "/api/v1/library/albums/MPREb_1/songs",
            "/api/v1/library/albums/MPREb_1/artwork",
            "/api/v1/library/artists",
            "/api/v1/library/artists/UC1/songs",
            "/api/v1/library/artists/UC2/artwork",
            "/api/v1/library/cache",
            "/api/v1/artwork/id-a",
        )) {
            val missing = client.get(path)
            assertEquals(HttpStatusCode.Unauthorized, missing.status, path)
            assertEquals("UNAUTHORIZED", missing.errorCode(), path)
            val revoked = client.get(path) { bearerAuth("revoked-token") }
            assertEquals(HttpStatusCode.Unauthorized, revoked.status, path)
            assertEquals("DEVICE_REVOKED", revoked.errorCode(), path)
        }
        // The §10.2 writes (since 1.7) answer the same auth errors before touching the library
        for (path in listOf(
            "/api/v1/library/songs/id-a/like",
            "/api/v1/library/albums/MPREb_1/bookmark",
            "/api/v1/library/artists/UC1/follow",
            "/api/v1/library/playlists/7/pin",
        )) {
            val missing = client.post(path) { setBody("{}") }
            assertEquals(HttpStatusCode.Unauthorized, missing.status, path)
            assertEquals("UNAUTHORIZED", missing.errorCode(), path)
            val revoked = client.post(path) { bearerAuth("revoked-token"); setBody("{}") }
            assertEquals(HttpStatusCode.Unauthorized, revoked.status, path)
            assertEquals("DEVICE_REVOKED", revoked.errorCode(), path)
        }
        assertTrue(library.calls.isEmpty())
        assertEquals(null, library.artworkSize)
    }

    @Test
    fun `song like write answers the resulting state and writes the fake library`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val response = client.post("/api/v1/library/songs/id-a/like") {
            bearerAuth(TOKEN)
            setBody("""{"state":"disliked"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("disliked", response.json()["state"]?.jsonPrimitive?.content)
        assertEquals(TrackLike.DISLIKED, library.songLikes["id-a"])
        assertTrue("setSongLike" in library.calls)

        val neutral = client.post("/api/v1/library/songs/id-a/like") {
            bearerAuth(TOKEN)
            setBody("""{"state":"neutral"}""")
        }
        assertEquals(HttpStatusCode.OK, neutral.status)
        assertEquals("neutral", neutral.json()["state"]?.jsonPrimitive?.content)
        assertEquals(TrackLike.NEUTRAL, library.songLikes["id-a"])
    }

    @Test
    fun `album bookmark and playlist pin answer the resulting booleans`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val album = client.post("/api/v1/library/albums/MPREb_1/bookmark") {
            bearerAuth(TOKEN)
            setBody("""{"bookmarked":true}""")
        }
        assertEquals(HttpStatusCode.OK, album.status)
        assertEquals(true, album.json()["bookmarked"]?.jsonPrimitive?.boolean)
        assertEquals(true, library.albumBookmarks["MPREb_1"])

        val pin = client.post("/api/v1/library/playlists/7/pin") {
            bearerAuth(TOKEN)
            setBody("""{"pinned":false}""")
        }
        assertEquals(HttpStatusCode.OK, pin.status)
        assertEquals(false, pin.json()["pinned"]?.jsonPrimitive?.boolean)
        assertEquals(false, library.playlistPins[7L])
    }

    @Test
    fun `artist follow write answers the resulting tri-state`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        for (state in listOf("followed", "disliked", "neutral")) {
            val response = client.post("/api/v1/library/artists/UC2/follow") {
                bearerAuth(TOKEN)
                setBody("""{"state":"$state"}""")
            }
            assertEquals(HttpStatusCode.OK, response.status, state)
            assertEquals(state, response.json()["state"]?.jsonPrimitive?.content, state)
        }
        assertEquals(ArtistFollow.NEUTRAL, library.artistFollows["UC2"])
        assertTrue("setArtistFollow" in library.calls)
    }

    @Test
    fun `write routes reject unknown targets and invalid bodies`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        // Unknown targets -> 404 NOT_FOUND
        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/library/songs/ghost/like") {
            bearerAuth(TOKEN); setBody("""{"state":"liked"}""")
        }.status)
        assertEquals("NOT_FOUND", client.post("/api/v1/library/songs/ghost/like") {
            bearerAuth(TOKEN); setBody("""{"state":"liked"}""")
        }.errorCode())
        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/library/albums/ghost/bookmark") {
            bearerAuth(TOKEN); setBody("""{"bookmarked":true}""")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/library/artists/ghost/follow") {
            bearerAuth(TOKEN); setBody("""{"state":"followed"}""")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/library/playlists/99/pin") {
            bearerAuth(TOKEN); setBody("""{"pinned":true}""")
        }.status)

        // Invalid bodies -> 400 BAD_REQUEST (no write happened)
        for (body in listOf("""{"state":"bogus"}""", """{"state":42}""", """not json""", """{"other":"liked"}""", "")) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/library/songs/id-a/like") {
                bearerAuth(TOKEN); setBody(body)
            }.status, "body $body")
        }
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/library/playlists/abc/pin") {
            bearerAuth(TOKEN); setBody("""{"pinned":true}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/library/playlists/7/pin") {
            bearerAuth(TOKEN); setBody("""{"pinned":"bogus"}""")
        }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/library/albums/MPREb_1/bookmark") {
            bearerAuth(TOKEN); setBody("""{"bookmarked":"bogus"}""")
        }.status)
        assertEquals(TrackLike.NEUTRAL, library.songLikes["id-a"])
        assertEquals(false, library.albumBookmarks["MPREb_1"])
        assertEquals(ArtistFollow.NEUTRAL, library.artistFollows["UC2"])
        assertEquals(false, library.playlistPins[7L])
    }

    @Test
    fun `songs carry the like tri-state and playlists their origin, pin and bookmark`() = testApplication {
        mount(FakeLibrary())

        val songs = getAuthed("/api/v1/library/songs").json()
        val tracks = songs.getValue("items").jsonArray.associate { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject }
        assertEquals("liked", tracks["id-b"]?.getValue("like")?.jsonPrimitive?.content)
        assertEquals(true, tracks["id-b"]?.getValue("isLiked")?.jsonPrimitive?.boolean)
        assertEquals("neutral", tracks["id-a"]?.getValue("like")?.jsonPrimitive?.content)
        assertEquals(false, tracks["id-a"]?.getValue("isLiked")?.jsonPrimitive?.boolean)
        assertEquals("neutral", tracks["local:1"]?.getValue("like")?.jsonPrimitive?.content)

        val playlists = getAuthed("/api/v1/library/playlists").json()
        val byName = playlists.getValue("items").jsonArray.associate { it.jsonObject.getValue("name").jsonPrimitive.content to it.jsonObject }
        val zebra = byName.getValue("Zebra")
        assertEquals("ytmusic", zebra.getValue("origin").jsonPrimitive.content)
        assertEquals(true, zebra.getValue("isPinned").jsonPrimitive.boolean)
        assertEquals(true, zebra.getValue("isBookmarked").jsonPrimitive.boolean)
        val apple = byName.getValue("apple")
        assertEquals("local", apple.getValue("origin").jsonPrimitive.content)
        assertEquals(false, apple.getValue("isPinned").jsonPrimitive.boolean)
        assertEquals(false, apple.getValue("isBookmarked").jsonPrimitive.boolean)
        // Since 1.7.2: the phone's raw browse id (the playlist-menu guards)
        assertEquals("VLCOMPLEX", zebra.getValue("browseId").jsonPrimitive.content)
        assertEquals(JsonNull, apple["browseId"])
    }

    @Test
    fun `cache route answers the phone used and configured caps`() = testApplication {
        val library = FakeLibrary()
        mount(library)

        val cache = getAuthed("/api/v1/library/cache").json()
        val cached = cache.getValue("cached").jsonObject
        assertEquals(1_000L, cached.getValue("usedBytes").jsonPrimitive.long)
        assertEquals(2_000L, cached.getValue("maxBytes").jsonPrimitive.long)
        // Since 1.7.2: the phone's label of its configured cap
        assertEquals("2GB", cached.getValue("maxText").jsonPrimitive.content)
        val downloaded = cache.getValue("downloaded").jsonObject
        assertEquals(3_000L, downloaded.getValue("usedBytes").jsonPrimitive.long)
        // maxBytes null = unlimited (the phone hides its bar in that case)
        assertEquals(JsonNull, downloaded["maxBytes"])
        assertEquals(JsonNull, downloaded["maxText"])
        assertEquals(1, library.cacheSpaceRequest)
    }

    @Test
    fun `the songs page carries the phone sort menu, the other pages do not`() = testApplication {
        val library = FakeLibrary()
        library.sortMenuResult = listOf("playCount", "title", "dateAdded")
        mount(library)

        val songs = getAuthed("/api/v1/library/songs?filter=top&sort=playCount").json()
        assertEquals(
            listOf("playCount", "title", "dateAdded"),
            songs.getValue("sortMenu").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(SongFilter.TOP, library.sortMenuFilter)

        // `sortMenu` rides on the songs pages only (contract §10.1)
        assertEquals(JsonNull, getAuthed("/api/v1/library/playlists").json()["sortMenu"])
        assertEquals(JsonNull, getAuthed("/api/v1/library/albums").json()["sortMenu"])
        assertEquals(JsonNull, getAuthed("/api/v1/library/artists").json()["sortMenu"])
        assertEquals(JsonNull, getAuthed("/api/v1/library/playlists/7/songs").json()["sortMenu"])
        assertEquals(JsonNull, getAuthed("/api/v1/library/albums/MPREb_1/songs").json()["sortMenu"])
    }

    @Test
    fun `server without a library answers empty pages`() = testApplication {
        application { BridgeServerCore(serverName = "Pixel test", authenticator = acceptOnly).install(this) }

        val page = getAuthed("/api/v1/library/songs").json()
        assertEquals(0, page.getValue("total").jsonPrimitive.int)
        assertEquals(HttpStatusCode.NotFound, getAuthed("/api/v1/artwork/id-a").status)
    }
}
