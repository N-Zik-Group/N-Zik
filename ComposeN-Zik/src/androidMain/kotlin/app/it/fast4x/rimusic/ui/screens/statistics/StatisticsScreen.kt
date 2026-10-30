package app.it.fast4x.rimusic.ui.screens.statistics

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
import app.it.fast4x.compose.persist.PersistMapCleanup
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.StatisticsType
import app.n_zik.android.components.navigation.Skeleton

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

            val topContentInset = rememberTopContentInsetProvider(hasNavBar = !app.it.fast4x.rimusic.utils.hideBarsInLandscapeMobile())

            Skeleton(
                navController,
                tabIndex,
                onTabIndexChanged,
                miniPlayer,
                topInsetHandledByContent = true,
                topContentInsetProvider = topContentInset,
                navBarContent = { item ->
                    item(0, stringResource(R.string.today), R.drawable.stat_today)
                    item(1, stringResource(R.string._1_week), R.drawable.stat_week)
                    item(2, stringResource(R.string._1_month), R.drawable.stat_month)
                    item(3, stringResource(R.string._3_month), R.drawable.stat_3months)
                    item(4, stringResource(R.string._6_month), R.drawable.stat_6months)
                    item(5, stringResource(R.string._1_year), R.drawable.stat_year)
                    item(6, stringResource(R.string.all), R.drawable.calendar_clear)
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



