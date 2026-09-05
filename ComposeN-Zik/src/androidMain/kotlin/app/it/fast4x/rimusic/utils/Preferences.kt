package app.it.fast4x.rimusic.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.HomePage
import app.it.fast4x.rimusic.models.Song
import kotlinx.serialization.json.Json
import timber.log.Timber
import androidx.compose.runtime.DisposableEffect

const val showVisualizerStateKey = "showVisualizerStateKey"
const val showLyricsStateKey = "showLyricsStateKey"
const val saveVisualizerStateKey = "saveVisualizerStateKey"
const val saveLyricsStateKey = "saveLyricsStateKey"
const val filterContentTypeKey = "filterContentType"
const val lastPlayerThumbnailSizeKey = "lastPlayerThumbnailSize"
const val lastThumbnailSizeDpKey = "lastThumbnailSizeDp"
const val lastPlayerPlayButtonTypeKey = "lastPlayerPlayButtonType"
const val lastPlayerTimelineTypeKey = "lastPlayerTimelineType"
const val lastPlayerVisualizerTypeKey = "lastPlayerVisualizerType"
const val playerPlayButtonTypeKey = "playerPlayButtonType"
const val playerTimelineTypeKey = "playerTimelineType"
const val playerVisualizerTypeKey = "playerVisualizerType"
const val currentVisualizerKey = "currentVisualizer"
const val visualizerEnabledKey = "visualizerEnabled"
const val visualizerWhiteColorOptionKey = "visualizerWhiteColorOption"
const val visualizerCustomColorKey = "visualizerCustomColor"
const val showVisualizerButtonsKey = "showVisualizerButtons"
const val thumbnailTapEnabledKey = "thumbnailTapEnabled"
const val wavedPlayerTimelineKey = "wavedPlayerTimeline"
const val languageAppKey = "languageApp"
const val otherLanguageAppLyricsKey = "otherLanguageAppLyrics"
const val otherLanguageAppAlbumKey = "otherLanguageAppAlbum"
const val otherLanguageAppArtistKey = "otherLanguageAppArtist"
const val otherLanguageAppPlaylistKey = "otherLanguageAppPlaylist"
const val otherLanguageAppUpdateKey = "otherLanguageAppUpdate"
const val indexNavigationTabKey = "indexNavigationTab"
const val effectRotationKey = "effectRotation"
const val playerThumbnailSizeKey = "playerThumbnailSize"
const val playerThumbnailSizeLKey = "playerThumbnailSizeL"
const val playerTimelineSizeKey = "playerTimelineSize"
const val colorPaletteNameKey = "colorPaletteName"
const val colorPaletteModeKey = "colorPaletteMode"
const val thumbnailRoundnessKey = "thumbnailRoundness"
const val coilDiskCacheMaxSizeKey = "coilDiskCacheMaxSize"
const val exoPlayerDiskCacheMaxSizeKey = "exoPlayerDiskCacheMaxSize"
const val exoPlayerDiskDownloadCacheMaxSizeKey = "exoPlayerDiskDownloadCacheMaxSize"
const val exoPlayerMinTimeForEventKey = "exoPlayerMinTimeForEvent"
const val exoPlayerAlternateCacheLocationKey = "exoPlayerAlternateCacheLocation"
const val exoPlayerCacheLocationKey = "exoPlayerCacheLocationKey"
const val isInvincibilityEnabledKey = "isInvincibilityEnabled"
const val useSystemFontKey = "useSystemFont"
const val applyFontPaddingKey = "applyFontPadding"
const val songSortOrderKey = "songSortOrder"
const val songSortByKey = "songSortBy"
const val onDeviceSongSortByKey = "onDeviceSongSortBy"
const val onDeviceFolderSortByKey = "onDeviceFolderSortBy"
const val playlistSortOrderKey = "playlistSortOrder"
const val playlistSortByKey = "playlistSortBy"
const val albumSortOrderKey = "albumSortOrder"
const val albumSortByKey = "albumSortBy"
const val artistSortOrderKey = "artistSortOrder"
const val artistSortByKey = "artistSortBy"
const val queueLoopTypeKey = "queueLoopType"
const val reorderInQueueEnabledKey = "reorderInQueueEnabled"
const val skipSilenceKey = "skipSilence"
const val skipMediaOnErrorKey = "skipMediaOnError"
const val volumeNormalizationKey = "volumeNormalization"
const val resumePlaybackWhenDeviceConnectedKey = "resumePlaybackWhenDeviceConnected"
const val persistentQueueKey = "persistentQueue"
const val resumePlaybackOnStartKey = "resumePlaybackOnStart"
const val rememberQueueScrollPositionKey = "rememberQueueScrollPosition"
const val closebackgroundPlayerKey = "closebackgroundPlayer"
const val closeWithBackButtonKey = "closeWithBackButton"
const val isShowingSynchronizedLyricsKey = "isShowingSynchronizedLyrics"
const val lyricsTypeKey = "lyricsType"
const val isShowingThumbnailInLockscreenKey = "isShowingThumbnailInLockscreen"
const val homeScreenTabIndexKey = "homeScreenTabIndex"
const val searchResultScreenTabIndexKey = "searchResultScreenTabIndex"
const val artistScreenTabIndexKey = "artistScreenTabIndex"
const val pauseSearchHistoryKey = "pauseSearchHistory"
const val searchDisplayOrderKey = "searchDisplayOrder"
const val UiTypeKey = "UiType"
const val disablePlayerHorizontalSwipeKey = "disablePlayerHorizontalSwipe"
const val playerSwipeSensitivityKey = "playerSwipeSensitivity"
const val disableIconButtonOnTopKey = "disableIconButtonOnTop"
const val exoPlayerCustomCacheKey = "exoPlayerCustomCache"
const val coilCustomDiskCacheKey = "exoPlayerCustomCache"
const val disableScrollingTextKey = "disableScrollingText"
const val audioQualityFormatKey = "audioQualityFormat"
const val showLikeButtonBackgroundPlayerKey = "showLikeButtonBackgroundPlayer"
const val showDownloadButtonBackgroundPlayerKey = "showDownloadButtonBackgroundPlayer"
const val playEventsTypeKey = "playEventsType"
const val fontTypeKey = "fontType"
const val playlistSongSortByKey = "playlistSongSortBy"
const val showTipsKey = "showTips"
const val showChartsKey = "showCharts"
const val showRelatedAlbumsKey = "showRelatedAlbums"
const val showSimilarArtistsKey = "showSimilarArtists"
const val showNewAlbumsArtistsKey = "showNewAlbumsArtists"
const val showNewAlbumsKey = "showNewAlbums"
const val showPlaylistMightLikeKey = "showPlaylistMightLike"
const val showMoodsAndGenresKey = "showMoodsAndGenres"
const val showFreshFindsOldFavoritesKey = "showFreshFindsOldFavorites"
const val showMixedForYouKey = "showMixedForYou"
const val showForgottenFavoritesKey = "showForgottenFavorites"
const val showYourDailyDiscoverKey = "showYourDailyDiscover"
const val showFreshNewMusicKey = "showFreshNewMusic"
const val showNewReleasesKey = "showNewReleases"
const val showAlbumsForYouKey = "showAlbumsForYou"
const val showTodaysBiggestHitsKey = "showTodaysBiggestHits"
const val showAllHitsKey = "showAllHits"
const val showFeaturedPlaylistsKey = "showFeaturedPlaylists"
const val showTrendingCommunityPlaylistsKey = "showTrendingCommunityPlaylists"
const val showFromTheCommunityKey = "showFromTheCommunity"
const val showTrendingSongsForYouKey = "showTrendingSongsForYou"
const val showTopMusicVideosKey = "showTopMusicVideos"
const val showCoverAndRemixesKey = "showCoverAndRemixes"
const val showTrendingInShortsKey = "showTrendingInShorts"
const val showMusicVideosForYouKey = "showMusicVideosForYou"
const val showLivePerformancesKey = "showLivePerformances"
const val showMoodsKey = "showMoods"
const val showGenericYtmSectionsKey = "showGenericYtmSections"
const val quickPicksSectionOrderKey = "quickPicksSectionOrder"
const val libraryVisibilityOrderKey = "libraryVisibilityOrder"
const val streamClientsOrderKey = "streamClientsOrder"
const val homeTabsOrderKey = "homeTabsOrder"
const val maxStatisticsItemsKey = "maxStatisticsItems"
const val lastVersionCodeKey = "lastVersionCode"
const val lastBuildTypeKey = "lastBuildType"
const val showStatsListeningTimeKey = "showStatsListeningTime"
const val isProxyEnabledKey = "isProxyEnabled"
const val proxyHostnameKey = "proxyHostname"
const val proxyPortKey = "proxyPort"
const val proxyModeKey = "ProxyMode"
const val proxyUsernameKey = "proxyUsername"
const val proxyPasswordKey = "proxyPassword"
const val regionOverrideKey = "regionOverride"
const val useLoginForBrowseKey = "useLoginForBrowse"
const val isRecommendationEnabledKey = "isRecommendationEnabled"
const val showButtonPlayerAddToPlaylistKey = "showButtonPlayerAddToPlaylist"
const val showButtonPlayerArrowKey = "showButtonPlayerArrow"
const val showButtonPlayerDownloadKey = "showButtonPlayerDownload"
const val showButtonPlayerLoopKey = "showButtonPlayerLoop"
const val showButtonPlayerLyricsKey = "showButtonPlayerLyrics"
const val showButtonPlayerShuffleKey = "showButtonPlayerShuffle"
const val isKeepScreenOnEnabledKey = "isKeepScreenOnEnabled"
const val isEnabledDiscoveryLangCodeKey = "isEnabledDiscoveryLangCode"
const val recommendationsNumberKey = "recommendationsNumber"
const val checkUpdateStateKey = "checkUpdateState"
const val checkBetaUpdatesKey = "checkBetaUpdates"
const val lastUpdateCheckKey = "lastUpdateCheck"
const val updateCancelledKey = "updateCancelled"
const val seenChangelogsVersionKey = "seenChangelogsVersion"
const val showButtonPlayerSleepTimerKey = "showButtonPlayerSleepTimer"
const val keepPlayerMinimizedKey = "keepPlayerMinimized"
const val isSwipeToActionEnabledKey = "isSwipeToActionEnabled"
const val showButtonPlayerMenuKey = "showButtonPlayerMenu"
const val showButtonPlayerStartRadioKey = "showButtonPlayerStartRadio"
const val showButtonPlayerSystemEqualizerKey = "showButtonPlayerSystemEqualizer"
const val showButtonPlayerDiscoverKey = "showButtonPlayerDiscover"
const val showButtonPlayerVideoKey = "showButtonPlayerVideo"
const val playerActionBarButtonOrderKey = "playerActionBarButtonOrder"
const val disableClosingPlayerSwipingDownKey = "disableClosingPlayerSwipingDown"
const val showSearchTabKey = "showSearchTab"
const val MaxTopPlaylistItemsKey = "MaxTopPlaylistItems"
const val topPlaylistPeriodKey = "topPlaylistPeriod"
const val navigationBarPositionKey = "navigationBarPosition"
const val navigationBarTypeKey = "navigationBarType"
const val pauseBetweenSongsKey = "pauseBetweenSongs"
const val crossfadeEnabledKey = "crossfadeEnabled"
const val crossfadeDurationKey = "crossfadeDuration"
const val crossfadeGaplessKey = "crossfadeGapless"
const val showFavoritesPlaylistKey = "showFavoritesPlaylist"
const val showCachedPlaylistKey = "showCachedPlaylist"
const val showMyTopPlaylistKey = "showMyTopPlaylist"
const val showDownloadedPlaylistKey = "showDownloadedPlaylist"
const val showOnDevicePlaylistKey = "showOnDevicePlaylist"
const val showDislikedPlaylistKey = "showDislikedPlaylist"
const val showPlaylistsKey = "showPlaylists"
const val showYtPlaylistsKey = "showYtPlaylists"
const val showFavoritesArtistKey = "showFavoritesArtist"
const val showLibraryArtistKey = "showLibraryArtist"
const val showFavoritesAlbumKey = "showFavoritesAlbum"
const val showLibraryAlbumKey = "showLibraryAlbum"
const val homeSongsOrderKey = "homeSongsOrder"
const val homeArtistsOrderKey = "homeArtistsOrder"
const val homeAlbumsOrderKey = "homeAlbumsOrder"
const val homePlaylistsOrderKey = "homePlaylistsOrder"
const val homeSongsToolbarOrderKey = "homeSongsToolbarOrder"
const val homeSongsFavoritesToolbarOrderKey = "homeSongsFavoritesToolbarOrder"
const val homeSongsOfflineToolbarOrderKey = "homeSongsOfflineToolbarOrder"
const val homeSongsDownloadedToolbarOrderKey = "homeSongsDownloadedToolbarOrder"
const val homeSongsTopToolbarOrderKey = "homeSongsTopToolbarOrder"
const val homeSongsOnDeviceToolbarOrderKey = "homeSongsOnDeviceToolbarOrder"
const val homeSongsDislikedToolbarOrderKey = "homeSongsDislikedToolbarOrder"
const val homeArtistsToolbarOrderKey = "homeArtistsToolbarOrder"
const val homeArtistsFavoritesToolbarOrderKey = "homeArtistsFavoritesToolbarOrder"
const val homeArtistsLibraryToolbarOrderKey = "homeArtistsLibraryToolbarOrder"
const val homeAlbumsToolbarOrderKey = "homeAlbumsToolbarOrder"
const val homeAlbumsFavoritesToolbarOrderKey = "homeAlbumsFavoritesToolbarOrder"
const val homeAlbumsLibraryToolbarOrderKey = "homeAlbumsLibraryToolbarOrder"
const val homeLibraryToolbarOrderKey = "homeLibraryToolbarOrder"
const val homeLibraryYTPlaylistToolbarOrderKey = "homeLibraryYTPlaylistToolbarOrder"
const val homeLibraryPinnedPlaylistToolbarOrderKey = "homeLibraryPinnedPlaylistToolbarOrder"
const val homeLibraryMonthlyPlaylistToolbarOrderKey = "homeLibraryMonthlyPlaylistToolbarOrder"

