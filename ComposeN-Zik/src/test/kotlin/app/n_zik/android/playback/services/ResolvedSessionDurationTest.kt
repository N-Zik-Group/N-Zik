package app.n_zik.android.playback.services

import android.app.Application
import app.n_zik.innertube.models.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ResolvedSessionDurationTest {
    @Test
    fun `only positive finite response lengths are accepted`() {
        assertEquals(238_000L, ResolvedSessionDuration.fromLengthSeconds("238"))
        for (invalid in listOf(null, "", "0", "-1", "LIVE", "2.5", Long.MAX_VALUE.toString())) {
            assertNull(ResolvedSessionDuration.fromLengthSeconds(invalid))
        }
    }

    @Test
    fun `cached response must belong to the requested track`() {
        val id = "duration-test"
        try {
            playbackDataCache[id] = resolvedDurationData("different-track", "238")
            assertNull(ResolvedSessionDuration.forMediaId(id))
            playbackDataCache[id] = resolvedDurationData(id, "238")
            val before = ResolvedSessionDuration.updates.value
            ResolvedSessionDuration.notifyResolved()
            assertEquals(before + 1, ResolvedSessionDuration.updates.value)
            assertEquals(238_000L, ResolvedSessionDuration.forMediaId(id))
        } finally {
            playbackDataCache.remove(id)
        }
    }
}

internal fun resolvedDurationData(id: String, seconds: String) = PlaybackData(
    streamUrl = "", format = null, loudnessDb = null,
    videoDetails = PlayerResponse.VideoDetails(
        videoId = id, title = null, author = null, channelId = null, authorAvatar = null,
        authorSubCount = null, lengthSeconds = seconds, musicVideoType = null, viewCount = null,
        thumbnail = null, description = null,
    ),
    playbackTracking = null, streamExpiresInSeconds = null, streamClient = "test",
)
