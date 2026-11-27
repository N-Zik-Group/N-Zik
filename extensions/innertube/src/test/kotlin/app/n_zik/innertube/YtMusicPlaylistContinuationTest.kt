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
    fun `empty continuation section follows a token on its nested playlist shelf`() {
        val response = json.decodeFromString<BrowseResponse>(
            """
            {
                "continuationContents": {
                    "sectionListContinuation": {
                        "contents": [{"musicPlaylistShelfRenderer": {
                            "contents": [],
                            "continuations": [{"nextContinuationData": {"continuation": "next-nested"}}]
                        }}]
                    }
                }
            }
            """.trimIndent(),
        )

        val page = YtMusic.parsePlaylistContinuation(response)

        assertEquals(emptyList<Innertube.SongItem>(), page.songs)
        assertEquals("next-nested", page.continuation)
    }

    @Test
    fun `legacy first page follows its continuation item even without tracks`() {
        val response = json.decodeFromString<BrowseResponse>(
            """
            {
                "header": {"musicDetailHeaderRenderer": {
                    "title": {"runs": [{"text": "Playlist"}]},
                    "subtitle": {},
                    "secondSubtitle": {},
                    "thumbnail": {},
                    "menu": {"menuRenderer": {}}
                }},
                "contents": {"singleColumnBrowseResultsRenderer": {
                    "tabs": [{"tabRenderer": {"content": {"sectionListRenderer": {
                        "contents": [{"musicPlaylistShelfRenderer": {
                            "contents": [{"continuationItemRenderer": {
                                "continuationEndpoint": {"continuationCommand": {"token": "next-legacy"}}
                            }}]
                        }}]
                    }}}}]
                }}
            }
            """.trimIndent(),
        )

        val page = YtMusic.parsePlaylistFirstPage("VLPLexample", response)

        assertEquals(emptyList<Innertube.SongItem>(), page.songs)
        assertEquals("next-legacy", page.songsContinuation)
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
