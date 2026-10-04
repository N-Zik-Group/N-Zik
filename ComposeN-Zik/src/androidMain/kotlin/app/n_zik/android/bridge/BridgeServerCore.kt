package app.n_zik.android.bridge

import app.n_zik.android.bridge.audio.AudioLibrary
import app.n_zik.android.bridge.audio.AudioOpenResult
import app.n_zik.android.bridge.audio.AudioSourceGoneException
import app.n_zik.android.bridge.audio.AudioStream
import app.n_zik.android.bridge.audio.AudioTokenCheck
import app.n_zik.android.bridge.audio.AudioTokens
import app.n_zik.android.bridge.audio.ByteRange
import app.n_zik.android.bridge.audio.ByteRanges
import app.n_zik.android.bridge.command.BridgeCommandExecutor
import app.n_zik.android.bridge.command.BridgeCommandParser
import app.n_zik.android.bridge.command.CommandResult
import app.n_zik.android.bridge.command.ParsedCommand
import app.n_zik.android.bridge.library.ArtworkResult
import app.n_zik.android.bridge.library.CollectionFilter
import app.n_zik.android.bridge.library.LibraryProvider
import app.n_zik.android.bridge.library.LibraryQueries
import app.n_zik.android.bridge.library.PageRequest
import app.n_zik.android.bridge.pairing.CodeValidation
import app.n_zik.android.bridge.pairing.InMemoryPairedDeviceStorage
import app.n_zik.android.bridge.pairing.JsonPairedDeviceStore
import app.n_zik.android.bridge.pairing.PairedDeviceStore
import app.n_zik.android.bridge.pairing.PairingCodeManager
import app.n_zik.android.bridge.pairing.PairingRateLimiter
import app.n_zik.android.bridge.pairing.RateLimitResult
import app.n_zik.android.bridge.state.BridgeServerMessage
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.bridge.state.PongMessage
import app.n_zik.android.bridge.state.encodeServerMessage
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.TrackLike
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

private const val TAG = "BridgeServer"
private const val BEARER_PREFIX = "Bearer "
private val DeviceIdKey = AttributeKey<String>("BridgeDeviceId")
private const val MAX_VALIDATE_BODY_BYTES = 4_096L

/** Outgoing frames buffered per session; beyond that a message is dropped (see [BridgeServerCore]). */
private const val OUTGOING_BUFFER = 64

/** How long a closing session may take to flush its queued frames before its last frame. */
private const val FLUSH_TIMEOUT_MS = 500L

