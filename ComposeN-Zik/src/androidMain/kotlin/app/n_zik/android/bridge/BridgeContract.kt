package app.n_zik.android.bridge

import app.n_zik.android.bridge.state.RepeatModeDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire values of the local PC bridge, contract v1
 * (`_bmad-output/specs/spec-n-zik-pc-bridge/contract/CONTRACT-v1.md` in the BMAD workspace).
 * Every literal that travels on the wire lives here.
 */
internal object BridgeContract {
    const val CONTRACT_VERSION = "1.3"
    const val API_PREFIX = "/api/v1"

    /** Port 42420, then 42421–42429, then an OS-assigned ephemeral port (contract §11.1). */
    val DEFAULT_PORT_CANDIDATES: List<Int> = (42420..42429).toList() + 0

    /** Close code sent right after the `serverStopped` frame (contract §6.4). */
    const val CLOSE_SERVER_STOPPED: Short = 1001
    const val CLOSE_REASON_SERVER_STOPPED = "SERVER_STOPPED"

    /** Hard deadline of the stop sequence on `onTimeout()` (contract §11.3: < 5 s). */
    const val STOP_TIMEOUT_MS = 4_000L

    const val TYPE_SERVER_STOPPED = "serverStopped"

    /** Close code of a session taken over by a new connection of the same device (contract §6.2, §6.4). */
    const val CLOSE_SESSION_REPLACED: Short = 4000
    const val CLOSE_REASON_SESSION_REPLACED = "SESSION_REPLACED"

    /** Close code of a session disconnected from the phone, pairing kept (contract §6.4, §8.4). */
    const val CLOSE_KICKED: Short = 4001
    const val CLOSE_REASON_KICKED = "KICKED"

    /** Close code of the session of a revoked device (contract §4.7, §6.4). */
    const val CLOSE_DEVICE_REVOKED: Short = 4003
    const val CLOSE_REASON_DEVICE_REVOKED = "DEVICE_REVOKED"

    /** Close code of a session silent for [PING_TIMEOUT_MS] (contract §6.3, §6.4). */
    const val CLOSE_PING_TIMEOUT: Short = 4008
    const val CLOSE_REASON_PING_TIMEOUT = "PING_TIMEOUT"

    /** Without any client message for this long, the session is closed with `4008` (contract §6.3). */
    const val PING_TIMEOUT_MS = 45_000L

    /** Period of the non-revised `heartbeat` (contract §7.3). */
    const val HEARTBEAT_INTERVAL_MS = 10_000L

    /** Client → server message types (contract §7.8); any other type is ignored. */
    const val TYPE_PING = "ping"
    const val TYPE_REQUEST_SNAPSHOT = "requestSnapshot"

    /** Late failure of a confirmed command (contract §7.6). */
    const val TYPE_ERROR = "error"

    /** `features` of `GET /api/v1/meta` implemented so far (contract §5). */
    val FEATURES: List<String> = listOf(
        "pairing.qr",
        "pairing.manual",
        "playback",
        "queue",
        "library.songs",
        "library.playlists",
        "library.albums",
        "library.artists",
        "artwork",
        "audio",
        "audio.output",
        "ws.state",
    )

    /** Pagination bounds (contract §1, §14). */
    const val PAGE_OFFSET_DEFAULT = 0
    const val PAGE_LIMIT_DEFAULT = 50
    const val PAGE_LIMIT_MIN = 1
    const val PAGE_LIMIT_MAX = 200

    /** Longest `query` of `/library/songs` (contract §10). */
    const val LIBRARY_QUERY_MAX_LENGTH = 100

    /** Bounds of the artwork `size` parameter, in px (contract §10, §14). */
    const val ARTWORK_SIZE_MIN = 64
    const val ARTWORK_SIZE_MAX = 1200
    const val ARTWORK_SIZE_DEFAULT = 544

    /** `Cache-Control` of every artwork response (contract §10). */
    const val ARTWORK_CACHE_CONTROL = "private, max-age=86400"

    /** Largest command body read (contract §9 commands); anything bigger is `400 BAD_REQUEST`. */
    const val MAX_COMMAND_BODY_BYTES = 64 * 1_024

