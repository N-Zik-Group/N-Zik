package app.n_zik.android.bridge

import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.withTimeoutOrNull
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import timber.log.Timber

private const val TAG = "BridgeServer"
private const val GRACE_PERIOD_MS = 500L

/**
 * CIO engine hosting [BridgeServerCore], bound to a single address (the Wi-Fi IPv4).
 * Tries each port of [portCandidates] in order; `0` lets the OS pick a free port.
 */
internal class BridgeServer(
    private val core: BridgeServerCore,
    private val portCandidates: List<Int> = BridgeContract.DEFAULT_PORT_CANDIDATES,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    /** Starts the engine on [host] and returns the port actually bound. */
    suspend fun start(host: String): Int {
        check(server == null) { "Bridge server already started" }
        for (port in portCandidates) {
            // Probe first: when CIO itself hits a busy port, the BindException also escapes
            // from an internal coroutine as an uncaught exception
            if (!isPortFree(host, port)) {
                Timber.tag(TAG).w("Port $port unavailable on $host")
                continue
            }
            val candidate = embeddedServer(CIO, port = port, host = host) { core.install(this) }
            try {
                candidate.startSuspend(wait = false)
            } catch (e: Exception) {
                // CIO reports a busy port as a cancellation whose cause chain holds the BindException
                val bindFailure = generateSequence<Throwable>(e) { it.cause }.firstOrNull { it is BindException } ?: throw e
                Timber.tag(TAG).w("Port $port unavailable on $host: ${bindFailure.message}")
                runCatching { candidate.stopSuspend(0, 0) }
                continue
            }
            server = candidate
            val bound = candidate.engine.resolvedConnectors().first().port
            Timber.tag(TAG).i("Bridge server listening on $host:$bound")
            return bound
        }
        throw BindException("No port available on $host among $portCandidates")
    }

    /**
     * Runs the stop sequence (contract §11.3) within [BridgeContract.STOP_TIMEOUT_MS]:
     * sessions are closed first (with or without the `serverStopped` frame), then the engine.
     */
    suspend fun stop(code: BridgeStopCode?) {
        val finished = withTimeoutOrNull(BridgeContract.STOP_TIMEOUT_MS) {
            core.shutdownSessions(code)
            server?.stopSuspend(GRACE_PERIOD_MS, BridgeContract.STOP_TIMEOUT_MS / 2)
        }
        if (finished == null) Timber.tag(TAG).w("Bridge stop sequence exceeded ${BridgeContract.STOP_TIMEOUT_MS} ms")
        server = null
    }

    private fun isPortFree(host: String, port: Int): Boolean {
        if (port == 0) return true
        return runCatching { ServerSocket().use { it.bind(InetSocketAddress(host, port)) } }.isSuccess
    }
}
