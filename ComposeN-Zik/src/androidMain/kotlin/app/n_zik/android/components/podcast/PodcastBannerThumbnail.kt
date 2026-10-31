package app.n_zik.android.components.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.shimmer
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.n_zik.android.colorPalette
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.thumbnailShape
import app.n_zik.android.uiRoundnessShape
import com.valentinilk.shimmer.shimmer

/** The large landscape banner keeps its native clips without an image contour. */
@Composable
fun PodcastBannerThumbnail(isLoading: Boolean, url: String?) {
    val horizontalPadding by rememberPreference(thumbnailSizeDpKey, 85f)
    val modifier = Modifier.padding(horizontal = horizontalPadding.dp).padding(top = 16.dp)
        .clip(thumbnailShape()).clip(uiRoundnessShape())
    Box(contentAlignment = Alignment.Center) {
        if (isLoading) Spacer(modifier.shimmer().background(colorPalette().shimmer))
        else ImageCacheFactory.AsyncImage(thumbnailUrl = url, modifier = modifier, shape = null)
    }
}