    /** Maximum length of a command's `commandId` (contract §9). */
    const val COMMAND_ID_MAX_LENGTH = 64

    /** Bounds of `/player/speed` (contract §9, §14). */
    const val SPEED_MIN = 0.25f
    const val SPEED_MAX = 4.0f

    /** Size bounds of a `trackIds` list (contract §9, §14). */
    const val TRACK_IDS_MAX = 500

    /** A player error this soon after a command that loads a track is reported as a WS `error` (contract §7.6). */
    const val LATE_FAILURE_WINDOW_MS = 10_000L

    /** Length bounds of `deviceName` after trim (contract §4.5). */
    const val DEVICE_NAME_MAX_LENGTH = 64

    /** Query parameter carrying the signed audio token (contract §8.2). */
    const val AUDIO_TOKEN_PARAM = "t"

    /** Longest audio token ever issued or accepted (contract §8.1). */
    const val AUDIO_TOKEN_MAX_LENGTH = 512

    /** Audio URL lifetime: forge + min(duration + margin, max), or the fallback without a duration (contract §8.1, §14). */
    const val AUDIO_URL_DURATION_MARGIN_MS = 120_000L
    const val AUDIO_URL_MAX_TTL_MS = 900_000L
    const val AUDIO_URL_UNKNOWN_DURATION_TTL_MS = 600_000L

    /** Largest forge body read (`{ "quality": … }`); anything bigger is `400 BAD_REQUEST`. */
    const val MAX_AUDIO_FORGE_BODY_BYTES = 4 * 1_024
}

/** Error codes of contract §3 used by the server so far. */
internal object BridgeErrorCode {
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val DEVICE_REVOKED = "DEVICE_REVOKED"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val PAIRING_REJECTED = "PAIRING_REJECTED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val NOT_FOUND = "NOT_FOUND"
    const val QUEUE_MISMATCH = "QUEUE_MISMATCH"
    const val CONFLICT_ACTIVE_CLIENT = "CONFLICT_ACTIVE_CLIENT"
    const val PLAYER_REJECTED = "PLAYER_REJECTED"
    const val PLAYER_UNAVAILABLE = "PLAYER_UNAVAILABLE"
    const val SERVER_STOPPING = "SERVER_STOPPING"
    const val AUDIO_UPSTREAM_FAILED = "AUDIO_UPSTREAM_FAILED"
    const val AUDIO_URL_EXPIRED = "AUDIO_URL_EXPIRED"
    const val AUDIO_URL_INVALID = "AUDIO_URL_INVALID"
    const val RANGE_NOT_SATISFIABLE = "RANGE_NOT_SATISFIABLE"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}

/**
 * `AudioOutput` (contract §1.1, since 1.2): the device the phone's playback sounds on. [PC] =
 * the active client plays the current track with its local player while the phone keeps
 * playing silently (contract §8.5).
 */
@Serializable
enum class AudioOutput {
    @SerialName("phone") PHONE,
    @SerialName("pc") PC,
}

/** Why the server stops; sent as `serverStopped.code` (contract §7.7). */
enum class BridgeStopCode { STOP_USER, AUTO_STOP, TIMEOUT }

@Serializable
internal data class MetaResponse(
    val contractVersion: String,
    val serverName: String,
    val serverTimeMs: Long,
    val features: List<String>,
)

@Serializable
internal data class ErrorResponse(
    val code: String,
    val message: String,
)

/** `429 RATE_LIMITED` body: the error model plus `retryAfterMs` (contract §3). */
@Serializable
internal data class RateLimitedResponse(
    val code: String,
    val message: String,
    val retryAfterMs: Long,
)

@Serializable
internal data class ValidateRequest(
    val code: String,
    val requestId: String? = null,
    val deviceName: String,
)

@Serializable
internal data class ValidateResponse(
    val deviceToken: String,
    val deviceId: String,
    val serverName: String,
    val serverPort: Int,
)

@Serializable
internal data class ServerStoppedFrame(
    val type: String = BridgeContract.TYPE_SERVER_STOPPED,
    val code: BridgeStopCode,
    val message: String,
)

/** `409 QUEUE_MISMATCH` body: the error model plus the current `revision` (contract §3). */
@Serializable
internal data class QueueMismatchResponse(
    val code: String,
    val message: String,
    val revision: Long,
)

