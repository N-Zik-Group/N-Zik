package app.n_zik.android.components.settings.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import app.n_zik.android.enums.AlbumSwipeAction
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.FontType
import app.n_zik.android.enums.HomeScreenTabs
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.MusicAnimationType
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.NavigationBarType
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.enums.PlaylistSwipeAction
import app.n_zik.android.enums.QueueSwipeAction
import app.n_zik.android.enums.SearchDisplayOrder
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.albumSwipeLeftActionKey
import app.n_zik.android.utils.albumSwipeRightActionKey
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.customColorKey
import app.n_zik.android.utils.customThemeDark_Background0Key
import app.n_zik.android.utils.customThemeDark_Background1Key
import app.n_zik.android.utils.customThemeDark_Background2Key
import app.n_zik.android.utils.customThemeDark_Background3Key
import app.n_zik.android.utils.customThemeDark_Background4Key
import app.n_zik.android.utils.customThemeDark_TextKey
import app.n_zik.android.utils.customThemeDark_accentKey
import app.n_zik.android.utils.customThemeDark_iconButtonPlayerKey
import app.n_zik.android.utils.customThemeDark_textDisabledKey
import app.n_zik.android.utils.customThemeDark_textSecondaryKey
import app.n_zik.android.utils.customThemeLight_Background0Key
import app.n_zik.android.utils.customThemeLight_Background1Key
import app.n_zik.android.utils.customThemeLight_Background2Key
import app.n_zik.android.utils.customThemeLight_Background3Key
import app.n_zik.android.utils.customThemeLight_Background4Key
import app.n_zik.android.utils.customThemeLight_TextKey
import app.n_zik.android.utils.customThemeLight_accentKey
import app.n_zik.android.utils.customThemeLight_iconButtonPlayerKey
import app.n_zik.android.utils.customThemeLight_textDisabledKey
import app.n_zik.android.utils.customThemeLight_textSecondaryKey
import app.n_zik.android.utils.disableIconButtonOnTopKey
import app.n_zik.android.utils.disableNavigationBackStackKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.nowPlayingIndicatorKey
import app.n_zik.android.utils.playlistindicatorKey
import app.n_zik.android.utils.rightToLeftInterfaceKey
import app.n_zik.android.utils.indexNavigationTabKey
import app.n_zik.android.utils.isSwipeToActionEnabledKey
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.navigationBarTypeKey
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.utils.playlistSwipeLeftActionKey
import app.n_zik.android.utils.playlistSwipeRightActionKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.queueSwipeLeftActionKey
import app.n_zik.android.utils.queueSwipeRightActionKey
import app.n_zik.android.utils.searchDisplayOrderKey
import app.n_zik.android.utils.showCachedPlaylistKey
import app.n_zik.android.utils.showDownloadedPlaylistKey
import app.n_zik.android.utils.showFavoritesPlaylistKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.showOnDevicePlaylistKey
import app.n_zik.android.utils.showPinnedPlaylistsKey
import app.n_zik.android.utils.showSearchTabKey
import app.n_zik.android.utils.showStatsInNavbarKey
import app.n_zik.android.utils.transitionEffectKey
import app.n_zik.android.utils.uiRoundnessDpKey
import app.n_zik.android.utils.useSystemFontKey
import app.n_zik.android.components.dialog.settings.BurgerMenuSettingsDialog
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
import app.n_zik.android.components.dialog.settings.resetInterfaceToolbarSettings
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog
import app.n_zik.android.components.settings.common.resetInterfaceReaderDefaults
import app.n_zik.android.components.theme.DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey

