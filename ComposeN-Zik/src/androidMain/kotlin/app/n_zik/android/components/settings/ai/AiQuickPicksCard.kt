package app.n_zik.android.components.settings.ai

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.LocalRecommandationsNumber
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.it.fast4x.rimusic.utils.playEventsTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showTipsKey
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

/** Preserves the original QuickPicks settings and actions. */
@UnstableApi
@Composable
internal fun AiQuickPicksCard(search: Search) {
    var playEventType by rememberPreference(
        playEventsTypeKey,
        PlayEventsType.MostPlayed
    )
    var showTips by rememberPreference(showTipsKey, true)
    var enableQuickPicksPage by rememberPreference(enableQuickPicksPageKey, true)
    var showTipsDialog by remember { mutableStateOf(false) }
    var showQuickSelectionDialog by remember { mutableStateOf(false) }
    var localRecommandationsNumber by rememberPreference(
        key = "LocalRecommandationsNumber",
        defaultValue = LocalRecommandationsNumber.SixQ
    )
    val searchCtx_1 = search.inputValue.isBlank() || stringResource(R.string.quick_picks).contains(search.inputValue, true) || stringResource(R.string.quick_picks_content).contains(search.inputValue, true) || stringResource(R.string.show).contains(search.inputValue, true) || stringResource(R.string.tips).contains(search.inputValue, true) || stringResource(R.string.charts).contains(search.inputValue, true) || stringResource(R.string.related_albums).contains(search.inputValue, true) || stringResource(R.string.similar_artists).contains(search.inputValue, true) || stringResource(R.string.new_albums_of_your_artists).contains(search.inputValue, true) || stringResource(R.string.new_albums).contains(search.inputValue, true) || stringResource(R.string.playlists_you_might_like).contains(search.inputValue, true) || stringResource(R.string.moods_and_genres).contains(search.inputValue, true) || stringResource(R.string.quick_selection_type).contains(search.inputValue, true) || stringResource(R.string.disable_if_you_do_not_want_to_see).contains(search.inputValue, true)
    SettingsSectionCard(
        title = stringResource(R.string.quick_picks),
        icon = R.drawable.star_brilliant,
        visible = enableQuickPicksPage && searchCtx_1,
        content = {
            if (search.inputValue.isBlank() || stringResource(R.string.quick_picks_content).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.quick_picks.quick_picks_content"),
                    title = stringResource(R.string.quick_picks_content),
                    text = stringResource(R.string.quick_picks_content_description),
                    onClick = { QuickPicksContentSettingsDialog.showDialog() },
                    icon = R.drawable.star_brilliant
                )
            }

            if (showTips && (search.inputValue.isBlank() || stringResource(R.string.tips).contains(search.inputValue, true))) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.quick_picks.tips"),
                    title = stringResource(R.string.tips),
                    text = stringResource(R.string.settings_desc_ai_quick_picks_tips),
                    selectedValueText = playEventType.text,
                    icon = R.drawable.sort_vertical,
                    onClick = { showTipsDialog = true }
                )
            }
            if (showTipsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.tips),
                    selectedValue = playEventType,
                    values = PlayEventsType.values().toList(),
                    onValueSelected = { playEventType = it },
                    valueText = { it.text },
                    onDismiss = { showTipsDialog = false }
                )
            }

            if (showTips && (search.inputValue.isBlank() || stringResource(R.string.quick_selection_type).contains(search.inputValue, true) || (stringResource(R.string.quick_selection, localRecommandationsNumber.value)).contains(search.inputValue, true))) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.quick_picks.quick_selection_type"),
                    title = stringResource(R.string.quick_selection_type),
                    text = stringResource(R.string.settings_desc_ai_quick_picks_quick_selection_type),
                    selectedValueText = stringResource(R.string.quick_selection, localRecommandationsNumber.value),
                    icon = R.drawable.sparkles,
                    onClick = { showQuickSelectionDialog = true }
                )
            }
            if (showQuickSelectionDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.quick_selection_type),
                    selectedValue = localRecommandationsNumber,
                    values = LocalRecommandationsNumber.values().toList(),
                    onValueSelected = { localRecommandationsNumber = it },
                    valueText = { option ->
                        stringResource(R.string.quick_selection, option.value)
                    },
                    onDismiss = { showQuickSelectionDialog = false }
                )
            }
        }
    )
}
