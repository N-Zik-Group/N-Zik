package app.n_zik.lastfm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LastFmResponse(
    @SerialName("error") val error: Int? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("session") val session: SessionKey? = null,
    @SerialName("nowplaying") val nowPlaying: NowPlayingResponse? = null,
    @SerialName("scrobbles") val scrobbles: ScrobbleResponse? = null
) {
    /**
     * Maps the API error field to a failed [Result], or success when no error is present.
     */
    fun toApiResult(defaultMessage: String): Result<Unit> {
        error?.let { code ->
            return Result.failure(LastFmApiException(code, message ?: defaultMessage))
        }
        return Result.success(Unit)
    }
}

@Serializable
data class LastFmError(
    val error: Int,
    val message: String? = null
)

@Serializable
data class SessionKey(
    @SerialName("key") val key: String,
    @SerialName("name") val name: String
)

@Serializable
data class NowPlayingResponse(
    @SerialName("artist") val artist: NowPlayingCorrected? = null,
    @SerialName("track") val track: NowPlayingCorrected? = null,
    @SerialName("ignoredMessage") val ignoredMessage: NowPlayingCorrected? = null
)

@Serializable
data class NowPlayingCorrected(
    @SerialName("#text") val text: String = "",
    @SerialName("corrected") val corrected: Int = 0
)

@Serializable
data class ScrobbleResponse(
    @SerialName("@attr") val attr: ScrobbleAttr,
    @SerialName("scrobble") val scrobble: ScrobbleStatus? = null
)

@Serializable
data class ScrobbleAttr(
    @SerialName("accepted") val accepted: Int,
    @SerialName("ignored") val ignored: Int
)

@Serializable
data class ScrobbleStatus(
    val message: String?
)

class LastFmApiException(
    val errorCode: Int,
    message: String
) : Exception(message)

@Serializable
data class LastFmUserInfoResponse(
    @SerialName("user") val user: LastFmUser? = null
)

@Serializable
data class LastFmUser(
    @SerialName("name") val name: String? = null,
    @SerialName("image") val image: List<LastFmImage> = emptyList()
)

@Serializable
data class LastFmImage(
    @SerialName("#text") val text: String? = null,
    @SerialName("size") val size: String? = null
)

/**
 * Picks the largest available profile picture URL
 * (large > medium > small > last non-blank entry).
 */
fun LastFmUser.largestImageUrl(): String? {
    val rank = mapOf("large" to 3, "medium" to 2, "small" to 1)
    return image
        .mapNotNull { img ->
            img.text?.takeIf { it.isNotBlank() }?.let { (rank[img.size] ?: 0) to it }
        }
        .maxByOrNull { it.first }
        ?.second
}