/**
 * Engine-independent part of the bridge server: routes, authentication gate, pairing
 * validation, session registry, state synchronisation over the WebSocket (contract §6.3,
 * §7) and the stop sequence of contract §11.3. [BridgeServer] mounts [install] on a CIO
 * engine; tests mount it on Ktor's test host.
 *
 * Each session has one outgoing channel written without blocking: a message that does not
 * fit is dropped, and the client notices the gap at the next `heartbeat` (contract §7.3)
 * and asks for a snapshot — the server never re-sends.
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
    /** Player state of this server run: a new server starts again at revision `0`. */
    private val stateHub: BridgeStateHub = BridgeStateHub(clock),
    private val pingTimeoutMs: Long = BridgeContract.PING_TIMEOUT_MS,
    private val heartbeatIntervalMs: Long = BridgeContract.HEARTBEAT_INTERVAL_MS,
    /** Applies the commands of contract §9; without a player behind the server, always `503`. */
    private val commandExecutor: BridgeCommandExecutor = BridgeCommandExecutor.UNAVAILABLE,
    /** Read-only library of contract §10; empty without a phone behind the server. */
    private val libraryProvider: LibraryProvider = LibraryProvider.EMPTY,
    /** Audio of the phone's tracks (contract §8); nothing to serve without a phone behind the server. */
    private val audioLibrary: AudioLibrary = AudioLibrary.EMPTY,
    /** Signs the audio URLs of this server run: a new core draws a new key (contract §8.1). */
    private val audioTokens: AudioTokens = AudioTokens(clock = clock),
    /** Whether the device an audio token was forged for is still paired (contract §8.2 step 2). */
    private val isDevicePaired: (String) -> Boolean = deviceStore::isPaired,
    /** Called after every change of [activeDevice]. */
    private val onActiveDeviceChanged: () -> Unit = {},
    /** Called for each valid command handed to [commandExecutor]: the inactivity auto-stop tick (contract §11.2). */
    private val onCommandAccepted: () -> Unit = {},
    /**
     * Called once each WebSocket session has ended, whatever the reason (client close, kick,
     * revocation, ping timeout, network loss, server stop), after the session is freed, unless
     * the same device's replacement session is already active — then the active session
     * continues and no fallback runs (contract §6.2). Runs non-cancellable.
     */
    private val onSessionEnded: suspend () -> Unit = {},
    /**
     * Called once a session has been claimed, i.e. has become the active session (contract §6.2):
     * the audio output handoff to the PC of contract §8.5 (since 1.3). Fires on every successful
     * claim, including the same device replacing its own session.
     */
    private val onSessionClaimed: () -> Unit = {},
) {
    private val stopping = AtomicBoolean(false)

    /** Claim of the active session taken by a WebSocket call before its upgrade. */
    private val sessionClaimKey = AttributeKey<SessionClaim>("BridgeSessionClaim")

    /**
     * Contract §2 then §6.2 on a REST Bearer route: authentication (`401`) first, then the
     * active-client rule (`409`), both before any other processing of the route.
     */
    private val bearerAuth = createRouteScopedPlugin("BridgeBearerAuth") {
        onCall { call ->
            val deviceId = call.authenticateOrReject() ?: return@onCall
            activeDeviceOtherThan(deviceId)?.let { call.respondConflict(it) }
        }
    }

    /**
     * Contract §6.1–§6.2 before the WebSocket upgrade: authentication, then the session is
     * taken atomically (`409` when another device holds it). The same device's previous
     * session is closed with `4000` here, before the new one is upgraded and gets its snapshot.
     */
    private val sessionAuth = createRouteScopedPlugin("BridgeSessionAuth") {
        onCall { call ->
            val deviceId = call.authenticateOrReject() ?: return@onCall
            when (val result = claimSession(deviceId)) {
                is ClaimResult.Conflict -> call.respondConflict(result.active)
                ClaimResult.Stopping ->
                    call.respondError(HttpStatusCode.ServiceUnavailable, BridgeErrorCode.SERVER_STOPPING, "Server is stopping")
                is ClaimResult.Claimed -> {
                    call.attributes.put(sessionClaimKey, result.claim)
                    result.replaced?.let { closeSession(it, sessionReplaced) }
                }
            }
        }
        // A claim whose upgrade never happened must not keep the session taken
        on(ResponseSent) { call ->
            val claim = call.attributes.getOrNull(sessionClaimKey) ?: return@on
            if (call.response.status() != HttpStatusCode.SwitchingProtocols) releaseClaim(claim)
        }
    }

    /** Open WebSocket sessions and their state stream (which knows the device id). */
    private val sessions = ConcurrentHashMap<DefaultWebSocketServerSession, SessionStream>()

    /** Guards [activeClaim] and the fields of every [SessionClaim]. */
    private val claimLock = Any()

    /** The single active session of contract §6.2, taken before the upgrade; `null` when free. */
    private var activeClaim: SessionClaim? = null

    private val _activeDevice = MutableStateFlow<ActiveDevice?>(null)

    /** Device holding the active session (contract §6.2), `null` when none. */
    val activeDevice: StateFlow<ActiveDevice?> = _activeDevice.asStateFlow()

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
                // The path only: an audio query string carries a signed token, never logged
                Timber.tag(TAG).e(cause, "Unhandled error on ${call.request.path()}")
                // A body already under way (audio stream) cannot turn into an error any more
                if (!call.response.isCommitted) {
                    call.respondError(HttpStatusCode.InternalServerError, BridgeErrorCode.INTERNAL_ERROR, "Unexpected server error")
                }
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
                // Contract §9: playback and queue commands, Bearer only
                for (group in COMMAND_GROUPS) {
                    route(group) {
                        install(bearerAuth)
                        post("{action}") { call.handleCommand("$group/${call.parameters["action"]}") }
                    }
                }
                // Contract §10: read-only library consultation, Bearer only
                route("library") {
                    install(bearerAuth)
                    get("songs") { call.handleSongs() }
                    get("playlists") { call.handlePlaylists() }
                    get("playlists/{id}/songs") { call.handlePlaylistSongs() }
                    // Since 1.7.2: the phone's custom playlist cover (no `size` parameter: the local file is served as-is)
                    get("playlists/{id}/artwork") { call.handlePlaylistArtwork() }
                    get("rewind") { call.handleRewind() }
                    get("dislikeMode") { call.handleDislikeMode() }
                    get("albums") { call.handleAlbums() }
                    get("albums/{id}/songs") {
                        val id = call.parameters["id"].orEmpty()
                        call.respondTracks("Unknown album") { libraryProvider.albumSongs(id) }
                    }
                    get("albums/{id}/artwork") {
                        val id = call.parameters["id"].orEmpty()
                        call.respondArtwork { size -> libraryProvider.albumArtwork(id, size) }
                    }
                    get("artists") { call.handleArtists() }
                    get("artists/{id}/songs") {
                        val id = call.parameters["id"].orEmpty()
                        call.respondTracks("Unknown artist") { libraryProvider.artistSongs(id) }
                    }
                    get("artists/{id}/artwork") {
                        val id = call.parameters["id"].orEmpty()
                        call.respondArtwork { size -> libraryProvider.artistArtwork(id, size) }
                    }
                    get("cache") { call.handleCache() }
                    // Contract §10.2 (since 1.7): the explicit writes, local Room only
                    post("songs/{id}/like") { call.handleSongLike() }
                    post("albums/{id}/bookmark") { call.handleAlbumBookmark() }
                    post("albums/{id}/like") { call.handleAlbumLike() }
                    post("artists/{id}/follow") { call.handleArtistFollow() }
                    post("playlists/{id}/pin") { call.handlePlaylistPin() }
                    post("playlists/{id}/bookmark") { call.handlePlaylistBookmark() }
                }
                route("artwork") {
                    install(bearerAuth)
                    get("{trackId}") {
                        val trackId = call.parameters["trackId"].orEmpty()
                        call.respondArtwork { size -> libraryProvider.trackArtwork(trackId, size) }
                    }
                }
                // Contract §8: forge under Bearer; delivery in the audio regime, where the
                // signed token is the only credential and any Authorization header is ignored
                route("audio/{trackId}") {
                    get { call.handleAudio(headOnly = false) }
                    head { call.handleAudio(headOnly = true) }
                    route("url") {
                        install(bearerAuth)
                        post { call.handleForge() }
                    }
                }
                route("ws") {
                    // Contract §6.1: authentication and the active-client rule happen before the upgrade
                    install(sessionAuth)
                    webSocket {
                        val claim = call.attributes[sessionClaimKey]
                        // Released whatever happens after the 101 (no-op once replaced or ended)
                        try {
                            handleSession(this, claim, call.request.headers[HttpHeaders.Authorization])
                        } finally {
                            releaseClaim(claim)
                            // Contract §6.2: the active session ended unless the same device's
                            // replacement session is already active, then it continues
                            val continued = _activeDevice.value?.deviceId == claim.device.deviceId
                            if (!continued) {
                                withContext(NonCancellable) {
                                    runCatching { onSessionEnded() }
                                        .onFailure { Timber.tag(TAG).w(it, "Audio output fallback failed") }
                                }
                            }
                        }
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
        // Under claimLock: no claim can be taken between this and the endClaim below
        synchronized(claimLock) { stopping.set(true) }
        endClaim(CloseReason(BridgeContract.CLOSE_SERVER_STOPPED, BridgeContract.CLOSE_REASON_SERVER_STOPPED)) { true }
        sessions.entries.toList().forEach { (session, stream) ->
            runCatching {
                // serverStopped must stay the last message: silence the state stream first
                stream.detach()
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
        val reason = CloseReason(BridgeContract.CLOSE_DEVICE_REVOKED, BridgeContract.CLOSE_REASON_DEVICE_REVOKED)
        endClaim(reason) { it.device.deviceId == deviceId }
        closeSessionsOf(deviceId, reason)
        return removed
    }

    /**
     * Disconnects [deviceId] without revoking it (contract §6.4, §8.4): its session is closed
     * with `4001 KICKED` first, then every audio URL already forged for it is invalidated.
     * It stays paired and may open a new session later. `false` when it held no session.
     */
    suspend fun kick(deviceId: String): Boolean {
        endClaim(kicked) { it.device.deviceId == deviceId } ?: return false
        closeSessionsOf(deviceId, kicked)
        audioTokens.invalidate(deviceId)
        Timber.tag(TAG).i("Device $deviceId kicked")
        return true
    }

    private suspend fun closeSessionsOf(deviceId: String, reason: CloseReason) {
        sessions.filter { it.value.deviceId == deviceId }.forEach { (session, stream) -> closeSession(session to stream, reason) }
    }

    /** Silences the state stream first, so the close is the last frame the session receives. */
    private suspend fun closeSession(entry: Pair<DefaultWebSocketServerSession, SessionStream>, reason: CloseReason) {
        val (session, stream) = entry
        sessions.remove(session)
        runCatching {
            stream.detach()
            session.close(reason)
        }.onFailure { Timber.tag(TAG).w(it, "Failed to close a bridge session (${reason.message})") }
    }

    // --- Single active client (contract §6.2) ---

    /** One take of the active session by a device, from its pre-upgrade check to its end. */
    private class SessionClaim(val device: ActiveDevice) {
        /** The session once upgraded and registered; guarded by [claimLock]. */
        var session: Pair<DefaultWebSocketServerSession, SessionStream>? = null

        /** Why the claim was taken away, for a session registering afterwards; guarded by [claimLock]. */
        var endedBy: CloseReason? = null
    }

    private sealed interface ClaimResult {
        data class Conflict(val active: ActiveDevice) : ClaimResult

        /** The server began stopping: `503 SERVER_STOPPING` (contract §6.1, §11.3). */
        data object Stopping : ClaimResult

        /** [replaced]: the same device's previous session, to close with `4000`. */
        class Claimed(
            val claim: SessionClaim,
            val replaced: Pair<DefaultWebSocketServerSession, SessionStream>?,
        ) : ClaimResult
    }

    private val sessionReplaced = CloseReason(BridgeContract.CLOSE_SESSION_REPLACED, BridgeContract.CLOSE_REASON_SESSION_REPLACED)
    private val kicked = CloseReason(BridgeContract.CLOSE_KICKED, BridgeContract.CLOSE_REASON_KICKED)

    /** Atomic: of two devices claiming a free session at once, exactly one wins. */
    private fun claimSession(deviceId: String): ClaimResult {
        val result = synchronized(claimLock) {
            if (stopping.get()) return ClaimResult.Stopping
            val current = activeClaim
            if (current != null && current.device.deviceId != deviceId) return ClaimResult.Conflict(current.device)
            val claim = SessionClaim(ActiveDevice(deviceId, deviceNameOf(deviceId)))
            current?.endedBy = sessionReplaced
            activeClaim = claim
            _activeDevice.value = claim.device
            ClaimResult.Claimed(claim, current?.session)
        }
        onActiveDeviceChanged()
        onSessionClaimed()
        return result
    }

    /** Binds the upgraded session to [claim]; the close reason instead when the claim was taken away meanwhile. */
    private fun attachSession(claim: SessionClaim, session: Pair<DefaultWebSocketServerSession, SessionStream>): CloseReason? =
        synchronized(claimLock) {
            if (activeClaim === claim) {
                claim.session = session
                null
            } else {
                claim.endedBy ?: sessionReplaced
            }
        }

    /** Frees the session when [claim] still holds it (client close, `4008`, failed upgrade). */
    private fun releaseClaim(claim: SessionClaim) {
        synchronized(claimLock) {
            if (activeClaim !== claim) return
            activeClaim = null
            _activeDevice.value = null
        }
        onActiveDeviceChanged()
    }

    /** Takes the session away from its holder when [matches]; returns the claim that held it. */
    private fun endClaim(reason: CloseReason, matches: (SessionClaim) -> Boolean): SessionClaim? {
        val ended = synchronized(claimLock) {
            val current = activeClaim?.takeIf(matches) ?: return null
            current.endedBy = reason
            activeClaim = null
            _activeDevice.value = null
            current
        }
        onActiveDeviceChanged()
        return ended
    }

    /** The active device when it is not [deviceId]: that caller gets `409` (contract §6.2). */
    private fun activeDeviceOtherThan(deviceId: String): ActiveDevice? =
        synchronized(claimLock) { activeClaim?.device?.takeIf { it.deviceId != deviceId } }

    /** Name shown in the `409` and on the phone; empty only for a device unknown to the store. */
    private fun deviceNameOf(deviceId: String): String =
        deviceStore.devices.value.firstOrNull { it.deviceId == deviceId }?.deviceName.orEmpty()

    private suspend fun ApplicationCall.respondConflict(active: ActiveDevice) =
        respond(
            HttpStatusCode.Conflict,
            ConflictActiveClientResponse(BridgeErrorCode.CONFLICT_ACTIVE_CLIENT, "Another device holds the active session", active),
        )

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

    /** Contract §9: route, body (≤ 64 KiB), then the executor, whose outcome maps to one status. */
    private suspend fun ApplicationCall.handleCommand(route: String) {
        if (!BridgeCommandParser.isKnown(route)) {
            respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown route")
            return
        }
        val body = receiveBoundedText(BridgeContract.MAX_COMMAND_BODY_BYTES)
        val parsed = body?.let { BridgeCommandParser.parse(route, it) }
        if (parsed !is ParsedCommand.Valid) {
            respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid command body")
            return
        }
        onCommandAccepted()
        when (val result = commandExecutor.execute(parsed.command)) {
            is CommandResult.Applied -> respond(CommandResponse(applied = true, changed = result.changed, revision = result.revision))
            CommandResult.Rejected ->
                respondError(HttpStatusCode.UnprocessableEntity, BridgeErrorCode.PLAYER_REJECTED, "The player refused the command")
            CommandResult.Unavailable ->
                respondError(HttpStatusCode.ServiceUnavailable, BridgeErrorCode.PLAYER_UNAVAILABLE, "The phone's player is not available")
            is CommandResult.QueueMismatch -> respond(
                HttpStatusCode.Conflict,
                QueueMismatchResponse(BridgeErrorCode.QUEUE_MISMATCH, "The queue changed", result.revision),
            )
            CommandResult.NotFound -> respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown track")
        }
    }

    /** Contract §10 `/library/songs` (since 1.6): pagination, `filter`, `sort`, `reverse`, `period`, then search. */
    private suspend fun ApplicationCall.handleSongs() {
        val params = request.queryParameters
        val page = LibraryQueries.parsePage(params["offset"], params["limit"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid pagination")
        val text = LibraryQueries.songsText(params["query"])
        if (text != null && text.length > BridgeContract.LIBRARY_QUERY_MAX_LENGTH) {
            return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Query too long")
        }
        val filter = LibraryQueries.parseSongFilter(params["filter"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid filter")
        val sort = LibraryQueries.parseSongSort(params["sort"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid sort")
        val reverse = LibraryQueries.parseReverse(params["reverse"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid reverse")
        // Absent `period` keeps the phone's own Top period: only a present value is validated
        val period = when (val raw = params["period"]) {
            null -> null
            else -> LibraryQueries.parseTopPeriod(raw)
                ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid period")
        }
        val tracks = LibraryQueries.searchSongs(libraryProvider.songs(filter, sort, reverse, period), text)
        respond(LibraryQueries.paginate(tracks, page))
    }

    /** Contract §10 `/library/playlists/{id}/songs` (since 1.6): pagination, `sort`, `reverse`; since 1.7.2, `text` and `totalDurationMs`. */
    private suspend fun ApplicationCall.handlePlaylistSongs() {
        val id = LibraryQueries.parsePlaylistId(parameters["id"])
        val page = pageOrReject() ?: return
        val sort = LibraryQueries.parsePlaylistSongSort(request.queryParameters["sort"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid sort")
        val reverse = LibraryQueries.parseReverse(request.queryParameters["reverse"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid reverse")
        val text = LibraryQueries.playlistText(request.queryParameters["text"])
        if (text != null && text.length > BridgeContract.LIBRARY_QUERY_MAX_LENGTH) {
            return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Query too long")
        }
        val all = id?.let { libraryProvider.playlistSongs(it, sort, reverse) }
            ?: return respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown playlist")
        val tracks = LibraryQueries.searchSongs(all, text)
        respond(
            LibraryQueries.paginate(tracks, page, total = all.size).copy(
                totalDurationMs = LibraryQueries.totalDurationMsOf(all),
            ),
        )
    }

    /** Contract §1 pagination parameters, or `null` once a `400` has been answered. */
    private suspend fun ApplicationCall.pageOrReject(): PageRequest? {
        val page = LibraryQueries.parsePage(request.queryParameters["offset"], request.queryParameters["limit"])
        if (page == null) respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid pagination")
        return page
    }

    /**
     * Contract §10 `/library/playlists` (since 1.6): pagination, `filter`, `sort`, `reverse`;
     * since 1.7.2, `rewind` (applied and persisted by the phone) and `text` (the phone's search,
     * `total` kept pre-`text`).
     */
    private suspend fun ApplicationCall.handlePlaylists() {
        val params = request.queryParameters
        val page = LibraryQueries.parsePage(params["offset"], params["limit"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid pagination")
        val filter = LibraryQueries.parsePlaylistsFilter(params["filter"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid filter")
        val sort = LibraryQueries.parsePlaylistSort(params["sort"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid sort")
        val reverse = LibraryQueries.parseReverse(params["reverse"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid reverse")
        // Absent `rewind` keeps the phone's own setting; a present value is always validated
        val rewind = when (val raw = params["rewind"]) {
            null -> null
            else -> LibraryQueries.parseRewindFilter(raw)
                ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid rewind filter")
        }
        val text = LibraryQueries.playlistText(params["text"])
        if (text != null && text.length > BridgeContract.LIBRARY_QUERY_MAX_LENGTH) {
            return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Query too long")
        }
        val all = libraryProvider.playlists(filter, sort, reverse, rewind)
        val shown = LibraryQueries.searchPlaylists(all, text)
        respond(LibraryQueries.paginate(shown, page, total = all.size))
    }

    /** Contract §10 `/library/albums` (since 1.6): pagination, `filter`, `sort` and `reverse`. */
    private suspend fun ApplicationCall.handleAlbums() {
        val params = request.queryParameters
        val page = LibraryQueries.parsePage(params["offset"], params["limit"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid pagination")
        val filter = LibraryQueries.parseCollectionFilter(params["filter"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid filter")
        val sort = LibraryQueries.parseAlbumSort(params["sort"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid sort")
        val reverse = LibraryQueries.parseReverse(params["reverse"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid reverse")
        respond(LibraryQueries.paginate(libraryProvider.albums(filter, sort, reverse), page))
    }

    /** Contract §10 `/library/artists` (since 1.6): pagination, `filter`, `sort` and `reverse`. */
    private suspend fun ApplicationCall.handleArtists() {
        val params = request.queryParameters
        val page = LibraryQueries.parsePage(params["offset"], params["limit"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid pagination")
        val filter = LibraryQueries.parseCollectionFilter(params["filter"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid filter")
        val sort = LibraryQueries.parseArtistSort(params["sort"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid sort")
        val reverse = LibraryQueries.parseReverse(params["reverse"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid reverse")
        respond(LibraryQueries.paginate(libraryProvider.artists(filter, sort, reverse), page))
    }

    /** Tracks of one playlist, album or artist; [load] gives `null` when it is unknown (`404`). */
    private suspend inline fun ApplicationCall.respondTracks(notFound: String, load: () -> List<TrackDto>?) {
        val page = pageOrReject() ?: return
        val tracks = load() ?: return respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, notFound)
        respond(LibraryQueries.paginate(tracks, page))
    }

    /** Contract §10 artwork: the image bytes with their real type, `404` without one, `502` when the upstream failed. */
    private suspend inline fun ApplicationCall.respondArtwork(load: (Int) -> ArtworkResult) {
        val size = LibraryQueries.parseArtworkSize(request.queryParameters["size"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid artwork size")
        when (val result = load(size)) {
            is ArtworkResult.Image -> {
                val type = runCatching { ContentType.parse(result.contentType) }.getOrDefault(ContentType.Image.JPEG)
                response.header(HttpHeaders.CacheControl, BridgeContract.ARTWORK_CACHE_CONTROL)
                respondBytes(result.bytes, type)
            }
            ArtworkResult.NotFound -> respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "No artwork")
            ArtworkResult.UpstreamFailed ->
                respondError(HttpStatusCode.BadGateway, BridgeErrorCode.AUDIO_UPSTREAM_FAILED, "The artwork could not be fetched")
        }
    }

    // --- Contract §10.2 (since 1.7): the explicit writes, local Room only ---

    /**
     * The body of a §10.2 write: one small JSON object carrying a single value for [key].
     * `null` when the body is missing, over the size cap, not a JSON object, or the key is
     * absent or not a primitive — the handler answers `400 BAD_REQUEST`.
     */
    private suspend fun ApplicationCall.writeBody(key: String): JsonPrimitive? {
        val body = receiveBoundedText(BridgeContract.MAX_LIBRARY_WRITE_BODY_BYTES) ?: return null
        val json = runCatching { BridgeJson.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        return json[key] as? JsonPrimitive
    }

    /** Contract §10 `/library/cache` (since 1.7.1): the phone's disk caches, used vs configured cap. */
    private suspend fun ApplicationCall.handleCache() {
        respond(libraryProvider.cacheSpace())
    }

    /** Contract §10 `/library/rewind` (since 1.7.2): the phone's Month/Year/All row state. */
    private suspend fun ApplicationCall.handleRewind() {
        respond(libraryProvider.rewindState())
    }

    /** Contract §10 `/library/dislikeMode` (since 1.7.2): the phone's `DislikeMode` per collection. */
    private suspend fun ApplicationCall.handleDislikeMode() {
        respond(libraryProvider.dislikeMode())
    }

    /** Contract §10 `/library/playlists/{id}/artwork` (since 1.7.2): the phone's custom playlist cover, `404` without one. */
    private suspend fun ApplicationCall.handlePlaylistArtwork() {
        val id = LibraryQueries.parsePlaylistId(parameters["id"])
        val result = id?.let { libraryProvider.playlistArtwork(it) } ?: ArtworkResult.NotFound
        when (result) {
            is ArtworkResult.Image -> {
                val type = runCatching { ContentType.parse(result.contentType) }.getOrDefault(ContentType.Image.JPEG)
                response.header(HttpHeaders.CacheControl, BridgeContract.ARTWORK_CACHE_CONTROL)
                respondBytes(result.bytes, type)
            }

            ArtworkResult.NotFound -> respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "No custom cover")
            ArtworkResult.UpstreamFailed ->
                respondError(HttpStatusCode.BadGateway, BridgeErrorCode.AUDIO_UPSTREAM_FAILED, "The artwork could not be fetched")
        }
    }

    /** Contract §10.2 `/library/songs/{id}/like` (since 1.7): the explicit like state. */
    private suspend fun ApplicationCall.handleSongLike() {
        val songId = parameters["id"].orEmpty()
        val state = writeBody("state")?.contentOrNull?.let { TrackLike.parse(it) }
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid state")
        val result = libraryProvider.setSongLike(songId, state)
            ?: return respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown track")
        respond(SongLikeResponse(result))
    }

    /** Contract §10.2 `/library/albums/{id}/bookmark` (since 1.7): the explicit bookmark. */
    private suspend fun ApplicationCall.handleAlbumBookmark() {
        val albumId = parameters["id"].orEmpty()
        val bookmarked = writeBody("bookmarked")?.booleanOrNull
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid bookmarked")
        val result = libraryProvider.setAlbumBookmark(albumId, bookmarked)
            ?: return respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown album")
        respond(AlbumBookmarkResponse(result))
    }

    /** Contract §10.2 `/library/albums/{id}/like` (since 1.7.2): the explicit album tri-state. */
    private suspend fun ApplicationCall.handleAlbumLike() {
        val albumId = parameters["id"].orEmpty()
        val state = writeBody("state")?.contentOrNull?.let { AlbumLike.parse(it) }
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid state")
        val result = libraryProvider.setAlbumLike(albumId, state)
            ?: return respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown album")
        respond(AlbumLikeResponse(result))
    }

    /** Contract §10.2 `/library/artists/{id}/follow` (since 1.7): the explicit follow state. */
    private suspend fun ApplicationCall.handleArtistFollow() {
        val artistId = parameters["id"].orEmpty()
        val state = writeBody("state")?.contentOrNull?.let { ArtistFollow.parse(it) }
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid state")
        val result = libraryProvider.setArtistFollow(artistId, state)
            ?: return respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown artist")
        respond(ArtistFollowResponse(result))
    }

    /** Contract §10.2 `/library/playlists/{id}/pin` (since 1.7): the explicit pin. */
    private suspend fun ApplicationCall.handlePlaylistPin() {
        val playlistId = parameters["id"]?.toLongOrNull()
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid playlist id")
        val pinned = writeBody("pinned")?.booleanOrNull
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid pinned")
        val result = libraryProvider.setPlaylistPin(playlistId, pinned)
            ?: return respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown playlist")
        respond(PlaylistPinResponse(result))
    }

    /** Contract §10.2 `/library/playlists/{id}/bookmark` (since 1.7.2): the explicit bookmark (the phone's `isYoutubePlaylist`). */
    private suspend fun ApplicationCall.handlePlaylistBookmark() {
        val playlistId = parameters["id"]?.toLongOrNull()
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid playlist id")
        val bookmarked = writeBody("bookmarked")?.booleanOrNull
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid bookmarked")
        val result = libraryProvider.setPlaylistBookmark(playlistId, bookmarked)
            ?: return respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown playlist")
        respond(PlaylistBookmarkResponse(result))
    }

    /** Contract §8.1: `{ "quality" }`, library lookup, then a URL signed for the calling device. */
    private suspend fun ApplicationCall.handleForge() {
        val trackId = parameters["trackId"].orEmpty()
        val quality = receiveBoundedText(BridgeContract.MAX_AUDIO_FORGE_BODY_BYTES)?.let(::parseForgeQuality)
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid audio URL request")
        val track = audioLibrary.track(trackId)
            ?: return respondError(HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown track")
        val expiresAtMs = audioTokens.expiryFor(clock(), track.durationMs)
        val token = audioTokens.forge(trackId, quality, attributes[DeviceIdKey], expiresAtMs)
        // Built from the socket address and port this request reached (not the Host header):
        // the PC always talks to the phone
        val host = request.local.localAddress.let { if (it.contains(':') && !it.startsWith("[")) "[$it]" else it }
        val url = "http://$host:${request.local.localPort}${BridgeContract.API_PREFIX}/audio/" +
            "${trackId.encodeURLParameter()}?${BridgeContract.AUDIO_TOKEN_PARAM}=${token.encodeURLParameter()}"
        respond(AudioUrlResponse(trackId, quality.wire, url, expiresAtMs, track.durationMs))
    }

    /** `quality` of a forge body, `null` when the body or the value is invalid. */
    private fun parseForgeQuality(body: String): AudioQuality? {
        val json = runCatching { BridgeJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val quality = json["quality"] as? JsonPrimitive ?: return null
        return if (quality.isString) AudioQuality.parse(quality.content) else null
    }

    /** Contract §8.2: the token is checked on every request, before anything is opened. */
    private suspend fun ApplicationCall.handleAudio(headOnly: Boolean) {
        val trackId = parameters["trackId"].orEmpty()
        val token = request.queryParameters[BridgeContract.AUDIO_TOKEN_PARAM]
        when (val check = audioTokens.check(token, trackId, isDevicePaired)) {
            AudioTokenCheck.Invalid ->
                respondAudioError(headOnly, HttpStatusCode.Forbidden, BridgeErrorCode.AUDIO_URL_INVALID, "Invalid audio URL")
            AudioTokenCheck.Revoked ->
                respondAudioError(headOnly, HttpStatusCode.Unauthorized, BridgeErrorCode.DEVICE_REVOKED, "Unknown or revoked device")
            AudioTokenCheck.Expired ->
                respondAudioError(headOnly, HttpStatusCode.Forbidden, BridgeErrorCode.AUDIO_URL_EXPIRED, "Audio URL expired")
            is AudioTokenCheck.Valid -> serveAudio(trackId, check.quality, headOnly)
        }
    }

    /**
     * Whole file (`200`) or one range (`206`), with the real type; `HEAD` gets the same headers
     * only. The range headers are set once the source is open, so an error never carries them.
     */
    private suspend fun ApplicationCall.serveAudio(trackId: String, quality: AudioQuality, headOnly: Boolean) {
        val source = when (val opened = audioLibrary.open(trackId, quality)) {
            is AudioOpenResult.Ready -> opened.source
            AudioOpenResult.NotFound -> return respondAudioNotFound(headOnly)
            AudioOpenResult.UpstreamFailed -> return respondUpstreamFailed(headOnly)
        }
        val total = source.length
        val range = ByteRanges.resolve(request.headers[HttpHeaders.Range], total)
        val (status, start, length) = when (range) {
            ByteRange.Full -> Triple(HttpStatusCode.OK, 0L, total)
            is ByteRange.Partial -> Triple(HttpStatusCode.PartialContent, range.start, range.length)
            ByteRange.Unsatisfiable -> {
                response.header(HttpHeaders.AcceptRanges, ByteRanges.BYTES_UNIT)
                response.header(HttpHeaders.ContentRange, ByteRanges.unsatisfiedRange(total))
                return respondAudioError(
                    headOnly,
                    HttpStatusCode.RequestedRangeNotSatisfiable,
                    BridgeErrorCode.RANGE_NOT_SATISFIABLE,
                    "Range outside the file",
                )
            }
        }
        val type = runCatching { ContentType.parse(source.contentType) }.getOrDefault(ContentType.Application.OctetStream)
        if (headOnly) {
            setRangeHeaders(range, total)
            return respond(AudioHeadContent(status, type, length))
        }
        // Opened before any header leaves: a source that cannot be reached is still a clean error
        val stream = try {
            withContext(NzikDispatchers.DATA) { source.openStream(start, length) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AudioSourceGoneException) {
            Timber.tag(TAG).w(e, "Audio file of $trackId gone")
            return respondAudioNotFound(headOnly = false)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Audio source of $trackId unavailable")
            return respondUpstreamFailed(headOnly = false)
        }
        try {
            setRangeHeaders(range, total)
            respond(AudioBodyContent(stream, status, type, length))
        } finally {
            withContext(NonCancellable + NzikDispatchers.DATA) {
                runCatching { stream.close() }.onFailure { Timber.tag(TAG).w(it, "Could not close an audio source") }
            }
        }
    }

    /** `Accept-Ranges` always, `Content-Range` on a `206` (contract §8.2). */
    private fun ApplicationCall.setRangeHeaders(range: ByteRange, total: Long) {
        response.header(HttpHeaders.AcceptRanges, ByteRanges.BYTES_UNIT)
        if (range is ByteRange.Partial) response.header(HttpHeaders.ContentRange, ByteRanges.contentRange(range, total))
    }

    private suspend fun ApplicationCall.respondAudioNotFound(headOnly: Boolean) =
        respondAudioError(headOnly, HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown track")

    private suspend fun ApplicationCall.respondUpstreamFailed(headOnly: Boolean) =
        respondAudioError(headOnly, HttpStatusCode.BadGateway, BridgeErrorCode.AUDIO_UPSTREAM_FAILED, "The audio could not be obtained")

    /** The error model of contract §3; a `HEAD` answer never carries a body. */
    private suspend fun ApplicationCall.respondAudioError(headOnly: Boolean, status: HttpStatusCode, code: String, message: String) {
        if (headOnly) respond(AudioHeadContent(status, null, null)) else respondError(status, code, message)
    }

    /** The request body as text, or `null` when it exceeds [maxBytes]; never buffers more than that. */
    private suspend fun ApplicationCall.receiveBoundedText(maxBytes: Int): String? {
        val declared = request.contentLength()
        if (declared != null && declared > maxBytes) return null
        val channel = receiveChannel()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > maxBytes) return null
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private suspend fun handleSession(session: DefaultWebSocketServerSession, claim: SessionClaim, authorizationHeader: String?) {
        val stream = SessionStream(session, claim.device.deviceId)
        sessions[session] = stream
        // A revocation, kick or replacement between the pre-upgrade check and this
        // registration did not see the session: it is closed the way it would have been
        val refused = if (authenticate(authorizationHeader) !is AuthOutcome.Success) {
            CloseReason(BridgeContract.CLOSE_DEVICE_REVOKED, BridgeContract.CLOSE_REASON_DEVICE_REVOKED)
        } else {
            attachSession(claim, session to stream)
        }
        if (refused != null) {
            sessions.remove(session)
            releaseClaim(claim)
            session.close(refused)
            return
        }
        Timber.tag(TAG).i("Bridge session opened (${sessions.size} active)")
        try {
            // Contract §6.1: the snapshot is always the first message
            stream.open()
            while (true) {
                // Contract §6.3: any client message, even an unknown or invalid one, is activity
                val received = withTimeoutOrNull(pingTimeoutMs) { session.incoming.receiveCatching() }
                if (received == null) {
                    Timber.tag(TAG).i("No client message for $pingTimeoutMs ms, closing the session")
                    stream.detach()
                    runCatching {
                        session.close(CloseReason(BridgeContract.CLOSE_PING_TIMEOUT, BridgeContract.CLOSE_REASON_PING_TIMEOUT))
                    }.onFailure { Timber.tag(TAG).w(it, "Failed to close a silent session") }
                    break
                }
                val frame = received.getOrNull() ?: break
                stream.handleClientFrame(frame)
            }
        } finally {
            sessions.remove(session)
            releaseClaim(claim)
            withContext(NonCancellable) { stream.detach() }
            Timber.tag(TAG).i("Bridge session closed (${sessions.size} active)")
        }
    }

    /**
     * State stream of one WebSocket session: the hub subscription (snapshot then deltas),
     * the heartbeat and the pongs, all funnelled through one non-blocking outgoing channel
     * drained by a single writer, so frames leave in the order they were queued.
     */
    private inner class SessionStream(private val session: DefaultWebSocketServerSession, val deviceId: String) {
        private val lock = Any()
        private val outgoing = Channel<String>(OUTGOING_BUFFER)
        private var detached = false
        private var subscription: BridgeStateHub.Subscription? = null
        private var writer: Job? = null
        private var heartbeat: Job? = null

        fun open() {
            synchronized(lock) {
                if (detached) return
                writer = session.launch {
                    for (text in outgoing) session.send(Frame.Text(text))
                }
                val sub = stateHub.subscribe(::enqueue)
                subscription = sub
                heartbeat = session.launch {
                    while (isActive) {
                        delay(heartbeatIntervalMs)
                        sub.sendHeartbeat()
                    }
                }
            }
        }

        private fun enqueue(message: BridgeServerMessage) {
            if (outgoing.trySend(encodeServerMessage(message)).isFailure && !outgoing.isClosedForSend) {
                Timber.tag(TAG).w("Outgoing buffer full, dropping a ${message::class.simpleName}")
            }
        }

        /** Contract §7.8: `ping` → immediate `pong`, `requestSnapshot` → `snapshot`, anything else ignored. */
        fun handleClientFrame(frame: Frame) {
            val receivedAt = clock()
            val text = (frame as? Frame.Text)?.readText() ?: return
            val message = runCatching { BridgeJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
            when ((message["type"] as? JsonPrimitive)?.contentOrNull) {
                BridgeContract.TYPE_PING -> {
                    val clientTimeMs = (message["clientTimeMs"] as? JsonPrimitive)?.longOrNull ?: return
                    enqueue(PongMessage(clientTimeMs, serverReceiveTimeMs = receivedAt, serverSendTimeMs = clock()))
                }
                BridgeContract.TYPE_REQUEST_SNAPSHOT -> synchronized(lock) { subscription }?.sendSnapshot()
            }
        }

        /**
         * Stops every state message for good and flushes what is already queued, so that a
         * closing frame sent afterwards (`serverStopped`, `4003`, `4008`) is the last one.
         */
        suspend fun detach() {
            val pendingWriter = synchronized(lock) {
                if (detached) return
                detached = true
                subscription?.cancel()
                heartbeat?.cancel()
                outgoing.close()
                writer
            } ?: return
            if (withTimeoutOrNull(FLUSH_TIMEOUT_MS) { pendingWriter.join() } == null) pendingWriter.cancelAndJoin()
        }
    }

    /** The caller's device id, or `null` once a `401` has been answered (contract §2). */
    private suspend fun ApplicationCall.authenticateOrReject(): String? =
        when (val outcome = authenticate(request.headers[HttpHeaders.Authorization])) {
            is AuthOutcome.Failure -> {
                respond(HttpStatusCode.Unauthorized, outcome.error)
                null
            }
            is AuthOutcome.Success -> outcome.deviceId.also { attributes.put(DeviceIdKey, it) }
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

private const val AUDIO_COPY_BUFFER_BYTES = 64 * 1_024

/** Headers of an audio answer without its body (`HEAD`). */
private class AudioHeadContent(
    override val status: HttpStatusCode,
    override val contentType: ContentType?,
    override val contentLength: Long?,
) : OutgoingContent.NoContent()

/**
 * Audio body of exactly [contentLength] bytes, copied from [stream] off the main thread
 * through a fixed buffer: the track is never held in memory. The expiry of the URL is not
 * checked again here, so a response under way is never cut (contract §8.2). A source that
 * ends early fails the body instead of completing it; the caller closes [stream].
 */
internal class AudioBodyContent(
    private val stream: AudioStream,
    override val status: HttpStatusCode,
    override val contentType: ContentType,
    override val contentLength: Long,
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) = withContext(NzikDispatchers.DATA) {
        val buffer = ByteArray(AUDIO_COPY_BUFFER_BYTES)
        var written = 0L
        while (written < contentLength) {
            val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), contentLength - written).toInt())
            if (read < 0) throw IOException("Audio source ended ${contentLength - written} bytes early")
            channel.writeFully(buffer, 0, read)
            written += read
        }
    }
}

/** Route groups of the commands of contract §9: `player` and `queue` below `/api/v1`. */
private val COMMAND_GROUPS = listOf("player", "queue")

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
    respond(status, ErrorResponse(code = code, message = message))
