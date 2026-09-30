package app.n_zik.android.bridge.library

import app.n_zik.android.bridge.AlbumDto
import app.n_zik.android.bridge.ArtistDto
import app.n_zik.android.bridge.AuthResult
import app.n_zik.android.bridge.BridgeJson
import app.n_zik.android.bridge.BridgeServerCore
import app.n_zik.android.bridge.DeviceAuthenticator
import app.n_zik.android.bridge.PlaylistDto
import app.n_zik.android.bridge.state.TrackDto
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val TOKEN = "valid-token"

/** In-memory library standing in for the phone's database. */
private class FakeLibrary : LibraryProvider {
    val calls = mutableListOf<String>()
    var albumFilter: CollectionFilter? = null
    var artworkSize: Int? = null

    val songList = listOf(
        libSong("id-b", "Bravo", liked = true),
        libSong("id-a", "alpha"),
        libSong("local:1", "Charlie"),
    )

    override suspend fun songs(): List<LibrarySong> = songList.also { calls += "songs" }

    override suspend fun playlists(): List<PlaylistDto> = listOf(
        PlaylistDto("7", "Zebra", 2, "id-a"),
        PlaylistDto("3", "apple", 0, null),
    ).also { calls += "playlists" }

    override suspend fun playlistSongs(playlistId: Long): List<TrackDto>? =
        calls.add("playlistSongs").let { if (playlistId == 7L) listOf(songList[2].track, songList[0].track) else null }

    override suspend fun albums(filter: CollectionFilter): List<AlbumDto> {
        calls += "albums"
        albumFilter = filter
        return listOf(
            AlbumDto("MPREb_2", "Zulu", "B", "2020", 3, false),
            AlbumDto("MPREb_1", "echo", null, null, 1, true),
        )
    }

    override suspend fun albumSongs(albumId: String): List<TrackDto>? =
        calls.add("albumSongs").let { if (albumId == "MPREb_1") listOf(songList[1].track) else null }

    override suspend fun artists(filter: CollectionFilter): List<ArtistDto> =
        listOf(ArtistDto("UC2", "yann", 4, true), ArtistDto("UC1", "Abba", 1, false)).also { calls += "artists" }

    override suspend fun artistSongs(artistId: String): List<TrackDto>? =
        calls.add("artistSongs").let { if (artistId == "UC1") listOf(songList[0].track) else null }

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
            "/api/v1/library/songs?query=${"a".repeat(101)}",
            "/api/v1/library/playlists?limit=abc",
            "/api/v1/library/albums?filter=all",
            "/api/v1/library/artists?filter=liked",
            "/api/v1/artwork/id-a?size=10",
            "/api/v1/library/albums/MPREb_1/artwork?size=5000",
            "/api/v1/library/songs?offset=%2B1",
            "/api/v1/library/playlists/7/songs?limit=0",
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
        mount(FakeLibrary())

        val playlists = getAuthed("/api/v1/library/playlists").json()
        assertEquals(listOf("3", "7"), playlists.itemIds())
        val zebra = playlists.getValue("items").jsonArray[1].jsonObject
        assertEquals(2, zebra.getValue("trackCount").jsonPrimitive.int)
        assertEquals("id-a", zebra.getValue("artworkTrackId").jsonPrimitive.content)

        val songs = getAuthed("/api/v1/library/playlists/7/songs").json()
        assertEquals(listOf("local:1", "id-b"), songs.itemIds())
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
            "/api/v1/artwork/id-a",
        )) {
            val missing = client.get(path)
            assertEquals(HttpStatusCode.Unauthorized, missing.status, path)
            assertEquals("UNAUTHORIZED", missing.errorCode(), path)
            val revoked = client.get(path) { bearerAuth("revoked-token") }
            assertEquals(HttpStatusCode.Unauthorized, revoked.status, path)
            assertEquals("DEVICE_REVOKED", revoked.errorCode(), path)
        }
        assertTrue(library.calls.isEmpty())
        assertEquals(null, library.artworkSize)
    }

    @Test
    fun `server without a library answers empty pages`() = testApplication {
        application { BridgeServerCore(serverName = "Pixel test", authenticator = acceptOnly).install(this) }

        val page = getAuthed("/api/v1/library/songs").json()
        assertEquals(0, page.getValue("total").jsonPrimitive.int)
        assertEquals(HttpStatusCode.NotFound, getAuthed("/api/v1/artwork/id-a").status)
    }
}