const val homeSongsAllSortMenuOrderKey = "homeSongsAllSortMenuOrder"
const val homeSongsFavoritesSortMenuOrderKey = "homeSongsFavoritesSortMenuOrder"
const val homeSongsCachedSortMenuOrderKey = "homeSongsCachedSortMenuOrder"
const val homeSongsDownloadedSortMenuOrderKey = "homeSongsDownloadedSortMenuOrder"
const val homeSongsTopSortMenuOrderKey = "homeSongsTopSortMenuOrder"
const val homeSongsOnDeviceSortMenuOrderKey = "homeSongsOnDeviceSortMenuOrder"
const val homeSongsDislikedSortMenuOrderKey = "homeSongsDislikedSortMenuOrder"
const val homeAlbumsLibrarySortMenuOrderKey = "homeAlbumsLibrarySortMenuOrder"
const val homeAlbumsFavoritesSortMenuOrderKey = "homeAlbumsFavoritesSortMenuOrder"
const val homeArtistsLibrarySortMenuOrderKey = "homeArtistsLibrarySortMenuOrder"
const val homeArtistsFavoritesSortMenuOrderKey = "homeArtistsFavoritesSortMenuOrder"
const val homeLibraryPlaylistSortMenuOrderKey = "homeLibraryPlaylistSortMenuOrder"
const val homeLibraryYTPlaylistSortMenuOrderKey = "homeLibraryYTPlaylistSortMenuOrder"
const val homeLibraryPinnedPlaylistSortMenuOrderKey = "homeLibraryPinnedPlaylistSortMenuOrder"
const val homeLibraryMonthlyPlaylistSortMenuOrderKey = "homeLibraryMonthlyPlaylistSortMenuOrder"
const val historySortMenuOrderKey = "historySortMenuOrder"
const val localPlaylistSortMenuOrderKey = "localPlaylistSortMenuOrder"
const val localPlaylistToolbarOrderKey = "localPlaylistToolbarOrder"
const val isGradientBackgroundEnabledKey = "isGradientBackgroundEnabled"
const val playbackSpeedKey = "playbackSpeed"
const val playbackPitchKey = "playbackPitch"
const val playbackVolumeKey = "playbackVolume"
const val playbackDeviceVolumeKey = "playbackDeviceVolume"
const val playbackDurationKey = "playbackDuration"
const val blurStrengthKey = "blurScale"
//const val blurStrength2Key = "blurScale"
const val blurDarkenFactorKey = "blurDarkenFactor"
const val playerBackdropKey = "playerBackdrop"
const val playbackFadeAudioDurationKey = "playbackFadeAudioDuration"
const val showTotalTimeQueueKey = "showTotalTimeQueue"
const val backgroundProgressKey = "backgroundProgress"
const val maxSongsInQueueKey = "maxSongsInQueue"
const val maxSongsInQueueAndroidAutoKey = "maxSongsInQueueAndroidAuto"
const val showFoldersOnDeviceKey = "showFoldersOnDevice"
const val showNextSongsInPlayerKey = "showNextSongsInPlayer"
const val showRemainingSongTimeKey = "showRemainingSongTime"
const val showSkipTimeButtonsKey = "showSkipTimeButtons"
const val lyricsFontSizeKey = "lyricsFontSize"
const val showBackgroundLyricsKey = "showBackgroundLyrics"
const val karaokeRespectAgentPositionKey = "karaokeRespectAgentPosition"
const val includeLocalSongsKey = "includeLocalSongs"
const val clickOnLyricsTextKey = "clickOnLyricsText"
const val defaultFolderKey = "defaultFolder"
const val menuStyleKey = "menuStyle"
const val shakeSensitivityThemeKey = "shakeSensitivityTheme"
const val useVolumeKeysToChangeSongKey = "useVolumeKeysToChangeSong"
const val showStatsInNavbarKey = "showStatsInNavbar"
const val showActionsBarKey = "showActionsBar"
const val floatActionIconOffsetXkey = "floatActionIconOffsetX"
const val floatActionIconOffsetYkey = "floatActionIconOffsetY"
const val multiFloatActionIconOffsetXkey = "multiFloatActionIconOffsetX"
const val multiFloatActionIconOffsetYkey = "multiFloatActionIconOffsetY"
const val showBuiltinPlaylistsKey = "showBuiltinPlaylists"
const val showPinnedPlaylistsKey = "showPinnedPlaylists"
const val showPlaylistsListKey = "showPlaylistsList"
const val showPlaylistsGeneralKey = "showPlaylistsGeneral"
const val libraryItemSizeKey = "libraryItemSize"
const val albumsItemSizeKey = "albumsItemSize"
const val artistsItemSizeKey = "artistsItemSize"
const val showFloatingIconKey = "showFloatingIcon"
const val transitionEffectKey = "transitionEffect"
const val disableNavigationBackStackKey = "disableNavigationBackStack"
const val showMonthlyPlaylistsKey = "showMonthlyPlaylists"

