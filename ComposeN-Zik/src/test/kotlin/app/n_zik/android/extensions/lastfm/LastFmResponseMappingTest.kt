package app.n_zik.android.extensions.lastfm

import it.fast4x.lastfm.models.LastFmApiException
import it.fast4x.lastfm.models.LastFmResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests the JSON to [LastFmResponse] mapping and [LastFmResponse.toApiResult] with the same lenient Json configuration as the LastFm client: Last.fm may return attribute values (accepted/ignored/corrected) as quoted strings, which strict Int decoding would reject. */
@OptIn(ExperimentalSerializationApi::class)
class LastFmResponseMappingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun `error payload 9 maps to a failed Result carrying LastFmApiException`() {
        val response = json.decodeFromString<LastFmResponse>(
            """{"error":9,"message":"Invalid session key - please re-authenticate"}"""
        )

        val exception = assertThrows(LastFmApiException::class.java) {
            response.toApiResult("default message").getOrThrow()
        }

        assertEquals(9, exception.errorCode)
        assertEquals("Invalid session key - please re-authenticate", exception.message)
    }

    @Test
    fun `error payload without message falls back to the default message`() {
        val response = json.decodeFromString<LastFmResponse>("""{"error":6}""")

        val exception = assertThrows(LastFmApiException::class.java) {
            response.toApiResult("Scrobble failed").getOrThrow()
        }

        assertEquals(6, exception.errorCode)
        assertEquals("Scrobble failed", exception.message)
    }

    @Test
    fun `successful now playing payload maps to a successful Result`() {
        val response = json.decodeFromString<LastFmResponse>(
            """{"nowplaying":{"artist":{"#text":"Kraftwerk"}}}"""
        )

        assertTrue(response.toApiResult("default").isSuccess)
    }

    @Test
    fun `mobile session success payload returns the session key`() {
        val response = json.decodeFromString<LastFmResponse>(
            """{"session":{"key":"abc123def456","name":"user"}}"""
        )

        assertEquals("abc123def456", response.session?.key)
        assertEquals("user", response.session?.name)
    }

    @Test
    fun `scrobble attributes quoted as strings decode with lenient json`() {
        val response = json.decodeFromString<LastFmResponse>(
            """{"scrobbles":{"@attr":{"accepted":"1","ignored":"0"},"scrobble":{"message":"ok"}}}"""
        )

        assertEquals(1, response.scrobbles?.attr?.accepted)
        assertEquals(0, response.scrobbles?.attr?.ignored)
    }

}
