package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.utils.medium
import app.n_zik.android.utils.shimmerEffect

@Composable
internal fun BrowseIdSearchResultRow(
    artworkShape: Shape,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    thumbnailUrl: String? = null,
    title: String = "",
    subtitle: String = "",
) {
    val artworkModifier = Modifier.size(40.dp).clip(artworkShape)
    val titleStyle = typography().xs.medium
    val subtitleStyle = typography().xxs
    Row(
        modifier = Modifier.fillMaxWidth().then(modifier).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            Box(artworkModifier.shimmerEffect())
        } else {
            ImageCacheFactory.Thumbnail(
                shape = artworkShape,
                thumbnailUrl = thumbnailUrl,
                modifier = artworkModifier,
            )
        }
        Column {
            if (loading) {
                ItemTextPlaceholder(text = null, style = titleStyle)
                ItemTextPlaceholder(text = null, style = subtitleStyle, width = 0.42f)
            } else {
                Text(
                    text = title,
                    style = titleStyle,
                    color = colorPalette().text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = subtitleStyle,
                    color = colorPalette().textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
