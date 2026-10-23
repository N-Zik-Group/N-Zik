package app.n_zik.android.bridge.library

import app.it.fast4x.rimusic.enums.BuiltInPlaylist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.bridge.AuthResult
import app.n_zik.android.bridge.BridgeJson
import app.n_zik.android.bridge.BridgeServerCore
import app.n_zik.android.bridge.DeviceAuthenticator
import app.n_zik.android.bridge.state.SongRecommendationsResponse
import app.n_zik.android.bridge.state.TrackLike
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val TOKEN = "valid-token"

/** `GET /library/songs/recommendations` (contract §10.1, since 1.12.0, `library.recommendations`): the shared holder the phone's Songs tab publishes to, its clearing, and the route's wire answer. */
class SongRecommendationsTest {

    private val acceptOnly = DeviceAuthenticator { token ->
        if (token == TOKEN) AuthResult.Accepted(deviceId = "test-device") else AuthResult.Revoked
    }

    private fun song(id: String) =
        Song(id = id, title = "e:Title $id", artistsText = "Artist", durationText = "3:00", thumbnailUrl = "https://i.ytimg.com/vi/$id/hq.jpg")

    @AfterEach
    fun tearDown() {
        SongRecommendations.changeListener = null
        SongRecommendations.clear()
    }

    @Test
    fun `a publish then a clear each notify once, an unchanged clear does not`() {
        var changes = 0
        SongRecommendations.changeListener = { changes++ }

        SongRecommendations.publish(BuiltInPlaylist.All, mapOf(song("aaaaaaaaaaa") to 3))
        assertEquals(SongFilter.ALL, SongRecommendations.state.value.filter)
        assertEquals(listOf(RecommendedSong(3, song("aaaaaaaaaaa"))), SongRecommendations.state.value.items)
        assertEquals(1, changes)

        SongRecommendations.clear()
        assertEquals(SongRecommendationsState.EMPTY, SongRecommendations.state.value)
        assertEquals(2, changes)

        SongRecommendations.clear()
        assertEquals(2, changes)
    }

    @Test
    fun `each phone tab has its wire filter, On device has none`() {
        assertEquals(SongFilter.ALL, SongRecommendations.filterOf(BuiltInPlaylist.All))
        assertEquals(SongFilter.LIKED, SongRecommendations.filterOf(BuiltInPlaylist.Favorites))
        assertEquals(SongFilter.OFFLINE, SongRecommendations.filterOf(BuiltInPlaylist.Offline))
        assertEquals(SongFilter.DOWNLOADED, SongRecommendations.filterOf(BuiltInPlaylist.Downloaded))
        assertEquals(SongFilter.TOP, SongRecommendations.filterOf(BuiltInPlaylist.Top))
        assertEquals(SongFilter.DISLIKED, SongRecommendations.filterOf(BuiltInPlaylist.Disliked))
        assertEquals(null, SongRecommendations.filterOf(BuiltInPlaylist.OnDevice))
    }

    @Test
    fun `the response maps each recommended song to a neutral online track`() {
        val state = SongRecommendationsState(SongFilter.LIKED, listOf(RecommendedSong(0, song("aaaaaaaaaaa")), RecommendedSong(7, song("bbbbbbbbbbb"))))

        val response = LibraryMapping.recommendations(state)

        assertEquals("liked", response.filter)
        assertEquals(listOf(0, 7), response.items.map { it.position })
        val track = response.items.first().track
        assertEquals("aaaaaaaaaaa", track.id)
        assertEquals("Title aaaaaaaaaaa", track.title)
        assertTrue(track.isExplicit)
        assertEquals(TrackLike.NEUTRAL, track.like)
        assertEquals(false, track.inPlaylist)
    }

    @Test
    fun `the route serves the published state and an empty one while off`() = testApplication {
        var current = SongRecommendationsState(SongFilter.ALL, listOf(RecommendedSong(2, song("aaaaaaaaaaa"))))
        application {
            BridgeServerCore(serverName = "Pixel test", authenticator = acceptOnly, songRecommendations = { current }).install(this)
        }

        val on = client.get("/api/v1/library/songs/recommendations") { bearerAuth(TOKEN) }
        assertEquals(HttpStatusCode.OK, on.status)
        val json = BridgeJson.parseToJsonElement(on.bodyAsText()).jsonObject
        assertEquals("all", json.getValue("filter").jsonPrimitive.content)
        val item = json.getValue("items").jsonArray.single().jsonObject
        assertEquals(2, item.getValue("position").jsonPrimitive.int)
        assertEquals("aaaaaaaaaaa", item.getValue("track").jsonObject.getValue("id").jsonPrimitive.content)
        // The client's model decodes it back
        assertEquals(1, BridgeJson.decodeFromString(SongRecommendationsResponse.serializer(), on.bodyAsText()).items.size)

        current = SongRecommendationsState.EMPTY
        val off = BridgeJson.parseToJsonElement(client.get("/api/v1/library/songs/recommendations") { bearerAuth(TOKEN) }.bodyAsText()).jsonObject
        assertEquals(JsonNull, off["filter"])
        assertEquals(0, off.getValue("items").jsonArray.size)

        // Bearer only, like every library route
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/library/songs/recommendations").status)
    }
}
