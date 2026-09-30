package app.n_zik.android.bridge

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
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.ktor.http.ContentType
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
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.utils.io.readAvailable
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayOutputStream
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
) {
    private val stopping = AtomicBoolean(false)

    /** Contract §2: on a Bearer route the authentication runs before any other processing. */
    private val bearerAuth = createRouteScopedPlugin("BridgeBearerAuth") {
        onCall { call -> call.rejectUnlessAuthenticated() }
    }

    /** Open WebSocket sessions and their state stream (which knows the device id). */
    private val sessions = ConcurrentHashMap<DefaultWebSocketServerSession, SessionStream>()

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
                    get("playlists") { call.respondListPage { LibraryQueries.sortPlaylists(libraryProvider.playlists()) } }
                    get("playlists/{id}/songs") {
                        val id = LibraryQueries.parsePlaylistId(call.parameters["id"])
                        call.respondTracks("Unknown playlist") { id?.let { libraryProvider.playlistSongs(it) } }
                    }
                    get("albums") { call.respondCollectionPage { LibraryQueries.sortAlbums(libraryProvider.albums(it)) } }
                    get("albums/{id}/songs") {
                        val id = call.parameters["id"].orEmpty()
                        call.respondTracks("Unknown album") { libraryProvider.albumSongs(id) }
                    }
                    get("albums/{id}/artwork") {
                        val id = call.parameters["id"].orEmpty()
                        call.respondArtwork { size -> libraryProvider.albumArtwork(id, size) }
                    }
                    get("artists") { call.respondCollectionPage { LibraryQueries.sortArtists(libraryProvider.artists(it)) } }
                    get("artists/{id}/songs") {
                        val id = call.parameters["id"].orEmpty()
                        call.respondTracks("Unknown artist") { libraryProvider.artistSongs(id) }
                    }
                    get("artists/{id}/artwork") {
                        val id = call.parameters["id"].orEmpty()
                        call.respondArtwork { size -> libraryProvider.artistArtwork(id, size) }
                    }
                }
                route("artwork") {
                    install(bearerAuth)
                    get("{trackId}") {
                        val trackId = call.parameters["trackId"].orEmpty()
                        call.respondArtwork { size -> libraryProvider.trackArtwork(trackId, size) }
                    }
                }
                route("ws") {
                    // Contract §6.1: authentication happens before the upgrade
                    install(bearerAuth)
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
        sessions.filterValues { it.deviceId == deviceId }.forEach { (session, stream) ->
            sessions.remove(session)
            runCatching {
                stream.detach()
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

    /** Contract §10 `/library/songs`: parameters, then filter, search, sort and pagination. */
    private suspend fun ApplicationCall.handleSongs() {
        val params = request.queryParameters
        val query = LibraryQueries.parseSongsQuery(params["offset"], params["limit"], params["query"], params["filter"], params["sort"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid library parameters")
        val tracks = LibraryQueries.selectSongs(libraryProvider.songs(), query).map { it.track }
        respond(LibraryQueries.paginate(tracks, query.page))
    }

    /** Contract §1 pagination parameters, or `null` once a `400` has been answered. */
    private suspend fun ApplicationCall.pageOrReject(): PageRequest? {
        val page = LibraryQueries.parsePage(request.queryParameters["offset"], request.queryParameters["limit"])
        if (page == null) respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid pagination")
        return page
    }

    private suspend inline fun <reified T> ApplicationCall.respondListPage(load: () -> List<T>) {
        val page = pageOrReject() ?: return
        respond(LibraryQueries.paginate(load(), page))
    }

    /** Albums and artists: pagination plus `filter` (`library` by default, contract §10). */
    private suspend inline fun <reified T> ApplicationCall.respondCollectionPage(load: (CollectionFilter) -> List<T>) {
        val page = pageOrReject() ?: return
        val filter = LibraryQueries.parseCollectionFilter(request.queryParameters["filter"])
            ?: return respondError(HttpStatusCode.BadRequest, BridgeErrorCode.BAD_REQUEST, "Invalid filter")
        respond(LibraryQueries.paginate(load(filter), page))
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

    private suspend fun handleSession(session: DefaultWebSocketServerSession, deviceId: String, authorizationHeader: String?) {
        val stream = SessionStream(session, deviceId)
        sessions[session] = stream
        // A revocation between the pre-upgrade check and this registration did not see the session
        if (authenticate(authorizationHeader) !is AuthOutcome.Success) {
            sessions.remove(session)
            session.close(CloseReason(BridgeContract.CLOSE_DEVICE_REVOKED, BridgeContract.CLOSE_REASON_DEVICE_REVOKED))
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

/** Route groups of the commands of contract §9: `player` and `queue` below `/api/v1`. */
private val COMMAND_GROUPS = listOf("player", "queue")

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
    respond(status, ErrorResponse(code = code, message = message))