/** The paired device holding the active session (contract §6.2): `activeDevice` of a `409`. */
@Serializable
data class ActiveDevice(
    val deviceId: String,
    val deviceName: String,
)

/** `409 CONFLICT_ACTIVE_CLIENT` body: the error model plus the active device (contract §3, §6.2). */
@Serializable
internal data class ConflictActiveClientResponse(
    val code: String,
    val message: String,
    val activeDevice: ActiveDevice,
)

/** `200` answer of every command (contract §9). */
@Serializable
internal data class CommandResponse(
    val applied: Boolean,
    val changed: Boolean,
    val revision: Long,
)

// --- Command bodies (contract §9); `commandId` is optional everywhere ---

@Serializable
internal data class EmptyCommandBody(val commandId: String? = null)

@Serializable
internal data class SeekCommandBody(val positionMs: Long, val commandId: String? = null)

@Serializable
internal data class SpeedCommandBody(val speed: Float, val commandId: String? = null)

@Serializable
internal data class RepeatCommandBody(val mode: RepeatModeDto, val commandId: String? = null)

@Serializable
internal data class ShuffleCommandBody(val enabled: Boolean, val commandId: String? = null)

/** `/player/output` (contract §9, since 1.2): an unknown `output` fails the decoding (`400`). */
@Serializable
internal data class OutputCommandBody(val output: AudioOutput, val commandId: String? = null)

@Serializable
internal data class QueuePlayCommandBody(
    val trackIds: List<String>,
    val startIndex: Int,
    val positionMs: Long = 0L,
    val commandId: String? = null,
)

/** Where `/queue/add` inserts (contract §9). */
@Serializable
internal enum class AddPosition {
    @SerialName("next") NEXT,
    @SerialName("end") END,
}

@Serializable
internal data class QueueAddCommandBody(
    val trackIds: List<String>,
    val position: AddPosition,
    val commandId: String? = null,
)

/** `/queue/remove` and `/queue/jump`. */
@Serializable
internal data class QueueItemCommandBody(val index: Int, val trackId: String, val commandId: String? = null)

@Serializable
internal data class QueueMoveCommandBody(
    val fromIndex: Int,
    val toIndex: Int,
    val trackId: String,
    val commandId: String? = null,
)

// --- Library consultation (contract §1, §10) ---

/** Paginated answer (contract §1): `total` counts every item after the filters. */
@Serializable
internal data class Page<T>(
    val items: List<T>,
    val total: Int,
    val offset: Int,
    val limit: Int,
)

/** `Playlist` (contract §1.1). */
@Serializable
internal data class PlaylistDto(
    val id: String,
    val name: String,
    val trackCount: Int,
    val artworkTrackId: String?,
)

/** `Album` (contract §1.1, since 1.1). */
@Serializable
internal data class AlbumDto(
    val id: String,
    val title: String,
    val artists: String?,
    val year: String?,
    val trackCount: Int,
    val hasArtwork: Boolean,
    /** Since 1.3: album bookmarked on the phone. */
    val isBookmarked: Boolean = false,
)

/** `Artist` (contract §1.1, since 1.1). */
@Serializable
internal data class ArtistDto(
    val id: String,
    val name: String,
    val trackCount: Int,
    val hasArtwork: Boolean,
    /** Since 1.3: artist followed (bookmarked) on the phone. */
    val isBookmarked: Boolean = false,
)

// --- Audio (contract §8) ---

/** `Quality` (contract §1.1): parsed by hand so an unknown value is a `400`, never a fallback. */
internal enum class AudioQuality(val wire: String) {
    LOW("low"),
    HIGH("high"),
    AUTO("auto");

    companion object {
        fun parse(value: String?): AudioQuality? = entries.firstOrNull { it.wire == value }
    }
}

/** `200` answer of `POST /api/v1/audio/{trackId}/url` (contract §8.1). */
@Serializable
internal data class AudioUrlResponse(
    val trackId: String,
    val quality: String,
    val url: String,
    val expiresAtMs: Long,
    val durationMs: Long?,
)

/** Shared JSON setup: tolerant reader (unknown keys ignored), defaults always written. */
internal val BridgeJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
