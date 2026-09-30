package app.n_zik.android.bridge.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import timber.log.Timber

private const val TAG = "BridgePairingCode"

/** Code shown on the pairing card; [expiresAtMs] is on the manager's clock. */
data class PairingCode(val code: String, val expiresAtMs: Long) {
    /** Display form `ABC-DEF` (contract §4.1). */
    val display: String get() = "${code.take(3)}-${code.drop(3)}"
}

/** Outcome of [PairingCodeManager.validate]. */
sealed interface CodeValidation {
    data object Accepted : CodeValidation
    /** Uniform refusal (contract §3 `PAIRING_REJECTED`), whatever the reason. */
    data object Rejected : CodeValidation
}

/**
 * Ephemeral pairing code of contract §4.1: a single active code, memory only, alive only
 * while the pairing card is open ([open] / [close]). Valid [CODE_VALIDITY_MS], single use,
 * invalidated after [MAX_FAILURES] refused validations. A QR `requestId` is bound to the
 * active code when the user confirms the scan (contract §4.2 step 5).
 */
class PairingCodeManager(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private val _code = MutableStateFlow<PairingCode?>(null)

    /** Active code, `null` while pairing mode is closed. */
    val code: StateFlow<PairingCode?> = _code.asStateFlow()

    private var failures = 0
    private var boundRequestId: String? = null
    private val usedRequestIds = mutableSetOf<String>()

    /** Enters pairing mode: generates a code unless a valid one is already active. */
    @Synchronized
    fun open() {
        val current = _code.value
        if (current == null || isExpired(current)) regenerate()
    }

    /** Leaves pairing mode: no code is active any more. */
    @Synchronized
    fun close() {
        _code.value = null
        resetCodeState()
    }

    /** Replaces the active code (manual "Regenerate"); the previous one is invalid at once. */
    @Synchronized
    fun regenerate() {
        resetCodeState()
        _code.value = PairingCode(code = generateCode(), expiresAtMs = clock() + CODE_VALIDITY_MS)
    }

    /** Automatic regeneration on expiry while pairing mode is open. */
    @Synchronized
    fun refreshIfExpired() {
        val current = _code.value ?: return
        if (isExpired(current)) regenerate()
    }

    /**
     * Binds a scanned QR's [requestId] to the active code, after the user confirmed the scan.
     * A code with less than [MIN_REMAINING_TO_BIND_MS] left is replaced first. Returns the code
     * to push in the offer, or `null` when pairing mode is closed or the request id was already used.
     */
    @Synchronized
    fun bindRequestId(requestId: String): String? {
        val active = _code.value ?: return null
        if (requestId in usedRequestIds) return null
        // The PC validates after the offer round trip: never bind a code about to expire
        if (active.expiresAtMs - clock() < MIN_REMAINING_TO_BIND_MS) regenerate()
        val current = _code.value ?: return null
        boundRequestId = requestId
        return current.code
    }

    /**
     * Contract §4.5 steps 2 to 5. Every refusal counts against the active code; the code is
     * replaced after [MAX_FAILURES] refusals. On success the code and request id are consumed
     * and a fresh code is shown (pairing mode stays open).
     */
    @Synchronized
    fun validate(rawCode: String, requestId: String?): CodeValidation {
        val current = _code.value ?: return CodeValidation.Rejected
        val normalized = normalize(rawCode)
        val codeMatches = normalized != null && normalized == current.code && !isExpired(current)
        val requestMatches = requestId == null ||
            (requestId == boundRequestId && requestId !in usedRequestIds)
        if (!codeMatches || !requestMatches) {
            failures++
            if (failures >= MAX_FAILURES) {
                Timber.tag(TAG).w("Pairing code invalidated after $MAX_FAILURES failed attempts")
                regenerate()
            }
            return CodeValidation.Rejected
        }
        requestId?.let { usedRequestIds += it }
        regenerate()
        return CodeValidation.Accepted
    }

    private fun resetCodeState() {
        failures = 0
        boundRequestId = null
    }

    private fun isExpired(code: PairingCode): Boolean = clock() >= code.expiresAtMs

    private fun generateCode(): String =
        String(CharArray(CODE_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    companion object {
        /** Contract §4.1 / §14: 31 symbols, without `0 O 1 I L`. */
        const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        const val CODE_LENGTH = 6
        const val CODE_VALIDITY_MS = 120_000L
        const val MAX_FAILURES = 10
        const val MIN_REMAINING_TO_BIND_MS = 30_000L

        /** Uppercase, spaces and dashes removed; `null` unless 6 characters of the alphabet. */
        fun normalize(raw: String): String? =
            raw.uppercase()
                .filterNot { it.isWhitespace() || it == '-' }
                .takeIf { code -> code.length == CODE_LENGTH && code.all { it in ALPHABET } }
    }
}
