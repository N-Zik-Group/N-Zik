package app.n_zik.android.playback.services.diagnostics

import android.os.Bundle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.audio.AudioSink
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@UnstableApi
class CrossfadeAudioDiagnosticsTest {
    @Test
    fun `buffering ready and first audio output retain identity and actual state`() {
        val player = player()
        val lines = mutableListOf<String>()
        var captureTime = 1000L
        val diagnostics = CrossfadeAudioDiagnostics(player, lines::add, clock = { captureTime++ })
        val identity = System.identityHashCode(player)

        every { player.playbackState } returns Player.STATE_BUFFERING
        every { player.isPlaying } returns false
        diagnostics.mark("BEGIN_INCOMING")
        diagnostics.onPlaybackStateChanged(eventTime(100), Player.STATE_BUFFERING)
        every { player.playbackState } returns Player.STATE_READY
        every { player.isPlaying } returns true
        every { player.currentPosition } returns 25L
        diagnostics.onPlaybackStateChanged(eventTime(150), Player.STATE_READY)
        diagnostics.onIsPlayingChanged(eventTime(151), true)
        diagnostics.onAudioPositionAdvancing(eventTime(175), 123456L)

        assertEquals(5, lines.size)
        assertTrue(lines.all { "player=$identity" in it && "sessionId=41" in it })
        assertEquals((1000L..1004L).toList(), lines.map { captureRealtimeMs(it) })
        assertTrue("event=BEGIN_INCOMING" in lines[0])
        assertTrue("state=2 playWhenReady=true isPlaying=false" in lines[1])
        assertTrue("eventRealtimeMs=100 reportedState=2" in lines[1])
        assertTrue("state=3 playWhenReady=true isPlaying=true playbackSuppressionReason=0 positionMs=25" in lines[2])
        assertTrue("eventRealtimeMs=150 reportedState=3" in lines[2])
        assertTrue("reportedIsPlaying=true" in lines[3])
        assertTrue("AUDIO_POSITION_ADVANCING" in lines[4])
        assertTrue("eventRealtimeMs=175 playoutStartSystemTimeMs=123456" in lines[4])
        assertTrue(lines.all { "bufferedPositionMs=8000 volume=0.25" in it })
        verify(exactly = 0) { player.currentMediaItem }
        verify(exactly = 0) { player.mediaMetadata }
        verify(exactly = 0) { player.currentTimeline }
    }

    @Test
    fun `session track underrun and sink callbacks log bounded primitive evidence`() {
        val player = player()
        val lines = mutableListOf<String>()
        var captureTime = 2000L
        val diagnostics = CrossfadeAudioDiagnostics(player, lines::add, clock = { captureTime++ })
        val config = AudioSink.AudioTrackConfig(2, 48000, 12, false, false, 4096)

        diagnostics.onAudioSessionIdChanged(eventTime(200), 42)
        diagnostics.onAudioTrackInitialized(eventTime(201), config)
        diagnostics.onAudioUnderrun(eventTime(202), 4096, 21, 500)
        diagnostics.onAudioSinkError(eventTime(203), IllegalStateException("https://private/title?token=secret"))
        diagnostics.onAudioTrackReleased(eventTime(204), config)

        assertEquals(5, lines.size)
        assertEquals((2000L..2004L).toList(), lines.map { captureRealtimeMs(it) })
        assertTrue("sessionId=41" in lines[0])
        assertTrue("reportedSessionId=42" in lines[0])
        assertTrue("event=AUDIO_TRACK_INITIALIZED" in lines[1])
        assertTrue("encoding=2 sampleRate=48000 channelConfig=12 tunneling=false offload=false bufferSize=4096" in lines[1])
        assertTrue("event=AUDIO_UNDERRUN" in lines[2])
        assertTrue("bufferSize=4096 bufferSizeMs=21 elapsedSinceLastFeedMs=500" in lines[2])
        assertTrue("errorClass=IllegalStateException" in lines[3])
        assertTrue("event=AUDIO_TRACK_RELEASED" in lines[4])
        assertFalse(lines.any { "private" in it || "secret" in it || "https://" in it })
        verify(exactly = 0) { player.currentMediaItem }
        verify(exactly = 0) { player.mediaMetadata }
    }

    @Test
    fun `unavailable snapshot reports exception class and retains callback evidence`() {
        val player = player()
        val lines = mutableListOf<String>()
        val diagnostics = CrossfadeAudioDiagnostics(player, lines::add, clock = { 3000L })
        every { player.audioSessionId } throws IllegalStateException("private detail")

        diagnostics.onAudioSessionIdChanged(eventTime(300), 42)

        assertEquals(1, lines.size)
        assertEquals(3000L, captureRealtimeMs(lines.single()))
        assertTrue("captureError=IllegalStateException" in lines.single())
        assertTrue("reportedSessionId=42" in lines.single())
        assertFalse("private detail" in lines.single())
    }

