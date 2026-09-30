package app.n_zik.android.bridge.pairing

import java.util.ArrayDeque

/** Outcome of [PairingRateLimiter.tryAcquire]. */
sealed interface RateLimitResult {
    data object Allowed : RateLimitResult
    /** [retryAfterMs]: time left before an attempt frees up in the saturated window(s). */
    data class Limited(val retryAfterMs: Long) : RateLimitResult
}

/**
 * Sliding-window budget of pairing validations (contract §4.5 step 1): at most
 * [PER_IP_LIMIT] per source IP over [PER_IP_WINDOW_MS] and [GLOBAL_LIMIT] overall over
 * [GLOBAL_WINDOW_MS]. A refused call does not consume budget, so `retryAfterMs` stays exact.
 */
class PairingRateLimiter(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val perIp = HashMap<String, ArrayDeque<Long>>()
    private val global = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(ip: String): RateLimitResult {
        val now = clock()
        prune(global, now - GLOBAL_WINDOW_MS)
        perIp.values.forEach { prune(it, now - PER_IP_WINDOW_MS) }
        perIp.values.removeAll { it.isEmpty() }
        val ipAttempts = perIp[ip]

        val waits = buildList {
            if (ipAttempts != null && ipAttempts.size >= PER_IP_LIMIT) {
                add(ipAttempts.first() + PER_IP_WINDOW_MS - now)
            }
            if (global.size >= GLOBAL_LIMIT) add(global.first() + GLOBAL_WINDOW_MS - now)
        }
        if (waits.isNotEmpty()) return RateLimitResult.Limited(retryAfterMs = waits.max().coerceAtLeast(1))

        perIp.getOrPut(ip) { ArrayDeque() }.addLast(now)
        global.addLast(now)
        return RateLimitResult.Allowed
    }

    private fun prune(attempts: ArrayDeque<Long>, threshold: Long) {
        while (attempts.isNotEmpty() && attempts.first() <= threshold) attempts.removeFirst()
    }

    companion object {
        const val PER_IP_LIMIT = 5
        const val PER_IP_WINDOW_MS = 60_000L
        const val GLOBAL_LIMIT = 20
        const val GLOBAL_WINDOW_MS = 600_000L
    }
}
