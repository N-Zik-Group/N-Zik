package app.n_zik.android.components.pullrefresh

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Offline-gated pull-to-refresh decision (spec-full-offline-mode): online runs the
 * real refresh exactly once; offline never refreshes and fires the no-network
 * handler once.
 */
class OfflineGatedRefreshDecisionTest {

    @Test
    fun `offline pull never refreshes and fires the no-network handler once`() = runBlocking {
        var refreshCalls = 0
        var noNetworkCalls = 0
        offlineGatedRefreshDecision(
            isOnline = false,
            refresh = { refreshCalls++ },
            onNoNetwork = { noNetworkCalls++ }
        )
        assertEquals(0, refreshCalls)
        assertEquals(1, noNetworkCalls)
    }

    @Test
    fun `online pull refreshes once and never fires the no-network handler`() = runBlocking {
        var refreshCalls = 0
        var noNetworkCalls = 0
        offlineGatedRefreshDecision(
            isOnline = true,
            refresh = { refreshCalls++ },
            onNoNetwork = { noNetworkCalls++ }
        )
        assertEquals(1, refreshCalls)
        assertEquals(0, noNetworkCalls)
    }
}