const val showMonthlyPlaylistInQuickPicksKey = "showMonthlyPlaylistInQuickPicks"
const val showMonthlyPlaylistInLibraryKey = "showMonthlyPlaylistInLibrary"
const val enableQuickPicksPageKey = "enableQuickPicksPage"
const val playerBackgroundColorsKey = "playerBackgroundColors"
const val playerControlsColorsKey = "playerControlsColors"
const val animatedGradientKey = "animatedGradient"
const val rotatingAlbumCoverKey = "rotatingAlbumCover"
const val playerControlsTypeKey = "playerControlsType"
const val playerInfoTypeKey = "playerInfoType"
const val showTopActionsBarKey = "showTopActionsBar"
const val transparentBackgroundPlayerActionBarKey = "transparentBackgroundPlayerActionBar"
const val enableCreateMonthlyPlaylistsKey = "enableCreateMonthlyPlaylists"
const val autoShuffleKey = "autoShuffle"
const val builtInPlaylistKey = "builtInPlaylist"
const val playlistTypeKey = "playlistType"
const val iconLikeTypeKey = "iconLikeType"
const val playerSwapControlsWithTimelineKey = "playerSwapControlsWithTimeline"
const val playerEnableLyricsPopupMessageKey = "playerEnableLyricsPopupMessage"
const val historyTypeKey = "historyType"
const val historySortOrderKey = "historySortOrder"
/**** CUSTOM THEME **** */
const val customThemeLight_Background0Key = "customThemeLight_Background0"
const val customThemeLight_Background1Key = "customThemeLight_Background1"
const val customThemeLight_Background2Key = "customThemeLight_Background2"
const val customThemeLight_Background3Key = "customThemeLight_Background3"
const val customThemeLight_Background4Key = "customThemeLight_Background4"
const val customThemeLight_TextKey = "customThemeLight_Text"
const val customThemeLight_textSecondaryKey = "customThemeLight_textSecondary"
const val customThemeLight_textDisabledKey = "customThemeLight_textDisabled"
const val customThemeLight_iconButtonPlayerKey = "customThemeLight_iconButtonPlayer"
const val customThemeLight_accentKey = "customThemeLight_accent"

