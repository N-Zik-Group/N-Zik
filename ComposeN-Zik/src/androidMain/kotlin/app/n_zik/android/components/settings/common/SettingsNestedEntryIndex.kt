package app.n_zik.android.components.settings.common

import app.n_zik.android.components.dialog.common.Dialog
import app.n_zik.android.components.dialog.settings.AppShortcutsSettingsDialog
import app.n_zik.android.components.dialog.settings.BurgerMenuSettingsDialog
import app.n_zik.android.components.dialog.settings.DockToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HistorySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibrarySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibraryToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomePlaylistsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistSortSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog

private data class NestedDialogRegistration(
    val dialogId: String,
    val openerId: String,
    val options: () -> List<SettingsNestedOption>,
    val dialog: Dialog,
)

private val nestedDialogRegistrations = listOf(
    NestedDialogRegistration("AppShortcutsSettingsDialog", "general.app_shortcuts_settings.app_shortcuts_settings", AppShortcutsSettingsDialog::searchOptions, AppShortcutsSettingsDialog),
    NestedDialogRegistration("HomeTabsSettingsDialog", "ui.user_interface.home_tabs_settings", HomeTabsSettingsDialog::searchOptions, HomeTabsSettingsDialog),
    NestedDialogRegistration("HomeSongsSettingsDialog", "ui.home_tabs_settings.songs", HomeSongsSettingsDialog::searchOptions, HomeSongsSettingsDialog),
    NestedDialogRegistration("HomeArtistsSettingsDialog", "ui.home_tabs_settings.artists", HomeArtistsSettingsDialog::searchOptions, HomeArtistsSettingsDialog),
    NestedDialogRegistration("HomeAlbumsSettingsDialog", "ui.home_tabs_settings.albums", HomeAlbumsSettingsDialog::searchOptions, HomeAlbumsSettingsDialog),
    NestedDialogRegistration("HomePlaylistsSettingsDialog", "ui.home_tabs_settings.playlists", HomePlaylistsSettingsDialog::searchOptions, HomePlaylistsSettingsDialog),
    NestedDialogRegistration("QuickPicksContentSettingsDialog", "ui.home_tabs_settings.quick_picks", QuickPicksContentSettingsDialog::searchOptions, QuickPicksContentSettingsDialog),
    NestedDialogRegistration("QuickPicksContentSettingsDialog", "ai.quick_picks.quick_picks_content", QuickPicksContentSettingsDialog::searchOptions, QuickPicksContentSettingsDialog),
    NestedDialogRegistration("HomeSongsSortSettingsDialog", "ui.sort_menu.songs", HomeSongsSortSettingsDialog::searchOptions, HomeSongsSortSettingsDialog),
    NestedDialogRegistration("HomeArtistsSortSettingsDialog", "ui.sort_menu.artists", HomeArtistsSortSettingsDialog::searchOptions, HomeArtistsSortSettingsDialog),
    NestedDialogRegistration("HomeAlbumsSortSettingsDialog", "ui.sort_menu.albums", HomeAlbumsSortSettingsDialog::searchOptions, HomeAlbumsSortSettingsDialog),
    NestedDialogRegistration("HomeLibrarySortSettingsDialog", "ui.sort_menu.playlists", HomeLibrarySortSettingsDialog::searchOptions, HomeLibrarySortSettingsDialog),
    NestedDialogRegistration("LocalPlaylistSortSettingsDialog", "ui.sort_menu.local_playlist_sort", LocalPlaylistSortSettingsDialog::searchOptions, LocalPlaylistSortSettingsDialog),
    NestedDialogRegistration("HistorySortSettingsDialog", "ui.sort_menu.history", HistorySortSettingsDialog::searchOptions, HistorySortSettingsDialog),
    NestedDialogRegistration("HomeSongsToolbarSettingsDialog", "ui.toolbar.songs", HomeSongsToolbarSettingsDialog::searchOptions, HomeSongsToolbarSettingsDialog),
    NestedDialogRegistration("HomeArtistsToolbarSettingsDialog", "ui.toolbar.artists", HomeArtistsToolbarSettingsDialog::searchOptions, HomeArtistsToolbarSettingsDialog),
    NestedDialogRegistration("HomeAlbumsToolbarSettingsDialog", "ui.toolbar.albums", HomeAlbumsToolbarSettingsDialog::searchOptions, HomeAlbumsToolbarSettingsDialog),
    NestedDialogRegistration("HomeLibraryToolbarSettingsDialog", "ui.toolbar.playlists", HomeLibraryToolbarSettingsDialog::searchOptions, HomeLibraryToolbarSettingsDialog),
    NestedDialogRegistration("LocalPlaylistToolbarSettingsDialog", "ui.toolbar.local_playlist_sort", LocalPlaylistToolbarSettingsDialog::searchOptions, LocalPlaylistToolbarSettingsDialog),
    NestedDialogRegistration("DockToolbarSettingsDialog", "ui.floating_dock.toolbar_order", DockToolbarSettingsDialog::searchOptions, DockToolbarSettingsDialog),
    NestedDialogRegistration("BurgerMenuSettingsDialog", "embedded.burger.burger_menu", BurgerMenuSettingsDialog::searchOptions, BurgerMenuSettingsDialog),
    NestedDialogRegistration("PlayerActionBarSettingsDialog", "appearance.player_action_bar.player_action_bar_buttons", PlayerActionBarSettingsDialog::searchOptions, PlayerActionBarSettingsDialog),
    NestedDialogRegistration("MiniPlayerButtonsSettingsDialog", "appearance.timeline_and_controls.miniplayer_buttons_config", MiniPlayerButtonsSettingsDialog::searchOptions, MiniPlayerButtonsSettingsDialog),
 )

/** Dialog options derive their IDs, resources and availability from the actual configuration source. */
val SettingsNestedEntryTargets: List<SettingsEntryTarget> by lazy {
    val openers = (SettingsEntryTargets + EmbeddedSettingsEntryTargets).associateBy { it.id }
    nestedDialogRegistrations.flatMap { registration ->
        val opener = openers.getValue(registration.openerId)
        registration.options().map { option ->
            SettingsEntryTarget(
                id = "nested.${opener.id}.${option.tabIndex}.${option.itemId}",
                tab = opener.tab,
                cardTitleRes = opener.cardTitleRes,
                labelRes = option.labelRes,
                keywordRes = listOfNotNull(option.descriptionRes),
                nestedDestination = SettingsNestedDestination(
                    openerId = opener.id,
                    dialogId = registration.dialogId,
                    tabIndex = option.tabIndex,
                    itemId = option.itemId,
                    sectionTitleRes = option.sectionTitleRes,
                ),
            )
        }
    }
}

/** Returns only configuration dialogs available from registered Settings rows. */
fun settingsNestedDialog(dialogId: String): Dialog? =
    nestedDialogRegistrations.firstOrNull { it.dialogId == dialogId }?.dialog
