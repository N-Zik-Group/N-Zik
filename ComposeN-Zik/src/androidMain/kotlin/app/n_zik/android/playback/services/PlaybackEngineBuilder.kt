package app.n_zik.android.playback.services

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.MediaSource

/** Shared playback policy for the main and crossfade engines. */
internal fun createPlaybackEngineBuilder(
    context: Context,
    mediaSourceFactory: MediaSource.Factory,
    renderersFactory: RenderersFactory,
    handleAudioFocus: Boolean,
): ExoPlayer.Builder = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .setRenderersFactory(renderersFactory)
    .setHandleAudioBecomingNoisy(false)
    .setWakeMode(C.WAKE_MODE_NETWORK)
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build(),
        handleAudioFocus,
    )
    .setUsePlatformDiagnostics(false)
    // Media3 1.11.1 position cadence: https://github.com/androidx/media/issues/3286
    // Re-evaluate when the position-update fix reaches a stable release.
    .experimentalSetDynamicSchedulingEnabled(false)
    .setLoadControl(createSeekFriendlyLoadControl())
