package app.n_zik.android.extensions.lastfm

import it.fast4x.lastfm.nowPlayingParams
import it.fast4x.lastfm.scrobbleParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Tests the conditional parameter assembly for Last.fm requests: `duration` / `duration[0]` are sent only when the track duration is known (> 0). */
class LastFmParamsTest {

    @Test
    fun `now playing params include duration only when positive`() {
        assertEquals("240", nowPlayingParams("Artist", "Title", "Album", 240L, "key")["duration"])
        assertNull(nowPlayingParams("Artist", "Title", "Album", 0L, "key")["duration"])
        assertNull(nowPlayingParams("Artist", "Title", "Album", -1L, "key")["duration"])
    }

    @Test
    fun `now playing params carry method and omit blank album`() {
        val params = nowPlayingParams("Artist", "Title", "Album", 240L, "key")
        assertEquals("track.updateNowPlaying", params["method"])
        assertEquals("json", params["format"])
        assertEquals("key", params["api_key"])
        assertEquals("Album", params["album"])
        assertNull(nowPlayingParams("Artist", "Title", null, 240L, "key")["album"])
        assertNull(nowPlayingParams("Artist", "Title", "  ", 240L, "key")["album"])
    }

    @Test
    fun `scrobble params include the duration parameter only when positive`() {
        val params = scrobbleParams("Artist", "Title", 1000L, "Album", 240L, "key")
        assertEquals("240", params["duration[0]"])
        assertEquals("1000", params["timestamp[0]"])
        assertEquals("Album", params["album[0]"])
        assertNull(scrobbleParams("Artist", "Title", 1000L, "Album", 0L, "key")["duration[0]"])
        assertNull(scrobbleParams("Artist", "Title", 1000L, "Album", -1L, "key")["duration[0]"])
    }

    @Test
    fun `scrobble params carry method and omit blank album`() {
        val params = scrobbleParams("Artist", "Title", 1000L, null, 0L, "key")
        assertEquals("track.scrobble", params["method"])
        assertEquals("Artist", params["artist[0]"])
        assertEquals("Title", params["track[0]"])
        assertEquals("key", params["api_key"])
        assertNull(params["album[0]"])
        assertNull(scrobbleParams("Artist", "Title", 1000L, "  ", 0L, "key")["album[0]"])
    }
}