const val customThemeDark_Background0Key = "customThemeDark_Background0"
const val customThemeDark_Background1Key = "customThemeDark_Background1"
const val customThemeDark_Background2Key = "customThemeDark_Background2"
const val customThemeDark_Background3Key = "customThemeDark_Background3"
const val customThemeDark_Background4Key = "customThemeDark_Background4"
const val customThemeDark_TextKey = "customThemeDark_Text"
const val customThemeDark_textSecondaryKey = "customThemeDark_textSecondary"
const val customThemeDark_textDisabledKey = "customThemeDark_textDisabled"
const val customThemeDark_iconButtonPlayerKey = "customThemeDark_iconButtonPlayer"
const val customThemeDark_accentKey = "customThemeDark_accent"
/**** CUSTOM THEME **** */

const val showthumbnailKey = "showthumbnail"
const val showlyricsthumbnailKey = "showlyricsthumbnail"
const val miniPlayerTypeKey = "miniPlayerType"
const val miniPlayerSlot1Key = "miniPlayerSlot1"
const val miniPlayerSlot2Key = "miniPlayerSlot2"
const val miniPlayerSlot3Key = "miniPlayerSlot3"
const val miniPlayerSlot4Key = "miniPlayerSlot4"
const val miniPlayerButtonOrderKey = "miniPlayerButtonOrder"
const val showMiniPlayerPlayPauseKey = "showMiniPlayerPlayPause"
const val showMiniPlayerSkipBackKey = "showMiniPlayerSkipBack"
const val showMiniPlayerSkipForwardKey = "showMiniPlayerSkipForward"
const val showMiniPlayerShuffleKey = "showMiniPlayerShuffle"
const val showMiniPlayerRepeatKey = "showMiniPlayerRepeat"
const val showMiniPlayerLikeKey = "showMiniPlayerLike"
const val showMiniPlayerAddToPlaylistKey = "showMiniPlayerAddToPlaylist"
const val showMiniPlayerDownloadKey = "showMiniPlayerDownload"
const val showMiniPlayerShareKey = "showMiniPlayerShare"
const val showMiniPlayerRadioKey = "showMiniPlayerRadio"
const val showMiniPlayerAudioOutputKey = "showMiniPlayerAudioOutput"
const val showMiniPlayerSleepTimerKey = "showMiniPlayerSleepTimer"
const val showMiniPlayerLyricsKey = "showMiniPlayerLyrics"
const val showMiniPlayerVisualizerKey = "showMiniPlayerVisualizer"
const val showMiniPlayerQueueKey = "showMiniPlayerQueue"
const val showMiniPlayerExpandPlayerKey = "showMiniPlayerExpandPlayer"
const val showMiniPlayerVideoKey = "showMiniPlayerVideo"
const val showMiniPlayerDiscoverKey = "showMiniPlayerDiscover"
const val lyricsColorKey = "lyricsColor"
const val lyricsCustomColorKey = "lyricsCustomColor"
const val lyricsOutlineKey = "lyricsOutline"
const val transparentbarKey = "transparentbar"
const val isShowingLyricsKey = "isShowingLyrics"
const val actionspacedevenlyKey = "actionspacedevenly"
const val expandedplayerKey = "expandedplayer"
const val expandedplayertoggleKey = "expandedplayertoggle"
const val blackgradientKey = "blackgradient"
const val bottomgradientKey = "bottomgradient"
const val textoutlineKey = "textoutline"
const val thumbnailTypeKey = "thumbnailType"
const val showvisthumbnailKey = "showvisthumbnail"
const val blackBackgroundForVisThumbnailKey = "blackBackgroundForVisThumbnail"
const val extraspaceKey = "extraspace"
const val expandedlyricsKey = "expandedlyrics"
const val lyricsHighlightKey = "lyricsHighlight"
const val buttonStateKey = "buttonState"
const val buttonzoomoutKey = "buttonzoomout"
const val thumbnailpauseKey = "thumbnailpause"
const val cropVideoThumbnailsKey = "cropVideoThumbnails"
const val showsongsKey = "showsongs"
const val showalbumcoverKey = "showalbumcover"
const val lyricsBackgroundKey = "lyricsBackground"
const val lyricsAlignmentKey = "lyricsAlignment"
const val hideprevnextKey = "hideprevnext"
const val prevNextSongsKey = "prevNextSongs"
const val tapqueueKey = "tapqueue"
const val swipeUpQueueKey = "swipeUpQueue"
const val playlistindicatorKey = "playlistindicator"
const val nowPlayingIndicatorKey = "nowPlayingIndicator"
const val statsfornerdsKey = "statsfornerds"
const val statsfornerdsfullKey = "statsfornerdsfull"
const val discoverKey = "discover"
const val playerTypeKey = "playerType"
const val noblurKey = "noblur"
const val fadingedgeKey = "fadingedge"
const val thumbnailOffsetKey = "thumbnailOffset"
const val thumbnailFadeKey = "thumbnailFade"
const val thumbnailFadeExKey = "thumbnailFadeEx"
const val carouselKey = "carousel"
const val carouselSizeKey = "carouselSize"
const val thumbnailSpacingKey = "thumbnailSpacing"
const val thumbnailSpacingLKey = "thumbnailSpacingL"
const val autosyncKey = "autosync"
const val autosyncArtistsKey = "autosyncArtists"
const val autosyncAlbumsKey = "autosyncAlbums"
const val autosyncPlaylistsKey = "autosyncPlaylists"
const val autosyncLikesKey = "autosyncLikes"
const val syncDirectionKey = "syncDirection"
const val syncPushSongLikeKey = "syncPushSongLike"
const val syncPushAlbumBookmarkKey = "syncPushAlbumBookmark"
const val syncPushArtistFollowKey = "syncPushArtistFollow"
const val syncPushPlaylistKey = "syncPushPlaylist"
const val syncImportHistoryKey = "syncImportHistory"
const val syncPushHistoryKey = "syncPushHistory"
const val syncImportLibrarySongsKey = "syncImportLibrarySongs"
const val syncImportUploadedSongsKey = "syncImportUploadedSongs"
const val syncImportUploadedAlbumsKey = "syncImportUploadedAlbums"
const val syncImportEpisodesKey = "syncImportEpisodes"
const val syncPushEpisodeKey = "syncPushEpisode"
const val syncCooldownKey = "syncCooldown"
const val syncShowDetailsKey = "syncShowDetails"
const val syncBackgroundGuardKey = "syncBackgroundGuard"
const val queueTypeKey = "queueType"


