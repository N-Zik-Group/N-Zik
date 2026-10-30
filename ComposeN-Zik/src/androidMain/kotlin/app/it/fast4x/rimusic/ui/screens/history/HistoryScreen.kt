package app.it.fast4x.rimusic.ui.screens.history

import app.n_zik.android.components.navigation.rememberTopContentInsetProvider

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.it.fast4x.compose.persist.PersistMapCleanup
import app.n_zik.android.components.navigation.Skeleton

@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun HistoryScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val topContentInset = rememberTopContentInsetProvider(hasNavBar = false)

    PersistMapCleanup(tagPrefix = "history")

            Skeleton(
                navController,
                miniPlayer = miniPlayer,
                topInsetHandledByContent = true,
                topContentInsetProvider = topContentInset,
                navBarContent = { item ->
                    item(0, stringResource(R.string.history), R.drawable.history)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> HistoryList(
                            topContentInsetProvider = topContentInset,
                            navController = navController,
                        )
                    }
                }
            }

}



