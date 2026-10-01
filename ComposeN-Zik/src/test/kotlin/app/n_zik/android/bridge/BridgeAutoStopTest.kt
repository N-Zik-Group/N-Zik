package app.n_zik.android.bridge

import app.n_zik.android.bridge.command.BridgeCommand
import app.n_zik.android.bridge.command.BridgeCommandExecutor
import app.n_zik.android.bridge.command.CommandResult
import app.n_zik.android.bridge.pairing.InMemoryPairedDeviceStorage
import app.n_zik.android.bridge.pairing.JsonPairedDeviceStore
import app.n_zik.android.bridge.state.BridgeStateHub
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private const val MINUTE = 60_000L
private val PC_A = ActiveDevice(deviceId = "a", deviceName = "PC-A")
private val PC_B = ActiveDevice(deviceId = "b", deviceName = "PC-B")

/** Contract §11.2: auto-stop timers, run in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class BridgeAutoStopTest {

    private class Watcher(
        val settings: MutableStateFlow<AutoStopSettings>,
        val active: MutableStateFlow<ActiveDevice?>,
        val ticks: MutableSharedFlow<Unit>,
    ) {
        var expiredAt: Long? = null
        var expiries = 0

        /** Time the watcher's clock is ahead of virtual time (deep sleep: `now` runs, `delay` does not). */
        var sleptMs = 0L
    }

    /** Starts the watcher at virtual time 0, recording when (and how often) it expires. */
    private fun TestScope.watch(
        settings: AutoStopSettings = AutoStopSettings(),
        active: ActiveDevice? = null,
    ): Watcher {
        val watcher = Watcher(MutableStateFlow(settings), MutableStateFlow(active), MutableSharedFlow(extraBufferCapacity = 1))
        backgroundScope.launch {
            BridgeAutoStop.awaitExpiry(watcher.settings, watcher.active, watcher.ticks) { testScheduler.currentTime + watcher.sleptMs }
            watcher.expiredAt = testScheduler.currentTime
            watcher.expiries++
        }
        runCurrent()
        return watcher
    }

    /** Moves virtual time to [timeMs] (absolute) and runs what is due. */
    private fun TestScope.at(timeMs: Long) {
        advanceTimeBy(timeMs - testScheduler.currentTime)
        runCurrent()
    }

    @Test
    fun `no client stops after the default 15 minutes`() = runTest {
        val watcher = watch()
        at(15 * MINUTE - 1)
        assertNull(watcher.expiredAt)
        at(15 * MINUTE)
        assertEquals(15 * MINUTE, watcher.expiredAt)
    }

    @Test
    fun `a session cancels the no-client timer, which restarts from zero once released`() = runTest {
        val watcher = watch()
        at(10 * MINUTE)
        watcher.active.value = PC_A
        runCurrent()
        at(40 * MINUTE)
        assertNull(watcher.expiredAt)

        watcher.active.value = null
        runCurrent()
        at(55 * MINUTE - 1)
        assertNull(watcher.expiredAt)
        at(55 * MINUTE)
        assertEquals(55 * MINUTE, watcher.expiredAt)
    }

    @Test
    fun `a replaced session does not restart the no-client timer`() = runTest {
        val watcher = watch(AutoStopSettings(noClientMinutes = 5), active = PC_A)
        at(3 * MINUTE)
        watcher.active.value = PC_B
        runCurrent()
        at(60 * MINUTE)
        assertNull(watcher.expiredAt)
    }

    @Test
    fun `inactivity stops an open session without any command`() = runTest {
        val watcher = watch(AutoStopSettings(noClientMinutes = 15, inactivityMinutes = 5), active = PC_A)
        at(5 * MINUTE - 1)
        assertNull(watcher.expiredAt)
        at(5 * MINUTE)
        assertEquals(5 * MINUTE, watcher.expiredAt)
    }

    @Test
    fun `a command restarts the inactivity timer`() = runTest {
        val watcher = watch(AutoStopSettings(noClientMinutes = 15, inactivityMinutes = 5), active = PC_A)
        at(4 * MINUTE)
        watcher.ticks.tryEmit(Unit)
        runCurrent()
        at(9 * MINUTE - 1)
        assertNull(watcher.expiredAt)
        at(9 * MINUTE)
        assertEquals(9 * MINUTE, watcher.expiredAt)
    }

    @Test
    fun `session changes do not reset the inactivity timer`() = runTest {
        val watcher = watch(AutoStopSettings(noClientMinutes = AutoStopSettings.NEVER, inactivityMinutes = 5), active = PC_A)
        at(2 * MINUTE)
        watcher.active.value = null
        runCurrent()
        at(3 * MINUTE)
        watcher.active.value = PC_A
        runCurrent()
        at(5 * MINUTE)
        assertEquals(5 * MINUTE, watcher.expiredAt)
    }

    @Test
    fun `never on both settings never stops`() = runTest {
        val never = AutoStopSettings(noClientMinutes = AutoStopSettings.NEVER, inactivityMinutes = AutoStopSettings.NEVER)
        val watcher = watch(never)
        at(24 * 60 * MINUTE)
        assertNull(watcher.expiredAt)
    }

    @Test
    fun `a shorter delay set while running applies from the start of the current state`() = runTest {
        val watcher = watch()
        at(2 * MINUTE)
        watcher.settings.value = AutoStopSettings(noClientMinutes = 5)
        runCurrent()
        at(5 * MINUTE - 1)
        assertNull(watcher.expiredAt)
        at(5 * MINUTE)
        assertEquals(5 * MINUTE, watcher.expiredAt)
    }

    @Test
    fun `a delay already elapsed under the new setting expires at once`() = runTest {
        val watcher = watch()
        at(7 * MINUTE)
        watcher.settings.value = AutoStopSettings(noClientMinutes = 5)
        runCurrent()
        assertEquals(7 * MINUTE, watcher.expiredAt)
    }

    @Test
    fun `switching a timer to never cancels it`() = runTest {
        val watcher = watch()
        at(10 * MINUTE)
        watcher.settings.value = AutoStopSettings(noClientMinutes = AutoStopSettings.NEVER)
        runCurrent()
        at(120 * MINUTE)
        assertNull(watcher.expiredAt)
    }

    @Test
    fun `the watcher expires only once`() = runTest {
        val watcher = watch(AutoStopSettings(noClientMinutes = 5, inactivityMinutes = 5))
        at(5 * MINUTE)
        watcher.ticks.tryEmit(Unit)
        watcher.active.value = PC_A
        watcher.active.value = null
        runCurrent()
        at(60 * MINUTE)
        assertEquals(1, watcher.expiries)
        assertEquals(5 * MINUTE, watcher.expiredAt)
    }

    @Test
    fun `a deadline passed during deep sleep expires at the next check`() = runTest {
        val watcher = watch()
        at(1 * MINUTE)
        // 20 minutes of deep sleep: the clock jumps, the pending delay did not advance
        watcher.sleptMs = 20 * MINUTE
        at(2 * MINUTE - 1)
        assertNull(watcher.expiredAt)
        at(2 * MINUTE)
        assertEquals(2 * MINUTE, watcher.expiredAt)
    }

    @Test
    fun `unknown stored values fall back to the default`() {
        assertEquals(15, AutoStopSettings.sanitize(7, AutoStopSettings.DEFAULT_NO_CLIENT_MINUTES))
        assertEquals(30, AutoStopSettings.sanitize(30, AutoStopSettings.DEFAULT_NO_CLIENT_MINUTES))
        assertEquals(AutoStopSettings.NEVER, AutoStopSettings.sanitize(-1, AutoStopSettings.DEFAULT_INACTIVITY_MINUTES))
    }

    // --- Core: the inactivity tick (contract §9, §11.2) ---

    private val acceptAll = DeviceAuthenticator { AuthResult.Accepted(deviceId = "test-device") }

    private object AppliedExecutor : BridgeCommandExecutor {
        override suspend fun execute(command: BridgeCommand): CommandResult = CommandResult.Applied(changed = false, revision = 0)
    }

    private suspend fun ApplicationTestBuilder.command(route: String, body: String) =
        client.post("/api/v1/$route") {
            bearerAuth("valid-token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    @Test
    fun `only a valid command handed to the executor emits the inactivity tick`() = testApplication {
        val ticks = AtomicInteger()
        val core = BridgeServerCore(
            serverName = "Pixel test",
            authenticator = acceptAll,
            commandExecutor = AppliedExecutor,
            onCommandAccepted = { ticks.incrementAndGet() },
        )
        application { core.install(this) }

        assertEquals(HttpStatusCode.OK, command("player/play", "{}").status)
        assertEquals(1, ticks.get())

        assertEquals(HttpStatusCode.BadRequest, command("player/play", "{not json").status)
        assertEquals(HttpStatusCode.BadRequest, command("player/speed", """{"speed":5}""").status)
        assertEquals(HttpStatusCode.NotFound, command("player/stop", "{}").status)
        assertEquals(1, ticks.get())
    }

    @Test
    fun `a core built by the controller feeds the controller's command ticks`() = testApplication {
        val store = JsonPairedDeviceStore(InMemoryPairedDeviceStorage())
        val device = store.issue("PC-A")
        val core = BridgeServerController.createCore("Pixel test", store, BridgeStateHub(), commandExecutor = AppliedExecutor)
        application { core.install(this) }

        coroutineScope {
            val tick = async(start = CoroutineStart.UNDISPATCHED) { BridgeServerController.commandTicks.first() }
            val response = client.post("/api/v1/player/play") {
                bearerAuth(device.deviceToken)
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(Unit, withTimeout(5_000) { tick.await() })
        }
    }
}
