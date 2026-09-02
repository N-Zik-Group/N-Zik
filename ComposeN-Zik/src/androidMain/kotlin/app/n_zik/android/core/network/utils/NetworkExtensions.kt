package app.n_zik.android.core.network.utils

import app.n_zik.android.core.network.models.NetworkQuality

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Checks if the network is currently connected (Internet and validated).
 */
inline val Context.isNetworkConnected: Boolean
    get() = NetworkQualityHelper.isNetworkConnected(this)

/**
 * Checks if the network is available (Internet only, not necessarily validated).
 */
inline val Context.isNetworkAvailable: Boolean
    get() = NetworkQualityHelper.isNetworkAvailable(this)

/**
 * Checks if the current network connection is metered (Data Saver, Roaming, or User Override).
 */
inline val Context.isMetered: Boolean
    get() = NetworkQualityHelper.isMetered(this)

/**
 * Returns the current network transport type (WIFI, CELLULAR, etc.).
 */
inline val Context.networkType: String
    get() = NetworkQualityHelper.getCurrentNetworkType(this)

/**
 * Returns the current network quality (LOW, MEDIUM, HIGH).
 */
inline val Context.networkQuality: NetworkQuality
    get() = NetworkQualityHelper.networkQuality(this)

/**
 * Extension for observing network availability in a Composable function.
 */
val Context.isNetworkAvailableComposable: State<Boolean>
    @Composable
    get() = NetworkQualityHelper.isNetworkAvailableComposable(this)

/**
 * Internal helper to expose quality detection via extension.
 */
fun NetworkQualityHelper.networkQuality(context: Context): NetworkQuality =
    this.getCurrentNetworkQuality(context)
