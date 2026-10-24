package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.uiRoundnessShape

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.colorPalette
import app.n_zik.android.core.network.OfflineMode
import app.it.fast4x.rimusic.enums.SearchType
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.ui.styling.px

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun HomeSearch(
    onSearchType: (SearchType) -> Unit,
    disableScrollingText: Boolean
) {
    val thumbnailSizeDp = 108.dp
    val thumbnailSizePx = thumbnailSizeDp.px
    val lazyGridState = rememberLazyGridState()
    // Full offline mode (spec-full-offline-mode): the network tiles disappear;
    // the local library tile stays (HomeSearch is unreferenced — edit kept for the spec).
    val offlineMode by OfflineMode.rememberEnabled()
    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth()
    ) {

        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Adaptive(Dimensions.thumbnails.song * 2 + Dimensions.itemsVerticalPadding * 2),
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.itemsVerticalPadding * 2),
            horizontalArrangement = Arrangement.spacedBy(
                space = Dimensions.itemsVerticalPadding * 2,
                alignment = Alignment.CenterHorizontally
            ),
            modifier = Modifier
                .fillMaxSize()
                .background(colorPalette().background0)
        ) {
            item(key = "header", contentType = 0, span = { GridItemSpan(maxLineSpan) }) {

                HeaderWithIcon(
                    title = stringResource(R.string.search),
                    iconId = R.drawable.search,
                    enabled = false,
                    showIcon = false,
                    modifier = Modifier,
                    onClick = {}
                )

            }

            if (!offlineMode)
                item(key = "online") {
                    PlaylistItem(
                        icon = R.drawable.globe,
                        colorTint = colorPalette().favoritesIcon,
                        name = "${stringResource(R.string.search)} ${stringResource(R.string.online)}",
                        songCount = null,
                        thumbnailSizeDp = thumbnailSizeDp,
                        alternative = true,
                        modifier = Modifier
                            .clip(thumbnailShape())
                            .clip(uiRoundnessShape()).clickable(onClick = { onSearchType(SearchType.Online) })
                            .animateItem(),
                        disableScrollingText = disableScrollingText
                    )
                }

            item(key = "library") {
                PlaylistItem(
                    icon = R.drawable.library,
                    colorTint = colorPalette().favoritesIcon,
                    name = "${stringResource(R.string.search)} ${stringResource(R.string.library)}",
                    songCount = null,
                    thumbnailSizeDp = thumbnailSizeDp,
                    alternative = true,
                    modifier = Modifier
                        .clip(thumbnailShape())
                        .clip(uiRoundnessShape()).clickable(onClick = { onSearchType(SearchType.Library) })
                        .animateItem(),
                    disableScrollingText = disableScrollingText
                )
            }

            if (!offlineMode)
                item(key = "gotolink") {
                    Modifier
                        .clip(thumbnailShape())
                        .clip(uiRoundnessShape()).clickable(onClick = { onSearchType(SearchType.Gotolink) })
                    PlaylistItem(
                        icon = R.drawable.query_stats,
                        colorTint = colorPalette().favoritesIcon,
                        name = stringResource(R.string.go_to_link),
                        songCount = null,
                        thumbnailSizeDp = thumbnailSizeDp,
                        alternative = true,
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        disableScrollingText = disableScrollingText
                    )
                }

        }

    }
}





