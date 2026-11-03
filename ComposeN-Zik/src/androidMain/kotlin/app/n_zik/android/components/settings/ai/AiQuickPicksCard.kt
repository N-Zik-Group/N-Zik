package app.n_zik.android.components.settings.ai

import app.n_zik.android.components.dialog.common.DialogPresence
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
    val searchCtx_1 = search.inputValue.isBlank() || stringResource(R.string.recommendations_title).contains(search.inputValue, true) || stringResource(R.string.recommendations_content).contains(search.inputValue, true) || stringResource(R.string.recommendations_show_section_label).contains(search.inputValue, true) || stringResource(R.string.recommendations_for_you_title).contains(search.inputValue, true) || stringResource(R.string.recommendations_charts_title).contains(search.inputValue, true) || stringResource(R.string.album_related_title).contains(search.inputValue, true) || stringResource(R.string.artist_similar_title).contains(search.inputValue, true) || stringResource(R.string.recommendations_followed_artists_new_albums_title).contains(search.inputValue, true) || stringResource(R.string.recommendations_new_albums_title).contains(search.inputValue, true) || stringResource(R.string.recommendations_playlists_title).contains(search.inputValue, true) || stringResource(R.string.recommendations_moods_and_genres_title).contains(search.inputValue, true) || stringResource(R.string.settings_recommendations_song_count_description).contains(search.inputValue, true) || stringResource(R.string.recommendations_section_visibility_description).contains(search.inputValue, true)
    SettingsSectionCard(
        title = stringResource(R.string.recommendations_title),
        icon = R.drawable.star_brilliant,
        visible = enableQuickPicksPage && searchCtx_1,
        content = {
            if (search.inputValue.isBlank() || stringResource(R.string.recommendations_content).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.quick_picks.quick_picks_content"),
                    title = stringResource(R.string.recommendations_content),
                    text = stringResource(R.string.recommendations_content_description),
                    onClick = { QuickPicksContentSettingsDialog.showDialog() },
                    icon = R.drawable.star_brilliant
                )
            }

            if (showTips && (search.inputValue.isBlank() || stringResource(R.string.recommendations_for_you_title).contains(search.inputValue, true))) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.quick_picks.tips"),
                    title = stringResource(R.string.recommendations_for_you_title),
                    text = stringResource(R.string.recommendations_history_source_description),
                    selectedValueText = playEventType.text,
                    icon = R.drawable.sort_vertical,
                    onClick = { showTipsDialog = true }
                )
            }
            DialogPresence(visible = showTipsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.recommendations_for_you_title),
                    selectedValue = playEventType,
                    values = PlayEventsType.values().toList(),
                    onValueSelected = { playEventType = it },
                    valueText = { it.text },
                    onDismiss = { showTipsDialog = false }
                )
            }

            if (showTips && (search.inputValue.isBlank() || stringResource(R.string.settings_recommendations_song_count_description).contains(search.inputValue, true) || (stringResource(R.string.recommendations_quick_picks_count, localRecommandationsNumber.value)).contains(search.inputValue, true))) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.quick_picks.quick_selection_type"),
                    title = stringResource(R.string.settings_recommendations_song_count_description),
                    text = stringResource(R.string.recommendations_local_song_count_description),
                    selectedValueText = stringResource(R.string.recommendations_quick_picks_count, localRecommandationsNumber.value),
                    icon = R.drawable.sparkles,
                    onClick = { showQuickSelectionDialog = true }
                )
            }
            DialogPresence(visible = showQuickSelectionDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_recommendations_song_count_description),
                    selectedValue = localRecommandationsNumber,
                    values = LocalRecommandationsNumber.values().toList(),
                    onValueSelected = { localRecommandationsNumber = it },
                    valueText = { option ->
                        stringResource(R.string.recommendations_quick_picks_count, option.value)
                    },
                    onDismiss = { showQuickSelectionDialog = false }
                )
            }
        }
    )
}
