package app.n_zik.android.core.coil

import android.content.Context
import android.os.SystemClock
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory.NetworkQuality
import app.n_zik.android.core.network.models.NetworkQuality as NZikNetworkQuality
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.enums.ImageQualityFormat
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.imageQualityFormatKey
import app.n_zik.android.utils.preferences

internal object ArtworkNetworkQuality {
    private const val NETWORK_SAMPLE_MAX_AGE_MS = 1_000L

    private data class NetworkSample(val quality: NetworkQuality, val measuredAt: Long)

    @Volatile private var lastNetworkSample: NetworkSample? = null

    fun getNetworkQuality(): NetworkQuality {
        val context = appContext()
        val forcedQuality = context.preferences.getEnum(imageQualityFormatKey, ImageQualityFormat.Auto)

        // Return forced quality if set
        if (forcedQuality != ImageQualityFormat.Auto) {
            return when (forcedQuality) {
                ImageQualityFormat.High -> NetworkQuality.HIGH
                ImageQualityFormat.Medium -> NetworkQuality.MEDIUM
                else -> NetworkQuality.LOW
            }
        }

        // Several visible artwork requests in the same frame can use the same bandwidth sample.
        val now = SystemClock.elapsedRealtime()
        lastNetworkSample?.takeIf { now - it.measuredAt < NETWORK_SAMPLE_MAX_AGE_MS }?.let {
            return it.quality
        }
        val detectedQuality = getActualNetworkQuality(context)
        lastNetworkSample = NetworkSample(detectedQuality, now)
        return detectedQuality
    }

    fun getActualNetworkQuality(context: Context): NetworkQuality =
        when (NetworkQualityHelper.getCurrentNetworkQuality(context)) {
            NZikNetworkQuality.HIGH -> NetworkQuality.HIGH
            NZikNetworkQuality.MEDIUM -> NetworkQuality.MEDIUM
            else -> NetworkQuality.LOW
        }
}
