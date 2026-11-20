package app.n_zik.android.ui.screens.mood

import app.n_zik.android.components.ui.screens.CollectionPageStatus
import androidx.compose.foundation.layout.PaddingValues
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.compose.persist.persist
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.discoverPage
import app.n_zik.android.colorPalette
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.toUiMood
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.components.ui.screens.home.MoodGridItemColored
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.center
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun MoodsPage(
    navController: NavController
) {

    var discoverPage by persist<Result<Innertube.DiscoverPage>>("moods/discovery/pageResult")
    LaunchedEffect(Unit) {
        val result = withContext(NzikDispatchers.DATA) { Innertube.discoverPage() }
        discoverPage = retainLastDiscoveryResult(discoverPage, result)
    }
    val thumbnailSizeDp = Dimensions.thumbnails.album + 24.dp

    val moodAngGenresLazyGridState = rememberLazyGridState()



    Column (
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        val showShimmer = forceLoadingShimmerPreview() || discoverPage == null
        if (!showShimmer && discoverPage?.isFailure == true) {
            CollectionPageStatus {
                BasicText(
                    text = stringResource(R.string.recommendations_load_failed_message),
                    style = typography().s.secondary.center,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
                state = moodAngGenresLazyGridState,
                columns = GridCells.Adaptive(thumbnailSizeDp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    HeaderWithIcon(
                        title = stringResource(R.string.recommendations_moods_and_genres_title),
                        iconId = R.drawable.search,
                        enabled = true,
                        showIcon = false,
                        modifier = Modifier,
                        onClick = {},
                    )
                }
                val moods = discoverPage?.getOrNull()?.moods.orEmpty().sortedBy { it.title }
                if (showShimmer && moods.isEmpty()) {
                    items(8, key = { "mood_loading_$it" }) {
                        MoodGridItemColored(null, onClick = {}, thumbnailSizeDp = thumbnailSizeDp, shimmer = true)
                    }
                } else {
                    items(moods, key = { it.endpoint.params ?: it.title }) { mood ->
                        MoodGridItemColored(
                            mood = mood,
                            onClick = {
                                if (mood.endpoint.browseId != null) {
                                    navController.currentBackStackEntry?.savedStateHandle?.set("mood", mood.toUiMood())
                                    navController.navigate(NavRoutes.mood.name)
                                }
                            },
                            thumbnailSizeDp = thumbnailSizeDp,
                            modifier = Modifier.animateItem(),
                            shimmer = showShimmer,
                        )
                    }
                }
            }
        }
    }
}

internal fun <T> retainLastDiscoveryResult(current: Result<T>?, incoming: Result<T>): Result<T> =
    if (incoming.isFailure && current?.isSuccess == true) current else incoming



