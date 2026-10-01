package app.n_zik.android.bridge.audio

import app.n_zik.android.bridge.AudioQuality
import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.pairing.Base64Url
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val HMAC_ALGORITHM = "HmacSHA256"
private const val KEY_BYTES = 32
private const val TOKEN_VERSION = "2"
private const val SEPARATOR = '.'
private val DEVICE_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
private val NUMBER_PATTERN = Regex("[0-9]{1,19}")

/** Outcome of checking an audio token, in the order of contract §8.2. */
internal sealed interface AudioTokenCheck {
    /** Accepted: the bytes of the track may be served at [quality]. */
    data class Valid(val deviceId: String, val quality: AudioQuality, val expiresAtMs: Long) : AudioTokenCheck

    /** Malformed, bad signature, other track or other server run: `403 AUDIO_URL_INVALID`. */
    data object Invalid : AudioTokenCheck

    /** The device it was forged for is no longer paired, or was kicked since the forge: `401 DEVICE_REVOKED`. */
    data object Revoked : AudioTokenCheck

    /** Past its `expiresAtMs`: `403 AUDIO_URL_EXPIRED`. */
    data object Expired : AudioTokenCheck
}

/**
 * Ephemeral audio tokens of contract §8.1–§8.2, signed with HMAC-SHA256 under a [key] drawn
 * once per server run: a token forged before a restart is `AUDIO_URL_INVALID` afterwards.
 *
 * Form: `<deviceId>.<generation>.<quality>.<expiresAtMs>.<mac>`, URL-safe, well under 512
 * characters. The MAC covers the version, those four fields and the `trackId`, which is not
 * carried in the token: the track of the URL path must be the one the token was forged for.
 * `deviceId` is public (contract §2), so nothing secret travels in clear.
 *
 * `generation` is the device's kick count of this server run (memory only, like [key]):
 * [invalidate] bumps it, so every URL forged before a kick is refused while those forged
 * afterwards stay valid (contract §8.4).
 */
internal class AudioTokens(
    private val key: ByteArray = randomKey(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(key.size >= KEY_BYTES) { "Audio token key too short" }
    }

    /** Current generation of each device ever kicked during this run; absent = `0`. */
    private val generations = ConcurrentHashMap<String, Long>()

    private fun generationOf(deviceId: String): Long = generations[deviceId] ?: 0L

    /** Contract §8.4: every URL already forged for [deviceId] becomes `401 DEVICE_REVOKED`. */
    fun invalidate(deviceId: String) {
        generations.merge(deviceId, 1L, Long::plus)
    }

    /** `expiresAtMs` of a URL forged now for a track of [durationMs] (contract §8.1). */
    fun expiryFor(forgedAtMs: Long, durationMs: Long?): Long =
        forgedAtMs + if (durationMs == null || durationMs <= 0) {
            BridgeContract.AUDIO_URL_UNKNOWN_DURATION_TTL_MS
        } else {
            minOf(durationMs + BridgeContract.AUDIO_URL_DURATION_MARGIN_MS, BridgeContract.AUDIO_URL_MAX_TTL_MS)
        }

    fun forge(trackId: String, quality: AudioQuality, deviceId: String, expiresAtMs: Long): String {
        require(DEVICE_ID_PATTERN.matches(deviceId)) { "Unexpected device id format" }
        require(expiresAtMs >= 0) { "Negative expiry" }
        val generation = generationOf(deviceId)
        val fields = "$deviceId$SEPARATOR$generation$SEPARATOR${quality.wire}$SEPARATOR$expiresAtMs"
        return "$fields$SEPARATOR${mac(trackId, deviceId, generation, quality, expiresAtMs)}"
    }

    /**
     * Contract §8.2, in order: invalid (form, signature, track) → revoked ([isPaired]
     * false for the bound device, or the device kicked since the forge) → expired
     * (checked against the clock now, at the start of the request). [isPaired] is only
     * consulted for a genuine token.
     */
    fun check(token: String?, trackId: String, isPaired: (String) -> Boolean): AudioTokenCheck {
        if (token == null || token.length > BridgeContract.AUDIO_TOKEN_MAX_LENGTH) return AudioTokenCheck.Invalid
        val parts = token.split(SEPARATOR)
        if (parts.size != 5) return AudioTokenCheck.Invalid
        val (deviceId, generationText, qualityWire, expiryText, presentedMac) = parts
        if (!DEVICE_ID_PATTERN.matches(deviceId) || !NUMBER_PATTERN.matches(generationText) || !NUMBER_PATTERN.matches(expiryText)) {
            return AudioTokenCheck.Invalid
        }
        val quality = AudioQuality.parse(qualityWire) ?: return AudioTokenCheck.Invalid
        val generation = generationText.toLongOrNull() ?: return AudioTokenCheck.Invalid
        val expiresAtMs = expiryText.toLongOrNull() ?: return AudioTokenCheck.Invalid
        val expected = mac(trackId, deviceId, generation, quality, expiresAtMs)
        // Constant-time comparison: no early exit revealing how much of the MAC matched
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), presentedMac.toByteArray(Charsets.US_ASCII))) {
            return AudioTokenCheck.Invalid
        }
        if (!isPaired(deviceId) || generation < generationOf(deviceId)) return AudioTokenCheck.Revoked
        if (clock() > expiresAtMs) return AudioTokenCheck.Expired
        return AudioTokenCheck.Valid(deviceId, quality, expiresAtMs)
    }

    private fun mac(trackId: String, deviceId: String, generation: Long, quality: AudioQuality, expiresAtMs: Long): String {
        // Newline-separated: none of the fields can hold a newline except the trackId, which comes last
        val message = "$TOKEN_VERSION\n$deviceId\n$generation\n${quality.wire}\n$expiresAtMs\n$trackId"
        val hmac = Mac.getInstance(HMAC_ALGORITHM).apply { init(SecretKeySpec(key, HMAC_ALGORITHM)) }
        return Base64Url.encode(hmac.doFinal(message.toByteArray(Charsets.UTF_8)))
    }

    companion object {
        /** A fresh signing key: one per server run. */
        fun randomKey(): ByteArray = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
    }
}
