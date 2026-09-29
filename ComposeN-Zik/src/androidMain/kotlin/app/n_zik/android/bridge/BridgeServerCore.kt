package app.n_zik.android.bridge

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
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

private const val TAG = "BridgeServer"
private const val BEARER_PREFIX = "Bearer "

/**
 * Engine-independent part of the bridge server: routes, authentication gate, session
 * registry and the stop sequence of contract §11.3. [BridgeServer] mounts [install] on a
 * CIO engine; tests mount it on Ktor's test host.
 */
internal class BridgeServerCore(
    private val serverName: String,
    private val authenticator: DeviceAuthenticator = RejectAllAuthenticator,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val stopping = AtomicBoolean(false)
    private val sessions = CopyOnWriteArraySet<DefaultWebSocketServerSession>()

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
                            features = emptyList(),
                        )
                    )
                }
                route("ws") {
                    // Contract §6.1: authentication happens before the upgrade
                    install(createRouteScopedPlugin("BridgeWsAuth") {
                        onCall { call -> call.rejectUnlessAuthenticated() }
                    })
                    webSocket { handleSession(this) }
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
        sessions.toList().forEach { session ->
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

    private suspend fun handleSession(session: DefaultWebSocketServerSession) {
        sessions.add(session)
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
        authorizationFailure(request.headers[HttpHeaders.Authorization])?.let { error ->
            respond(HttpStatusCode.Unauthorized, error)
        }
    }

    /**
     * Contract §2: `UNAUTHORIZED` when the `Authorization` header is missing or not a Bearer
     * token, `DEVICE_REVOKED` when the token is unknown or revoked, `null` when accepted.
     */
    fun authorizationFailure(authorizationHeader: String?): ErrorResponse? {
        val token = authorizationHeader
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
        return when {
            token.isNullOrEmpty() -> ErrorResponse(BridgeErrorCode.UNAUTHORIZED, "Missing or malformed Bearer token")
            authenticator.authenticate(token) is AuthResult.Revoked -> ErrorResponse(BridgeErrorCode.DEVICE_REVOKED, "Unknown or revoked device")
            else -> null
        }
    }

    private fun stopMessage(code: BridgeStopCode): String = when (code) {
        BridgeStopCode.STOP_USER -> "Server stopped from the phone"
        BridgeStopCode.AUTO_STOP -> "Server stopped automatically"
        BridgeStopCode.TIMEOUT -> "Android background time limit reached"
    }
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
    respond(status, ErrorResponse(code = code, message = message))
