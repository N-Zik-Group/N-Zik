package app.n_zik.android.components.settings.ui

import app.it.fast4x.rimusic.utils.albumSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.albumSwipeRightActionKey
import app.it.fast4x.rimusic.utils.applyFontPaddingKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.customColorKey
import app.it.fast4x.rimusic.utils.customThemeDark_accentKey
import app.it.fast4x.rimusic.utils.customThemeDark_Background0Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background1Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background2Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background3Key
import app.it.fast4x.rimusic.utils.customThemeDark_Background4Key
import app.it.fast4x.rimusic.utils.customThemeDark_iconButtonPlayerKey
import app.it.fast4x.rimusic.utils.customThemeDark_textDisabledKey
import app.it.fast4x.rimusic.utils.customThemeDark_TextKey
import app.it.fast4x.rimusic.utils.customThemeDark_textSecondaryKey
import app.it.fast4x.rimusic.utils.customThemeLight_accentKey
import app.it.fast4x.rimusic.utils.customThemeLight_Background0Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background1Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background2Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background3Key
import app.it.fast4x.rimusic.utils.customThemeLight_Background4Key
import app.it.fast4x.rimusic.utils.customThemeLight_iconButtonPlayerKey
import app.it.fast4x.rimusic.utils.customThemeLight_textDisabledKey
import app.it.fast4x.rimusic.utils.customThemeLight_TextKey
import app.it.fast4x.rimusic.utils.customThemeLight_textSecondaryKey
import app.it.fast4x.rimusic.utils.disableNavigationBackStackKey
import app.it.fast4x.rimusic.utils.fontTypeKey
import app.it.fast4x.rimusic.utils.hideStatusBarKey
import app.it.fast4x.rimusic.utils.indexNavigationTabKey
import app.it.fast4x.rimusic.utils.isSwipeToActionEnabledKey
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.messageTypeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.playlistSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.playlistSwipeRightActionKey
import app.it.fast4x.rimusic.utils.queueSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.queueSwipeRightActionKey
import app.it.fast4x.rimusic.utils.rememberQueueScrollPositionKey
import app.it.fast4x.rimusic.utils.searchDisplayOrderKey
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import app.it.fast4x.rimusic.utils.showSearchTabKey
import app.it.fast4x.rimusic.utils.showStatsInNavbarKey
import app.it.fast4x.rimusic.utils.transitionEffectKey
import app.it.fast4x.rimusic.utils.UiTypeKey
import app.it.fast4x.rimusic.utils.useSystemFontKey

/** Local inventory only; it does not activate global search or change navigation. */
internal object UiSettingTargets {
    val cards: List<UiCardTargets> = listOf(
        UiCardTargets("ui.card.theme",
            preferenceKeys = listOf(
                UiTypeKey,
                colorPaletteNameKey,
                customColorKey,
                customThemeLight_Background0Key,
                customThemeLight_Background1Key,
                customThemeLight_Background2Key,
                customThemeLight_Background3Key,
                customThemeLight_Background4Key,
                customThemeLight_TextKey,
                customThemeLight_textSecondaryKey,
                customThemeLight_textDisabledKey,
                customThemeLight_iconButtonPlayerKey,
                customThemeLight_accentKey,
                customThemeDark_Background0Key,
                customThemeDark_Background1Key,
                customThemeDark_Background2Key,
                customThemeDark_Background3Key,
                customThemeDark_Background4Key,
                customThemeDark_TextKey,
                customThemeDark_textSecondaryKey,
                customThemeDark_textDisabledKey,
                customThemeDark_iconButtonPlayerKey,
                customThemeDark_accentKey,
                colorPaletteModeKey,
            ),
            actions = listOf(
                "resetLightPalette",
                "resetDarkPalette",
            ),
        ),
        UiCardTargets("ui.card.interface",
            preferenceKeys = listOf(
                navigationBarPositionKey,
                navigationBarTypeKey,
                playerPositionKey,
                menuStyleKey,
                messageTypeKey,
                indexNavigationTabKey,
                transitionEffectKey,
                disableNavigationBackStackKey,
                searchDisplayOrderKey,
                showSearchTabKey,
                showStatsInNavbarKey,
                showFloatingIconKey,
                rememberQueueScrollPositionKey,
            ),
            actions = listOf(
                "HomeTabsSettingsDialog",
                "searchHistoryPages",
            ),
        ),
        UiCardTargets("ui.card.font",
            preferenceKeys = listOf(
                fontTypeKey,
                useSystemFontKey,
                applyFontPaddingKey,
                hideStatusBarKey,
            ),
        ),
        UiCardTargets("ui.card.swipe",
            preferenceKeys = listOf(
                isSwipeToActionEnabledKey,
                queueSwipeLeftActionKey,
                playlistSwipeLeftActionKey,
                playlistSwipeRightActionKey,
                albumSwipeLeftActionKey,
                albumSwipeRightActionKey,
                queueSwipeRightActionKey,
            ),
        ),
        UiCardTargets("ui.card.home-tabs",
            actions = listOf(
                "QuickPicksContentSettingsDialog",
                "HomeSongsSettingsDialog",
                "HomeArtistsSettingsDialog",
                "HomeAlbumsSettingsDialog",
                "HomePlaylistsSettingsDialog",
            ),
        ),
        UiCardTargets("ui.card.sort-menus",
            actions = listOf(
                "HomeSongsSortSettingsDialog",
                "HomeArtistsSortSettingsDialog",
                "HomeAlbumsSortSettingsDialog",
                "HomeLibrarySortSettingsDialog",
                "LocalPlaylistSortSettingsDialog",
                "HistorySortSettingsDialog",
            ),
        ),
        UiCardTargets("ui.card.floating-dock",
            preferenceKeys = listOf(
                app.n_zik.android.components.navigation.dock.DockToolbarPreferences.orderKey,
            ),
            actions = listOf("DockToolbarSettingsDialog"),
        ),
        UiCardTargets("ui.card.toolbars",
            actions = listOf(
                "HomeSongsToolbarSettingsDialog",
                "HomeArtistsToolbarSettingsDialog",
                "HomeAlbumsToolbarSettingsDialog",
                "HomeLibraryToolbarSettingsDialog",
                "LocalPlaylistToolbarSettingsDialog",
            ),
        ),
        UiCardTargets("ui.card.burger",
            actions = listOf(
                "BurgerMenuSettingsDialog",
            ),
        ),
        UiCardTargets("ui.card.reset",
            actions = listOf(
                "applyUiDefaults",
            ),
        ),
    )
    val ids: List<String> = cards.flatMap { card ->
        listOf(card.id) + card.preferenceKeys.map { "${card.id}.setting.$it" } +
            card.actions.map { "${card.id}.action.$it" }
    }
}

/** Semantic keys and action names remain stable across translations and display order. */
internal data class UiCardTargets(
    val id: String,
    val preferenceKeys: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
)
