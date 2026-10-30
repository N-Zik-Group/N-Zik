package app.it.fast4x.rimusic.ui.screens.mood

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
import app.it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.discoverPage
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.toUiMood
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.n_zik.android.components.ui.screens.home.MoodGridItemColored
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.secondary
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
        discoverPage = withContext(NzikDispatchers.DATA) { Innertube.discoverPage() }
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
        discoverPage?.getOrNull()?.let { moodResult ->
            LazyVerticalGrid(
                contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
                state = moodAngGenresLazyGridState,
                columns = GridCells.Adaptive(Dimensions.thumbnails.album + 24.dp),
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0,
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    HeaderWithIcon(
                        title = stringResource(R.string.moods_and_genres),
                        iconId = R.drawable.search,
                        enabled = true,
                        showIcon = false,
                        modifier = Modifier,
                        onClick = {}
                    )
                }

                discoverPage?.getOrNull()?.let { page ->
                    if (page.moods.isNotEmpty()) {

                            items(
                                items = page.moods.sortedBy { it.title },
                                key = { it.endpoint.params ?: it.title }
                            ) {
                                MoodGridItemColored(
                                    mood = it,
                                    onClick = { it.endpoint.browseId?.let { _ ->
                                        navController.currentBackStackEntry?.savedStateHandle?.set("mood", it.toUiMood())
                                        navController.navigate(NavRoutes.mood.name)
                                    } },
                                    thumbnailSizeDp = thumbnailSizeDp,
                                    modifier = Modifier
                                        .animateItem()

                                )
                            }
                        }

                    }

                }


        } ?: CollectionPageStatus {
            if (discoverPage?.exceptionOrNull() != null) {
                BasicText(
                    text = stringResource(R.string.page_not_been_loaded),
                    style = typography().s.secondary.center,
                    modifier = Modifier.padding(all = 16.dp)
                )
            } else {
                Loader(modifier = Modifier.fillMaxSize())
            }
        }
    }
}



