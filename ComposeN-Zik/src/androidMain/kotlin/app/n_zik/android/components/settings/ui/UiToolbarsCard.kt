package app.n_zik.android.components.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.AlbumDetailsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.ArtistDetailsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.CustomToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HeaderToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibraryToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.OnlinePlaylistToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.PodcastToolbarSettingsDialog
import app.n_zik.android.components.settings.common.CardCategoryLabel
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsCardSectionDivider
import app.n_zik.android.components.settings.common.SettingsEntryTarget
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.searchSettingsEntries
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.tab.Search

private data class ToolbarSettingsEntry(
    val id: String,
    val label: Int,
    val icon: Int,
    val open: () -> Unit,
    val searchAlias: Int = label,
) {
    constructor(dialog: CustomToolbarSettingsDialog) : this(dialog.entryId, dialog.label, dialog.icon, dialog::showDialog)
}

private val toolbarSettingGroups = listOf(
    R.string.library_title to listOf(
        ToolbarSettingsEntry("ui.toolbar.songs", R.string.library_songs_title, R.drawable.musical_notes,
            HomeSongsToolbarSettingsDialog::showDialog),
        ToolbarSettingsEntry("ui.toolbar.artists", R.string.library_artists_title, R.drawable.people,
            HomeArtistsToolbarSettingsDialog::showDialog),
        ToolbarSettingsEntry("ui.toolbar.albums", R.string.library_albums_title, R.drawable.album,
            HomeAlbumsToolbarSettingsDialog::showDialog),
        ToolbarSettingsEntry("ui.toolbar.playlists", R.string.library_playlists_title, R.drawable.library,
            HomeLibraryToolbarSettingsDialog::showDialog),
    ),
    R.string.settings_toolbar_details_group to listOf(
        ToolbarSettingsEntry(AlbumDetailsToolbarSettingsDialog),
        ToolbarSettingsEntry(ArtistDetailsToolbarSettingsDialog),
        ToolbarSettingsEntry("ui.toolbar.local_playlist_sort", R.string.settings_local_playlist_title, R.drawable.playlist,
            LocalPlaylistToolbarSettingsDialog::showDialog, searchAlias = R.string.library_playlists_title),
        ToolbarSettingsEntry(OnlinePlaylistToolbarSettingsDialog),
        ToolbarSettingsEntry(PodcastToolbarSettingsDialog),
    ),
    R.string.common_navigation_section_title to listOf(ToolbarSettingsEntry(HeaderToolbarSettingsDialog)),
)

internal val interfaceToolbarEntryTargets = toolbarSettingGroups.flatMap { (group, entries) ->
    entries.map { entry ->
        SettingsEntryTarget(
            entry.id, 1, R.string.settings_library_toolbar_title, entry.label,
            listOf(group, entry.searchAlias, R.string.settings_library_toolbar_customization_description),
        )
    }
}

@Composable
internal fun UiToolbarsCard(search: Search) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val query = search.inputValue.trim()
    val title = stringResource(R.string.settings_library_toolbar_title)
    val showAll = query.isBlank() || title.contains(query, ignoreCase = true)
    val indexedMatches = remember(context, configuration, query) {
        searchSettingsEntries(context, query).mapTo(mutableSetOf()) { target ->
            target.nestedDestination?.openerId ?: target.id
        }
    }
    val groups = toolbarSettingGroups.mapNotNull { (label, entries) ->
        val groupTitle = stringResource(label)
        val visible = if (showAll || groupTitle.contains(query, ignoreCase = true)) entries
        else entries.filter {
            it.id in indexedMatches || stringResource(it.label).contains(query, ignoreCase = true) ||
                stringResource(it.searchAlias).contains(query, ignoreCase = true)
        }
        if (visible.isEmpty()) null else groupTitle to visible
    }

    AnimatedVisibility(visible = groups.isNotEmpty(), enter = settingsCardEnter, exit = settingsCardExit) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.toolbars"),
            title = title,
            icon = R.drawable.sort_grid,
            content = {
                groups.forEachIndexed { index, (groupTitle, entries) ->
                    if (index > 0) SettingsCardSectionDivider()
                    CardCategoryLabel(groupTitle)
                    entries.forEach { entry ->
                        OtherSettingsEntry(
                            modifier = Modifier.settingsSearchTarget(entry.id),
                            title = stringResource(entry.label),
                            text = stringResource(R.string.settings_library_toolbar_customization_description),
                            onClick = entry.open,
                            icon = entry.icon,
                        )
                    }
                }
            },
        )
    }
}
