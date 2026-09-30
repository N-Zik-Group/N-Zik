package app.n_zik.android.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.n_zik.android.bridge.pairing.InMemoryPairedDeviceStorage
import app.n_zik.android.bridge.pairing.JsonPairedDeviceStore
import app.n_zik.android.bridge.pairing.OfferResult
import app.n_zik.android.bridge.pairing.PairedDevice
import app.n_zik.android.bridge.pairing.PairedDeviceStore
import app.n_zik.android.bridge.pairing.PairingCode
import app.n_zik.android.bridge.pairing.PairingCodeManager
import app.n_zik.android.bridge.pairing.PairingOffer
import app.n_zik.android.bridge.pairing.PairingOfferSender
import app.n_zik.android.bridge.pairing.PairingQrPayload
import app.n_zik.android.bridge.pairing.SharedPreferencesPairedDeviceStorage
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val TAG = "BridgeController"

/**
 * Process-wide entry point of the PC bridge: the UI observes [state] and starts/stops the
 * server through here, without binding to [BridgeServerService]. It also owns what must
 * outlive a single server run: the pairing code (memory only) and the paired devices.
 */
object BridgeServerController {
    private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val LOCAL_NETWORK_PERMISSION_SDK = 37

    private val _state = MutableStateFlow<BridgeState>(BridgeState.Stopped)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    /** Single pairing code of contract §4.1, shared by the pairing card and the server. */
    internal val pairingCodes = PairingCodeManager()

    /** Active code, `null` while pairing mode is closed. */
    val pairingCode: StateFlow<PairingCode?> = pairingCodes.code

    private val storeMutex = Mutex()
    private val _deviceStore = MutableStateFlow<PairedDeviceStore?>(null)

    /** Paired devices; empty until [loadDeviceStore] has run once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pairedDevices: Flow<List<PairedDevice>> =
        _deviceStore.flatMapLatest { store -> store?.devices ?: flowOf(emptyList()) }

    @Volatile
    private var activeCore: BridgeServerCore? = null

    private val offerSender = PairingOfferSender()

    internal fun publish(state: BridgeState) {
        _state.value = state
    }

    internal fun attachCore(core: BridgeServerCore?) {
        activeCore = core
    }

    private val _pairedEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Name of each newly paired device, for the success toast of the "PC server" page. */
    val pairedEvents: SharedFlow<String> = _pairedEvents.asSharedFlow()

    /**
     * Core of one server run, sharing the controller's pairing code with the pairing card.
     * A successful pairing closes pairing mode: the card folds back and no code stays active.
     */
    internal fun createCore(serverName: String, deviceStore: PairedDeviceStore): BridgeServerCore =
        BridgeServerCore(
            serverName = serverName,
            deviceStore = deviceStore,
            pairingCodes = pairingCodes,
            onDevicePaired = { deviceName ->
                pairingCodes.close()
                _pairedEvents.tryEmit(deviceName)
            },
        )

    /**
     * Paired devices of the active profile, kept in `encryptedPreferences` (only token
     * hashes are stored). Opened once per process, off the main thread.
     */
    internal suspend fun loadDeviceStore(context: Context): PairedDeviceStore =
        _deviceStore.value ?: storeMutex.withLock {
            _deviceStore.value ?: withContext(NzikDispatchers.DATA) {
                val appContext = context.applicationContext
                val storage = runCatching { SharedPreferencesPairedDeviceStorage(appContext.encryptedPreferences) }
                    .onFailure { Timber.tag(TAG).e(it, "Encrypted preferences unavailable, pairings kept in memory") }
                    .getOrElse { InMemoryPairedDeviceStorage() }
                JsonPairedDeviceStore(storage)
            }.also { _deviceStore.value = it }
        }

    /**
     * Android 17 (API 37) local network protection: without this runtime permission the
     * system drops the bridge's replies to LAN/hotspot clients. `null` when nothing is missing.
     */
    fun missingPermission(context: Context): String? =
        LOCAL_NETWORK_PERMISSION.takeIf {
            Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK &&
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun start(context: Context) {
        val intent = Intent(context, BridgeServerService::class.java).setAction(BridgeServerService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, BridgeServerService::class.java).setAction(BridgeServerService.ACTION_STOP)
        context.startService(intent)
    }

    /** Opens pairing mode (a code becomes active); only while the server runs. */
    fun openPairing() {
        if (_state.value is BridgeState.Running) pairingCodes.open()
    }

    /** Closes pairing mode: no code is active any more (contract §4.1). */
    fun closePairing() = pairingCodes.close()

    fun regeneratePairingCode() {
        if (pairingCodes.code.value != null) pairingCodes.regenerate()
    }

    /** Regenerates the code once expired, while pairing mode stays open. */
    fun refreshPairingCode() = pairingCodes.refreshIfExpired()

    /**
     * Revokes a paired device (contract §4.7). Through the running server when there is one,
     * so its WebSocket session is closed with `4003` as well.
     */
    suspend fun revoke(context: Context, deviceId: String) {
        val core = activeCore
        if (core != null) {
            core.revokeDevice(deviceId)
        } else {
            val store = loadDeviceStore(context)
            withContext(NzikDispatchers.DATA) { store.revoke(deviceId) }
        }
    }

    /**
     * Contract §4.2 steps 5–6, after the user confirmed the scanned QR: binds the request id
     * to the active code, then pushes the offer to the PC's candidate addresses.
     */
    suspend fun sendOffer(payload: PairingQrPayload): OfferResult {
        val running = _state.value as? BridgeState.Running ?: return OfferResult.Unreachable
        val core = activeCore ?: return OfferResult.Unreachable
        val code = pairingCodes.bindRequestId(payload.requestId) ?: return OfferResult.Expired
        return withContext(NzikDispatchers.DATA) {
            val prefix = PairingQrPayload.prefixLengthOf(running.ip)
            val candidates = PairingQrPayload.orderCandidates(payload.ips, running.ip, prefix)
            val offer = PairingOffer(
                requestId = payload.requestId,
                code = code,
                serverIps = listOf(running.ip),
                serverPort = running.port,
                serverName = core.serverName,
            )
            offerSender.send(candidates, payload.port, offer)
        }
    }
}