const val parentalControlEnabledKey = "parentalControlEnabled"
const val disabledStreamClientsKey = "disabledStreamClients"
const val streamClientWebRemixEnabledKey = "stream_client_web_remix"
const val streamClientVisionosEnabledKey = "stream_client_visionos"
const val streamClientTvEmbeddedEnabledKey = "stream_client_tv_embedded"
const val streamClientTvSimplyEnabledKey = "stream_client_tv_simply"
const val streamClientTvHtml5EnabledKey = "stream_client_tv_html5"
const val streamClientAndroidVrEnabledKey = "stream_client_android_vr"
const val streamClientAndroidCreatorEnabledKey = "stream_client_android_creator"
const val streamClientIosEnabledKey = "stream_client_ios"
const val streamClientIpadosEnabledKey = "stream_client_ipados"
const val streamClientWebEnabledKey = "stream_client_web"
const val streamClientWebCreatorEnabledKey = "stream_client_web_creator"
const val streamClientMobileEnabledKey = "stream_client_mobile"
const val streamClientAndroidEnabledKey = "stream_client_android"
const val preferredStreamClientKey = "preferred_stream_client"
const val streamClientRestartNeededKey = "stream_client_restart_needed"
const val playerPositionKey = "playerPosition"
const val excludeSongsWithDurationLimitKey = "excludeSongsWithDurationLimit"
const val logDebugEnabledKey = "logDebugEnabled"


