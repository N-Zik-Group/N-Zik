package app.n_zik.android.core.coil

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.n_zik.android.appContext
import app.n_zik.android.cleanPrefix
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.utils.getActiveProfile

/** Phone renderers need original pixels, not a session provider's crop snapshot. */
internal fun displayArtworkSource(url: String?, profile: String? = null): String? {
    if (url == null) return null
    val source = signedRemoteArtworkSource(url, profile)
        ?: return url
    val original = url.removeSuffix(cleanPrefix(url)) + source
    return original.takeIf(::isVideoArtwork) ?: url
}

internal fun signedRemoteArtworkSource(url: String, profile: String? = null): String? {
    val uri = remoteArtworkUri(url) ?: return null
    return ArtworkContentProvider.remoteSource(uri, profile ?: getActiveProfile(appContext()))?.first
}

private fun remoteArtworkUri(url: String?): Uri? {
    val cleaned = url?.let(::cleanPrefix) ?: return null
    if (!cleaned.startsWith("content://")) return null
    return Uri.parse(cleaned).takeIf {
        it.authority == ArtworkContentProvider.AUTHORITY && it.pathSegments == listOf("remote")
    }
}

/** Verifies the signed source once per URL/profile rather than on redraws. */
@Composable
internal fun rememberDisplayArtworkSource(url: String?, profile: String? = null): String? {
    if (remember(url) { remoteArtworkUri(url) } == null) return url
    val observedProfile = profile ?: ImageCacheFactory.rememberArtworkProfile()
    return remember(url, observedProfile) { displayArtworkSource(url, observedProfile) }
}
