package app.n_zik.android.components.settings.data

import app.n_zik.android.components.settings.common.CardCategoryLabel
import app.n_zik.android.components.settings.common.RestartPlayerService
import app.n_zik.android.components.settings.common.SettingsCardSectionDivider
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.ExoPlayerMinTimeForEvent
import app.n_zik.android.utils.exoPlayerMinTimeForEventKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.pauseListenHistoryKey
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.search.SearchHistoryPageSettings
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.search.rememberSearchHistoryCount
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.waveformsDir
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.isLegacyStorageShared
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Owns history preferences and the existing history actions. */
@UnstableApi
@Composable
internal fun DataHistoryCard(
    search: Search,
    navController: NavController,
    restartState: MutableState<Boolean>,
    openModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var restartService by restartState
    var pauseSearchHistory by rememberPreference(pauseSearchHistoryKey, false)
    var pauseListenHistory by rememberPreference(pauseListenHistoryKey, false)
    var minimumListeningTime by rememberPreference(exoPlayerMinTimeForEventKey, ExoPlayerMinTimeForEvent.`20s`)
    var showMinimumListeningTimeDialog by remember { mutableStateOf(false) }
    var confirmListenClear by remember { mutableStateOf(false) }
    var confirmSearchClear by remember { mutableStateOf(false) }
    SettingsSectionCard(
        title = stringResource(R.string.library_history_title),
        icon = R.drawable.history,
        modifier = Modifier.testTag("data.card.history"),
        content = {
            CardCategoryLabel(stringResource(R.string.settings_history_group_listening))
            // The burger History item only reaches this partial card: the "Ouvrir"
            // row (spec-customizable-burger-menu goal B) opens the full History screen.
            OtherSettingsEntry(
                modifier = openModifier.settingsSearchTarget("data.history.module_open"),
                title = stringResource(R.string.modules_open),
                text = stringResource(R.string.settings_history_open_description),
                icon = R.drawable.chevron_forward,
                onClick = { navController.navigate(NavRoutes.history.name) }
            )

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("data.history.player_pause_listen_history"),
                title = stringResource(R.string.player_pause_listen_history),
                text = stringResource(R.string.player_pause_listen_history_info),
                isChecked = pauseListenHistory,
                onCheckedChange = {
                    pauseListenHistory = it
                    restartService = true
                },
                icon = R.drawable.pause
            )
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("general.playback.min_listening_time"),
                title = stringResource(R.string.recommendations_minimum_playback_duration_label),
                text = stringResource(R.string.player_minimum_listening_time_description),
                selectedValueText = minimumListeningTime.text,
                onClick = { showMinimumListeningTimeDialog = true },
                icon = R.drawable.time,
            )
            DialogPresence(visible = showMinimumListeningTimeDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.recommendations_minimum_playback_duration_label),
                    selectedValue = minimumListeningTime,
                    onValueSelected = { minimumListeningTime = it },
                    valueText = { it.text },
                    values = ExoPlayerMinTimeForEvent.entries,
                    onDismiss = { showMinimumListeningTimeDialog = false },
                )
            }

            val eventsCount by remember {
                Database.eventTable.countAll()
            }.collectAsStateWithLifecycle(0L, context = NzikDispatchers.DATA)

            if (search.inputValue.isBlank() || stringResource(R.string.settings_history_action_clear_listening).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("data.history.clear_listen_history"),
                    title = stringResource(R.string.settings_history_action_clear_listening),
                    text = stringResource(R.string.history_clear_listen_history_description),
                    selectedValueText = if (eventsCount > 0) {
                        pluralStringResource(R.plurals.history_delete_playback_event_count, eventsCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), eventsCount)
                    } else {
                        stringResource(R.string.search_history_empty_message)
                    },
                    icon = R.drawable.trash,
                    isEnabled = eventsCount > 0,
                    onClick = { confirmListenClear = true },
                )
            }

            SettingsCardSectionDivider()
            CardCategoryLabel(stringResource(R.string.search_saved_history_title))

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("data.history.pause_search_history"),
                title = stringResource(R.string.search_history_pause_label),
                text = stringResource(R.string.search_history_pause_description),
                isChecked = pauseSearchHistory,
                onCheckedChange = {
                    pauseSearchHistory = it
                },
                icon = R.drawable.pause
            )
            SearchHistoryPageSettings(Modifier.settingsSearchTarget("data.history.search_history_per_page"))

            val queriesCount by rememberSearchHistoryCount()

            if (search.inputValue.isBlank() || stringResource(R.string.search_history_action_clear).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("data.history.clear_search_history"),
                    title = stringResource(R.string.search_history_action_clear),
                    text = stringResource(R.string.history_clear_search_history_description),
                    selectedValueText = if (queriesCount > 0) {
                        pluralStringResource(R.plurals.history_delete_search_query_count, queriesCount, queriesCount)
                    } else {
                        stringResource(R.string.search_history_empty_message)
                    },
                    icon = R.drawable.trash,
                    isEnabled = queriesCount > 0,
                    onClick = { confirmSearchClear = true },
                )
            }

            DialogPresence(visible = confirmListenClear) {
                ConfirmationDialog(
                    title = stringResource(R.string.settings_history_action_clear_listening),
                    text = stringResource(R.string.history_clear_listen_history_description),
                    confirmText = stringResource(R.string.common_action_clear),
                    onDismiss = { confirmListenClear = false },
                    onConfirm = {
                        val profile = getActiveProfile(context)
                        Database.asyncTransaction {
                            eventTable.deleteAll()
                        }
                        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                            withContext(NzikDispatchers.DATA) {
                                DatabaseRestoreJournal.withProfileStorageMutation(context) {
                                    if (profile == getActiveProfile(context) && profileStorageIdentity(context, profile) != null &&
                                        !isLegacyStorageShared(context, profile, ProfileDataItem.WAVEFORMS)
                                    ) {
                                        val root = context.filesDir.canonicalFile
                                        ProfileResetSnapshot.deleteVerifiedTree(root.resolve(waveformsDir(context, profile).name))
                                    }
                                }
                            }
                            WaveformExtractor.refreshSignal.tryEmit(System.currentTimeMillis())
                            Toaster.done()
                        }
                    },
                )
            }
            DialogPresence(visible = confirmSearchClear) {
                ConfirmationDialog(
                    title = stringResource(R.string.search_history_action_clear),
                    text = stringResource(R.string.history_clear_search_history_description),
                    confirmText = stringResource(R.string.common_action_clear),
                    onDismiss = { confirmSearchClear = false },
                    onConfirm = {
                        SearchHistoryStore.clearAll(context.preferences)
                        Toaster.done()
                    },
                )
            }

            RestartPlayerService(restartService, onRestart = { restartService = false })
        }
    )
}
