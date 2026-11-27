package app.n_zik.innertube

import app.n_zik.innertube.models.BrowseResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class YtMusicPlaylistContinuationTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `playlist page without songs retains its shelf continuation`() {
        val response = json.decodeFromString<BrowseResponse>(
            """
            {"continuationContents":{"musicPlaylistShelfContinuation":{
                "contents":[],
                "continuations":[{"nextContinuationData":{"continuation":"next-shelf"}}]
            }}}
            """.trimIndent(),
        )

        val page = YtMusic.parsePlaylistContinuation(response)

        assertEquals(emptyList<Innertube.SongItem>(), page.songs)
        assertEquals("next-shelf", page.continuation)
    }

    @Test
    fun `playlist page with only an append continuation retains its token`() {
        val response = json.decodeFromString<BrowseResponse>(
            """
            {"onResponseReceivedActions":[{},{"appendContinuationItemsAction":{"continuationItems":[
                {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-append"}}}}
            ]}}]}
            """.trimIndent(),
        )

        val page = YtMusic.parsePlaylistContinuation(response)

        assertEquals(emptyList<Innertube.SongItem>(), page.songs)
        assertEquals("next-append", page.continuation)
    }

    @Test
    fun `an explicit empty terminal page completes but a missing payload fails`() {
        val terminal = json.decodeFromString<BrowseResponse>(
            """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[]}}}""",
        )
        val missing = json.decodeFromString<BrowseResponse>("{}")

        assertNull(YtMusic.parsePlaylistContinuation(terminal).continuation)
        assertThrows(IllegalArgumentException::class.java) { YtMusic.parsePlaylistContinuation(missing) }
    }
}
