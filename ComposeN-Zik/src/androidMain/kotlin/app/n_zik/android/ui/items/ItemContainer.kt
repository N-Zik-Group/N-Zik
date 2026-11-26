package app.n_zik.android.ui.items

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.shimmerEffect

@Composable
internal fun ItemArtworkPlaceholder(thumbnailSize: Dp, card: Boolean, shape: Shape) {
    val geometry = if (card) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(thumbnailSize)
    Spacer(geometry.clip(shape).shimmerEffect())
}

@Composable
fun ItemContainer(
    alternative: Boolean,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable () -> Unit,
) {
    val childIndication = LocalIndication.current
    val itemIndication = mediaItemRipple()
    CompositionLocalProvider(LocalIndication provides itemIndication) {
        if (alternative) {
            Column(
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier
                    .padding(vertical = Dimensions.itemsVerticalPadding, horizontal = 16.dp)
                    .width(thumbnailSizeDp),
            ) {
                CompositionLocalProvider(LocalIndication provides childIndication, content = content)
            }
        } else {
            Row(
                verticalAlignment = verticalAlignment,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier
                    .padding(vertical = Dimensions.itemsVerticalPadding, horizontal = 16.dp)
                    .fillMaxWidth(),
            ) {
                CompositionLocalProvider(LocalIndication provides childIndication, content = content)
            }
        }
    }
}

@Composable
inline fun ItemInfoContainer(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
        content = content
    )
}



