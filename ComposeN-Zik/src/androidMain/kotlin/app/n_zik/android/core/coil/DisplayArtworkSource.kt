package app.n_zik.android.core.coil

import android.net.Uri
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.ArtworkContentProvider

/** Phone renderers need original pixels, not a session provider's crop snapshot. */
internal fun displayArtworkSource(url: String?, profile: String? = null): String? {
    if (url == null) return null
    val uri = remoteArtworkUri(url) ?: return url
    val source = ArtworkContentProvider.remoteSource(uri, profile ?: getActiveProfile(appContext()))?.first
        ?: return url
    val original = url.removeSuffix(cleanPrefix(url)) + source
    return original.takeIf(::isVideoArtwork) ?: url
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
internal fun rememberDisplayArtworkSource(url: String?): String? {
    if (remember(url) { remoteArtworkUri(url) } == null) return url
    val context = LocalContext.current
    val preferences = context.profilePreferences
    var profile by remember(preferences) { mutableStateOf(getActiveProfile(context)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == activeProfileKey) profile = getActiveProfile(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        profile = getActiveProfile(context)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return remember(url, profile) { displayArtworkSource(url, profile) }
}
