package app.n_zik.android.listentogether

import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi

/** Forwards every Player listener callback while filtering advertised session commands. */
@UnstableApi
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class CommandFilteringPlayerListener(
    private val listener: Player.Listener,
    private val filterCommands: (Player.Commands) -> Player.Commands,
) : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) =
        listener.onEvents(player, events)

    override fun onTimelineChanged(timeline: Timeline, reason: Int) =
        listener.onTimelineChanged(timeline, reason)

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
        listener.onMediaItemTransition(mediaItem, reason)

    override fun onTracksChanged(tracks: Tracks) =
        listener.onTracksChanged(tracks)

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) =
        listener.onMediaMetadataChanged(mediaMetadata)

    override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) =
        listener.onPlaylistMetadataChanged(mediaMetadata)

    override fun onIsLoadingChanged(isLoading: Boolean) =
        listener.onIsLoadingChanged(isLoading)

    override fun onLoadingChanged(isLoading: Boolean) =
        listener.onLoadingChanged(isLoading)

    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
        listener.onAvailableCommandsChanged(filterCommands(availableCommands))

    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) =
        listener.onTrackSelectionParametersChanged(parameters)

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) =
        listener.onPlayerStateChanged(playWhenReady, playbackState)

    override fun onPlaybackStateChanged(playbackState: Int) =
        listener.onPlaybackStateChanged(playbackState)

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
        listener.onPlayWhenReadyChanged(playWhenReady, reason)

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) =
        listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)

    override fun onIsPlayingChanged(isPlaying: Boolean) =
        listener.onIsPlayingChanged(isPlaying)

    override fun onRepeatModeChanged(repeatMode: Int) =
        listener.onRepeatModeChanged(repeatMode)

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
        listener.onShuffleModeEnabledChanged(shuffleModeEnabled)

    override fun onPlayerError(error: PlaybackException) =
        listener.onPlayerError(error)

    override fun onPlayerErrorChanged(error: PlaybackException?) =
        listener.onPlayerErrorChanged(error)

    override fun onPositionDiscontinuity(reason: Int) =
        listener.onPositionDiscontinuity(reason)

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) =
        listener.onPositionDiscontinuity(oldPosition, newPosition, reason)

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
        listener.onPlaybackParametersChanged(playbackParameters)

    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) =
        listener.onSeekBackIncrementChanged(seekBackIncrementMs)

    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) =
        listener.onSeekForwardIncrementChanged(seekForwardIncrementMs)

    override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) =
        listener.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)

    override fun onVideoSizeChanged(videoSize: VideoSize) =
        listener.onVideoSizeChanged(videoSize)

    override fun onSurfaceSizeChanged(width: Int, height: Int) =
        listener.onSurfaceSizeChanged(width, height)

    override fun onRenderedFirstFrame() =
        listener.onRenderedFirstFrame()

    override fun onAudioSessionIdChanged(audioSessionId: Int) =
        listener.onAudioSessionIdChanged(audioSessionId)

    override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) =
        listener.onAudioAttributesChanged(audioAttributes)

    override fun onVolumeChanged(volume: Float) =
        listener.onVolumeChanged(volume)

    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) =
        listener.onSkipSilenceEnabledChanged(skipSilenceEnabled)

    override fun onCues(cues: List<Cue>) =
        listener.onCues(cues)

    override fun onCues(cueGroup: CueGroup) =
        listener.onCues(cueGroup)

    override fun onMetadata(metadata: Metadata) =
        listener.onMetadata(metadata)

    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) =
        listener.onDeviceInfoChanged(deviceInfo)

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) =
        listener.onDeviceVolumeChanged(volume, muted)
}
