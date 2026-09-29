package app.n_zik.android.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import timber.log.Timber

private const val TAG = "BridgeWifiMonitor"

/**
 * Tracks the IPv4 address of the Wi-Fi network (never cellular, never a VPN: the default
 * [NetworkRequest] carries `NOT_VPN`). [address] is `null` while no Wi-Fi network is up,
 * which is how the bridge detects both "not on Wi-Fi" and "Wi-Fi lost" (contract §11.1).
 */
internal class WifiNetworkMonitor(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _address = MutableStateFlow<Inet4Address?>(null)
    val address: StateFlow<Inet4Address?> = _address.asStateFlow()

    private var trackedNetwork: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            val ipv4 = linkProperties.linkAddresses
                .map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
            trackedNetwork = network
            _address.value = ipv4
        }

        override fun onLost(network: Network) {
            if (network != trackedNetwork) return
            Timber.tag(TAG).i("Wi-Fi network lost")
            trackedNetwork = null
            _address.value = null
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { connectivity?.registerNetworkCallback(request, callback) }
            .onFailure { Timber.tag(TAG).e(it, "Failed to register the Wi-Fi callback") }
    }

    fun stop() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
            .onFailure { Timber.tag(TAG).w(it, "Wi-Fi callback was not registered") }
        trackedNetwork = null
        _address.value = null
    }
}
