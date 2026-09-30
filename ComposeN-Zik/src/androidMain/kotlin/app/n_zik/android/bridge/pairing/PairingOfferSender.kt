package app.n_zik.android.bridge.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import timber.log.Timber

private const val TAG = "BridgePairingOffer"
private const val OFFER_PATH = "/nzik-pair/v1/offer"

/** Body of `POST /nzik-pair/v1/offer` (contract §4.4). */
@Serializable
data class PairingOffer(
    val v: Int = PairingQrPayload.VERSION,
    val requestId: String,
    val code: String,
    val serverIps: List<String>,
    val serverPort: Int,
    val serverName: String,
)

/** Outcome of [PairingOfferSender.send]. */
sealed interface OfferResult {
    /** The PC answered `200`: it now calls `pairing/validate`. */
    data class Accepted(val ip: String) : OfferResult
    /** The PC answered `410`: its QR expired or was already used, a new scan is needed. */
    data object Expired : OfferResult
    /** Some candidate answered with another status, none accepted the offer. */
    data class Refused(val status: Int) : OfferResult
    /** No candidate could be reached. */
    data object Unreachable : OfferResult
}

/**
 * Pushes the offer to the PC's temporary listener. A raw HTTP/1.1 socket is used on purpose:
 * the app's network security config forbids cleartext outside localhost for OkHttp, and this
 * single LAN request (spine AD-7) must not loosen it.
 */
class PairingOfferSender(
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) {
    private val json = Json { encodeDefaults = true }

    /** Tries each candidate in order; blocking, call it off the main thread. */
    fun send(ips: List<String>, port: Int, offer: PairingOffer): OfferResult {
        val body = json.encodeToString(PairingOffer.serializer(), offer).toByteArray(Charsets.UTF_8)
        var refusedStatus: Int? = null
        for (ip in ips) {
            val status = runCatching { post(ip, port, body) }
                .onFailure { Timber.tag(TAG).i("Candidate $ip:$port unreachable: ${it.message}") }
                .getOrNull() ?: continue
            when (status) {
                200 -> return OfferResult.Accepted(ip)
                410 -> return OfferResult.Expired
                else -> {
                    Timber.tag(TAG).w("Candidate $ip:$port refused the offer with HTTP $status")
                    refusedStatus = status
                }
            }
        }
        return refusedStatus?.let { OfferResult.Refused(it) } ?: OfferResult.Unreachable
    }

    private fun post(ip: String, port: Int, body: ByteArray): Int = Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), connectTimeoutMs)
        socket.soTimeout = readTimeoutMs
        val head = buildString {
            append("POST $OFFER_PATH HTTP/1.1\r\n")
            append("Host: $ip:$port\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
        val statusLine = readLine(socket)
        // "HTTP/1.1 200 OK"
        statusLine.takeIf { it.startsWith("HTTP/") }
            ?.split(' ')?.getOrNull(1)?.toIntOrNull()
            ?: throw IOException("Malformed status line")
    }

    private fun readLine(socket: Socket): String {
        val input = socket.getInputStream()
        val line = StringBuilder()
        while (line.length < MAX_STATUS_LINE) {
            val byte = input.read()
            if (byte == -1 || byte == '\n'.code) break
            if (byte != '\r'.code) line.append(byte.toChar())
        }
        return line.toString()
    }

    companion object {
        /** Contract §4.3 / §14: 2 000 ms per candidate IP. */
        const val CONNECT_TIMEOUT_MS = 2_000
        const val READ_TIMEOUT_MS = 5_000
        private const val MAX_STATUS_LINE = 256
    }
}