const val messageTypeKey = "messageType"
const val isPauseOnVolumeZeroEnabledKey = "isPauseOnVolumeZeroEnabled"
const val pauseOnHeadphoneDisconnectKey = "pauseOnHeadphoneDisconnect"
const val playerInfoShowIconsKey = "playerInfoShowIcons"
const val minimumSilenceDurationKey = "minimumSilenceDuration"
const val pauseListenHistoryKey = "pauseListenHistory"
const val selectedCountryCodeKey = "selectedCountryCode"

const val lastVideoIdKey = "lastVideoId"
const val lastVideoSecondsKey = "lastVideoSeconds"
const val loudnessBaseGainKey = "loudnessBaseGain"
const val volumeBoostLevelKey = "volumeBoostLevel"
const val statisticsCategoryKey = "statisticsCategory"
const val queueDurationExpandedKey = "queueDurationExpanded"
const val titleExpandedKey = "titleExpanded"
const val timelineExpandedKey = "timelineExpanded"
const val controlsExpandedKey = "controlsExpanded"
const val miniQueueExpandedKey = "miniQueueExpanded"
const val statsExpandedKey = "statsExpanded"
const val actionExpandedKey = "actionExpanded"
//const val showVinylThumbnailAnimationKey = "showVinylThumbnailAnimation"
const val showCoverThumbnailAnimationKey = "showCoverThumbnailAnimation"
const val coverThumbnailAnimationKey = "coverThumbnailAnimation"

const val restartActivityKey = "restartActivity"


