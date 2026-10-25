package app.it.fast4x.rimusic.ui.screens.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.it.fast4x.rimusic.utils.encryptedPreferencesUpdateTrigger
import kotlinx.coroutines.withContext


@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun SettingsScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
    initialTab: Int = 0,
    focus: String = "",
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val (tabIndex, onTabChanged) = rememberSaveable {
        mutableIntStateOf(initialTab)
    }

    val trigger = encryptedPreferencesUpdateTrigger
    var ytLoggedIn by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        ytLoggedIn = withContext(NzikDispatchers.DATA) {
            isYouTubeLoginEnabled() && isYouTubeLoggedIn()
        }
    }

    Skeleton(
        navController,
        tabIndex,
        onTabChanged,
        miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.tab_general), R.drawable.ic_launcher_monochrome)
            item(1, stringResource(R.string.ui_tab), R.drawable.ui)
            item(2, stringResource(R.string.player_appearance), R.drawable.color_palette)
            item(3, stringResource(R.string.ai_recommendations), R.drawable.sparkles)
            item(4, stringResource(R.string.tab_data), R.drawable.server)
            item(5, stringResource(R.string.tab_accounts), R.drawable.person)
            item(6, stringResource(R.string.tab_network), R.drawable.network)
            item(7, stringResource(R.string.tab_miscellaneous), R.drawable.equalizer)
            item(8, stringResource(R.string.about), R.drawable.information)

        }
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(currentTabIndex) {
            when (currentTabIndex) {
                0 -> GeneralSettings(navController = navController, focus = focus)
                1 -> UiSettings(navController = navController, focus = focus)
                2 -> AppearanceSettings(navController = navController)
                3 -> AIRecommendationSettings(navController = navController, ytLoggedIn = ytLoggedIn, focus = focus)
                4 -> DataSettings(navController = navController, focus = focus)
                5 -> AccountsSettings(navController = navController, focus = focus)
                6 -> NetworkSettings(navController = navController, focus = focus)
                7 -> OtherSettings(focus = focus)
                8 -> About(navController = navController)

            }
        }
    }

    RestartAppDialog.Render()
}
