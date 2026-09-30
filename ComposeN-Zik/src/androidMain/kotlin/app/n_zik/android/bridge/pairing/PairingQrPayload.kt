package app.n_zik.android.bridge.pairing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.Inet4Address
import java.net.NetworkInterface
import timber.log.Timber

private const val TAG = "BridgePairingQr"
private const val DEFAULT_PREFIX_LENGTH = 24

/** QR shown by the PC (contract §4.3), once validated. */
data class PairingQrPayload(
    val requestId: String,
    val deviceName: String,
    val port: Int,
    val ips: List<String>,
) {
    /** Outcome of [parse]. */
    sealed interface ParseResult {
        data class Valid(val payload: PairingQrPayload) : ParseResult
        /** Not an N-Zik pairing QR at all (`type` ≠ `nzik-pair`, or not JSON). */
        data object NotPairingQr : ParseResult
        /** `v` ≠ 1: pairing version not supported. */
        data object UnsupportedVersion : ParseResult
        /** A pairing QR with a missing or out-of-range field. */
        data object Invalid : ParseResult
    }

    companion object {
        const val TYPE = "nzik-pair"
        const val VERSION = 1
        private const val MAX_IPS = 4
        private const val MAX_NAME_LENGTH = 64
        private val REQUEST_ID = Regex("^[A-Za-z0-9_-]{22}$")
        private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ParseResult {
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?: return ParseResult.NotPairingQr
            if (root.string("type") != TYPE) return ParseResult.NotPairingQr
            if ((root["v"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull != VERSION) {
                return ParseResult.UnsupportedVersion
            }
            val requestId = root.string("requestId")?.takeIf { REQUEST_ID.matches(it) }
            val deviceName = root.string("deviceName")?.trim()?.takeIf { it.length in 1..MAX_NAME_LENGTH }
            val port = (root["port"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it in 1..65_535 }
            val ips = (root["ips"] as? JsonArray)
                ?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                ?.takeIf { list -> list.size in 1..MAX_IPS && list.all { it != null && isPrivateIpv4(it) } }
                ?.filterNotNull()
            if (requestId == null || deviceName == null || port == null || ips == null) return ParseResult.Invalid
            return ParseResult.Valid(PairingQrPayload(requestId, deviceName, port, ips))
        }

        /**
         * Candidates on the phone's own subnet first, then the others, each group keeping
         * the QR order (contract §4.3, phone side).
         */
        fun orderCandidates(ips: List<String>, phoneIp: String, prefixLength: Int): List<String> {
            val phone = ipv4ToInt(phoneIp) ?: return ips
            val mask = if (prefixLength <= 0) 0 else (-1 shl (32 - prefixLength.coerceAtMost(32)))
            val (sameSubnet, others) = ips.partition { ip -> ipv4ToInt(ip)?.let { (it and mask) == (phone and mask) } == true }
            return sameSubnet + others
        }

        /** Prefix length of the interface holding [phoneIp], /24 when it cannot be read. */
        fun prefixLengthOf(phoneIp: String): Int = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .flatMap { it.interfaceAddresses }
                .firstOrNull { it.address is Inet4Address && it.address.hostAddress == phoneIp }
                ?.networkPrefixLength?.toInt()
        }.onFailure { Timber.tag(TAG).w(it, "Could not read the Wi-Fi prefix length") }
            .getOrNull() ?: DEFAULT_PREFIX_LENGTH

        /** RFC 1918 IPv4 only (`10/8`, `172.16/12`, `192.168/16`): the offer never leaves the LAN. */
        fun isPrivateIpv4(ip: String): Boolean {
            val value = ipv4ToInt(ip) ?: return false
            val first = value ushr 24
            val second = (value ushr 16) and 0xFF
            return first == 10 || (first == 172 && second in 16..31) || (first == 192 && second == 168)
        }

        private fun ipv4ToInt(ip: String): Int? {
            val parts = IPV4.matchEntire(ip)?.groupValues?.drop(1)?.map { it.toInt() } ?: return null
            if (parts.any { it > 255 }) return null
            return parts.fold(0) { acc, part -> (acc shl 8) or part }
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }
}
