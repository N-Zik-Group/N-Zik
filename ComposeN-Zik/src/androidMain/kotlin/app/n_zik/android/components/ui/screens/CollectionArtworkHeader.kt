package app.n_zik.android.components.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.n_zik.android.R
import app.n_zik.android.cleanPrefix
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArtworkBackdrop
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.components.glass.NativeIconActionSize
import app.n_zik.android.components.ui.HeaderIconAction
import app.n_zik.android.core.coil.imageLoadingViewport
import app.n_zik.android.core.coil.rememberImageLoadingViewportPainter
import app.n_zik.android.typography
import app.n_zik.android.ui.components.ArtworkShimmerPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
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
    isLoading: Boolean = false,
    fullWidthTitleShimmer: Boolean = false,
    action: @Composable () -> Unit,
) {
    val type = typography().l.semiBold
    val showShimmer = forceLoadingShimmerPreview() || isLoading
    GlassArtworkBackdrop(hasArtwork = !isLandscape && !showShimmer) { artworkBackdrop ->
        Box(Modifier.fillMaxWidth()) {
            if (!isLandscape) {
                val artworkModifier = Modifier.then(artworkBackdrop).aspectRatio(4f / 3).fillMaxWidth()
                    .align(Alignment.Center).fadingEdge(
                        top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + Dimensions.fadeSpacingTop,
                        bottom = Dimensions.fadeSpacingBottom,
                    )
                if (showShimmer) {
                    ArtworkShimmerPlaceholder(artworkModifier, aspectRatio = 4f / 3)
                } else {
                    val viewportPainter = rememberImageLoadingViewportPainter(artwork)
                    Image(
                        painter = viewportPainter, contentDescription = null, contentScale = ContentScale.FillWidth,
                        modifier = artworkModifier.imageLoadingViewport(viewportPainter),
                    )
                }
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
                    shimmer = showShimmer,
                    modifier = Modifier.padding(horizontal = 30.dp).then(
                        when {
                            showShimmer && fullWidthTitleShimmer -> Modifier.fillMaxWidth()
                            disableScrollingText || showShimmer -> Modifier
                            else -> Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        }
                    ),
                )
                subtitle()
            }
            if (frameAction) {
                CollectionArtworkTopAction(action)
            } else {
                Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp)) { action() }
            }
        }
    }
}


/** Common position and action seat for the control over collection artwork. */
@Composable
internal fun BoxScope.CollectionArtworkTopAction(action: @Composable () -> Unit) {
    Box(
        modifier = Modifier.align(Alignment.TopEnd)
            .padding(top = 5.dp, end = 12.dp)
            .size(NativeIconActionSize),
        contentAlignment = Alignment.Center,
    ) {
        GlassToolbarRow(1) { action() }
    }
}

@Composable
internal fun CollectionArtworkShareButton(shareUrl: String?) {
    val context = LocalContext.current
    HeaderIconAction(
        icon = R.drawable.share_social,
        iconSize = 24.dp,
        color = if (shareUrl == null) colorPalette().textDisabled else colorPalette().text,
        contentDescription = stringResource(R.string.player_mini_button_share),
        enabled = shareUrl != null,
        onClick = {
            shareUrl?.let { url ->
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                context.startActivity(Intent.createChooser(intent, null))
            }
        },
    )
}
