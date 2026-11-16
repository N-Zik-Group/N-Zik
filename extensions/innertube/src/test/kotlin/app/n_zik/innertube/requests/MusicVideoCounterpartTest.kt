package app.n_zik.innertube.requests

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MusicVideoCounterpartTest {
    @Test
    fun `resolves a counterpart and maps the position in either direction`() {
        val response = response(
            """{"primaryVideoStartTimeMilliseconds":"1000",
                "counterpartVideoStartTimeMilliseconds":"6000",
                "durationMilliseconds":"8000"}""",
        )
        val video = parseMusicVideoCounterpart(response, "studioTrack")
        assertEquals("officialClip", video?.targetVideoId)
        assertEquals("MUSIC_VIDEO_TYPE_OMV", video?.targetMusicVideoType)
        assertEquals(7_250L, video?.positionMap?.mapPosition(2_250))
        assertEquals(6_000L, video?.positionMap?.mapPosition(0))
        assertEquals(14_000L, video?.positionMap?.mapPosition(25_000))

        val track = parseMusicVideoCounterpart(response, "officialClip")
        assertEquals("studioTrack", track?.targetVideoId)
        assertEquals(2_250L, track?.positionMap?.mapPosition(7_250))
    }

    @Test
    fun `ignores unrelated rows and retains the clip when the segment is malformed`() {
        val response = response("""{"durationMilliseconds":"unknown"}""")
        val video = parseMusicVideoCounterpart(response, "studioTrack")
        assertEquals("officialClip", video?.targetVideoId)
        assertNull(video?.positionMap)
        assertNull(parseMusicVideoCounterpart(response, "unrelatedId"))
    }

    private fun response(segment: String) = Json.parseToJsonElement(
        """{"contents":[
            {"playlistPanelVideoRenderer":{"videoId":"unrelatedId"}},
            {"playlistPanelVideoWrapperRenderer":{
                "primaryRenderer":{"playlistPanelVideoRenderer":{"videoId":"studioTrack"}},
                "counterpart":[{
                    "counterpartRenderer":{"playlistPanelVideoRenderer":{
                        "videoId":"officialClip",
                        "navigationEndpoint":{"watchEndpoint":{"watchEndpointMusicSupportedConfigs":{
                            "watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_OMV"}
                        }}}
                    }},
                    "segmentMap":{"segment":[$segment]}
                }]
            }}
        ]}""",
    ).jsonObject
}
