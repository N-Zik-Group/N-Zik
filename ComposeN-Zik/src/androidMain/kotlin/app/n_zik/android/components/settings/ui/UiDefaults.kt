package app.n_zik.android.components.settings.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import app.n_zik.android.enums.AlbumSwipeAction
import app.n_zik.android.enums.BackgroundProgress
import app.n_zik.android.enums.CarouselSize
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.FontType
import app.n_zik.android.enums.HomeScreenTabs
import app.n_zik.android.enums.IconLikeType
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.MiniPlayerType
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.NavigationBarType
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerControlsType
import app.n_zik.android.enums.PlayerInfoType
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.enums.PlayerTimelineSize
import app.n_zik.android.enums.PlayerTimelineType
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.enums.PlaylistSwipeAction
import app.n_zik.android.enums.QueueSwipeAction
import app.n_zik.android.enums.QueueType
import app.n_zik.android.enums.SearchDisplayOrder
import app.n_zik.android.enums.ThumbnailType
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.utils.actionspacedevenlyKey
import app.n_zik.android.utils.albumSwipeLeftActionKey
import app.n_zik.android.utils.albumSwipeRightActionKey
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.backgroundProgressKey
import app.n_zik.android.utils.blackgradientKey
import app.n_zik.android.utils.buttonzoomoutKey
import app.n_zik.android.utils.carouselKey
import app.n_zik.android.utils.carouselSizeKey
import app.n_zik.android.utils.clickOnLyricsTextKey
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
import app.n_zik.android.utils.disablePlayerHorizontalSwipeKey
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.effectRotationKey
import app.n_zik.android.utils.expandedplayertoggleKey
import app.n_zik.android.utils.fadingedgeKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.iconLikeTypeKey
import app.n_zik.android.utils.indexNavigationTabKey
import app.n_zik.android.utils.isSwipeToActionEnabledKey
import app.n_zik.android.utils.keepPlayerMinimizedKey
import app.n_zik.android.utils.lastPlayerPlayButtonTypeKey
import app.n_zik.android.utils.lastPlayerTimelineTypeKey
import app.n_zik.android.utils.lastThumbnailSizeDpKey
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.miniPlayerTypeKey
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.navigationBarTypeKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.playerControlsTypeKey
import app.n_zik.android.utils.playerEnableLyricsPopupMessageKey
import app.n_zik.android.utils.playerInfoShowIconsKey
import app.n_zik.android.utils.playerInfoTypeKey
import app.n_zik.android.utils.playerPlayButtonTypeKey
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.utils.playerSwapControlsWithTimelineKey
import app.n_zik.android.utils.playerTimelineSizeKey
import app.n_zik.android.utils.playerTimelineTypeKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.playlistSwipeLeftActionKey
import app.n_zik.android.utils.playlistSwipeRightActionKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.queueSwipeLeftActionKey
import app.n_zik.android.utils.queueSwipeRightActionKey
import app.n_zik.android.utils.queueTypeKey
import app.n_zik.android.utils.rememberQueueScrollPositionKey
import app.n_zik.android.utils.searchDisplayOrderKey
import app.n_zik.android.utils.showButtonPlayerAddToPlaylistKey
import app.n_zik.android.utils.showButtonPlayerArrowKey
import app.n_zik.android.utils.showButtonPlayerDiscoverKey
import app.n_zik.android.utils.showButtonPlayerDownloadKey
import app.n_zik.android.utils.showButtonPlayerLoopKey
import app.n_zik.android.utils.showButtonPlayerLyricsKey
import app.n_zik.android.utils.showButtonPlayerMenuKey
import app.n_zik.android.utils.showButtonPlayerShuffleKey
import app.n_zik.android.utils.showButtonPlayerSleepTimerKey
import app.n_zik.android.utils.showButtonPlayerSystemEqualizerKey
import app.n_zik.android.utils.showCachedPlaylistKey
import app.n_zik.android.utils.showDownloadedPlaylistKey
import app.n_zik.android.utils.showFavoritesPlaylistKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.showNextSongsInPlayerKey
import app.n_zik.android.utils.showOnDevicePlaylistKey
import app.n_zik.android.utils.showPinnedPlaylistsKey
import app.n_zik.android.utils.showRemainingSongTimeKey
import app.n_zik.android.utils.showSearchTabKey
import app.n_zik.android.utils.showStatsInNavbarKey
import app.n_zik.android.utils.showTopActionsBarKey
import app.n_zik.android.utils.showTotalTimeQueueKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.swipeUpQueueKey
import app.n_zik.android.utils.tapqueueKey
import app.n_zik.android.utils.thumbnailSizeDpKey
import app.n_zik.android.utils.thumbnailSizeLDpKey
import app.n_zik.android.utils.thumbnailTapEnabledKey
import app.n_zik.android.utils.thumbnailTypeKey
import app.n_zik.android.utils.transitionEffectKey
import app.n_zik.android.utils.transparentBackgroundPlayerActionBarKey
import app.n_zik.android.utils.useSystemFontKey
import app.n_zik.android.utils.visualizerEnabledKey
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
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog
import app.n_zik.android.components.settings.common.resetInterfaceReaderDefaults
import app.n_zik.android.components.theme.DYNAMIC_THEME_RESPECT_BRIGHTNESS_DEFAULT
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.core.settings.DEFAULT_THUMBNAIL_SIZE_DP

