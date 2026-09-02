package app.n_zik.android.core.network.client

import timber.log.Timber

object GlobalNetworkLogger {
    var lastBandwidth: Int = -1
    var lastIsMetered: Boolean = false

    fun logNetworkState(source: String, bandwidth: Int, isMetered: Boolean, detectedQuality: String, finalDecision: String) {
        // Disabled
        /*
        val b = if (bandwidth != -1) bandwidth else lastBandwidth
        val m = if (bandwidth != -1) isMetered else lastIsMetered
        
        Timber.tag("NZik_Network").i(
            "[%s] BW: %dKbps | Met: %s | Qual: %s -> Dec: %s",
            source, b, m, detectedQuality, finalDecision
        )
        */
    }
}
