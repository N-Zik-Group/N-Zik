package app.it.fast4x.rimusic.utils

import it.fast4x.innertube.models.PlayerResponse
import timber.log.Timber

fun getSignatureTimestampOrNull(
    videoId: String
): Int? {
    // Legacy NewPipe pipeline removed — signature timestamp no longer available
    Timber.tag("YoutubeUtils").d("getSignatureTimestampOrNull: legacy pipeline removed, returning null")
    return null
}

fun getStreamUrl(
    format: PlayerResponse.StreamingData.Format,
    videoId: String
): String? {
    // Legacy NewPipe pipeline removed — stream URL resolution no longer available
    Timber.tag("YoutubeUtils").d("getStreamUrl: legacy pipeline removed, returning null")
    return null
}