/** Restores theme and interface defaults for the active profile and dialog stores. */
internal fun applyUiDefaults(context: Context) {
    resetInterfaceReaderDefaults(context.preferences)
    context.preferences.edit().remove(app.n_zik.android.components.navigation.dock.DockToolbarPreferences.orderKey).apply()
    context.preferences.edit {
        putBoolean(hideStatusBarKey, false)
        putFloat(uiRoundnessDpKey, 25f)
        putFloat(thumbnailRoundnessDpKey, 12f)
        putFloat(artistThumbnailRoundnessDpKey, 48f)
        putBoolean(rightToLeftInterfaceKey, false)
        putBoolean(disableIconButtonOnTopKey, false)
        putEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
        putBoolean(dynamicThemeRespectBrightnessKey, DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT)
        putEnum(colorPaletteModeKey, ColorPaletteMode.System)
        putEnum(indexNavigationTabKey, HomeScreenTabs.Default)
        putEnum(fontTypeKey, FontType.Rubik)
        putBoolean(useSystemFontKey, false)
        putBoolean(applyFontPaddingKey, false)
        putBoolean(isSwipeToActionEnabledKey, true)
        putBoolean(showSearchTabKey, false)
        putBoolean(showStatsInNavbarKey, false)
        putEnum(navigationBarPositionKey, NavigationBarPosition.BottomFloating)
        putEnum(navigationBarTypeKey, NavigationBarType.IconOnly)
        putBoolean(showFavoritesPlaylistKey, true)
        putBoolean(showDownloadedPlaylistKey, true)
        putBoolean(showOnDevicePlaylistKey, true)
        putBoolean(showFloatingIconKey, false)
        putEnum(menuStyleKey, MenuStyle.List)
        putEnum(transitionEffectKey, TransitionEffect.Fade)
        putBoolean(showPinnedPlaylistsKey, true)
        putInt(customThemeLight_Background0Key, DefaultLightColorPalette.background0.hashCode())
        putInt(customThemeLight_Background1Key, DefaultLightColorPalette.background1.hashCode())
        putInt(customThemeLight_Background2Key, DefaultLightColorPalette.background2.hashCode())
        putInt(customThemeLight_Background3Key, DefaultLightColorPalette.background3.hashCode())
        putInt(customThemeLight_Background4Key, DefaultLightColorPalette.background4.hashCode())
        putInt(customThemeLight_TextKey, DefaultLightColorPalette.text.hashCode())
        putInt(customThemeLight_textSecondaryKey, DefaultLightColorPalette.textSecondary.hashCode())
        putInt(customThemeLight_textDisabledKey, DefaultLightColorPalette.textDisabled.hashCode())
        putInt(customThemeLight_iconButtonPlayerKey, DefaultLightColorPalette.iconButtonPlayer.hashCode())
        putInt(customThemeLight_accentKey, DefaultLightColorPalette.accent.hashCode())
        putInt(customThemeDark_Background0Key, DefaultDarkColorPalette.background0.hashCode())
        putInt(customThemeDark_Background1Key, DefaultDarkColorPalette.background1.hashCode())
        putInt(customThemeDark_Background2Key, DefaultDarkColorPalette.background2.hashCode())
        putInt(customThemeDark_Background3Key, DefaultDarkColorPalette.background3.hashCode())
        putInt(customThemeDark_Background4Key, DefaultDarkColorPalette.background4.hashCode())
        putInt(customThemeDark_TextKey, DefaultDarkColorPalette.text.hashCode())
        putInt(customThemeDark_textSecondaryKey, DefaultDarkColorPalette.textSecondary.hashCode())
        putInt(customThemeDark_textDisabledKey, DefaultDarkColorPalette.textDisabled.hashCode())
        putInt(customThemeDark_iconButtonPlayerKey, DefaultDarkColorPalette.iconButtonPlayer.hashCode())
        putInt(customThemeDark_accentKey, DefaultDarkColorPalette.accent.hashCode())
        putEnum(playerPositionKey, PlayerPosition.Bottom)
        putEnum(queueSwipeLeftActionKey, QueueSwipeAction.RemoveFromQueue)
        putEnum(queueSwipeRightActionKey, QueueSwipeAction.PlayNext)
        putEnum(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
        putEnum(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)
        putEnum(albumSwipeLeftActionKey, AlbumSwipeAction.PlayNext)
        putEnum(albumSwipeRightActionKey, AlbumSwipeAction.Bookmark)
        putEnum(searchDisplayOrderKey, SearchDisplayOrder.SuggestionsFirst)
        putBoolean(playlistindicatorKey, false)
        putEnum(nowPlayingIndicatorKey, MusicAnimationType.Bubbles)
        putInt(customColorKey, Color.Green.hashCode())
        putBoolean(disableNavigationBackStackKey, false)
        putBoolean(showCachedPlaylistKey, true)
    }
    HomeTabsSettingsDialog.reset(context)
    HomeSongsSettingsDialog.reset(context)
    HomeArtistsSettingsDialog.reset(context)
    HomeAlbumsSettingsDialog.reset(context)
    HomePlaylistsSettingsDialog.reset(context)
    QuickPicksContentSettingsDialog.reset(context)
    HomeSongsToolbarSettingsDialog.reset(context)
    HomeArtistsToolbarSettingsDialog.reset(context)
    HomeAlbumsToolbarSettingsDialog.reset(context)
    HomeLibraryToolbarSettingsDialog.reset(context)
    LocalPlaylistToolbarSettingsDialog.reset(context)
    resetInterfaceToolbarSettings(context)
    BurgerMenuSettingsDialog.reset(context)
    HomeSongsSortSettingsDialog.reset(context)
    HomeAlbumsSortSettingsDialog.reset(context)
    HomeArtistsSortSettingsDialog.reset(context)
    HomeLibrarySortSettingsDialog.reset(context)
    LocalPlaylistSortSettingsDialog.reset(context)
    HistorySortSettingsDialog.reset(context)
}

/** Restores only the selected custom palette in the active profile. */
internal fun resetUiPalette(context: Context, dark: Boolean) {
    context.preferences.edit {
        if (dark) {
            putInt(customThemeDark_Background0Key, DefaultDarkColorPalette.background0.hashCode())
            putInt(customThemeDark_Background1Key, DefaultDarkColorPalette.background1.hashCode())
            putInt(customThemeDark_Background2Key, DefaultDarkColorPalette.background2.hashCode())
            putInt(customThemeDark_Background3Key, DefaultDarkColorPalette.background3.hashCode())
            putInt(customThemeDark_Background4Key, DefaultDarkColorPalette.background4.hashCode())
            putInt(customThemeDark_TextKey, DefaultDarkColorPalette.text.hashCode())
            putInt(customThemeDark_textSecondaryKey, DefaultDarkColorPalette.textSecondary.hashCode())
            putInt(customThemeDark_textDisabledKey, DefaultDarkColorPalette.textDisabled.hashCode())
            putInt(customThemeDark_iconButtonPlayerKey, DefaultDarkColorPalette.iconButtonPlayer.hashCode())
            putInt(customThemeDark_accentKey, DefaultDarkColorPalette.accent.hashCode())
        } else {
            putInt(customThemeLight_Background0Key, DefaultLightColorPalette.background0.hashCode())
            putInt(customThemeLight_Background1Key, DefaultLightColorPalette.background1.hashCode())
            putInt(customThemeLight_Background2Key, DefaultLightColorPalette.background2.hashCode())
            putInt(customThemeLight_Background3Key, DefaultLightColorPalette.background3.hashCode())
            putInt(customThemeLight_Background4Key, DefaultLightColorPalette.background4.hashCode())
            putInt(customThemeLight_TextKey, DefaultLightColorPalette.text.hashCode())
            putInt(customThemeLight_textSecondaryKey, DefaultLightColorPalette.textSecondary.hashCode())
            putInt(customThemeLight_textDisabledKey, DefaultLightColorPalette.textDisabled.hashCode())
            putInt(customThemeLight_iconButtonPlayerKey, DefaultLightColorPalette.iconButtonPlayer.hashCode())
            putInt(customThemeLight_accentKey, DefaultLightColorPalette.accent.hashCode())
        }
    }
}
