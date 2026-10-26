package app.n_zik.android.components.settings.ai

import app.n_zik.android.components.settings.common.settingsSearchTarget
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.MaxStatisticsItems
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.utils.maxStatisticsItemsCustomValueKey
import app.it.fast4x.rimusic.utils.maxStatisticsItemsKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showStatsListeningTimeKey
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

/** Preserves the original Statistics settings and actions. */
@UnstableApi
@Composable
internal fun AiStatisticsCard(search: Search, navController: NavController, modifier: Modifier = Modifier) {
    var showStatsListeningTime by rememberPreference(showStatsListeningTimeKey, true)
    var maxStatisticsItems by rememberPreference(maxStatisticsItemsKey, MaxStatisticsItems.`10`)
    var maxStatisticsItemsCustomValue by rememberPreference(maxStatisticsItemsCustomValueKey, 10)
    val searchCtx_5 = search.inputValue.isBlank() || stringResource(R.string.statistics).contains(search.inputValue, true) || stringResource(R.string.statistics_max_number_of_items).contains(search.inputValue, true) || stringResource(R.string.listening_time).contains(search.inputValue, true)
    SettingsSectionCard(
        title = stringResource(R.string.statistics),
        icon = R.drawable.trending,
        modifier = modifier,
        visible = searchCtx_5,
        content = {


            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("ai.statistics.module_open"),
                title = stringResource(R.string.module_open),
                text = stringResource(R.string.statistics_open_desc),
                icon = R.drawable.chevron_forward,
                onClick = { navController.navigate(NavRoutes.statistics.name) }
            )
            var showStatisticsDialog by remember { mutableStateOf(false) }
            var showCustomStatisticsItemsDialog by remember { mutableStateOf(false) }
            if (search.inputValue.isBlank() || stringResource(R.string.statistics_max_number_of_items).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.statistics.statistics_max_number_of_items"),
                    title = stringResource(R.string.statistics_max_number_of_items),
                    text = maxStatisticsItems.displayName(maxStatisticsItemsCustomValue),
                    icon = R.drawable.musical_notes,
                    onClick = { showStatisticsDialog = true }
                )
            }

            if (showStatisticsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.statistics_max_number_of_items),
                    selectedValue = maxStatisticsItems,
                    values = MaxStatisticsItems.values().toList(),
                    onValueSelected = {
                        maxStatisticsItems = it
                        if (it == MaxStatisticsItems.Custom) showCustomStatisticsItemsDialog = true
                    },
                    valueText = { it.optionLabel() },
                    onDismiss = { showStatisticsDialog = false }
                )
            }

            if (showCustomStatisticsItemsDialog) {
                SettingsInputDialog(
                    title = stringResource(R.string.statistics_max_number_of_items),
                    initialValue = maxStatisticsItemsCustomValue.toString(),
                    placeholder = stringResource(R.string.statistics_max_number_of_items),
                    onDismiss = { showCustomStatisticsItemsDialog = false },
                    onSetValue = {
                        if (TextUtils.isDigitsOnly(it) && it.length <= 9)
                            maxStatisticsItemsCustomValue = it.toIntOrNull()?.coerceAtLeast(1) ?: 10
                    }
                ).apply {
                    showDialog()
                    Render()
                }
            }

            if (search.inputValue.isBlank() || stringResource(R.string.listening_time).contains(search.inputValue, true) || (stringResource(R.string.shows_the_number_of_songs_heard_and_their_listening_time)).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("ai.statistics.listening_time"),
                    title = stringResource(R.string.listening_time),
                    text = stringResource(R.string.shows_the_number_of_songs_heard_and_their_listening_time),
                    isChecked = showStatsListeningTime,
                    onCheckedChange = {
                        showStatsListeningTime = it
                    },
                    icon = R.drawable.time
                )
            }
        }
    )
}
