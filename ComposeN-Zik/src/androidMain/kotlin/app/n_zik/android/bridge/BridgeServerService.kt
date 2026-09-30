package app.n_zik.android.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.n_zik.android.R
import app.n_zik.android.bridge.command.LateFailureTracker
import app.n_zik.android.bridge.command.PlayerCommandExecutor
import app.n_zik.android.bridge.command.PreferencePlayerSettings
import app.n_zik.android.bridge.library.DatabaseLibraryProvider
import app.n_zik.android.bridge.state.BridgePlayerSource
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import timber.log.Timber

private const val TAG = "BridgeServerService"
private const val CHANNEL_ID = "bridge_server_channel"
private const val NOTIFICATION_ID_RUNNING = 4201
private const val NOTIFICATION_ID_STOPPED = 4202
private const val WIFI_WAIT_MS = 1_500L
private const val HOTSPOT_POLL_MS = 5_000L

/**
 * Dedicated `dataSync` foreground service hosting the local PC bridge (spine AD-8),
 * independent from the `mediaPlayback` service. Started and stopped by the user; stops
 * cleanly on `onTimeout()` (Android 15+ dataSync budget) and when the Wi-Fi is lost.
 */
class BridgeServerService : Service() {

    companion object {
        const val ACTION_START = "app.n_zik.android.bridge.action.START"
        const val ACTION_STOP = "app.n_zik.android.bridge.action.STOP"
    }

    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
    private var wifiMonitor: WifiNetworkMonitor? = null
    private var server: BridgeServer? = null
    // Written on the DATA scope, released from the DATA scope or onDestroy (main thread)
    private val playerSource = AtomicReference<BridgePlayerSource?>(null)
    private var wifiWatch: Job? = null
    private var lifecycleJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // startForeground must happen right away after startForegroundService()
                startInForeground(getString(R.string.bridge_server_starting))
                if (lifecycleJob?.isActive != true && server == null) {
                    lifecycleJob = scope.launch { startServer() }
                }
            }
            ACTION_STOP -> scope.launch { stopServer(BridgeStopCode.STOP_USER) }
            else -> Timber.tag(TAG).w("Ignoring unknown action ${intent?.action}")
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.tag(TAG).w("dataSync time budget exhausted, stopping the bridge")
        scope.launch { stopServer(BridgeStopCode.TIMEOUT) }
    }

    override fun onDestroy() {
        wifiMonitor?.stop()
        releasePlayerSource()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startServer() {
        BridgeServerController.publish(BridgeState.Starting)
        val monitor = WifiNetworkMonitor(this).also { it.start() }
        wifiMonitor = monitor
        // A joined Wi-Fi network wins; otherwise the phone's own hotspot, never mobile data
        val wifiAddress = withTimeoutOrNull(WIFI_WAIT_MS) { monitor.address.filterNotNull().first() }
        val ipv4 = wifiAddress ?: HotspotAddressResolver.currentAddress()
        val viaHotspot = wifiAddress == null
        if (ipv4 == null) {
            Timber.tag(TAG).i("Neither on Wi-Fi nor sharing a hotspot, bridge not started")
            finish(BridgeState.NotOnWifi, getString(R.string.bridge_server_stopped_wifi))
            return
        }
        val host = ipv4.hostAddress ?: return finish(BridgeState.Failed, getString(R.string.bridge_server_failed))
        // One hub, one player source and one command executor per server run: the revision starts again at 0
        val stateHub = BridgeStateHub()
        val lateFailures = LateFailureTracker(stateHub)
        val source = BridgePlayerSource(this, stateHub, onPlayerError = lateFailures::onPlayerError).also { it.start() }
        playerSource.getAndSet(source)?.stop()
        val executor = PlayerCommandExecutor(
            player = source,
            hub = stateHub,
            lateFailures = lateFailures,
            settings = PreferencePlayerSettings(this),
            guestLocked = { listenTogetherGuestLock.value },
        )
        val core = BridgeServerController.createCore(
            serverName = Build.MODEL,
            deviceStore = BridgeServerController.loadDeviceStore(this),
            stateHub = stateHub,
            commandExecutor = executor,
            libraryProvider = DatabaseLibraryProvider(this),
        )
        val bridge = BridgeServer(core)
        val port = runCatching { bridge.start(host) }
            .onFailure { Timber.tag(TAG).e(it, "Bridge server failed to start") }
            .getOrNull()
        if (port == null) {
            releasePlayerSource()
            return finish(BridgeState.Failed, getString(R.string.bridge_server_failed))
        }
        server = bridge
        BridgeServerController.attachCore(core)
        BridgeServerController.publish(BridgeState.Running(host, port, viaHotspot))
        startInForeground(getString(R.string.bridge_server_running_address, host, port))
        // Network lost or address changed: stop without the serverStopped frame (transient for clients)
        wifiWatch = scope.launch {
            if (viaHotspot) {
                // No callback exists for the hotspot address: poll the interfaces instead
                while (HotspotAddressResolver.currentAddress() == ipv4) delay(HOTSPOT_POLL_MS)
            } else {
                monitor.address.drop(1).first { it != ipv4 }
            }
            Timber.tag(TAG).i("Network address changed or lost, stopping the bridge")
            stopServer(code = null)
        }
    }

    private suspend fun stopServer(code: BridgeStopCode?) {
        val bridge = server ?: run {
            // Stop requested while still starting (or already stopped): abort the start
            lifecycleJob?.cancel()
            releasePlayerSource()
            finish(BridgeState.Stopped, null)
            return
        }
        server = null
        wifiWatch?.cancel()
        BridgeServerController.closePairing()
        BridgeServerController.attachCore(null)
        BridgeServerController.publish(BridgeState.Stopping)
        bridge.stop(code)
        // After the sessions are closed: serverStopped stays the last message they received
        releasePlayerSource()
        val message = when (code) {
            BridgeStopCode.STOP_USER, BridgeStopCode.AUTO_STOP -> getString(R.string.bridge_server_stopped_user)
            BridgeStopCode.TIMEOUT -> getString(R.string.bridge_server_stopped_timeout)
            null -> getString(R.string.bridge_server_stopped_wifi)
        }
        finish(if (code == null) BridgeState.NotOnWifi else BridgeState.Stopped, message)
    }

    private fun releasePlayerSource() {
        playerSource.getAndSet(null)?.stop()
    }

    /** Publishes the final [state], posts the "stopped" notification when [message] is set, and ends the service. */
    private fun finish(state: BridgeState, message: String?) {
        wifiMonitor?.stop()
        wifiMonitor = null
        // No server, no pairing: the code only lives while the server runs (contract §4.1)
        BridgeServerController.closePairing()
        BridgeServerController.publish(state)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (message != null) {
            notificationManager()?.notify(NOTIFICATION_ID_STOPPED, buildNotification(getString(R.string.bridge_server_stopped), message, ongoing = false))
        }
        stopSelf()
    }

    private fun startInForeground(text: String) {
        notificationManager()?.cancel(NOTIFICATION_ID_STOPPED)
        val notification = buildNotification(getString(R.string.bridge_server), text, ongoing = true)
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID_RUNNING,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }.onFailure { Timber.tag(TAG).e(it, "Could not enter the foreground") }
    }

    private fun buildNotification(title: String, text: String, ongoing: Boolean): Notification {
        ensureChannel()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
        if (ongoing) {
            val stopIntent = PendingIntent.getService(
                this,
                0,
                Intent(this, BridgeServerService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(R.drawable.close, getString(R.string.bridge_server_stop), stopIntent)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.bridge_server), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notificationManager(): NotificationManager? = getSystemService(NotificationManager::class.java)
}
