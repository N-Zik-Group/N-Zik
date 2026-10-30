package app.n_zik.android.components.settings.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import app.it.fast4x.rimusic.enums.AlbumSwipeAction
import app.it.fast4x.rimusic.enums.BackgroundProgress
import app.it.fast4x.rimusic.enums.CarouselSize
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.HomeScreenTabs
import app.it.fast4x.rimusic.enums.IconLikeType
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.MessageType
import app.it.fast4x.rimusic.enums.MiniPlayerType
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.enums.PlayerInfoType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.PlaylistSwipeAction
import app.it.fast4x.rimusic.enums.QueueSwipeAction
import app.it.fast4x.rimusic.enums.QueueType
import app.it.fast4x.rimusic.enums.SearchDisplayOrder
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.utils.actionspacedevenlyKey
import app.it.fast4x.rimusic.utils.albumSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.albumSwipeRightActionKey
import app.it.fast4x.rimusic.utils.applyFontPaddingKey
import app.it.fast4x.rimusic.utils.backgroundProgressKey
import app.it.fast4x.rimusic.utils.blackgradientKey
import app.it.fast4x.rimusic.utils.buttonzoomoutKey
import app.it.fast4x.rimusic.utils.carouselKey
import app.it.fast4x.rimusic.utils.carouselSizeKey
import app.it.fast4x.rimusic.utils.clickOnLyricsTextKey
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
import app.it.fast4x.rimusic.utils.disableIconButtonOnTopKey
import app.it.fast4x.rimusic.utils.disableNavigationBackStackKey
import app.it.fast4x.rimusic.utils.disablePlayerHorizontalSwipeKey
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.expandedplayertoggleKey
import app.it.fast4x.rimusic.utils.fadingedgeKey
import app.it.fast4x.rimusic.utils.fontTypeKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.hideStatusBarKey
import app.it.fast4x.rimusic.utils.iconLikeTypeKey
import app.it.fast4x.rimusic.utils.indexNavigationTabKey
import app.it.fast4x.rimusic.utils.isSwipeToActionEnabledKey
import app.it.fast4x.rimusic.utils.keepPlayerMinimizedKey
import app.it.fast4x.rimusic.utils.lastPlayerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.lastPlayerTimelineTypeKey
import app.it.fast4x.rimusic.utils.lastThumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.messageTypeKey
import app.it.fast4x.rimusic.utils.miniPlayerTypeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerControlsTypeKey
import app.it.fast4x.rimusic.utils.playerEnableLyricsPopupMessageKey
import app.it.fast4x.rimusic.utils.playerInfoShowIconsKey
import app.it.fast4x.rimusic.utils.playerInfoTypeKey
import app.it.fast4x.rimusic.utils.playerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.playerSwapControlsWithTimelineKey
import app.it.fast4x.rimusic.utils.playerTimelineSizeKey
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.playlistSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.playlistSwipeRightActionKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.putEnum
import app.it.fast4x.rimusic.utils.queueSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.queueSwipeRightActionKey
import app.it.fast4x.rimusic.utils.queueTypeKey
import app.it.fast4x.rimusic.utils.rememberQueueScrollPositionKey
import app.it.fast4x.rimusic.utils.searchDisplayOrderKey
import app.it.fast4x.rimusic.utils.showButtonPlayerAddToPlaylistKey
import app.it.fast4x.rimusic.utils.showButtonPlayerArrowKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDiscoverKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDownloadKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLoopKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLyricsKey
import app.it.fast4x.rimusic.utils.showButtonPlayerMenuKey
import app.it.fast4x.rimusic.utils.showButtonPlayerShuffleKey
import app.it.fast4x.rimusic.utils.showButtonPlayerSleepTimerKey
import app.it.fast4x.rimusic.utils.showButtonPlayerSystemEqualizerKey
import app.it.fast4x.rimusic.utils.showCachedPlaylistKey
import app.it.fast4x.rimusic.utils.showDownloadedPlaylistKey
import app.it.fast4x.rimusic.utils.showFavoritesPlaylistKey
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import app.it.fast4x.rimusic.utils.showNextSongsInPlayerKey
import app.it.fast4x.rimusic.utils.showOnDevicePlaylistKey
import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.showSearchTabKey
import app.it.fast4x.rimusic.utils.showStatsInNavbarKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.showTopActionsBarKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.swipeUpQueueKey
import app.it.fast4x.rimusic.utils.tapqueueKey
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.thumbnailTapEnabledKey
import app.it.fast4x.rimusic.utils.thumbnailTypeKey
import app.it.fast4x.rimusic.utils.transitionEffectKey
import app.it.fast4x.rimusic.utils.transparentBackgroundPlayerActionBarKey
import app.it.fast4x.rimusic.utils.UiTypeKey
import app.it.fast4x.rimusic.utils.useSystemFontKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.n_zik.android.components.dialog.settings.BurgerMenuSettingsDialog
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

