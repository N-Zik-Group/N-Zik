package app.it.fast4x.rimusic.ui.screens.localplaylist

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import app.it.fast4x.compose.persist.PersistMapCleanup
import app.n_zik.android.R
import app.n_zik.android.components.navigation.Skeleton

@OptIn(KotlinCsvExperimental::class)
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    playlistId: Long,
    miniPlayer: @Composable () -> Unit = {}
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    PersistMapCleanup(tagPrefix = "localPlaylist/$playlistId/")

    Skeleton(
        navController,
        miniPlayer = miniPlayer,
        topInsetHandledByContent = true,
        navBarContent = { item ->
            item(0, stringResource(R.string.songs), R.drawable.musical_notes)
        }
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
            when (currentTabIndex) {
                0 -> LocalPlaylistSongs(
                    navController = navController,
                    playlistId = playlistId,
                    onDelete = {}
                )
            }
        }
    }
}