const val autoLoadSongsInQueueKey = "autoLoadSongsInQueue"
const val showSecondLineKey = "showSecondLine"
const val VinylSizeKey = "VinylSize"
const val romanizationEnabledKey = "romanization_enabled"
const val lyricsIntervalIndicatorKey = "lyrics_interval_indicator"

const val quickPicsTrendingSongKey = "quickPicsTrendingSong"
const val quickPicsRelatedPageKey = "quickPicsRelatedPage"
const val quickPicsChartsPageKey = "quickPicsChartsPage"
const val quickPicsDiscoverPageKey = "quickPicsDiscoverPage"
const val quickPicsHomePageKey = "quickPicsHomePage"
const val quickPicsYtmQuickPicksKey = "quickPicsYtmQuickPicks"
const val loadedDataKey = "loadedData"

const val enablePictureInPictureKey = "enablePicturInPicture"
const val enablePictureInPictureAutoKey = "enablePicturInPictureAuto"
const val pipModuleKey = "pipModule"

const val notificationPlayerFirstIconKey = "notificationPlayerFirstIcon"
const val notificationPlayerSecondIconKey = "notificationPlayerSecondIcon"
const val jumpPreviousKey = "jumpPrevious"

const val artistTypeKey = "artistType"
const val albumTypeKey = "albumType"

const val enableWallpaperKey = "enableWallpaper"
const val wallpaperTypeKey = "wallpaperType"

const val notificationTypeKey = "notificationType"
const val topPaddingKey = "topPadding"

const val autoDownloadSongKey = "autoDownloadSong"
const val autoDownloadSongWhenLikedKey = "autoDownloadSongWhenLiked"
const val autoDownloadSongWhenAlbumBookmarkedKey = "autoDownloadSongWhenAlbumBookmarked"

const val queueSwipeLeftActionKey = "queueSwipeLeftAction"
const val queueSwipeRightActionKey = "queueSwipeRightAction"
const val playlistSwipeLeftActionKey = "playlistSwipeLeftAction"
const val playlistSwipeRightActionKey = "playlistSwipeRightAction"
const val albumSwipeLeftActionKey = "albumSwipeLeftAction"
const val albumSwipeRightActionKey = "albumSwipeRightAction"
const val customColorKey = "customColor"
const val lyricsSizeAnimateKey = "lyricsSizeAnimate"
const val lyricsSizeKey = "lyricsSize"
const val lyricsSizeLKey = "lyricsSizeL"
const val albumCoverRotationKey = "albumCoverRotation"
const val isConnectionMeteredEnabledKey = "isConnectionMeteredEnabled"
const val imageQualityFormatKey = "imageQualityFormat"
const val landscapeControlsKey = "landscapeControls"
const val swipeAnimationsNoThumbnailKey = "swipeAnimationsNoThumbnail"
const val playlistSongsTypeFilterKey = "playlistSongsTypeFilter"

const val filterByKey = "filterBy"

const val bassboostEnabledKey = "bassboostEnabled"
const val bassboostLevelKey = "bassboostLevel"
const val audioReverbPresetKey = "audioReverbPreset"
const val handleAudioFocusEnabledKey = "handleAudioFocusEnabled"


const val thumbnailSizeDpKey = "thumbnailSizeDpKey"
const val thumbnailSizeLDpKey = "thumbnailSizeLDpKey"
const val thumbnailRoundnessDpKey = "thumbnailRoundnessDpKey"
const val artistThumbnailRoundnessDpKey = "artistThumbnailRoundnessDpKey"
const val uiRoundnessDpKey = "uiRoundnessDpKey"
const val hideStatusBarKey = "hideStatusBarKey"

const val visualizerLineThicknessKey = "visualizerLineThickness"