/** Applies the legacy Interface reset once to the active profile and dialog stores. */
internal fun applyUiDefaults(context: Context) {
    context.preferences.edit().remove(app.n_zik.android.components.navigation.dock.DockToolbarPreferences.orderKey).apply()
    context.preferences.edit {
        putBoolean(hideStatusBarKey, false)
        putBoolean(keepPlayerMinimizedKey, true)
        putBoolean(disableIconButtonOnTopKey, false)
        putEnum(lastPlayerTimelineTypeKey, PlayerTimelineType.Default)
        putFloat(lastThumbnailSizeDpKey, 30f)
        putEnum(UiTypeKey, UiType.RiMusic)
        putBoolean(disablePlayerHorizontalSwipeKey, false)
        putEnum(lastPlayerPlayButtonTypeKey, PlayerPlayButtonType.Rectangular)
        putEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
        putEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
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
        putEnum(messageTypeKey, MessageType.Modern)
        putBoolean(showTopActionsBarKey, true)
        putEnum(playerControlsTypeKey, PlayerControlsType.Modern)
        putEnum(playerInfoTypeKey, PlayerInfoType.Modern)
        putEnum(playerTypeKey, PlayerType.Essential)
        putEnum(queueTypeKey, QueueType.Modern)
        putBoolean(fadingedgeKey, false)
        putBoolean(carouselKey, true)
        putEnum(carouselSizeKey, CarouselSize.Biggest)
        putEnum(thumbnailTypeKey, ThumbnailType.Modern)
        putEnum(playerTimelineTypeKey, PlayerTimelineType.Wavy)
        putFloat(thumbnailSizeDpKey, 0f)
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

/** Applies the asymmetric legacy ViMusic/RiMusic preference rewrites. */
internal fun applyUiType(context: Context, selectedType: UiType) {
    val preferences = context.preferences
    preferences.edit {
        putEnum(UiTypeKey, selectedType)
        if (selectedType == UiType.ViMusic) {
            putBoolean(disablePlayerHorizontalSwipeKey, true)
            putBoolean(disableIconButtonOnTopKey, true)
            putEnum(playerTimelineTypeKey, PlayerTimelineType.AudioWaves)
            putBoolean(visualizerEnabledKey, false)
            putFloat(thumbnailSizeDpKey, 30f)
            putBoolean(thumbnailTapEnabledKey, true)
            putBoolean(showSearchTabKey, true)
            putBoolean(showStatsInNavbarKey, true)
            putEnum(navigationBarPositionKey, NavigationBarPosition.Left)
            putBoolean(showTopActionsBarKey, false)
            putEnum(playerTypeKey, PlayerType.Modern)
            putEnum(queueTypeKey, QueueType.Modern)
            putBoolean(fadingedgeKey, false)
            putBoolean(carouselKey, true)
            putEnum(carouselSizeKey, CarouselSize.Medium)
            putEnum(thumbnailTypeKey, ThumbnailType.Essential)
            putEnum(playerTimelineSizeKey, PlayerTimelineSize.Medium)
            putBoolean(playerInfoShowIconsKey, true)
            putEnum(miniPlayerTypeKey, MiniPlayerType.Modern)
            putBoolean(playerSwapControlsWithTimelineKey, false)
            putBoolean(transparentBackgroundPlayerActionBarKey, false)
            putEnum(playerControlsTypeKey, PlayerControlsType.Essential)
            putEnum(playerPlayButtonTypeKey, PlayerPlayButtonType.Disabled)
            putBoolean(buttonzoomoutKey, true)
            putEnum(iconLikeTypeKey, IconLikeType.Essential)
            putEnum(playerBackgroundColorsKey, PlayerBackgroundColors.CoverColorGradient)
            putBoolean(blackgradientKey, true)
            putBoolean(showTotalTimeQueueKey, false)
            putBoolean(showRemainingSongTimeKey, false)
            putBoolean(showNextSongsInPlayerKey, false)
            putBoolean(disableScrollingTextKey, false)
            putBoolean(effectRotationKey, true)
            putBoolean(clickOnLyricsTextKey, true)
            putBoolean(playerEnableLyricsPopupMessageKey, true)
            putEnum(backgroundProgressKey, BackgroundProgress.MiniPlayer)
            putBoolean(transparentBackgroundPlayerActionBarKey, true)
            putBoolean(actionspacedevenlyKey, false)
            putBoolean(tapqueueKey, false)
            putBoolean(swipeUpQueueKey, true)
            putBoolean(showButtonPlayerDiscoverKey, false)
            putBoolean(showButtonPlayerDownloadKey, false)
            putBoolean(showButtonPlayerAddToPlaylistKey, false)
            putBoolean(showButtonPlayerLoopKey, false)
            putBoolean(showButtonPlayerShuffleKey, false)
            putBoolean(showButtonPlayerLyricsKey, false)
            putBoolean(expandedplayertoggleKey, false)
            putBoolean(showButtonPlayerSleepTimerKey, false)
            putBoolean(showButtonPlayerSystemEqualizerKey, false)
            putBoolean(showButtonPlayerArrowKey, true)
            putBoolean(showButtonPlayerShuffleKey, false)
            putBoolean(showButtonPlayerMenuKey, true)
            putBoolean(showthumbnailKey, true)
            putBoolean(keepPlayerMinimizedKey, false)
        } else {
            putBoolean(disablePlayerHorizontalSwipeKey, false)
            putBoolean(disableIconButtonOnTopKey, false)
            putEnum(playerTimelineTypeKey, preferences.getEnum(lastPlayerTimelineTypeKey, PlayerTimelineType.Default))
            putFloat(thumbnailSizeDpKey, preferences.getFloat(lastThumbnailSizeDpKey, 30f))
            putEnum(playerPlayButtonTypeKey, preferences.getEnum(lastPlayerPlayButtonTypeKey, PlayerPlayButtonType.Rectangular))
            putEnum(navigationBarPositionKey, NavigationBarPosition.Bottom)
        }
    }
}
