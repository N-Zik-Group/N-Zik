package app.n_zik.android.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide entry point of the PC bridge: the UI observes [state] and starts/stops the
 * server through here, without binding to [BridgeServerService].
 */
object BridgeServerController {
    private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val LOCAL_NETWORK_PERMISSION_SDK = 37

    private val _state = MutableStateFlow<BridgeState>(BridgeState.Stopped)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    internal fun publish(state: BridgeState) {
        _state.value = state
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
}
