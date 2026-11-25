package app.n_zik.android.components.settings.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.HistorySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibrarySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistSortSettingsDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.normalizedSettingsText
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.tab.Search

private data class SortMenuSetting(
    val id: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val open: () -> Unit,
)

private val sortMenuSettings = listOf(
    SortMenuSetting("songs", R.string.library_songs_title, R.drawable.musical_notes) { HomeSongsSortSettingsDialog.showDialog() },
    SortMenuSetting("artists", R.string.library_artists_title, R.drawable.people) { HomeArtistsSortSettingsDialog.showDialog() },
    SortMenuSetting("albums", R.string.library_albums_title, R.drawable.album) { HomeAlbumsSortSettingsDialog.showDialog() },
    SortMenuSetting("playlists", R.string.library_playlists_title, R.drawable.library) { HomeLibrarySortSettingsDialog.showDialog() },
    SortMenuSetting("local_playlist_sort", R.string.settings_local_playlist_title, R.drawable.playlist) {
        LocalPlaylistSortSettingsDialog.showDialog()
    },
    SortMenuSetting("history", R.string.library_history_title, R.drawable.time) { HistorySortSettingsDialog.showDialog() },
)

@Composable
internal fun UiSortMenusCard(search: Search) {
    val query = normalizedSettingsText(search.inputValue)
    val title = stringResource(R.string.library_sort_menu)
    val groupTitle = stringResource(R.string.settings_group_sort_defaults)
    val description = stringResource(R.string.settings_library_sort_options_description)
    val showAll = query.isEmpty() || sequenceOf(title, groupTitle, description).any {
        normalizedSettingsText(it).contains(query)
    }
    val visibleSettings = sortMenuSettings.filter { setting ->
        showAll || normalizedSettingsText(stringResource(setting.title)).contains(query)
    }

    SettingsSectionCard(
        modifier = Modifier.testTag("ui.card.sort-menus"),
        title = title,
        icon = R.drawable.arrow_up,
        groupTitleRes = R.string.settings_group_sort_defaults,
        visible = visibleSettings.isNotEmpty(),
        content = {
            visibleSettings.forEach { setting ->
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("ui.sort_menu.${setting.id}"),
                    title = stringResource(setting.title),
                    text = description,
                    icon = setting.icon,
                    onClick = setting.open,
                )
            }
        },
    )
}
