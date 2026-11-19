package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.utils.shimmerEffect

@Composable
internal fun StoreLoadingLine(
    text: String?,
    style: TextStyle,
    width: Float = 0.6f,
    maxLines: Int = 1,
) {
    if (text == null) {
        BasicText("", Modifier.fillMaxWidth(width).clip(uiRoundnessShape()).shimmerEffect(), style = style)
    } else if (text.isNotBlank()) {
        MeasuredMetadataPlaceholder(text, style, maxLines = maxLines)
    }
}

@Composable
internal fun ThemeStoreCardPlaceholder(
    modifier: Modifier = Modifier,
    title: String? = null,
    author: String? = null,
    catalogInfo: Boolean = true,
    download: Boolean = true,
    open: Boolean = true,
) {
    ThemeCardSurface(modifier) {
        Spacer(Modifier.fillMaxWidth().aspectRatio(StorePreviewAspectRatio).shimmerEffect())
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (open) 0.dp else 10.dp)) {
            StoreLoadingLine(title, typography().xs.copy(fontWeight = FontWeight.SemiBold))
            Box(Modifier.padding(top = 2.dp)) {
                StoreLoadingLine(author, typography().xxs, width = 0.45f)
            }
            if (catalogInfo) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.width(54.dp)) { StoreLoadingLine(null, typography().xxs, 1f) }
                Box(Modifier.width(32.dp)) { StoreLoadingLine(null, typography().xxs, 1f) }
            }
        }
        if (download) Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.weight(1f)) { StoreLoadingLine(null, typography().xxs, 0.5f) }
            StoreLoadingShape(Modifier.size(20.dp))
        }
        if (open) Box(
            Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp, bottom = 10.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            StoreLoadingShape(Modifier.size(48.dp, 32.dp))
        }
    }
}

@Composable
internal fun StoreLoadingShape(modifier: Modifier) {
    Spacer(modifier.clip(uiRoundnessShape()).shimmerEffect())
}

@Composable
internal fun Modifier.storeLoadingContent(show: Boolean): Modifier =
    if (show) clip(uiRoundnessShape()).shimmerEffect().drawWithContent {}.clearAndSetSemantics {}
    else this

@Composable
internal fun CreatorCardPlaceholder(name: String? = null, count: String? = null, hasActions: Boolean = true) {
    ThemeCardSurface {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(36.dp).clip(artistThumbnailShape()).shimmerEffect())
            Column(Modifier.weight(1f)) {
                StoreLoadingLine(name, typography().s)
                StoreLoadingLine(count, typography().xxs, 0.35f)
            }
            if (hasActions) {
                StoreLoadingShape(Modifier.size(64.dp, 36.dp))
                StoreLoadingShape(Modifier.size(36.dp))
            }
            StoreLoadingShape(Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun CreatorProfilePlaceholder(name: String? = null, author: String? = null, bio: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val bannerHeight = maxWidth / 2.2f
            StoreLoadingShape(Modifier.fillMaxWidth().height(bannerHeight))
            Row(
                Modifier.fillMaxWidth().padding(top = bannerHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(70.dp).offset(y = (-28).dp), contentAlignment = Alignment.Center) {
                    Spacer(Modifier.size(66.dp).clip(artistThumbnailShape()).shimmerEffect())
                }
                Column(Modifier.weight(1f)) {
                    StoreLoadingLine(name, typography().m)
                    StoreLoadingLine(author, typography().xs, 0.4f)
                }
                StoreLoadingShape(Modifier.size(64.dp, 36.dp))
                StoreLoadingShape(Modifier.size(36.dp))
            }
        }
        if (!bio.isNullOrBlank()) StoreLoadingLine(bio, typography().xs, maxLines = Int.MAX_VALUE)
    }
}
