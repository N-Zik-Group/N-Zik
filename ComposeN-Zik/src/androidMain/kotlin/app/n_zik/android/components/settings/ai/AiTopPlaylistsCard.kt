package app.n_zik.android.components.settings.ai

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsCustomValueKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showMyTopPlaylistKey
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

/** Preserves the original TopPlaylists settings and actions. */
@UnstableApi
@Composable
internal fun AiTopPlaylistsCard(search: Search) {
    var showMyTopPlaylist by rememberPreference(showMyTopPlaylistKey, true)
    var maxTopPlaylistItems by rememberPreference(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
    var maxTopPlaylistItemsCustomValue by rememberPreference(MaxTopPlaylistItemsCustomValueKey, 10)
    val searchCtx_6 = search.inputValue.isBlank() || stringResource(R.string.playlist_top).contains(search.inputValue, true) || stringResource(R.string.statistics_max_number_of_items).contains(search.inputValue, true) || stringResource(R.string.my_playlist_top1).contains(search.inputValue, true)
    SettingsSectionCard(
        title = stringResource(R.string.playlist_top),
        icon = R.drawable.playlist,
        visible = searchCtx_6,
        content = {
            var showTopPlaylistsDialog by remember { mutableStateOf(false) }
            var showCustomTopPlaylistsItemsDialog by remember { mutableStateOf(false) }
            if (search.inputValue.isBlank() || stringResource(R.string.statistics_max_number_of_items).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ai.playlist_top.statistics_max_number_of_items"),
                    title = stringResource(R.string.statistics_max_number_of_items),
                    text = maxTopPlaylistItems.displayName(maxTopPlaylistItemsCustomValue),
                    icon = R.drawable.musical_notes,
                    onClick = { showTopPlaylistsDialog = true }
                )
            }

            if (showTopPlaylistsDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.statistics_max_number_of_items),
                    selectedValue = maxTopPlaylistItems,
                    values = MaxTopPlaylistItems.values().toList(),
                    onValueSelected = {
                        maxTopPlaylistItems = it
                        if (it == MaxTopPlaylistItems.Custom) showCustomTopPlaylistsItemsDialog = true
                    },
                    valueText = { it.optionLabel() },
                    onDismiss = { showTopPlaylistsDialog = false }
                )
            }

            if (showCustomTopPlaylistsItemsDialog) {
                SettingsInputDialog(
                    title = stringResource(R.string.statistics_max_number_of_items),
                    initialValue = maxTopPlaylistItemsCustomValue.toString(),
                    placeholder = stringResource(R.string.statistics_max_number_of_items),
                    onDismiss = { showCustomTopPlaylistsItemsDialog = false },
                    onSetValue = {
                        if (TextUtils.isDigitsOnly(it) && it.length <= 9)
                            maxTopPlaylistItemsCustomValue = it.toIntOrNull()?.coerceAtLeast(1) ?: 10
                    }
                ).apply {
                    showDialog()
                    Render()
                }
            }

            if (search.inputValue.isBlank() || "${stringResource(R.string.show)} ${stringResource(R.string.my_playlist_top1)}".contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("ai.playlist_top.show"),
                    title = "${stringResource(R.string.show)} ${stringResource(R.string.my_playlist_top1)}",
                    text = "",
                    isChecked = showMyTopPlaylist,
                    onCheckedChange = {
                        showMyTopPlaylist = it
                    },
                    icon = R.drawable.trending
                )
            }
        }

    )
}
