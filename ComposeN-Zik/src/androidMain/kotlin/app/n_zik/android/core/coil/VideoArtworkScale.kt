package app.n_zik.android.core.coil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.ContentScale
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.rememberPreference

private val videoArtworkUrl = Regex("^https?://(?:[a-z0-9-]+\\.)?ytimg\\.com/(?:vi|vi_webp)/[^/?#]+/", RegexOption.IGNORE_CASE)

/** Identifies remote video frames without changing custom covers or playlist/artist artwork. */
internal fun isVideoArtwork(url: String?): Boolean {
    if (url == null) return false
    val cleaned = cleanPrefix(url)
    val prefixes = url.removeSuffix(cleaned).split(':')
    return prefixes.none { "$it:" == MODIFIED_PREFIX } && videoArtworkUrl.containsMatchIn(cleaned)
}

internal fun videoArtworkContentScale(url: String?, crop: Boolean, requested: ContentScale): ContentScale {
    if (!isVideoArtwork(url)) return requested
    // Explicit Fit/Inside/None also serve decorative backdrops and must retain their layout.
    if (requested != ContentScale.Crop && requested != ContentScale.FillHeight && requested != ContentScale.FillWidth) return requested
    return if (crop) ContentScale.Crop else ContentScale.Fit
}

@Composable
internal fun rememberVideoArtworkContentScale(url: String?, requested: ContentScale): ContentScale {
    val crop by rememberPreference(cropVideoThumbnailsKey, true)
    return videoArtworkContentScale(url, crop, requested)
}
