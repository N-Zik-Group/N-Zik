package app.n_zik.android.components.podcast

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.thumbnailSizeDpKey
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.settings.DEFAULT_THUMBNAIL_SIZE_DP
import app.n_zik.android.thumbnailShape
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.ui.components.ArtworkShimmerPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview

/** The large landscape banner keeps its native clips without an image contour. */
@Composable
fun PodcastBannerThumbnail(isLoading: Boolean, url: String?) {
    val horizontalPadding by rememberPreference(thumbnailSizeDpKey, DEFAULT_THUMBNAIL_SIZE_DP)
    val modifier = Modifier.padding(horizontal = horizontalPadding.dp).padding(top = 16.dp)
        .clip(thumbnailShape()).clip(uiRoundnessShape())
    Box(contentAlignment = Alignment.Center) {
        if (isLoading || forceLoadingShimmerPreview()) ArtworkShimmerPlaceholder(modifier)
        else ImageCacheFactory.AsyncImage(thumbnailUrl = url, modifier = modifier, shape = null)
    }
}
