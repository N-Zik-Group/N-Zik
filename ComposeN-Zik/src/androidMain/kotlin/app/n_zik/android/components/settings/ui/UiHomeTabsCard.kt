package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.dialog.settings.HomeAlbumsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomePlaylistsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsSettingsDialog
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.R

/** Owns the home tab customization actions. */
@Composable
internal fun UiHomeTabsCard(search: Search) {
    val homeTabsSearchContextMatch = search.inputValue.isBlank() ||
        stringResource(R.string.home_tabs_settings).contains(search.inputValue, true) ||
        stringResource(R.string.quick_picks).contains(search.inputValue, true) ||
        stringResource(R.string.songs).contains(search.inputValue, true) ||
        stringResource(R.string.artists).contains(search.inputValue, true) ||
        stringResource(R.string.albums).contains(search.inputValue, true) ||
        stringResource(R.string.playlists).contains(search.inputValue, true)

    AnimatedVisibility(
        visible = homeTabsSearchContextMatch,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.home-tabs"),
            title = stringResource(R.string.home_tabs_settings),
            icon = R.drawable.library,
            content = {
                if (search.inputValue.isBlank() || stringResource(R.string.quick_picks).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.home_tabs_settings.quick_picks"),
                        title = stringResource(R.string.quick_picks),
                        text = stringResource(R.string.library_visibility_description),
                        onClick = { QuickPicksContentSettingsDialog.showDialog() },
                        icon = R.drawable.sparkles
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.songs).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.home_tabs_settings.songs"),
                        title = stringResource(R.string.songs),
                        text = stringResource(R.string.library_visibility_description),
                        onClick = { HomeSongsSettingsDialog.showDialog() },
                        icon = R.drawable.musical_notes
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.artists).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.home_tabs_settings.artists"),
                        title = stringResource(R.string.artists),
                        text = stringResource(R.string.library_visibility_description),
                        onClick = { HomeArtistsSettingsDialog.showDialog() },
                        icon = R.drawable.people
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.albums).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.home_tabs_settings.albums"),
                        title = stringResource(R.string.albums),
                        text = stringResource(R.string.library_visibility_description),
                        onClick = { HomeAlbumsSettingsDialog.showDialog() },
                        icon = R.drawable.album
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.playlists).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.home_tabs_settings.playlists"),
                        title = stringResource(R.string.playlists),
                        text = stringResource(R.string.library_visibility_description),
                        onClick = { HomePlaylistsSettingsDialog.showDialog() },
                        icon = R.drawable.library
                    )
                }
            }
        )
    }
}
