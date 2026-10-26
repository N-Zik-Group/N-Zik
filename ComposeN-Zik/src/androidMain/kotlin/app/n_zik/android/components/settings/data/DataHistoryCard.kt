package app.n_zik.android.components.settings.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.pauseListenHistoryKey
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.rememberSearchHistoryCount
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.waveformsDir
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Owns history preferences and the existing history actions. */
@UnstableApi
@Composable
internal fun DataHistoryCard(search: Search, navController: NavController, restartState: MutableState<Boolean>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var restartService by restartState
    var pauseSearchHistory by rememberPreference(pauseSearchHistoryKey, false)
    var pauseListenHistory by rememberPreference(pauseListenHistoryKey, false)
    SettingsSectionCard(
        title = stringResource(R.string.history),
        icon = R.drawable.history,
        modifier = modifier.testTag("data.card.history"),
        content = {
            // The burger History item only reaches this partial card: the "Ouvrir"
            // row (spec-customizable-burger-menu goal B) opens the full History screen.
            OtherSettingsEntry(
                title = stringResource(R.string.module_open),
                text = stringResource(R.string.history_open_desc),
                icon = R.drawable.chevron_forward,
                onClick = { navController.navigate(NavRoutes.history.name) }
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.pause_search_history),
                text = stringResource(R.string.neither_save_new_searched_query),
                isChecked = pauseSearchHistory,
                onCheckedChange = {
                    pauseSearchHistory = it
                },
                icon = R.drawable.pause
            )

            val queriesCount by rememberSearchHistoryCount()

            OtherSwitchSettingEntry(
                title = stringResource(R.string.player_pause_listen_history),
                text = stringResource(R.string.player_pause_listen_history_info),
                isChecked = pauseListenHistory,
                onCheckedChange = {
                    pauseListenHistory = it
                    restartService = true
                },
                icon = R.drawable.pause
            )

            val eventsCount by remember {
                Database.eventTable.countAll()
            }.collectAsStateWithLifecycle(0L, context = NzikDispatchers.DATA)

            if (search.inputValue.isBlank() || stringResource(R.string.clear_search_history).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    title = stringResource(R.string.clear_search_history),
                    text = if (queriesCount > 0) {
                        "${stringResource(R.string.delete)} " + queriesCount + stringResource(R.string.search_queries)
                    } else {
                        stringResource(R.string.history_is_empty)
                    },
                    icon = R.drawable.trash,
                    onClick = {
                        SearchHistoryStore.clearAll(context.preferences)
                        Toaster.done()
                    }
                )
            }

            if (search.inputValue.isBlank() || stringResource(R.string.clear_listen_history).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    title = stringResource(R.string.clear_listen_history),
                    text = if (eventsCount > 0) {
                        stringResource(R.string.delete_playback_events, eventsCount.toString())
                    } else {
                        stringResource(R.string.history_is_empty)
                    },
                    icon = R.drawable.trash,
                    onClick = {
                        Database.asyncTransaction {
                            eventTable.deleteAll()
                        }
                        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                            withContext(NzikDispatchers.DATA) {
                                waveformsDir(context, getActiveProfile(context)).deleteRecursively()
                            }
                            WaveformExtractor.refreshSignal.tryEmit(System.currentTimeMillis())
                            Toaster.done()
                        }
                    }
                )
            }

            RestartPlayerService(restartService, onRestart = { restartService = false })
        }
    )
}
