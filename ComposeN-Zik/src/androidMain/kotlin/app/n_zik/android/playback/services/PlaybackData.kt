package app.n_zik.android.playback.services

import it.fast4x.innertube.models.PlayerResponse

/**
 * Enriched playback data returned from stream resolution.
 * Carries metadata beyond just the stream URL for loudness normalization,
 * playback tracking, and UI display.
 */
data class PlaybackData(
    val streamUrl: String,
    val format: PlayerResponse.StreamingData.Format?,
    val loudnessDb: Float?,
    val videoDetails: PlayerResponse.VideoDetails?,
    val playbackTracking: PlayerResponse.PlaybackTracking?,
    val streamExpiresInSeconds: Long?,
    val streamClient: String,
    val clientPlaybackNonce: String = "",
)
