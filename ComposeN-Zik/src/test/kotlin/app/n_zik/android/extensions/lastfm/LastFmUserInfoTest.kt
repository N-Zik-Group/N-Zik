package app.n_zik.android.extensions.lastfm

import it.fast4x.lastfm.models.LastFmImage
import it.fast4x.lastfm.models.LastFmUser
import it.fast4x.lastfm.models.LastFmUserInfoResponse
import it.fast4x.lastfm.models.largestImageUrl
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Tests the `user.getinfo` payload mapping and the [largestImageUrl] selection with the same lenient Json configuration as the LastFm client. */
@OptIn(ExperimentalSerializationApi::class)
class LastFmUserInfoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun `user getinfo payload maps name and image entries`() {
        val response = json.decodeFromString<LastFmUserInfoResponse>(
            """{"user":{"name":"NEVARLeVrai","image":[{"#text":"https://s.example/small","size":"small"},{"#text":"https://s.example/large","size":"large"}]}}"""
        )

        assertEquals("NEVARLeVrai", response.user?.name)
        assertEquals(2, response.user?.image?.size)
    }

    @Test
    fun `largest image url prefers large over medium and small`() {
        val user = LastFmUser(
            name = "u",
            image = listOf(
                LastFmImage(text = "https://s.example/small", size = "small"),
                LastFmImage(text = "https://s.example/large", size = "large"),
                LastFmImage(text = "https://s.example/medium", size = "medium")
            )
        )

        assertEquals("https://s.example/large", user.largestImageUrl())
    }

    @Test
    fun `largest image url falls back to medium when large is missing`() {
        val user = LastFmUser(
            name = "u",
            image = listOf(
                LastFmImage(text = "https://s.example/small", size = "small"),
                LastFmImage(text = "https://s.example/medium", size = "medium")
            )
        )

        assertEquals("https://s.example/medium", user.largestImageUrl())
    }

    @Test
    fun `largest image url returns null when the user has no image`() {
        val user = LastFmUser(name = "u", image = emptyList())

        assertNull(user.largestImageUrl())
    }

    @Test
    fun `largest image url skips blank entries`() {
        val user = LastFmUser(
            name = "u",
            image = listOf(
                LastFmImage(text = "", size = "large"),
                LastFmImage(text = null, size = "medium"),
                LastFmImage(text = "https://s.example/small", size = "small")
            )
        )

        assertEquals("https://s.example/small", user.largestImageUrl())
    }

    @Test
    fun `unknown fields in the payload are ignored`() {
        val response = json.decodeFromString<LastFmUserInfoResponse>(
            """{"user":{"name":"u","country":"fr","realname":"x","image":[]},"@attr":{"version":"2"}}"""
        )

        assertEquals("u", response.user?.name)
        assertNull(response.user?.largestImageUrl())
    }
}
