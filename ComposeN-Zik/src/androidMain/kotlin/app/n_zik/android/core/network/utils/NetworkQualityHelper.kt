package app.n_zik.android.core.network.utils

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.client.GlobalNetworkLogger
import app.n_zik.android.core.network.models.NetworkQuality
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.isConnectionMeteredEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.profilePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

object NetworkQualityHelper {

    private class ApplicationObservation(val context: Context) {
        private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
        private val lock = Mutex()
        private val state = MutableStateFlow<Boolean?>(null)
        private var subscribers = 0
        private var upstream: Job? = null

        val connection = flow {
            lock.withLock {
                if (subscribers++ == 0) {
                    upstream = scope.launch {
                        observeEffectiveConnection(context)
                            .catch {
                                Timber.tag("NetworkQualityHelper").w(it, "Connection observation failed")
                                emit(false)
                            }
                            .collect { state.value = it }
                    }
                }
            }
            try {
                emitAll(state.filterNotNull())
            } finally {
                withContext(NonCancellable) {
                    lock.withLock {
                        if (--subscribers == 0) {
                            // A new collector must wait for cleanup and a fresh initial value.
                            upstream?.cancelAndJoin()
                            upstream = null
                            state.value = null
                        }
                    }
                }
            }
        }
    }

    private val applicationObservationLock = Any()
    private var applicationObservation: ApplicationObservation? = null

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
        // Full offline mode overrides real connectivity: every existing gate sees "offline"
        if (OfflineMode.enabled(context)) return false
        return physicalNetworkConnected(context)
    }

    private fun physicalNetworkConnected(context: Context): Boolean {
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
        // Full offline mode overrides real connectivity (same predicate as isNetworkConnected)
        if (OfflineMode.enabled(context)) return false
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

    /** Shares the effective connection while the app has observers. */
    fun observeConnection(context: Context): Flow<Boolean> = flow {
        val application = sharedApplicationContext(context)
        emitAll(
            if (application != null) applicationConnection(application)
            else observeEffectiveConnection(context)
        )
    }

    private fun applicationConnection(context: Context): Flow<Boolean> =
        synchronized(applicationObservationLock) {
            val current = applicationObservation
            if (current?.context === context) return@synchronized current.connection
            ApplicationObservation(context).also {
                applicationObservation = it
            }.connection
        }

    private fun sharedApplicationContext(context: Context): Context? {
        val application = context.applicationContext ?: return null
        if (context === application) return application
        if (context.profilePreferences !== application.profilePreferences) return null
        val profile = getActiveProfile(application)
        if (profileDataPrefs(context, profile) !== profileDataPrefs(application, profile)) return null

        // Activity and service ContextImpls can each own a ConnectivityManager. Only compare
        // a wrapper with its base so an overridden service stays local to that wrapper.
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        var base = context
        while (base is ContextWrapper && base !== application) {
            base = base.baseContext ?: return null
            if (base.getSystemService(Context.CONNECTIVITY_SERVICE) !== manager) return null
        }
        val applicationBase = (application as? ContextWrapper)?.baseContext
        return application.takeIf { base === application || base.javaClass == applicationBase?.javaClass }
    }

    private fun observeEffectiveConnection(context: Context): Flow<Boolean> = combine(
        callbackFlow {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                trySend(physicalNetworkConnected(context))
                close()
                return@callbackFlow
            }

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
                    // onAvailable can precede validation, such as on a captive portal.
                    trySend(physicalNetworkConnected(context))
                }
            }

            connectivityManager.registerDefaultNetworkCallback(callback)
            trySend(physicalNetworkConnected(context))

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged(),
        OfflineMode.enabledFlow(context)
    ) { connected, modeOn ->
        // Full offline mode is an explicit "offline" regardless of real connectivity
        connected && !modeOn
    }.distinctUntilChanged()

    @Composable
    fun isNetworkAvailableComposable(context: Context): State<Boolean> {
        val connection = remember(context) { observeConnection(context) }
        return connection.collectAsStateWithLifecycle(
            initialValue = isNetworkConnected(context),
            context = NzikDispatchers.DATA
        )
    }
}