    @Test
    fun `a failing log destination cannot escape into playback`() {
        val diagnostics = CrossfadeAudioDiagnostics(
            player(), logger = { error("logger unavailable") }, clock = { 4000L }
        )

        diagnostics.mark("END")
    }

    @Test
    fun `ready with play intent records suppression and its changes`() {
        val player = player()
        val lines = mutableListOf<String>()
        var captureTime = 5000L
        val diagnostics = CrossfadeAudioDiagnostics(player, lines::add, clock = { captureTime++ })
        every { player.isPlaying } returns false
        every { player.playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS

        diagnostics.mark("MIDPOINT_INCOMING")
        diagnostics.onPlaybackSuppressionReasonChanged(
            eventTime(501), Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        )
        every { player.playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_NONE
        diagnostics.onPlaybackSuppressionReasonChanged(eventTime(502), Player.PLAYBACK_SUPPRESSION_REASON_NONE)

        assertEquals(3, lines.size)
        assertEquals((5000L..5002L).toList(), lines.map { captureRealtimeMs(it) })
        assertTrue("state=3 playWhenReady=true isPlaying=false playbackSuppressionReason=1" in lines[0])
        assertTrue("event=PLAYBACK_SUPPRESSION_REASON" in lines[1])
        assertTrue("eventRealtimeMs=501 reportedPlaybackSuppressionReason=1" in lines[1])
        assertTrue("playbackSuppressionReason=0" in lines[2])
        assertTrue("eventRealtimeMs=502 reportedPlaybackSuppressionReason=0" in lines[2])
    }

    @Test
    fun `source and codec errors retain only bounded code and class evidence`() {
        val player = player()
        val lines = mutableListOf<String>()
        var captureTime = 6000L
        val diagnostics = CrossfadeAudioDiagnostics(player, lines::add, clock = { captureTime++ })
        val privateDetail = "https://private/title?token=secret"
        val cause = IllegalArgumentException(privateDetail)

        diagnostics.onPlayerError(eventTime(601), SourceFailure(privateDetail, cause))
        diagnostics.onAudioCodecError(eventTime(602), IllegalStateException(privateDetail, cause))

        assertEquals(2, lines.size)
        assertEquals(listOf(6000L, 6001L), lines.map { captureRealtimeMs(it) })
        assertTrue("event=PLAYER_ERROR" in lines[0])
        assertTrue("eventRealtimeMs=601 errorCode=2001 errorClass=SourceFailure" in lines[0])
        assertTrue("event=AUDIO_CODEC_ERROR" in lines[1])
        assertTrue("eventRealtimeMs=602 errorClass=IllegalStateException" in lines[1])
        assertFalse(lines.any { "private" in it || "secret" in it || "https://" in it || "title" in it })
        assertFalse(lines.any { "IllegalArgumentException" in it })
        verify(exactly = 0) { player.currentMediaItem }
        verify(exactly = 0) { player.mediaMetadata }
        verify(exactly = 0) { player.currentTimeline }
    }

    @Test
    fun `a failing clock cannot escape and preserves snapshot and event evidence`() {
        val lines = mutableListOf<String>()
        val diagnostics = CrossfadeAudioDiagnostics(
            player(), lines::add, clock = { error("private clock detail") }
        )

        diagnostics.mark("END")
        diagnostics.onPlaybackStateChanged(eventTime(701), Player.STATE_READY)

        assertEquals(2, lines.size)
        assertTrue(lines.all { "captureRealtimeMs=unavailable clockError=IllegalStateException" in it })
        assertTrue(lines.all { "sessionId=41 state=3" in it })
        assertTrue("eventRealtimeMs=701 reportedState=3" in lines[1])
        assertFalse(lines.any { "private" in it })
    }

    private fun player(): Player = mockk<Player> {
        every { audioSessionId } returns 41
        every { playbackState } returns Player.STATE_READY
        every { playWhenReady } returns true
        every { isPlaying } returns true
        every { playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_NONE
        every { currentPosition } returns 0L
        every { bufferedPosition } returns 8000L
        every { volume } returns 0.25f
    }

    private fun captureRealtimeMs(line: String): Long =
        line.substringAfter("captureRealtimeMs=").substringBefore(' ').toLong()

    // The explicit timestamp avoids PlaybackException's Android clock in plain JVM tests.
    private class SourceFailure(message: String, cause: Throwable) : PlaybackException(
        message, cause, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, mockk<Bundle>(), 0L
    )

    private fun eventTime(realtimeMs: Long) = EventTime(
        realtimeMs, Timeline.EMPTY, 0, null, 0,
        Timeline.EMPTY, 0, null, 0, 0
    )
}