inline fun <reified T : Enum<T>> SharedPreferences.getEnum(
    key: String,
    defaultValue: T
): T =
    getString(key, null)?.let {
        try {
            enumValueOf<T>(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    } ?: defaultValue

inline fun <reified T : Enum<T>> SharedPreferences.Editor.putEnum(
    key: String,
    value: T
): SharedPreferences.Editor =
    putString(key, value.name)

val Context.preferences: SharedPreferences
    get() = getSharedPreferences("preferences", Context.MODE_PRIVATE)


@Composable
fun rememberPreference(key: String, defaultValue: List<Song>): MutableState<List<Song>> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                val raw = context.preferences.getString(key, json)
                if (raw == null || raw == "null") {
                    emptyList()
                } else {
                    Json.decodeFromString<List<Song>>(raw)
                }
            } catch (e: Exception) {
                Timber.tag("Preferences").e("RememberPreference List<Song> Error: ${ e.stackTraceToString() }")
                emptyList()
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Song?): MutableState<Song?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                val raw = context.preferences.getString(key, json)
                if (raw == null || raw == "null") {
                    null
                } else {
                    Json.decodeFromString<Song>(raw)
                }
            } catch (e: Exception) {
                Timber.tag("Preferences").e("RememberPreference Song Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Innertube.DiscoverPage?): MutableState<Innertube.DiscoverPage?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                val raw = context.preferences.getString(key, json)
                if (raw == null || raw == "null") {
                    null
                } else {
                    Json.decodeFromString<Innertube.DiscoverPage>(raw)
                }
            } catch (e: Exception) {
                Timber.tag("Preferences").e("RememberPreference DiscoverPage Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Innertube.ChartsPage?): MutableState<Innertube.ChartsPage?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                val raw = context.preferences.getString(key, json)
                if (raw == null || raw == "null") {
                    null
                } else {
                    Json.decodeFromString<Innertube.ChartsPage>(raw)
                }
            } catch (e: Exception) {
                Timber.tag("Preferences").e("RememberPreference ChartsPage Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Innertube.RelatedPage?): MutableState<Innertube.RelatedPage?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                val raw = context.preferences.getString(key, json)
                if (raw == null || raw == "null") {
                    null
                } else {
                    Json.decodeFromString<Innertube.RelatedPage>(raw)
                }
            } catch (e: Exception) {
                Timber.tag("Preferences").e("RememberPreference RelatedPage Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: HomePage?): MutableState<HomePage?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                val raw = context.preferences.getString(key, json)
                if (raw == null || raw == "null") {
                    null
                } else {
                    Json.decodeFromString<HomePage>(raw)
                }
            } catch (e: Exception) {
                Timber.tag("Preferences").e("RememberPreference HomePage Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val prefs = context.preferences
    val state = remember {
        mutableStatePreferenceOf(prefs.getBoolean(key, defaultValue)) {
            prefs.edit { putBoolean(key, it) }
        }
    }
    
    val listener = remember(prefs, key) {
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                val newValue = sharedPreferences.getBoolean(key, defaultValue)
                if (state.value != newValue) {
                    state.value = newValue
                }
            }
        }
    }
    
    DisposableEffect(prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}

@Composable
fun rememberPreference(key: String, defaultValue: Int): MutableState<Int> {
    val context = LocalContext.current
    val prefs = context.preferences
    val state = remember {
        mutableStatePreferenceOf(prefs.getInt(key, defaultValue)) {
            prefs.edit { putInt(key, it) }
        }
    }
    
    val listener = remember(prefs, key) {
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                val newValue = sharedPreferences.getInt(key, defaultValue)
                if (state.value != newValue) {
                    state.value = newValue
                }
            }
        }
    }
    
    DisposableEffect(prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}



@Composable
fun rememberPreference(key: String, defaultValue: Float): MutableState<Float> {
    val context = LocalContext.current
    val prefs = context.preferences
    val state = remember {
        mutableStatePreferenceOf(prefs.getFloat(key, defaultValue)) {
            prefs.edit { putFloat(key, it) }
        }
    }
    
    val listener = remember(prefs, key) {
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                val newValue = sharedPreferences.getFloat(key, defaultValue)
                if (state.value != newValue) {
                    state.value = newValue
                }
            }
        }
    }
    
    DisposableEffect(prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}

@Composable
fun rememberPreference(key: String, defaultValue: Long): MutableState<Long> {
    val context = LocalContext.current
    val prefs = context.preferences
    val state = remember {
        mutableStatePreferenceOf(prefs.getLong(key, defaultValue)) {
            prefs.edit { putLong(key, it) }
        }
    }
    
    val listener = remember(prefs, key) {
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                val newValue = sharedPreferences.getLong(key, defaultValue)
                if (state.value != newValue) {
                    state.value = newValue
                }
            }
        }
    }
    
    DisposableEffect(prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}

@Composable
fun rememberPreference(key: String, defaultValue: String): MutableState<String> {
    val context = LocalContext.current
    val prefs = context.preferences
    val state = remember {
        mutableStatePreferenceOf(prefs.getString(key, null) ?: defaultValue) {
            prefs.edit { putString(key, it) }
        }
    }
    
    val listener = remember(prefs, key) {
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                val newValue = sharedPreferences.getString(key, null) ?: defaultValue
                if (state.value != newValue) {
                    state.value = newValue
                }
            }
        }
    }
    
    DisposableEffect(prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}

@Composable
inline fun <reified T : Enum<T>> rememberPreference(key: String, defaultValue: T): MutableState<T> {
    val context = LocalContext.current
    val prefs = context.preferences
    val state = remember {
        mutableStatePreferenceOf(prefs.getEnum(key, defaultValue)) {
            prefs.edit { putEnum(key, it) }
        }
    }

    val listener = remember(prefs, key) {
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                val newValue = sharedPreferences.getEnum(key, defaultValue)
                if (state.value != newValue) {
                    state.value = newValue
                }
            }
        }
    }
    
    DisposableEffect(prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}

fun clearPreference(context: Context, key: String): Unit {
    try {
        context.preferences.edit { remove(key) }
    } catch (e: Exception) {
        Timber.tag("Preferences").e("ClearPreference Error: ${e.stackTraceToString()}")
    }
}


inline fun <T> mutableStatePreferenceOf(
    value: T,
    crossinline onStructuralInequality: (newValue: T) -> Unit
) =
    mutableStateOf(
        value = value,
        policy = object : SnapshotMutationPolicy<T> {
            override fun equivalent(a: T, b: T): Boolean {
                val areEquals = a == b
                if (!areEquals) onStructuralInequality(b)
                return areEquals
            }
        })



