package app.n_zik.android.ui.screens.statistics

import app.n_zik.android.components.navigation.rememberTopContentInsetProvider

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.compose.persist.PersistMapCleanup
import app.n_zik.android.enums.StatisticsType
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.dock.statisticsDockTabs

@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun StatisticsScreen(
    navController: NavController,
    statisticsType: StatisticsType,
    miniPlayer: @Composable () -> Unit = {},
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val (tabIndex, onTabIndexChanged) = rememberSaveable {
        mutableStateOf(when (statisticsType) {
            StatisticsType.Today -> 0
            StatisticsType.OneWeek -> 1
            StatisticsType.OneMonth -> 2
            StatisticsType.ThreeMonths -> 3
            StatisticsType.SixMonths -> 4
            StatisticsType.OneYear -> 5
            StatisticsType.All -> 6
        })
    }

    PersistMapCleanup(tagPrefix = "${statisticsType.name}/")

            val topContentInset = rememberTopContentInsetProvider(hasNavBar = !app.n_zik.android.utils.hideBarsInLandscapeMobile())

            Skeleton(
                navController,
                tabIndex,
                onTabIndexChanged,
                miniPlayer,
                topInsetHandledByContent = true,
                topContentInsetProvider = topContentInset,
                navBarContent = { item ->
                    statisticsDockTabs.forEachIndexed { index, tab ->
                        item(index, stringResource(tab.labelRes), tab.iconRes)
                    }
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    val type: StatisticsType =
                        when( currentTabIndex ) {
                            0 -> StatisticsType.Today
                            1 -> StatisticsType.OneWeek
                            2 -> StatisticsType.OneMonth
                            3 -> StatisticsType.ThreeMonths
                            4 -> StatisticsType.SixMonths
                            5 -> StatisticsType.OneYear
                            else -> StatisticsType.All
                        }

                    StatisticsPage(navController, type, topContentInsetProvider = topContentInset)
                }
            }
}



