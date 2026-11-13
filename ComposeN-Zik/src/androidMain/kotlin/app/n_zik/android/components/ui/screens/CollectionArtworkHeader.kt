package app.n_zik.android.components.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.glass.GlassArtworkBackdrop
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.AutoResizeText
import app.n_zik.android.ui.components.themed.FontSizeRange
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.fadingEdge
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.semiBold

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollectionArtworkHeader(
    title: String,
    artwork: Painter,
    disableScrollingText: Boolean,
    subtitle: @Composable () -> Unit = {},
    frameAction: Boolean = true,
    action: @Composable () -> Unit,
) {
    val type = typography().l.semiBold
    GlassArtworkBackdrop(hasArtwork = !isLandscape) { artworkBackdrop ->
        Box(Modifier.fillMaxWidth()) {
            if (!isLandscape) {
                Image(
                    painter = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.then(artworkBackdrop).aspectRatio(4f / 3).fillMaxWidth()
                        .align(Alignment.Center).fadingEdge(
                            top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + Dimensions.fadeSpacingTop,
                            bottom = Dimensions.fadeSpacingBottom,
                        ),
                )
            }
            Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                AutoResizeText(
                    text = cleanPrefix(title),
                    style = type,
                    fontSizeRange = FontSizeRange(32.sp, 38.sp),
                    fontWeight = type.fontWeight,
                    fontFamily = type.fontFamily,
                    color = type.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp).then(
                        if (disableScrollingText) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    ),
                )
                subtitle()
            }
            val actionModifier = Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp)
            if (frameAction) {
                GlassToolbarRow(1, modifier = actionModifier) { action() }
            } else {
                Box(actionModifier) { action() }
            }
        }
    }
}
