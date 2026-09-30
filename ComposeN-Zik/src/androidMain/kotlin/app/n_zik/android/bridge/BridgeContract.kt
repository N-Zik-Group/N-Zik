package app.n_zik.android.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire values of the local PC bridge, contract v1
 * (`_bmad-output/specs/spec-n-zik-pc-bridge/contract/CONTRACT-v1.md` in the BMAD workspace).
 * Every literal that travels on the wire lives here.
 */
internal object BridgeContract {
    const val CONTRACT_VERSION = "1.0"
    const val API_PREFIX = "/api/v1"

    /** Port 42420, then 42421–42429, then an OS-assigned ephemeral port (contract §11.1). */
    val DEFAULT_PORT_CANDIDATES: List<Int> = (42420..42429).toList() + 0

    /** Close code sent right after the `serverStopped` frame (contract §6.4). */
    const val CLOSE_SERVER_STOPPED: Short = 1001
    const val CLOSE_REASON_SERVER_STOPPED = "SERVER_STOPPED"

    /** Hard deadline of the stop sequence on `onTimeout()` (contract §11.3: < 5 s). */
    const val STOP_TIMEOUT_MS = 4_000L

    const val TYPE_SERVER_STOPPED = "serverStopped"

    /** Close code of the session of a revoked device (contract §4.7, §6.4). */
    const val CLOSE_DEVICE_REVOKED: Short = 4003
    const val CLOSE_REASON_DEVICE_REVOKED = "DEVICE_REVOKED"

    /** `features` of `GET /api/v1/meta` implemented so far (contract §5). */
    val FEATURES: List<String> = listOf("pairing.qr", "pairing.manual")

    /** Length bounds of `deviceName` after trim (contract §4.5). */
    const val DEVICE_NAME_MAX_LENGTH = 64
}

/** Error codes of contract §3 used by the server so far. */
internal object BridgeErrorCode {
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val DEVICE_REVOKED = "DEVICE_REVOKED"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val PAIRING_REJECTED = "PAIRING_REJECTED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val NOT_FOUND = "NOT_FOUND"
    const val SERVER_STOPPING = "SERVER_STOPPING"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
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

/** Shared JSON setup: tolerant reader (unknown keys ignored), defaults always written. */
internal val BridgeJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
