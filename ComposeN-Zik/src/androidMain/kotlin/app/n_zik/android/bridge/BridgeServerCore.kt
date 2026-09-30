package app.n_zik.android.bridge

import app.n_zik.android.bridge.pairing.CodeValidation
import app.n_zik.android.bridge.pairing.InMemoryPairedDeviceStorage
import app.n_zik.android.bridge.pairing.JsonPairedDeviceStore
import app.n_zik.android.bridge.pairing.PairedDeviceStore
import app.n_zik.android.bridge.pairing.PairingCodeManager
import app.n_zik.android.bridge.pairing.PairingRateLimiter
import app.n_zik.android.bridge.pairing.RateLimitResult
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

private const val TAG = "BridgeServer"
private const val BEARER_PREFIX = "Bearer "
private val DeviceIdKey = AttributeKey<String>("BridgeDeviceId")
private const val MAX_VALIDATE_BODY_BYTES = 4_096L

/**
 * Engine-independent part of the bridge server: routes, authentication gate, pairing
 * validation, session registry and the stop sequence of contract §11.3. [BridgeServer]
 * mounts [install] on a CIO engine; tests mount it on Ktor's test host.
 */
internal class BridgeServerCore(
    val serverName: String,
    private val deviceStore: PairedDeviceStore = JsonPairedDeviceStore(InMemoryPairedDeviceStorage()),
    private val authenticator: DeviceAuthenticator = StoreDeviceAuthenticator(deviceStore),
    private val pairingCodes: PairingCodeManager = PairingCodeManager(),
    private val rateLimiter: PairingRateLimiter = PairingRateLimiter(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called with the device name once a pairing succeeded. */
    private val onDevicePaired: (String) -> Unit = {},
) {
    private val stopping = AtomicBoolean(false)

    /** Open WebSocket sessions and the `deviceId` each one authenticated with. */
    private val sessions = ConcurrentHashMap<DefaultWebSocketServerSession, String>()

    val isStopping: Boolean get() = stopping.get()
    val sessionCount: Int get() = sessions.size

    fun install(application: Application) = with(application) {
        install(ContentNegotiation) { json(BridgeJson) }
        install(WebSockets)
        install(createApplicationPlugin("BridgeGuard") {
            // Contract §11.3 step 1: once stopping, every new request gets 503
            onCall { call ->
                if (stopping.get()) {
                    call.respondError(HttpStatusCode.ServiceUnavailable, BridgeErrorCode.SERVER_STOPPING, "Server is stopping")
                }
            }
            on(CallFailed) { call, cause ->
                if (cause is CancellationException) throw cause
                Timber.tag(TAG).e(cause, "Unhandled error on ${call.request.local.uri}")
                call.respondError(HttpStatusCode.InternalServerError, BridgeErrorCode.INTERNAL_ERROR, "Unexpected server error")
            }
        })

        routing {
            route(BridgeContract.API_PREFIX) {
                get("meta") {
                    call.respond(
                        MetaResponse(
                            contractVersion = BridgeContract.CONTRACT_VERSION,
                            serverName = serverName,
                            serverTimeMs = clock(),
                            // Each later story adds its identifier once its routes exist
                            features = BridgeContract.FEATURES,
                        )
                    )
                }
                // Public regime (contract §2): protected by the ephemeral code and the rate-limit
                post("pairing/validate") { call.handleValidate() }
                route("ws") {
                    // Contract §6.1: authentication happens before the upgrade
                    install(createRouteScopedPlugin("BridgeWsAuth") {
                        onCall { call -> call.rejectUnlessAuthenticated() }
                    })
                    webSocket {
                        handleSession(this, call.attributes[DeviceIdKey], call.request.headers[HttpHeaders.Authorization])
                    }
                }
            }
            route("{...}") {
                handle {
                    call.respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown route")
                }
            }
        }
    }

    /**
     * Stops accepting requests and closes every WebSocket session. With a [code], each
     * session first receives the `serverStopped` frame then a `1001` close (no automatic
     * reconnect on the client). Without one (Wi-Fi lost), sessions are dropped without a
     * frame so the client treats the loss as transient (contract §6.4).
     */
    suspend fun shutdownSessions(code: BridgeStopCode?) {
        stopping.set(true)
        sessions.keys.toList().forEach { session ->
            runCatching {
                if (code != null) {
                    val frame = ServerStoppedFrame(code = code, message = stopMessage(code))
                    session.send(Frame.Text(BridgeJson.encodeToString(ServerStoppedFrame.serializer(), frame)))
                    session.close(CloseReason(BridgeContract.CLOSE_SERVER_STOPPED, BridgeContract.CLOSE_REASON_SERVER_STOPPED))
                } else {
                    session.cancel()
                }
            }.onFailure { Timber.tag(TAG).w(it, "Failed to close a bridge session") }
        }
        sessions.clear()
    }

    /**
     * Revokes [deviceId] (contract §4.7): its token is forgotten first, so any new request
     * gets `401 DEVICE_REVOKED`, then its WebSocket session, if any, is closed with
     * `4003 DEVICE_REVOKED`. Other devices are untouched.
     */
    suspend fun revokeDevice(deviceId: String): Boolean {
        val removed = withContext(NzikDispatchers.DATA) { deviceStore.revoke(deviceId) }
        sessions.filterValues { it == deviceId }.keys.forEach { session ->
            sessions.remove(session)
            runCatching {
                session.close(CloseReason(BridgeContract.CLOSE_DEVICE_REVOKED, BridgeContract.CLOSE_REASON_DEVICE_REVOKED))
            }.onFailure { Timber.tag(TAG).w(it, "Failed to close the session of a revoked device") }
        }
        return removed
    }

    /** Contract §4.5, rules applied in order: rate-limit, body, code and requestId, issue. */
    private suspend fun ApplicationCall.handleValidate() {
        val limit = rateLimiter.tryAcquire(request.origin.remoteAddress)
        if (limit is RateLimitResult.Limited) {
            respond(
                HttpStatusCode.TooManyRequests,
                RateLimitedResponse(BridgeErrorCode.RATE_LIMITED, "Too many pairing attempts", limit.retryAfterMs),
            )
            return
        }
        // Public route: never buffer an unbounded body
        val length = request.contentLength()
        if (length == null || length > MAX_VALIDATE_BODY_BYTES) {
            respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid pairing request")
            return
        }
        val body = runCatching { BridgeJson.decodeFromString(ValidateRequest.serializer(), receiveText()) }.getOrNull()
        val deviceName = body?.deviceName?.trim()
        if (body == null || deviceName.isNullOrEmpty() || deviceName.length > BridgeContract.DEVICE_NAME_MAX_LENGTH) {
            respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid pairing request")
            return
        }
        if (pairingCodes.validate(body.code, body.requestId) != CodeValidation.Accepted) {
            respondError(HttpStatusCode.Forbidden, BridgeErrorCode.PAIRING_REJECTED, "Invalid or expired pairing code")
            return
        }
        val issued = withContext(NzikDispatchers.DATA) { deviceStore.issue(deviceName) }
        val path = if (body.requestId == null) "manual" else "QR"
        Timber.tag(TAG).i("Device ${issued.deviceId} paired ($path)")
        onDevicePaired(deviceName)
        respond(
            ValidateResponse(
                deviceToken = issued.deviceToken,
                deviceId = issued.deviceId,
                serverName = serverName,
                // The port the request reached is the real bridge port (contract §4.5)
                serverPort = request.local.localPort,
            )
        )
    }

    private suspend fun handleSession(session: DefaultWebSocketServerSession, deviceId: String, authorizationHeader: String?) {
        sessions[session] = deviceId
        // A revocation between the pre-upgrade check and this registration did not see the session
        if (authenticate(authorizationHeader) !is AuthOutcome.Success) {
            sessions.remove(session)
            session.close(CloseReason(BridgeContract.CLOSE_DEVICE_REVOKED, BridgeContract.CLOSE_REASON_DEVICE_REVOKED))
            return
        }
        Timber.tag(TAG).i("Bridge session opened (${sessions.size} active)")
        try {
            // Client messages (ping, requestSnapshot) are handled by story 5; unknown ones are ignored
            session.incoming.consumeEach { }
        } finally {
            sessions.remove(session)
            Timber.tag(TAG).i("Bridge session closed (${sessions.size} active)")
        }
    }

    private suspend fun ApplicationCall.rejectUnlessAuthenticated() {
        when (val outcome = authenticate(request.headers[HttpHeaders.Authorization])) {
            is AuthOutcome.Failure -> respond(HttpStatusCode.Unauthorized, outcome.error)
            is AuthOutcome.Success -> attributes.put(DeviceIdKey, outcome.deviceId)
        }
    }

    /**
     * Contract §2: `UNAUTHORIZED` when the `Authorization` header is missing or not a Bearer
     * token, `DEVICE_REVOKED` when the token is unknown or revoked, `null` when accepted.
     */
    fun authorizationFailure(authorizationHeader: String?): ErrorResponse? =
        (authenticate(authorizationHeader) as? AuthOutcome.Failure)?.error

    private fun authenticate(authorizationHeader: String?): AuthOutcome {
        val token = authorizationHeader
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
        if (token.isNullOrEmpty()) {
            return AuthOutcome.Failure(ErrorResponse(BridgeErrorCode.UNAUTHORIZED, "Missing or malformed Bearer token"))
        }
        return when (val result = authenticator.authenticate(token)) {
            is AuthResult.Accepted -> AuthOutcome.Success(result.deviceId)
            AuthResult.Revoked -> AuthOutcome.Failure(ErrorResponse(BridgeErrorCode.DEVICE_REVOKED, "Unknown or revoked device"))
        }
    }

    private sealed interface AuthOutcome {
        data class Success(val deviceId: String) : AuthOutcome
        data class Failure(val error: ErrorResponse) : AuthOutcome
    }

    private fun stopMessage(code: BridgeStopCode): String = when (code) {
        BridgeStopCode.STOP_USER -> "Server stopped from the phone"
        BridgeStopCode.AUTO_STOP -> "Server stopped automatically"
        BridgeStopCode.TIMEOUT -> "Android background time limit reached"
    }
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
    respond(status, ErrorResponse(code = code, message = message))
