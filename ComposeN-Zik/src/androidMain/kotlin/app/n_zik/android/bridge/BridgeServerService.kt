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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.n_zik.android.R
import app.n_zik.android.bridge.audio.PhoneAudioLibrary
import app.n_zik.android.bridge.command.LateFailureTracker
import app.n_zik.android.bridge.command.PlayerCommandExecutor
import app.n_zik.android.bridge.command.PreferencePlayerSettings
import app.n_zik.android.bridge.library.BridgeLibraryObserver
import app.n_zik.android.bridge.library.DatabaseLibraryProvider
import app.n_zik.android.bridge.state.BridgePlayerSource
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    // Output control of the running server: its fallback runs at stop (contract §6.2)
    private var outputs: AudioOutputController? = null
    // Written on the DATA scope, released from the DATA scope or onDestroy (main thread)
    private val playerSource = AtomicReference<BridgePlayerSource?>(null)
    private var wifiWatch: Job? = null
    private var autoStopWatch: Job? = null
    private var lifecycleJob: Job? = null
    // Contract §10.3 (since 1.7.3): the library writes and sort-menu edits pushed to the
    // clients; written on the DATA scope, released from the DATA scope or onDestroy (main)
    private var libraryObserver: BridgeLibraryObserver? = null
    // Stops come from the DATA pool (user, Wi-Fi, onTimeout, auto-stop): one at a time
    private val stopMutex = Mutex()

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
        // Never leave the phone muted without a server
        BridgeServerController.attachOutputs(null)
        BridgeServerController.publishAudioOutput(AudioOutput.PHONE)
        wifiMonitor?.stop()
        libraryObserver?.stop()
        libraryObserver = null
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
        BridgeServerController.loadAutoStopSettings(this)
        // One hub, one player source and one command executor per server run: the revision starts again at 0
        val stateHub = BridgeStateHub()
        // Contract §10.3 (since 1.7.3): every library write and songs-sort-menu edit reaches the
        // clients — an `onDestroy` that already ran (the scope cancelled) must not leave the
        // observer running: it holds the process-level write notifier and the prefs listener
        libraryObserver?.stop()
        libraryObserver = if (scope.isActive) BridgeLibraryObserver(this, stateHub).also { it.start() } else null
        val lateFailures = LateFailureTracker(stateHub)
        val source = BridgePlayerSource(this, stateHub, onPlayerError = lateFailures::onPlayerError).also { it.start() }
        playerSource.getAndSet(source)?.stop()
        // The output control needs the core (active session) and the core its fallback: tied lazily
        lateinit var core: BridgeServerCore
        val audioOutputs = AudioOutputController(
            hub = stateHub,
            player = source,
            hasActiveSession = { core.activeDevice.value != null },
            onOutputChanged = BridgeServerController::publishAudioOutput,
        )
        val executor = PlayerCommandExecutor(
            player = source,
            hub = stateHub,
            lateFailures = lateFailures,
            settings = PreferencePlayerSettings(this),
            guestLocked = { listenTogetherGuestLock.value },
            outputs = audioOutputs,
        )
        core = BridgeServerController.createCore(
            serverName = Build.MODEL,
            deviceStore = BridgeServerController.loadDeviceStore(this),
            stateHub = stateHub,
            commandExecutor = executor,
            libraryProvider = DatabaseLibraryProvider(this, mediaCacheSpace = source::mediaCacheSpace),
            audioLibrary = PhoneAudioLibrary(this),
            // Contract §5 (since 1.9.0, `ui.language`): the phone's effective UI language, read on
            // every `meta` request (the mirror of the language the phone's UI shows)
            uiLanguage = { BridgeUiLanguage.effective(this.applicationContext) },
            onSessionEnded = audioOutputs::fallback,
            // Contract §8.5 (since 1.3): as soon as a session is active, the audio sounds on the PC
            onSessionClaimed = { scope.launch { audioOutputs.select(AudioOutput.PC) } },
        )
        val bridge = BridgeServer(core)
        val port = runCatching { bridge.start(host) }
            .onFailure { Timber.tag(TAG).e(it, "Bridge server failed to start") }
            .getOrNull()
        if (port == null) {
            libraryObserver?.stop()
            libraryObserver = null
            releasePlayerSource()
            return finish(BridgeState.Failed, getString(R.string.bridge_server_failed))
        }
        server = bridge
        outputs = audioOutputs
        BridgeServerController.attachOutputs(audioOutputs)
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
            // Separate coroutine: stopServer cancels this watcher, the stop sequence must not be cancelled with it
            scope.launch { stopServer(code = null, expected = bridge) }
        }
        // Contract §11.2: the settings are read live, a change applies to this run at once
        autoStopWatch = scope.launch {
            BridgeAutoStop.awaitExpiry(
                settings = BridgeServerController.autoStopSettings,
                activeDevice = core.activeDevice,
                commandTicks = BridgeServerController.commandTicks,
                now = SystemClock::elapsedRealtime,
            )
            Timber.tag(TAG).i("Auto-stop delay elapsed, stopping the bridge")
            // Separate coroutine: stopServer cancels this watcher, the stop sequence must not be cancelled with it
            scope.launch { stopServer(BridgeStopCode.AUTO_STOP, expected = bridge) }
        }
    }

    /** [expected]: stop only that run; a late stop of an already stopped run does nothing. */
    private suspend fun stopServer(code: BridgeStopCode?, expected: BridgeServer? = null) = stopMutex.withLock {
        if (expected != null && server !== expected) return@withLock
        val bridge = server ?: run {
            // Stop requested while still starting (or already stopped): abort the start
            lifecycleJob?.cancel()
            releasePlayerSource()
            finish(BridgeState.Stopped, null)
            return@withLock
        }
        server = null
        wifiWatch?.cancel()
        autoStopWatch?.cancel()
        BridgeServerController.closePairing()
        BridgeServerController.attachCore(null)
        // No output change from the UI once stopping: the fallback below is the last one
        BridgeServerController.attachOutputs(null)
        BridgeServerController.publish(BridgeState.Stopping)
        bridge.stop(code)
        libraryObserver?.stop()
        libraryObserver = null
        // Contract §6.2: the server stop ends the session, pause then phone (no-op when already done)
        outputs?.let { withContext(NonCancellable) { it.fallback() } }
        outputs = null
        // After the sessions are closed: serverStopped stays the last message they received
        releasePlayerSource()
        val message = when (code) {
            BridgeStopCode.STOP_USER -> getString(R.string.bridge_server_stopped_user)
            BridgeStopCode.AUTO_STOP -> getString(R.string.bridge_server_stopped_auto)
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
        BridgeServerController.publishAudioOutput(AudioOutput.PHONE)
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
