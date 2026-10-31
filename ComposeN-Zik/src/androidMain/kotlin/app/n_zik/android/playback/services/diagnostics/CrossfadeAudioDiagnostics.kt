package app.n_zik.android.playback.services.diagnostics

import android.os.SystemClock
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.audio.AudioSink
import timber.log.Timber

/** Records sparse audio lifecycle events without reading media metadata. */
@UnstableApi
internal class CrossfadeAudioDiagnostics(
    private val player: Player,
    private val logger: (String) -> Unit = { Timber.tag(TAG).i("%s", it) },
    private val clock: () -> Long = SystemClock::elapsedRealtime
) : AnalyticsListener {
    private val playerIdentity = System.identityHashCode(player)

    /** Captures the current state for a caller-owned lifecycle label. */
    internal fun mark(event: String) {
        record(event.take(80))
    }

    override fun onPlaybackStateChanged(eventTime: EventTime, state: Int) {
        record("PLAYBACK_STATE", "eventRealtimeMs=${eventTime.realtimeMs} reportedState=$state")
    }

    override fun onIsPlayingChanged(eventTime: EventTime, isPlaying: Boolean) {
        record("IS_PLAYING", "eventRealtimeMs=${eventTime.realtimeMs} reportedIsPlaying=$isPlaying")
    }

    override fun onPlaybackSuppressionReasonChanged(eventTime: EventTime, playbackSuppressionReason: Int) {
        record(
            "PLAYBACK_SUPPRESSION_REASON",
            "eventRealtimeMs=${eventTime.realtimeMs} reportedPlaybackSuppressionReason=$playbackSuppressionReason"
        )
    }

    override fun onAudioSessionIdChanged(eventTime: EventTime, audioSessionId: Int) {
        record("AUDIO_SESSION", "eventRealtimeMs=${eventTime.realtimeMs} reportedSessionId=$audioSessionId")
    }

    override fun onAudioPositionAdvancing(eventTime: EventTime, playoutStartSystemTimeMs: Long) {
        record(
            "AUDIO_POSITION_ADVANCING",
            "eventRealtimeMs=${eventTime.realtimeMs} playoutStartSystemTimeMs=$playoutStartSystemTimeMs"
        )
    }

    override fun onAudioTrackInitialized(eventTime: EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        record("AUDIO_TRACK_INITIALIZED", trackDetails(eventTime, audioTrackConfig))
    }

    override fun onAudioTrackReleased(eventTime: EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        record("AUDIO_TRACK_RELEASED", trackDetails(eventTime, audioTrackConfig))
    }

    override fun onAudioUnderrun(
        eventTime: EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long
    ) {
        record(
            "AUDIO_UNDERRUN",
            "eventRealtimeMs=${eventTime.realtimeMs} bufferSize=$bufferSize " +
                "bufferSizeMs=$bufferSizeMs elapsedSinceLastFeedMs=$elapsedSinceLastFeedMs"
        )
    }

    override fun onAudioSinkError(eventTime: EventTime, audioSinkError: Exception) {
        record(
            "AUDIO_SINK_ERROR",
            "eventRealtimeMs=${eventTime.realtimeMs} errorClass=${audioSinkError.javaClass.simpleName.take(80)}"
        )
    }

    override fun onPlayerError(eventTime: EventTime, error: PlaybackException) {
        record(
            "PLAYER_ERROR",
            "eventRealtimeMs=${eventTime.realtimeMs} errorCode=${error.errorCode} " +
                "errorClass=${error.javaClass.simpleName.take(80)}"
        )
    }

    override fun onAudioCodecError(eventTime: EventTime, audioCodecError: Exception) {
        record(
            "AUDIO_CODEC_ERROR",
            "eventRealtimeMs=${eventTime.realtimeMs} errorClass=${audioCodecError.javaClass.simpleName.take(80)}"
        )
    }

    private fun trackDetails(eventTime: EventTime, config: AudioSink.AudioTrackConfig): String =
        "eventRealtimeMs=${eventTime.realtimeMs} encoding=${config.encoding} " +
            "sampleRate=${config.sampleRate} channelConfig=${config.channelConfig} " +
            "tunneling=${config.tunneling} offload=${config.offload} bufferSize=${config.bufferSize}"

    private fun record(event: String, details: String = "") {
        val prefix = "event=$event player=$playerIdentity"
        val captureTime = runCatching { "captureRealtimeMs=${clock()}" }
            .getOrElse { "captureRealtimeMs=unavailable clockError=${it.javaClass.simpleName.take(80)}" }
        val snapshot = runCatching {
            "sessionId=${player.audioSessionId} state=${player.playbackState} " +
                "playWhenReady=${player.playWhenReady} isPlaying=${player.isPlaying} " +
                "playbackSuppressionReason=${player.playbackSuppressionReason} " +
                "positionMs=${player.currentPosition} bufferedPositionMs=${player.bufferedPosition} " +
                "volume=${player.volume}"
        }.getOrElse { "captureError=${it.javaClass.simpleName.take(80)}" }
        runCatching {
            logger("$prefix $captureTime $snapshot $details".trimEnd())
        }.onFailure {
            // Diagnostics must not interrupt playback even if the log destination fails.
            runCatching {
                Timber.tag(TAG).w("Diagnostic logger failed: %s", it.javaClass.simpleName.take(80))
            }
        }
    }

    private companion object {
        const val TAG = "CrossfadeAudio"
    }
}
