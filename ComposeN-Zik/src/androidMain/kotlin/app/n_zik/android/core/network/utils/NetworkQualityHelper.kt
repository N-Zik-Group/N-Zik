package app.n_zik.android.core.network.utils

import app.n_zik.android.core.network.client.GlobalNetworkLogger
import app.n_zik.android.core.network.models.NetworkQuality

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import app.it.fast4x.rimusic.utils.isConnectionMeteredEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

object NetworkQualityHelper {

    fun isMetered(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            
            var metered = false
            
            // 1. Check if Roaming
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) {
                metered = true
            }

            // 2. Check System Data Saver (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
                    metered = true
                }
            }

            // 3. Check user override preference in the app (Manual button)
            val forceMetered = context.preferences.getBoolean(isConnectionMeteredEnabledKey, false)
            if (forceMetered) {
                metered = true
            }
            metered
        } catch (e: Exception) {
            false
        }
    }

    fun isNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getCurrentNetworkType(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val nw = cm.activeNetwork ?: return "-"
            val actNw = cm.getNetworkCapabilities(nw) ?: return "-"
            when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                else -> "?"
            }
        } catch (e: Exception) {
            "?"
        }
    }

    fun getCurrentNetworkQuality(context: Context): NetworkQuality {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkQuality.LOW
            
            // Use our centralized metered check
            val metered = isMetered(context)
            val bandwidth = capabilities.linkDownstreamBandwidthKbps
            
            // Update Global Logger State
            GlobalNetworkLogger.lastBandwidth = bandwidth
            GlobalNetworkLogger.lastIsMetered = metered

            var quality = when {
                bandwidth > 20000 -> NetworkQuality.HIGH
                bandwidth > 5000 -> NetworkQuality.MEDIUM
                else -> NetworkQuality.LOW
            }

            // If ANY of the metered conditions are met, cap quality to MEDIUM to save data
            if (metered && quality == NetworkQuality.HIGH) {
                quality = NetworkQuality.MEDIUM
            }

            // Centralized Log
            GlobalNetworkLogger.logNetworkState("NetworkHelper", bandwidth, metered, "RAW_DETECT", quality.name)

            quality
        } catch (e: Exception) {
            Timber.tag("NetworkQualityHelper").e(e, "Failed to detect quality")
            NetworkQuality.LOW
        }
    }

    /**
     * Observe connection status as a Flow (Migration from AndroidConnectivityObserver)
     */
    fun observeConnection(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val callback = object : NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val connected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                } else {
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
                trySend(connected)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                trySend(false)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                trySend(false)
            }

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                trySend(true)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            // Deprecated way could be added here if needed for API < 24
            trySend(isNetworkConnected(context))
        }

        trySend(isNetworkConnected(context))

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    @Composable
    fun isNetworkAvailableComposable(context: Context): State<Boolean> {
        return produceState(initialValue = isNetworkConnected(context)) {
            observeConnection(context).collect { value = it }
        }
    }
}