/** Applies the legacy Interface reset once to the active profile and dialog stores. */
internal fun applyUiDefaults(context: Context) {
    resetInterfaceReaderDefaults(context.preferences)
    context.preferences.edit().remove(app.n_zik.android.components.navigation.dock.DockToolbarPreferences.orderKey).apply()
    context.preferences.edit {
        putBoolean(hideStatusBarKey, false)
        putBoolean(keepPlayerMinimizedKey, true)
        putBoolean(disableIconButtonOnTopKey, false)
        putEnum(lastPlayerTimelineTypeKey, PlayerTimelineType.Default)
        putFloat(lastThumbnailSizeDpKey, 30f)
        putBoolean(disablePlayerHorizontalSwipeKey, false)
        putEnum(lastPlayerPlayButtonTypeKey, PlayerPlayButtonType.Rectangular)
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
        putBoolean(showTopActionsBarKey, true)
        putEnum(playerControlsTypeKey, PlayerControlsType.Essential)
        putEnum(playerInfoTypeKey, PlayerInfoType.Modern)
        putEnum(playerTypeKey, PlayerType.Essential)
        putEnum(queueTypeKey, QueueType.Modern)
        putBoolean(fadingedgeKey, false)
        putBoolean(carouselKey, true)
        putEnum(carouselSizeKey, CarouselSize.Biggest)
        putEnum(thumbnailTypeKey, ThumbnailType.Modern)
        putEnum(playerTimelineTypeKey, PlayerTimelineType.Wavy)
        putFloat(thumbnailSizeDpKey, DEFAULT_THUMBNAIL_SIZE_DP)
        remove(thumbnailSizeLDpKey)
        putEnum(playerTimelineSizeKey, PlayerTimelineSize.Biggest)
        putBoolean(playerInfoShowIconsKey, true)
        putEnum(miniPlayerTypeKey, MiniPlayerType.Essential)
        putBoolean(playerSwapControlsWithTimelineKey, false)
        putEnum(playerPlayButtonTypeKey, PlayerPlayButtonType.CircularRibbed)
        putBoolean(buttonzoomoutKey, true)
        putEnum(iconLikeTypeKey, IconLikeType.Essential)
        putEnum(playerBackgroundColorsKey, PlayerBackgroundColors.AnimatedGradient)
        putBoolean(blackgradientKey, false)
        putBoolean(showTotalTimeQueueKey, true)
        putBoolean(showNextSongsInPlayerKey, false)
        putBoolean(showRemainingSongTimeKey, true)
        putBoolean(disableScrollingTextKey, false)
        putBoolean(effectRotationKey, false)
        putBoolean(thumbnailTapEnabledKey, true)
        putBoolean(clickOnLyricsTextKey, true)
        putEnum(backgroundProgressKey, BackgroundProgress.MiniPlayer)
        putBoolean(transparentBackgroundPlayerActionBarKey, true)
        putBoolean(actionspacedevenlyKey, true)
        putBoolean(tapqueueKey, true)
        putBoolean(swipeUpQueueKey, true)
        putBoolean(showButtonPlayerAddToPlaylistKey, true)
        putBoolean(showButtonPlayerArrowKey, true)
        putBoolean(showButtonPlayerDownloadKey, true)
        putBoolean(showButtonPlayerLoopKey, false)
        putBoolean(showButtonPlayerLyricsKey, true)
        putBoolean(expandedplayertoggleKey, false)
        putBoolean(showButtonPlayerShuffleKey, true)
        putBoolean(showButtonPlayerSleepTimerKey, false)
        putBoolean(showButtonPlayerMenuKey, false)
        putBoolean(showButtonPlayerSystemEqualizerKey, false)
        putEnum(queueSwipeLeftActionKey, QueueSwipeAction.RemoveFromQueue)
        putEnum(queueSwipeRightActionKey, QueueSwipeAction.PlayNext)
        putEnum(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
        putEnum(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)
        putEnum(albumSwipeLeftActionKey, AlbumSwipeAction.PlayNext)
        putEnum(albumSwipeRightActionKey, AlbumSwipeAction.Bookmark)
        putBoolean(showButtonPlayerDiscoverKey, false)
        putBoolean(playerEnableLyricsPopupMessageKey, true)
        putBoolean(visualizerEnabledKey, false)
        putBoolean(showthumbnailKey, true)
        putEnum(searchDisplayOrderKey, SearchDisplayOrder.SuggestionsFirst)
        putInt(customColorKey, Color.Green.hashCode())
        putBoolean(disableNavigationBackStackKey, false)
        putBoolean(showCachedPlaylistKey, true)
        putBoolean(rememberQueueScrollPositionKey, true)
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
    BurgerMenuSettingsDialog.reset(context)
    PlayerActionBarSettingsDialog.reset(context)
    MiniPlayerButtonsSettingsDialog.reset(context)
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
