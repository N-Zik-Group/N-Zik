package app.n_zik.android.components.settings.appearance.preview

import app.n_zik.android.utils.effectRotationDefault
import app.n_zik.android.utils.showButtonPlayerVideoDefault
import app.n_zik.android.components.dialog.settings.defaultHomeTabsOrder
import app.n_zik.android.components.dialog.settings.playerActionBarThemeIds
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.glassAmountKey
import app.n_zik.android.components.glass.glassBlurKey
import app.n_zik.android.components.glass.glassCustomColorKey
import app.n_zik.android.components.glass.glassDepthEffectKey
import app.n_zik.android.components.glass.glassDepthKey
import app.n_zik.android.components.glass.glassDialogDimmingKey
import app.n_zik.android.components.glass.glassDispersionKey
import app.n_zik.android.components.glass.glassEnabledDefault
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassIndicatorOpacityKey
import app.n_zik.android.components.glass.glassJellyEnabledKey
import app.n_zik.android.components.glass.glassJellyIntensityKey
import app.n_zik.android.components.glass.glassMenuDimmingKey
import app.n_zik.android.components.glass.glassOptimizationEnabledKey
import app.n_zik.android.components.glass.glassProfileDefaults
import app.n_zik.android.components.glass.glassQualityKey
import app.n_zik.android.components.glass.glassRimKey
import app.n_zik.android.components.glass.glassRippleEnabledKey
import app.n_zik.android.components.glass.glassRippleIntensityKey
import app.n_zik.android.components.glass.glassRippleSizeKey
import app.n_zik.android.components.glass.glassSettingsFromPreferences
import app.n_zik.android.components.glass.glassTintSourceKey
import app.n_zik.android.components.glass.glassTintStrengthKey
import app.n_zik.android.components.glass.glassVibrancyKey
import app.n_zik.android.components.glass.mode
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.core.settings.DEFAULT_THUMBNAIL_SIZE_DP
import app.n_zik.android.enums.AlbumSwipeAction
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.enums.BackgroundProgress
import app.n_zik.android.enums.CarouselSize
import app.n_zik.android.core.settings.DefaultColorPaletteMode
import app.n_zik.android.enums.PlayerOpeningMode
import app.n_zik.android.utils.DEFAULT_HOLD_TO_SPEED_UP_RATE
import app.n_zik.android.utils.holdToSpeedUpKey
import app.n_zik.android.utils.holdToSpeedUpRateKey
import app.n_zik.android.utils.playerOpeningModeKey
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.FontType
import app.n_zik.android.enums.HomeScreenTabs
import app.n_zik.android.enums.IconLikeType
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.MiniPlayerButton
import app.n_zik.android.enums.MiniPlayerType
import app.n_zik.android.enums.MusicAnimationType
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.NavigationBarType
import app.n_zik.android.enums.NotificationButtons
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerControlsColors
import app.n_zik.android.enums.PlayerControlsType
import app.n_zik.android.enums.PlayerInfoType
import app.n_zik.android.enums.PlayerPlayButtonType
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.enums.PlayerSwipeSensitivity
import app.n_zik.android.enums.PlayerTimelineSize
import app.n_zik.android.enums.PlayerTimelineType
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.enums.PlayerVisualizerType
import app.n_zik.android.enums.PlaylistSwipeAction
import app.n_zik.android.enums.QueueSwipeAction
import app.n_zik.android.enums.QueueType
import app.n_zik.android.enums.SearchDisplayOrder
import app.n_zik.android.enums.ShakeSensitivityTheme
import app.n_zik.android.enums.SongsNumber
import app.n_zik.android.enums.SwipeAnimationNoThumbnail
import app.n_zik.android.enums.ThumbnailCoverType
import app.n_zik.android.enums.ThumbnailType
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.enums.lyrics.LyricsAlignment
import app.n_zik.android.enums.lyrics.LyricsBackground
import app.n_zik.android.enums.lyrics.LyricsColor
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsHighlight
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.utils.VinylSizeKey
import app.n_zik.android.utils.actionExpandedKey
import app.n_zik.android.utils.actionspacedevenlyKey
import app.n_zik.android.utils.albumCoverRotationKey
import app.n_zik.android.utils.albumSwipeLeftActionKey
import app.n_zik.android.utils.albumSwipeRightActionKey
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.m3eAlwaysShowShapesKey
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.utils.backgroundProgressKey
import app.n_zik.android.utils.blackBackgroundForVisThumbnailKey
import app.n_zik.android.utils.defaultLyricsCoverDimmingOpacity
import app.n_zik.android.utils.defaultVisualizerCoverDimmingOpacity
import app.n_zik.android.utils.lyricsCoverDimmingEnabledKey
import app.n_zik.android.utils.lyricsCoverDimmingOpacityKey
import app.n_zik.android.utils.visualizerCoverDimmingOpacityKey
import app.n_zik.android.utils.blackgradientKey
import app.n_zik.android.utils.blurStrengthKey
import app.n_zik.android.utils.bottomgradientKey
import app.n_zik.android.utils.buttonzoomoutKey
import app.n_zik.android.utils.carouselKey
import app.n_zik.android.utils.carouselSizeKey
import app.n_zik.android.utils.clickOnLyricsTextKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.controlsExpandedKey
import app.n_zik.android.utils.coverThumbnailAnimationKey
import app.n_zik.android.utils.cropVideoThumbnailsKey
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
import app.n_zik.android.utils.disableNavigationBackStackKey
import app.n_zik.android.utils.disablePlayerHorizontalSwipeKey
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.effectRotationKey
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.expandedplayerKey
import app.n_zik.android.utils.expandedplayertoggleKey
import app.n_zik.android.utils.fadingedgeKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.homeTabsOrderKey
import app.n_zik.android.utils.iconLikeTypeKey
import app.n_zik.android.utils.indexNavigationTabKey
import app.n_zik.android.utils.isSwipeToActionEnabledKey
import app.n_zik.android.utils.karaokeRespectAgentPositionKey
import app.n_zik.android.utils.landscapeControlsKey
import app.n_zik.android.utils.lyricsAlignmentKey
import app.n_zik.android.utils.lyricsBackgroundKey
import app.n_zik.android.utils.lyricsColorKey
import app.n_zik.android.utils.lyricsCustomColorKey
import app.n_zik.android.utils.lyricsFontSizeKey
import app.n_zik.android.utils.lyricsHighlightKey
import app.n_zik.android.utils.lyricsIntervalIndicatorKey
import app.n_zik.android.utils.lyricsOutlineKey
import app.n_zik.android.utils.lyricsSizeAnimateKey
import app.n_zik.android.utils.lyricsSizeKey
import app.n_zik.android.utils.lyricsSizeLKey
import app.n_zik.android.utils.lyricsTypeKey
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.miniPlayerButtonOrderKey
import app.n_zik.android.utils.miniPlayerTypeKey
import app.n_zik.android.utils.miniQueueExpandedKey
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.navigationBarTypeKey
import app.n_zik.android.utils.noblurKey
import app.n_zik.android.utils.notificationPlayerFirstIconKey
import app.n_zik.android.utils.notificationPlayerSecondIconKey
import app.n_zik.android.utils.playerActionBarButtonOrderKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.playerControlsColorsKey
import app.n_zik.android.utils.playerControlsTypeKey
import app.n_zik.android.utils.playerEnableLyricsPopupMessageKey
import app.n_zik.android.utils.playerInfoShowIconsKey
import app.n_zik.android.utils.playerInfoTypeKey
import app.n_zik.android.utils.playerPlayButtonTypeKey
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.utils.playerSwapControlsWithTimelineKey
import app.n_zik.android.utils.playerSwipeSensitivityKey
import app.n_zik.android.utils.playerTimelineSizeKey
import app.n_zik.android.utils.playerTimelineTypeKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.playerVisualizerTypeKey
import app.n_zik.android.utils.playlistSwipeLeftActionKey
import app.n_zik.android.utils.playlistSwipeRightActionKey
import app.n_zik.android.utils.queueDurationExpandedKey
import app.n_zik.android.utils.queueSwipeLeftActionKey
import app.n_zik.android.utils.queueSwipeRightActionKey
import app.n_zik.android.utils.queueTypeKey
import app.n_zik.android.utils.rememberQueueScrollPositionKey
import app.n_zik.android.utils.rightToLeftInterfaceKey
import app.n_zik.android.utils.romanizationEnabledKey
import app.n_zik.android.utils.rotatingAlbumCoverKey
import app.n_zik.android.utils.searchDisplayOrderKey
import app.n_zik.android.utils.shakeSensitivityThemeKey
import app.n_zik.android.utils.showBackgroundLyricsKey
import app.n_zik.android.utils.showButtonPlayerAddToPlaylistKey
import app.n_zik.android.utils.showButtonPlayerArrowKey
import app.n_zik.android.utils.showButtonPlayerDiscoverKey
import app.n_zik.android.utils.showButtonPlayerDownloadKey
import app.n_zik.android.utils.showButtonPlayerLoopKey
import app.n_zik.android.utils.showButtonPlayerLyricsKey
import app.n_zik.android.utils.showButtonPlayerMenuKey
import app.n_zik.android.utils.showButtonPlayerShuffleKey
import app.n_zik.android.utils.showButtonPlayerSleepTimerKey
import app.n_zik.android.utils.showButtonPlayerStartRadioKey
import app.n_zik.android.utils.showButtonPlayerSystemEqualizerKey
import app.n_zik.android.utils.showButtonPlayerVideoKey
import app.n_zik.android.utils.showCoverThumbnailAnimationKey
import app.n_zik.android.utils.showDownloadButtonBackgroundPlayerKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.showLikeButtonBackgroundPlayerKey
import app.n_zik.android.utils.showLyricsStateKey
import app.n_zik.android.utils.showMiniPlayerAddToPlaylistKey
import app.n_zik.android.utils.showMiniPlayerAudioOutputKey
import app.n_zik.android.utils.showMiniPlayerDiscoverKey
import app.n_zik.android.utils.showMiniPlayerDownloadKey
import app.n_zik.android.utils.showMiniPlayerLikeKey
import app.n_zik.android.utils.showMiniPlayerLyricsKey
import app.n_zik.android.utils.disableClosingPlayerSwipingDownKey
import app.n_zik.android.utils.showMiniPlayerPlayPauseKey
import app.n_zik.android.utils.showMiniPlayerQueueKey
import app.n_zik.android.utils.showMiniPlayerRadioKey
import app.n_zik.android.utils.showMiniPlayerRepeatKey
import app.n_zik.android.utils.showMiniPlayerShareKey
import app.n_zik.android.utils.showMiniPlayerShuffleKey
import app.n_zik.android.utils.showMiniPlayerSmartShuffleKey
import app.n_zik.android.utils.showButtonPlayerSmartShuffleKey
import app.n_zik.android.utils.showMiniPlayerSkipBackKey
import app.n_zik.android.utils.showMiniPlayerSkipForwardKey
import app.n_zik.android.utils.showMiniPlayerSleepTimerKey
import app.n_zik.android.utils.showMiniPlayerVideoKey
import app.n_zik.android.utils.showMiniPlayerVisualizerKey
import app.n_zik.android.utils.showNextSongsInPlayerKey
import app.n_zik.android.utils.showRemainingSongTimeKey
import app.n_zik.android.utils.showSecondLineKey
import app.n_zik.android.utils.showSkipTimeButtonsKey
import app.n_zik.android.utils.showTopActionsBarKey
import app.n_zik.android.utils.showTotalTimeQueueKey
import app.n_zik.android.utils.showVisualizerButtonsKey
import app.n_zik.android.utils.showVisualizerStateKey
import app.n_zik.android.utils.showalbumcoverKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.showsongsKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.showvisthumbnailKey
import app.n_zik.android.utils.statsExpandedKey
import app.n_zik.android.utils.statsfornerdsKey
import app.n_zik.android.utils.swipeAnimationsNoThumbnailKey
import app.n_zik.android.utils.swipeUpQueueKey
import app.n_zik.android.utils.tapqueueKey
import app.n_zik.android.utils.textoutlineKey
import app.n_zik.android.utils.thumbnailFadeExKey
import app.n_zik.android.utils.thumbnailFadeKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.thumbnailSizeDpKey
import app.n_zik.android.utils.thumbnailSizeLDpKey
import app.n_zik.android.utils.thumbnailSpacingKey
import app.n_zik.android.utils.thumbnailSpacingLKey
import app.n_zik.android.utils.thumbnailTapEnabledKey
import app.n_zik.android.utils.thumbnailTypeKey
import app.n_zik.android.utils.thumbnailpauseKey
import app.n_zik.android.utils.timelineExpandedKey
import app.n_zik.android.utils.titleExpandedKey
import app.n_zik.android.utils.topPaddingKey
import app.n_zik.android.utils.transitionEffectKey
import app.n_zik.android.utils.transparentBackgroundPlayerActionBarKey
import app.n_zik.android.utils.transparentbarKey
import app.n_zik.android.utils.uiRoundnessDpKey
import app.n_zik.android.utils.useSystemFontKey
import app.n_zik.android.utils.visualizerCustomColorKey
import app.n_zik.android.utils.visualizerEnabledKey
import app.n_zik.android.utils.visualizerLineThicknessKey
import app.n_zik.android.utils.visualizerWhiteColorOptionKey
import enums.VisualizerWhiteColorOption
import app.n_zik.android.utils.currentVisualizerKey
import app.n_zik.android.utils.showSearchTabKey
import app.n_zik.android.utils.showStatsInNavbarKey
import app.n_zik.android.utils.keepPlayerMinimizedKey
import app.n_zik.android.utils.nowPlayingIndicatorKey
import app.n_zik.android.utils.playlistindicatorKey
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.showFoldersOnDeviceKey
import app.n_zik.android.utils.showStatsListeningTimeKey
import app.n_zik.android.utils.floatActionIconOffsetXkey
import app.n_zik.android.utils.floatActionIconOffsetYkey
import app.n_zik.android.utils.multiFloatActionIconOffsetXkey
import app.n_zik.android.utils.multiFloatActionIconOffsetYkey
import app.n_zik.android.utils.playerBackdropKey
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.themed.n_zik.screen.player.defaultPlayerActionBarTopSlots
import app.n_zik.android.themed.n_zik.screen.player.emptyPlayerActionBarTopSlot
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarBottomAudioVideoKey
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarTopSlotsKey
import app.n_zik.android.themed.n_zik.screen.player.resolveLegacyPlayerActionBarTopSlots
import app.n_zik.android.themed.n_zik.screen.player.resolvePlayerActionBarTopSlots
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal enum class ThemeCategory { COLORS, PLAYER_AND_QUEUE, NAVIGATION, GLASS }
internal data object UseThemeDefault

internal data class ThemeSettings(val plain: Map<String, Any>, val glass: Map<String, Any> = emptyMap())

internal const val ThemeAppearanceV2 = 2
internal const val ThemeAppearanceV3 = 3
internal const val ThemeAppearanceV3MinimumVersionCode = 101
internal const val ThemeAppearanceV2MinimumVersionCode = 99
internal const val ThemePureBlackMinimumVersionCode = 101
internal const val ThemeM3eAlwaysShowShapesMinimumVersionCode = 101
internal const val ThemeCustomToolbarsMinimumVersionCode = 101
internal const val ThemeBurgerMenuShortcutsMinimumVersionCode = 101

private val coverDimmingKeys = setOf(
    visualizerCoverDimmingOpacityKey, lyricsCoverDimmingEnabledKey, lyricsCoverDimmingOpacityKey,
)

/** Feature compatibility is independent of the portable document schema. */
internal fun themeAppearanceFeatureMinimumVersionCode(settings: Map<ThemeCategory, ThemeSettings>): Int {
    val pureBlack = if (settings[ThemeCategory.COLORS]?.plain?.get(colorPaletteModeKey) == ColorPaletteMode.PureBlack.name)
        ThemePureBlackMinimumVersionCode else 0
    val alwaysShowShapes = if (settings[ThemeCategory.PLAYER_AND_QUEUE]?.plain?.containsKey(m3eAlwaysShowShapesKey) == true)
        ThemeM3eAlwaysShowShapesMinimumVersionCode else 0
    val customToolbars = if (settings[ThemeCategory.NAVIGATION]?.plain?.keys?.hasCustomToolbarSettings() == true)
        ThemeCustomToolbarsMinimumVersionCode else 0
    val burgerShortcuts = if (settings[ThemeCategory.NAVIGATION]?.plain?.hasBurgerMenuShortcuts() == true)
        ThemeBurgerMenuShortcutsMinimumVersionCode else 0
    return maxOf(pureBlack, alwaysShowShapes, customToolbars, burgerShortcuts)
}

internal fun themeAppearancePublicationMinimumVersionCode(source: String): Int {
    val schema = requireNotNull(themeAppearanceSchema(Json.parseToJsonElement(source) as JsonObject))
    val schemaMinimum = themeSchemaMinimumVersionCode(schema)
    return maxOf(schemaMinimum, themeAppearanceFeatureMinimumVersionCode(parseThemeAppearance(source, allowEmptyCategories = true)))
}

internal fun themeAppearanceSchemaFor(
    versionCode: Int,
    preferences: Map<String, *> = emptyMap<String, Any>(),
): Int = when {
    versionCode >= ThemeAppearanceV3MinimumVersionCode || playerActionBarTopSlotsKey in preferences ||
        preferences[playerActionBarBottomAudioVideoKey] == true ||
        coverDimmingKeys.any(preferences::containsKey) -> ThemeAppearanceV3
    versionCode >= ThemeAppearanceV2MinimumVersionCode -> ThemeAppearanceV2
    else -> 1
}

internal fun themeSchemaMinimumVersionCode(schema: Int): Int = when (schema) {
    ThemeAppearanceV3 -> ThemeAppearanceV3MinimumVersionCode
    ThemeAppearanceV2 -> ThemeAppearanceV2MinimumVersionCode
    else -> FirstThemeStoreVersionCode
}

internal fun parseThemeAppearance(source: String): Map<ThemeCategory, ThemeSettings> =
    parseThemeAppearance(source, allowEmptyCategories = false)

internal fun themeAppearanceSchema(root: JsonObject): Int? {
    val version = root["schemaVersion"] as? JsonPrimitive
    val numericVersion = version?.takeUnless { it.isString }?.content
        ?.takeIf(jsonNumber::matches)?.toBigDecimalOrNull()
    return (1..ThemeAppearanceV3).firstOrNull { numericVersion?.compareTo(it.toBigDecimal()) == 0 }
}

internal fun parseThemeAppearance(
    source: String,
    allowEmptyCategories: Boolean,
    advertisedMinVersionCode: Int? = null,
    validateFeatureCompatibility: Boolean = true,
): Map<ThemeCategory, ThemeSettings> {
    val root = Json.parseToJsonElement(source) as? JsonObject ?: error("Invalid theme document")
    val schemaVersion = themeAppearanceSchema(root)
    require(root.keys == setOf("schemaVersion", "categories") && schemaVersion != null) {
        "Unsupported theme schema"
    }
    val schemaMinimum = themeSchemaMinimumVersionCode(schemaVersion)
    require(schemaVersion == 1 || advertisedMinVersionCode == null || advertisedMinVersionCode >= schemaMinimum) {
        "Theme appearance V$schemaVersion requires Android version code $schemaMinimum"
    }
    val categories = root["categories"] as? JsonObject ?: error("Invalid theme categories")
    require(allowEmptyCategories || categories.isNotEmpty()) { "The theme contains no categories" }
    val settings = categories.map { (name, content) ->
        val fields = ImportThemeFields(content, schemaVersion)
        val category = ThemeCategory.entries.firstOrNull { it.wireKey == name }
            ?: error("Unknown theme category $name")
        category to fields.readCategory(category, schemaVersion)
    }.toMap()
    val featureMinimum = themeAppearanceFeatureMinimumVersionCode(settings)
    require(featureMinimum == 0 || schemaVersion >= ThemeAppearanceV2) {
        "Pure Black mode requires theme appearance V2"
    }
    if (validateFeatureCompatibility && advertisedMinVersionCode != null && featureMinimum > 0) {
        require(advertisedMinVersionCode >= featureMinimum) {
            val feature = when {
                settings[ThemeCategory.NAVIGATION]?.plain?.keys?.hasCustomToolbarSettings() == true -> "Custom toolbars"
                settings[ThemeCategory.NAVIGATION]?.plain?.hasBurgerMenuShortcuts() == true -> "Burger menu shortcuts"
                settings[ThemeCategory.PLAYER_AND_QUEUE]?.plain?.containsKey(m3eAlwaysShowShapesKey) == true -> "Always Show M3E Shapes"
                else -> "Pure Black mode"
            }
            "$feature requires Android version code $featureMinimum"
        }
    }
    return settings
}

internal fun encodeThemeAppearance(
    plain: Map<String, *>,
    glass: Map<String, *>,
    defaultGlassQuality: GlassQuality = GlassQuality.Balanced,
    schemaVersion: Int = 1,
): String =
    buildJsonObject {
        require(schemaVersion in 1..ThemeAppearanceV3)
        require(schemaVersion == ThemeAppearanceV3 ||
            (playerActionBarTopSlotsKey !in plain && plain[playerActionBarBottomAudioVideoKey] != true)
        ) {
            "Player top action slots require theme appearance V3"
        }
        require(schemaVersion >= ThemeAppearanceV3 || coverDimmingKeys.none(plain::containsKey)) {
            "Cover dimming settings require theme appearance V3"
        }
        val navigationPreferences = plain.withoutDefaultBurgerMenuShortcuts()
        require(schemaVersion >= ThemeAppearanceV2 || plain[colorPaletteModeKey] != ColorPaletteMode.PureBlack.name) {
            "Pure Black mode requires theme appearance V2"
        }
        require(schemaVersion >= ThemeAppearanceV2 || m3eAlwaysShowShapesKey !in plain) {
            "Always Show M3E Shapes requires theme appearance V2"
        }
        require(schemaVersion >= ThemeAppearanceV2 || !plain.keys.hasCustomToolbarSettings()) {
            "Custom toolbars require theme appearance V2"
        }
        require(schemaVersion >= ThemeAppearanceV2 || !navigationPreferences.hasBurgerMenuShortcuts()) {
            "Burger menu shortcuts require theme appearance V2"
        }
        put("schemaVersion", schemaVersion)
        put("categories", buildJsonObject {
            ThemeCategory.entries.forEach { category ->
                val fields = ExportThemeFields(when (category) {
                    ThemeCategory.GLASS -> portableGlassPreferences(glass, defaultGlassQuality) + plain
                    ThemeCategory.COLORS -> {
                        val palette = ColorPaletteName.entries.firstOrNull { it.name == plain[colorPaletteNameKey] }
                        if (palette?.isAlwaysDark == true) plain + (colorPaletteModeKey to ColorPaletteMode.Dark.name)
                        else plain
                    }
                    ThemeCategory.NAVIGATION -> navigationPreferences
                    ThemeCategory.PLAYER_AND_QUEUE -> if (schemaVersion >= ThemeAppearanceV3) {
                        buildMap {
                            putAll(plain)
                            put(playerActionBarTopSlotsKey, portablePlayerTopSlots(plain))
                            if (playerActionBarBottomAudioVideoKey !in plain &&
                                playerActionBarTopSlotsKey !in plain && plain[showTopActionsBarKey] == false
                            ) {
                                val surface = ToolbarSurface.PLAYER_TOP
                                val videoEnabled = plain[surface.visibilityKey(ToolbarAction.AUDIO_VIDEO)] != false
                                put(playerActionBarBottomAudioVideoKey, videoEnabled)
                            }
                        }
                    } else plain
                })
                fields.fields(category, schemaVersion)
                put(category.wireKey, JsonObject(fields.values))
            }
        })
    }.toString()

private fun portablePlayerTopSlots(preferences: Map<String, *>): String {
    val configured = preferences[playerActionBarTopSlotsKey] as? String
    val surface = ToolbarSurface.PLAYER_TOP
    val slots = if (configured.isNullOrBlank()) {
        resolveLegacyPlayerActionBarTopSlots(
            order = (preferences[surface.orderKey] as? String).orEmpty(),
            showTopBar = preferences[showTopActionsBarKey] != false,
            showAudioVideo = preferences[surface.visibilityKey(ToolbarAction.AUDIO_VIDEO)] != false,
            showBottomMenu = preferences[showButtonPlayerMenuKey] == true,
        )
    } else resolvePlayerActionBarTopSlots(configured)
    return JsonArray(slots.map(::JsonPrimitive)).toString()
}

private fun portableGlassPreferences(
    preferences: Map<String, *>,
    defaultQuality: GlassQuality,
): Map<String, Any> {
    val settings = glassSettingsFromPreferences(preferences, defaultQuality)
    return buildMap {
        put(glassTintSourceKey, settings.source.name)
        put(glassQualityKey, settings.quality.name)
        put(glassCustomColorKey, settings.customColor)
        put(glassTintStrengthKey, settings.tintStrength)
        put(glassVibrancyKey, settings.vibrancy)
        put(glassBlurKey, settings.blur)
        put(glassFilmKey, settings.film)
        put(glassIndicatorOpacityKey, settings.indicatorOpacity)
        put(glassDialogDimmingKey, settings.dialogDimming)
        put(glassMenuDimmingKey, settings.menuDimming)
        put(glassRimKey, settings.rim)
        put(glassDepthKey, settings.depth)
        put(glassAmountKey, settings.amount)
        put(glassRippleIntensityKey, settings.rippleIntensity)
        put(glassRippleSizeKey, settings.rippleSize)
        put(glassJellyIntensityKey, settings.jellyIntensity)
        put(glassDispersionKey, settings.dispersion)
        put(glassDepthEffectKey, settings.depthEffect)
        put(glassRippleEnabledKey, settings.rippleEnabled)
        put(glassJellyEnabledKey, settings.jellyEnabled)
        put(glassOptimizationEnabledKey, settings.optimizationEnabled)
        GlassArea.entries.forEach { area -> put(area.preferenceKey, settings.mode(area).name) }
        settings.contextModes.forEach { (context, mode) -> put(context.preferenceKey, mode.name) }
    }
}

private class ExportThemeFields(private val preferences: Map<String, *>) : ThemeFields {
    val values = mutableMapOf<String, JsonElement>()

    fun hasPreference(key: String): Boolean = key in preferences

    override fun flag(field: String, key: String, default: Boolean) {
        values[field] = JsonPrimitive(preferences[key] as? Boolean ?: default)
    }

    override fun number(field: String, key: String, range: ClosedFloatingPointRange<Double>, default: Float, allowDefault: Boolean) {
        val stored = preferences[key] as? Number
        if (allowDefault && stored == null) {
            values[field] = JsonNull
            return
        }
        val value = stored?.toDouble() ?: default.toDouble()
        require(value in range) { "Number outside portable range for $field" }
        values[field] = JsonPrimitive(value)
    }

    override fun option(field: String, key: String, default: String, values: Set<String>) {
        val name = preferences[key] as? String ?: default
        require(name in values) { "Unsupported portable value for $field" }
        this.values[field] = JsonPrimitive(wireName(name))
    }

    override fun layoutMask(field: String, key: String, default: String) {
        val previous = (preferences[key] as? String)?.takeIf { it.isLayoutMask(default.length) }
        values[field] = JsonPrimitive(previous ?: default)
    }

    override fun integer(field: String, key: String, default: Int) {
        values[field] = JsonPrimitive(preferences[key] as? Int ?: default)
    }

    override fun order(field: String, key: String, allowedIds: Set<String>?, repeats: Map<String, Int>) {
        val stored = (preferences[key] as? String).orEmpty()
        val order = runCatching { Json.parseToJsonElement(stored) as JsonArray }.getOrNull()
            .orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            .filter { allowedIds?.contains(it) ?: dockPreferenceId.matches(it) }
        val counts = mutableMapOf<String, Int>()
        val validOrder = order.filter { id ->
            val count = counts.getOrDefault(id, 0) + 1
            counts[id] = count
            count <= repeats.getOrDefault(id, 1)
        }
        val portableOrder = if (allowedIds == null) validOrder.take(maxDockOrderEntries) else validOrder
        values[field] = JsonArray(portableOrder.map { JsonPrimitive(it.replace('_', '-')) })
    }

    override fun group(field: String, block: ThemeFields.() -> Unit) {
        values[field] = JsonObject(ExportThemeFields(preferences).apply(block).values)
    }
}

internal fun ThemeFields.acceptsPortableKey(key: String): Boolean =
    this !is ExportThemeFields || hasPreference(key)

internal fun selectThemeAppearanceCategories(source: String, included: Set<ThemeCategory>): String {
    val root = Json.parseToJsonElement(source) as JsonObject
    val categories = root.getValue("categories") as JsonObject
    val keys = included.map(ThemeCategory::wireKey).toSet()
    return JsonObject(root + ("categories" to JsonObject(categories.filterKeys { it in keys }))).toString()
}

private val ThemeCategory.wireKey: String get() = when (this) {
    ThemeCategory.COLORS -> "colors"
    ThemeCategory.PLAYER_AND_QUEUE -> "playerAndQueue"
    ThemeCategory.NAVIGATION -> "navigation"
    ThemeCategory.GLASS -> "glass"
}

private fun ThemeFields.fields(category: ThemeCategory, schemaVersion: Int) {
    when (category) {
        ThemeCategory.COLORS -> {
            option<ColorPaletteName>("palette", colorPaletteNameKey, ColorPaletteName.Dynamic)
            option<ColorPaletteMode>("mode", colorPaletteModeKey, DefaultColorPaletteMode)
            optional("respectThemeBrightness") { flag("respectThemeBrightness", dynamicThemeRespectBrightnessKey, false) }
            optional("customAccent") { integer("customAccent", customColorKey, androidx.compose.ui.graphics.Color.Green.hashCode()) }
            optional("customLight") {
                group("customLight") {
                    integer("background0", customThemeLight_Background0Key, DefaultLightColorPalette.background0.hashCode())
                    integer("background1", customThemeLight_Background1Key, DefaultLightColorPalette.background1.hashCode())
                    integer("background2", customThemeLight_Background2Key, DefaultLightColorPalette.background2.hashCode())
                    integer("background3", customThemeLight_Background3Key, DefaultLightColorPalette.background3.hashCode())
                    integer("background4", customThemeLight_Background4Key, DefaultLightColorPalette.background4.hashCode())
                    integer("text", customThemeLight_TextKey, DefaultLightColorPalette.text.hashCode())
                    integer("textSecondary", customThemeLight_textSecondaryKey, DefaultLightColorPalette.textSecondary.hashCode())
                    integer("textDisabled", customThemeLight_textDisabledKey, DefaultLightColorPalette.textDisabled.hashCode())
                    integer("iconButtonPlayer", customThemeLight_iconButtonPlayerKey, DefaultLightColorPalette.iconButtonPlayer.hashCode())
                    integer("accent", customThemeLight_accentKey, DefaultLightColorPalette.accent.hashCode())
                }
            }
            optional("customDark") {
                group("customDark") {
                    integer("background0", customThemeDark_Background0Key, DefaultDarkColorPalette.background0.hashCode())
                    integer("background1", customThemeDark_Background1Key, DefaultDarkColorPalette.background1.hashCode())
                    integer("background2", customThemeDark_Background2Key, DefaultDarkColorPalette.background2.hashCode())
                    integer("background3", customThemeDark_Background3Key, DefaultDarkColorPalette.background3.hashCode())
                    integer("background4", customThemeDark_Background4Key, DefaultDarkColorPalette.background4.hashCode())
                    integer("text", customThemeDark_TextKey, DefaultDarkColorPalette.text.hashCode())
                    integer("textSecondary", customThemeDark_textSecondaryKey, DefaultDarkColorPalette.textSecondary.hashCode())
                    integer("textDisabled", customThemeDark_textDisabledKey, DefaultDarkColorPalette.textDisabled.hashCode())
                    integer("iconButtonPlayer", customThemeDark_iconButtonPlayerKey, DefaultDarkColorPalette.iconButtonPlayer.hashCode())
                    integer("accent", customThemeDark_accentKey, DefaultDarkColorPalette.accent.hashCode())
                }
            }
        }
        ThemeCategory.PLAYER_AND_QUEUE -> player(schemaVersion)
        ThemeCategory.NAVIGATION -> {
            option<NavigationBarPosition>("position", navigationBarPositionKey, NavigationBarPosition.BottomFloating)
            option<NavigationBarType>("labels", navigationBarTypeKey, NavigationBarType.IconOnly)
            optional("disableBackStack") { flag("disableBackStack", disableNavigationBackStackKey, false) }
            optional("rememberQueueScroll") { flag("rememberQueueScroll", rememberQueueScrollPositionKey, true) }
            optional("swipeActionsEnabled") { flag("swipeActionsEnabled", isSwipeToActionEnabledKey, true) }
            optional("defaultTab") { option<HomeScreenTabs>("defaultTab", indexNavigationTabKey, HomeScreenTabs.Default) }
            optional("queueSwipeLeft") { option<QueueSwipeAction>("queueSwipeLeft", queueSwipeLeftActionKey, QueueSwipeAction.RemoveFromQueue) }
            optional("queueSwipeRight") { option<QueueSwipeAction>("queueSwipeRight", queueSwipeRightActionKey, QueueSwipeAction.PlayNext) }
            optional("playlistSwipeLeft") { option<PlaylistSwipeAction>("playlistSwipeLeft", playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite) }
            optional("playlistSwipeRight") { option<PlaylistSwipeAction>("playlistSwipeRight", playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext) }
            optional("albumSwipeLeft") { option<AlbumSwipeAction>("albumSwipeLeft", albumSwipeLeftActionKey, AlbumSwipeAction.PlayNext) }
            optional("albumSwipeRight") { option<AlbumSwipeAction>("albumSwipeRight", albumSwipeRightActionKey, AlbumSwipeAction.Bookmark) }
            homeLayouts()
            toolbars(schemaVersion)
            portableBurgerMenu(schemaVersion)
            optional("homeTabs") {
                group("homeTabs") {
                    order("order", homeTabsOrderKey, homeTabIds)
                    homeTabIds.filterNot { it == "quickpicks" }.forEach { id ->
                        flag(id, "hometab_${id}_enabled", true)
                    }
                }
            }
            optional("showSearchTab") { flag("showSearchTab", showSearchTabKey, false) }
            optional("showStatsTab") { flag("showStatsTab", showStatsInNavbarKey, false) }
            optional("homeArtistItemSize") { option("homeArtistItemSize", Preference.HOME_ARTIST_ITEM_SIZE) }
            optional("homeAlbumItemSize") { option("homeAlbumItemSize", Preference.HOME_ALBUM_ITEM_SIZE) }
            optional("homeLibraryItemSize") { option("homeLibraryItemSize", Preference.HOME_LIBRARY_ITEM_SIZE) }
            optional("searchGridLayouts") {
                val layout = Preference.SEARCH_RESULT_GRID_STATES
                layoutMask("searchGridLayouts", layout.key, layout.default)
            }
            optional("showOnDeviceFolders") { flag("showOnDeviceFolders", showFoldersOnDeviceKey, true) }
            optional("showListeningTimeInStatistics") { flag("showListeningTimeInStatistics", showStatsListeningTimeKey, true) }
            optional("floatingActionX") { number("floatingActionX", floatActionIconOffsetXkey, -16384.0..16384.0, 0f) }
            optional("floatingActionY") { number("floatingActionY", floatActionIconOffsetYkey, -16384.0..16384.0, 0f) }
            optional("multiFloatingActionX") { number("multiFloatingActionX", multiFloatActionIconOffsetXkey, -16384.0..16384.0, 0f) }
            optional("multiFloatingActionY") { number("multiFloatingActionY", multiFloatActionIconOffsetYkey, -16384.0..16384.0, 0f) }
            optional("enableQuickPicks") { flag("enableQuickPicks", enableQuickPicksPageKey, true) }
            optional("rightToLeft") { flag("rightToLeft", rightToLeftInterfaceKey, false) }
            optional("playerPosition") { option<PlayerPosition>("playerPosition", playerPositionKey, PlayerPosition.Bottom) }
            optional("menuStyle") { option<MenuStyle>("menuStyle", menuStyleKey, MenuStyle.List) }
            optional("transitionEffect") { option<TransitionEffect>("transitionEffect", transitionEffectKey, TransitionEffect.Fade) }
            optional("searchDisplayOrder") { option<SearchDisplayOrder>("searchDisplayOrder", searchDisplayOrderKey, SearchDisplayOrder.SuggestionsFirst) }
            optional("uiRoundnessDp") { number("uiRoundnessDp", uiRoundnessDpKey, 0.0..48.0, 25f) }
            optional("artistRoundnessDp") { number("artistRoundnessDp", artistThumbnailRoundnessDpKey, 0.0..48.0, 48f) }
            if (this !is ImportThemeFields || has("font")) option<FontType>("font", fontTypeKey, FontType.Rubik)
            if (this !is ImportThemeFields || has("useSystemFont")) flag("useSystemFont", useSystemFontKey, false)
            if (this !is ImportThemeFields || has("applyFontPadding")) flag("applyFontPadding", applyFontPaddingKey, false)
            if (this !is ImportThemeFields || has("hideStatusBar")) flag("hideStatusBar", hideStatusBarKey, false)
            if (this !is ImportThemeFields || has("showFloatingSearch")) flag("showFloatingSearch", showFloatingIconKey, false)
        }
        ThemeCategory.GLASS -> glass()
    }
}

private fun ImportThemeFields.readCategory(category: ThemeCategory, schemaVersion: Int): ThemeSettings {
    fields(category, schemaVersion)
    val settings = result()
    return if (category == ThemeCategory.GLASS) {
        ThemeSettings(plain = mapOf(glassEnabledKey to settings.getValue(glassEnabledKey)), glass = settings - glassEnabledKey)
    } else ThemeSettings(plain = settings)
}

private val homeTabIds get() = defaultHomeTabsOrder.toSet()
private val miniPlayerButtons by lazy { MiniPlayerButton.entries.map { wireName(it.name).replace('-', '_') }.toSet() }
internal val portableLyricsKeys: Set<String> = setOf(
    lyricsColorKey, lyricsCustomColorKey, lyricsFontSizeKey, lyricsSizeKey, lyricsSizeLKey, lyricsOutlineKey,
    showLyricsStateKey, clickOnLyricsTextKey, playerEnableLyricsPopupMessageKey, lyricsAlignmentKey, lyricsHighlightKey, lyricsBackgroundKey, lyricsTypeKey, lyricsSizeAnimateKey,
    showBackgroundLyricsKey, lyricsIntervalIndicatorKey,
    landscapeControlsKey, romanizationEnabledKey, showSecondLineKey, karaokeRespectAgentPositionKey,
    lyricsCoverDimmingEnabledKey, lyricsCoverDimmingOpacityKey,
)

private val legacyGlassAreas = setOf(GlassArea.Nav, GlassArea.MiniPlayer, GlassArea.NowPlaying)
private val glassModes = GlassAreaMode.entries.map { it.name }.toSet()

internal inline fun ThemeFields.optional(field: String, block: () -> Unit) {
    if (this !is ImportThemeFields || has(field)) block()
}

private fun ThemeFields.player(schemaVersion: Int) {
    if (schemaVersion >= ThemeAppearanceV2) {
        optional("openingMode") { option<PlayerOpeningMode>("openingMode", playerOpeningModeKey, PlayerOpeningMode.Audio) }
        optional("holdToSpeedUp") { flag("holdToSpeedUp", holdToSpeedUpKey, false) }
        optional("holdToSpeedUpRate") { number("holdToSpeedUpRate", holdToSpeedUpRateKey, 1.1..4.0, DEFAULT_HOLD_TO_SPEED_UP_RATE) }
        if (this !is ExportThemeFields || hasPreference(m3eAlwaysShowShapesKey)) {
            optional("m3eAlwaysShowShapes") { flag("m3eAlwaysShowShapes", m3eAlwaysShowShapesKey, false) }
        }
    }
    optional("coverBackdrop") { number("coverBackdrop", playerBackdropKey, 0.0..100.0, 0f) }
    optional("nowPlayingIndicator") {
        option<MusicAnimationType>("nowPlayingIndicator", nowPlayingIndicatorKey, MusicAnimationType.Bubbles)
    }
    optional("playlistIndicator") { flag("playlistIndicator", playlistindicatorKey, false) }
    optional("visualizerPreset") { integer("visualizerPreset", currentVisualizerKey, 0) }
    optional("disableCloseOnSwipeDown") { flag("disableCloseOnSwipeDown", disableClosingPlayerSwipingDownKey, false) }
    optional("keepPlayerMinimized") { flag("keepPlayerMinimized", keepPlayerMinimizedKey, true) }
    optional("rememberLyrics") { flag("rememberLyrics", showLyricsStateKey, false) }
    optional("rememberVisualizer") { flag("rememberVisualizer", showVisualizerStateKey, false) }
    optional("disableHorizontalSwipe") { flag("disableHorizontalSwipe", disablePlayerHorizontalSwipeKey, false) }
    optional("clickLyricsText") { flag("clickLyricsText", clickOnLyricsTextKey, true) }
    optional("showLyricsPopup") { flag("showLyricsPopup", playerEnableLyricsPopupMessageKey, true) }
    optional("swipeSensitivity") { option<PlayerSwipeSensitivity>("swipeSensitivity", playerSwipeSensitivityKey, PlayerSwipeSensitivity.Medium) }
    optional("shakeSensitivity") { option<ShakeSensitivityTheme>("shakeSensitivity", shakeSensitivityThemeKey, ShakeSensitivityTheme.Disabled) }
    optional("notificationFirstAction") { option<NotificationButtons>("notificationFirstAction", notificationPlayerFirstIconKey, NotificationButtons.Download) }
    optional("notificationSecondAction") { option<NotificationButtons>("notificationSecondAction", notificationPlayerSecondIconKey, NotificationButtons.Favorites) }
    optional("blackgradient") { flag("blackgradient", blackgradientKey, false) }
    optional("textoutline") { flag("textoutline", textoutlineKey, false) }
    optional("effectRotation") { flag("effectRotation", effectRotationKey, effectRotationDefault) }
    optional("disableScrollingText") { flag("disableScrollingText", disableScrollingTextKey, false) }
    optional("showLikeButtonBackgroundPlayer") { flag("showLikeButtonBackgroundPlayer", showLikeButtonBackgroundPlayerKey, true) }
    optional("showDownloadButtonBackgroundPlayer") { flag("showDownloadButtonBackgroundPlayer", showDownloadButtonBackgroundPlayerKey, true) }
    optional("visualizerWhiteColorOption") { option<VisualizerWhiteColorOption>("visualizerWhiteColorOption", visualizerWhiteColorOptionKey, VisualizerWhiteColorOption.White) }
    optional("visualizerCustomColor") { integer("visualizerCustomColor", visualizerCustomColorKey, -1) }
    optional("playerVisualizerType") { option<PlayerVisualizerType>("playerVisualizerType", playerVisualizerTypeKey, PlayerVisualizerType.Disabled) }
    optional("visualizerLineThickness") { number("visualizerLineThickness", visualizerLineThicknessKey, 0.0..100.0, 6f) }
    optional("thumbnailTapEnabled") { flag("thumbnailTapEnabled", thumbnailTapEnabledKey, true) }
    optional("showButtonPlayerSystemEqualizer") { flag("showButtonPlayerSystemEqualizer", showButtonPlayerSystemEqualizerKey, false) }
    optional("backgroundProgress") { option<BackgroundProgress>("backgroundProgress", backgroundProgressKey, BackgroundProgress.MiniPlayer) }
    optional("showSkipTimeButtons") { flag("showSkipTimeButtons", showSkipTimeButtonsKey, true) }
    optional("playerControlsColors") { option<PlayerControlsColors>("playerControlsColors", playerControlsColorsKey, PlayerControlsColors.Monochrome) }
    optional("iconLikeType") { option<IconLikeType>("iconLikeType", iconLikeTypeKey, IconLikeType.Essential) }
    optional("playerSwapControlsWithTimeline") { flag("playerSwapControlsWithTimeline", playerSwapControlsWithTimelineKey, false) }
    optional("showvisthumbnail") { flag("showvisthumbnail", showvisthumbnailKey, true) }
    optional("blackBackgroundForVisThumbnail") { flag("blackBackgroundForVisThumbnail", blackBackgroundForVisThumbnailKey, true) }
    if (schemaVersion >= ThemeAppearanceV3 && acceptsPortableKey(visualizerCoverDimmingOpacityKey)) {
        optional("visualizerCoverDimmingOpacity") {
            number("visualizerCoverDimmingOpacity", visualizerCoverDimmingOpacityKey, 0.0..1.0, defaultVisualizerCoverDimmingOpacity)
        }
    }
    optional("showVisualizerButtons") { flag("showVisualizerButtons", showVisualizerButtonsKey, true) }
    optional("buttonzoomout") { flag("buttonzoomout", buttonzoomoutKey, true) }
    optional("thumbnailpause") { flag("thumbnailpause", thumbnailpauseKey, true) }
    optional("cropVideoThumbnails") { flag("cropVideoThumbnails", cropVideoThumbnailsKey, true) }
    optional("showsongs") { option<SongsNumber>("showsongs", showsongsKey, SongsNumber.`2`) }
    optional("showalbumcover") { flag("showalbumcover", showalbumcoverKey, true) }
    optional("tapqueue") { flag("tapqueue", tapqueueKey, true) }
    optional("swipeUpQueue") { flag("swipeUpQueue", swipeUpQueueKey, true) }
    optional("statsfornerds") { flag("statsfornerds", statsfornerdsKey, false) }
    optional("queueDurationExpanded") { flag("queueDurationExpanded", queueDurationExpandedKey, true) }
    optional("titleExpanded") { flag("titleExpanded", titleExpandedKey, true) }
    optional("timelineExpanded") { flag("timelineExpanded", timelineExpandedKey, true) }
    optional("controlsExpanded") { flag("controlsExpanded", controlsExpandedKey, true) }
    optional("miniQueueExpanded") { flag("miniQueueExpanded", miniQueueExpandedKey, true) }
    optional("statsExpanded") { flag("statsExpanded", statsExpandedKey, true) }
    optional("actionExpanded") { flag("actionExpanded", actionExpandedKey, true) }
    optional("showCoverThumbnailAnimation") { flag("showCoverThumbnailAnimation", showCoverThumbnailAnimationKey, false) }
    optional("coverThumbnailAnimation") { option<ThumbnailCoverType>("coverThumbnailAnimation", coverThumbnailAnimationKey, ThumbnailCoverType.Vinyl) }
    optional("thumbnailSpacingL") { number("thumbnailSpacingL", thumbnailSpacingLKey, -100.0..100.0, 0f) }
    optional("vinylSize") { number("vinylSize", VinylSizeKey, 0.0..100.0, 50f) }
    optional("swipeAnimationsNoThumbnail") { option<SwipeAnimationNoThumbnail>("swipeAnimationsNoThumbnail", swipeAnimationsNoThumbnailKey, SwipeAnimationNoThumbnail.Sliding) }
    optional("rotatingAlbumCover") { flag("rotatingAlbumCover", rotatingAlbumCoverKey, false) }
    optional("albumCoverRotation") { flag("albumCoverRotation", albumCoverRotationKey, false) }
    optional("lyrics") {
        group("lyrics") {
            option<LyricsColor>("color", lyricsColorKey, LyricsColor.White)
            integer("customColor", lyricsCustomColorKey, -1)
            option<LyricsFontSize>("fontSize", lyricsFontSizeKey, LyricsFontSize.DEFAULT)
            number("size", lyricsSizeKey, 18.0..100.0, 20f)
            number("sizeLandscape", lyricsSizeLKey, 18.0..100.0, 20f)
            option<LyricsOutline>("outline", lyricsOutlineKey, LyricsOutline.None)
            option<LyricsAlignment>("alignment", lyricsAlignmentKey, LyricsAlignment.Center)
            option<LyricsHighlight>("highlight", lyricsHighlightKey, LyricsHighlight.None)
            option<LyricsBackground>("background", lyricsBackgroundKey, LyricsBackground.Black)
            option<LyricsType>("mode", lyricsTypeKey, LyricsType.Auto)
            flag("animateSize", lyricsSizeAnimateKey, false)
            flag("showBackground", showBackgroundLyricsKey, false)
            if (schemaVersion >= ThemeAppearanceV3 && acceptsPortableKey(lyricsCoverDimmingEnabledKey)) {
                optional("coverDimmingEnabled") { flag("coverDimmingEnabled", lyricsCoverDimmingEnabledKey, true) }
            }
            if (schemaVersion >= ThemeAppearanceV3 && acceptsPortableKey(lyricsCoverDimmingOpacityKey)) {
                optional("coverDimmingOpacity") {
                    number("coverDimmingOpacity", lyricsCoverDimmingOpacityKey, 0.0..1.0, defaultLyricsCoverDimmingOpacity)
                }
            }
            flag("showIntervalIndicator", lyricsIntervalIndicatorKey, true)
            optional("landscapeControls") { flag("landscapeControls", landscapeControlsKey, true) }
            optional("romanization") { flag("romanization", romanizationEnabledKey, true) }
            optional("secondLine") { flag("secondLine", showSecondLineKey, false) }
            optional("karaokeRespectAgentPosition") { flag("karaokeRespectAgentPosition", karaokeRespectAgentPositionKey, true) }
        }
    }
    optional("miniPlayer") {
        group("miniPlayer") {
            option<MiniPlayerType>("type", miniPlayerTypeKey, MiniPlayerType.Essential)
            order("buttonOrder", miniPlayerButtonOrderKey, if (schemaVersion >= ThemeAppearanceV3) miniPlayerButtons
                else miniPlayerButtons - "smart_shuffle")
            group("visibleButtons") {
                flag("playPause", showMiniPlayerPlayPauseKey, true)
                flag("skipBack", showMiniPlayerSkipBackKey, true)
                flag("skipForward", showMiniPlayerSkipForwardKey, true)
                flag("shuffle", showMiniPlayerShuffleKey, false)
                if (schemaVersion >= ThemeAppearanceV3) {
                    optional("smartShuffle") { flag("smartShuffle", showMiniPlayerSmartShuffleKey, false) }
                }
                flag("repeat", showMiniPlayerRepeatKey, false)
                flag("like", showMiniPlayerLikeKey, false)
                flag("addToPlaylist", showMiniPlayerAddToPlaylistKey, false)
                flag("download", showMiniPlayerDownloadKey, false)
                flag("share", showMiniPlayerShareKey, false)
                flag("radio", showMiniPlayerRadioKey, false)
                flag("audioOutput", showMiniPlayerAudioOutputKey, true)
                flag("sleepTimer", showMiniPlayerSleepTimerKey, false)
                flag("lyrics", showMiniPlayerLyricsKey, false)
                flag("visualizer", showMiniPlayerVisualizerKey, false)
                flag("queue", showMiniPlayerQueueKey, false)
                if (schemaVersion == 1) flag("video", showMiniPlayerVideoKey, false)
                flag("discover", showMiniPlayerDiscoverKey, false)
            }
        }
    }
    option<PlayerType>("playerStyle", playerTypeKey, PlayerType.Essential)
    option<QueueType>("queueStyle", queueTypeKey, QueueType.Modern)
    option<ThumbnailType>("coverStyle", thumbnailTypeKey, ThumbnailType.Modern)
    option<PlayerInfoType>("headerStyle", playerInfoTypeKey, PlayerInfoType.Modern)
    option<PlayerControlsType>("controlsStyle", playerControlsTypeKey, PlayerControlsType.Essential)
    option<PlayerBackgroundColors>("background", playerBackgroundColorsKey, PlayerBackgroundColors.AnimatedGradient)
    option<PlayerTimelineType>("timeline", playerTimelineTypeKey, PlayerTimelineType.Wavy)
    flag("showQueueDuration", showTotalTimeQueueKey, true)
    flag("showCover", showthumbnailKey, true)
    flag("transparentPlayerBar", transparentbarKey, true)
    flag("showLyricsOnCover", showlyricsthumbnailKey, true)
    flag("expandedPlayer", expandedplayerKey, false)
    option<PlayerPlayButtonType>("playButtonStyle", playerPlayButtonTypeKey, PlayerPlayButtonType.CircularRibbed)
    flag("bottomGradient", bottomgradientKey, false)
    flag("visualizerEnabled", visualizerEnabledKey, false)
    number("coverSize", thumbnailSizeDpKey, 0.0..100.0, DEFAULT_THUMBNAIL_SIZE_DP)
    optional("coverSizeLandscape") {
        number("coverSizeLandscape", thumbnailSizeLDpKey, 0.0..100.0, DEFAULT_THUMBNAIL_SIZE_DP, allowDefault = true)
    }
    option<PlayerTimelineSize>("timelineSize", playerTimelineSizeKey, PlayerTimelineSize.Biggest)
    flag("showExpandPlayerButton", expandedplayertoggleKey, false)
    flag("showNextSongs", showNextSongsInPlayerKey, false)
    flag("showRemainingSongTime", showRemainingSongTimeKey, true)
    number("coverRoundnessDp", thumbnailRoundnessDpKey, 0.0..48.0, 12f)
    if (schemaVersion < ThemeAppearanceV3) flag("showTopActionsBar", showTopActionsBarKey, true)
    flag("transparentActionBar", transparentBackgroundPlayerActionBarKey, true)
    flag("actionButtonsSpacedEvenly", actionspacedevenlyKey, true)
    flag("noBlur", noblurKey, true)
    flag("coverFadingEdge", fadingedgeKey, false)
    flag("carousel", carouselKey, true)
    option<CarouselSize>("carouselSize", carouselSizeKey, CarouselSize.Biggest)
    flag("topPadding", topPaddingKey, true)
    option<AnimatedGradient>("animatedGradient", animatedGradientKey, AnimatedGradient.M3EMorphingCover)
    number("blurStrength", blurStrengthKey, 0.0..100.0, 25f)
    number("expandedCoverFade", thumbnailFadeExKey, 0.0..100.0, 5f)
    number("coverFade", thumbnailFadeKey, 0.0..100.0, 5f)
    number("coverSpacing", thumbnailSpacingKey, -100.0..100.0, 0f)
    flag("showHeaderIcons", playerInfoShowIconsKey, true)
    val actionOrderField = if (schemaVersion >= ThemeAppearanceV3) "bottomActionOrder" else "actionBarOrder"
    val legacyActionIds = playerActionBarThemeIds() - ToolbarAction.AUDIO_VIDEO.id - "smart_shuffle"
    val actionIds = if (schemaVersion >= ThemeAppearanceV3) playerActionBarThemeIds() else legacyActionIds
    optional(actionOrderField) { order(actionOrderField, playerActionBarButtonOrderKey, actionIds) }
    if (schemaVersion >= ThemeAppearanceV3) {
        order(
            "topActionSlots",
            playerActionBarTopSlotsKey,
            playerActionBarThemeIds() + defaultPlayerActionBarTopSlots + emptyPlayerActionBarTopSlot,
            repeats = mapOf(emptyPlayerActionBarTopSlot to defaultPlayerActionBarTopSlots.size),
        )
    }
    group("actionButtons") {
        if (schemaVersion >= ThemeAppearanceV3 && acceptsPortableKey(playerActionBarBottomAudioVideoKey)) {
            optional("audioVideo") { flag("audioVideo", playerActionBarBottomAudioVideoKey, false) }
        }
        flag("addToPlaylist", showButtonPlayerAddToPlaylistKey, true)
        flag("arrow", showButtonPlayerArrowKey, true)
        flag("download", showButtonPlayerDownloadKey, true)
        flag("loop", showButtonPlayerLoopKey, false)
        flag("lyrics", showButtonPlayerLyricsKey, true)
        flag("shuffle", showButtonPlayerShuffleKey, true)
        if (schemaVersion >= ThemeAppearanceV3) {
            optional("smartShuffle") { flag("smartShuffle", showButtonPlayerSmartShuffleKey, false) }
        }
        flag("sleepTimer", showButtonPlayerSleepTimerKey, false)
        flag("menu", showButtonPlayerMenuKey, false)
        flag("startRadio", showButtonPlayerStartRadioKey, false)
        flag("discover", showButtonPlayerDiscoverKey, false)
        if (schemaVersion == 1) flag("video", showButtonPlayerVideoKey, showButtonPlayerVideoDefault)
    }
}

private fun ThemeFields.glass() {
    val defaults = glassProfileDefaults(GlassQuality.Balanced)
    flag("enabled", glassEnabledKey, glassEnabledDefault)
    option<GlassTintSource>("tintSource", glassTintSourceKey, GlassTintSource.None)
    option<GlassAreaMode>("navigation", GlassArea.Nav.preferenceKey, GlassAreaMode.GlassAndContour)
    option<GlassAreaMode>("miniPlayer", GlassArea.MiniPlayer.preferenceKey, GlassAreaMode.GlassAndContour)
    option<GlassAreaMode>("nowPlaying", GlassArea.NowPlaying.preferenceKey, GlassAreaMode.GlassAndContour)
    optional("quality") { option<GlassQuality>("quality", glassQualityKey, GlassQuality.Balanced) }
    optional("customColor") { integer("customColor", glassCustomColorKey, defaults.customColor) }
    optional("tintStrength") { number("tintStrength", glassTintStrengthKey, 0.0..1.0, defaults.tintStrength) }
    optional("vibrancy") { number("vibrancy", glassVibrancyKey, 0.0..2.0, defaults.vibrancy) }
    optional("blur") { number("blur", glassBlurKey, 0.0..24.0, defaults.blur) }
    optional("film") { number("film", glassFilmKey, 0.0..1.0, defaults.film) }
    optional("indicatorOpacity") { number("indicatorOpacity", glassIndicatorOpacityKey, 0.0..1.0, defaults.indicatorOpacity) }
    optional("dialogDimming") { number("dialogDimming", glassDialogDimmingKey, 0.0..1.0, defaults.dialogDimming) }
    optional("menuDimming") { number("menuDimming", glassMenuDimmingKey, 0.0..1.0, defaults.menuDimming) }
    optional("rim") { number("rim", glassRimKey, 0.0..1.0, defaults.rim) }
    optional("refractionHeight") { number("refractionHeight", glassDepthKey, 0.0..1.0, defaults.depth) }
    optional("refractionAmount") { number("refractionAmount", glassAmountKey, 0.0..1.0, defaults.amount) }
    optional("rippleIntensity") { number("rippleIntensity", glassRippleIntensityKey, 0.0..1.0, defaults.rippleIntensity) }
    optional("rippleSize") { number("rippleSize", glassRippleSizeKey, 0.5..2.0, defaults.rippleSize) }
    optional("jellyIntensity") { number("jellyIntensity", glassJellyIntensityKey, 0.0..1.0, defaults.jellyIntensity) }
    optional("dispersion") { flag("dispersion", glassDispersionKey, defaults.dispersion) }
    optional("depthEffect") { flag("depthEffect", glassDepthEffectKey, defaults.depthEffect) }
    optional("rippleEnabled") { flag("rippleEnabled", glassRippleEnabledKey, defaults.rippleEnabled) }
    optional("jellyEnabled") { flag("jellyEnabled", glassJellyEnabledKey, defaults.jellyEnabled) }
    optional("optimizationEnabled") { flag("optimizationEnabled", glassOptimizationEnabledKey, defaults.optimizationEnabled) }
    optional("areas") {
        group("areas") {
            GlassArea.entries.filterNot { it in legacyGlassAreas }.forEach { area ->
                val field = wireName(area.name)
                optional(field) { option<GlassAreaMode>(field, area.preferenceKey, defaults.mode(area)) }
            }
        }
    }
    optional("contextModes") {
        group("contextModes") {
            GlassScope.entries.filter { it.areas.isNotEmpty() }.forEach { scope ->
                val name = wireName(scope.name)
                optional(name) {
                    group(name) {
                        scope.areas.forEach { area ->
                            val field = wireName(area.name)
                            optional(field) {
                                val key = GlassContextArea(scope, area).preferenceKey
                                option(field, key, "Inherit", glassModes + "Inherit")
                            }
                        }
                    }
                }
            }
        }
    }
}

internal interface ThemeFields {
    fun flag(field: String, key: String, default: Boolean)
    fun number(field: String, key: String, range: ClosedFloatingPointRange<Double>, default: Float, allowDefault: Boolean = false)
    fun option(field: String, key: String, default: String, values: Set<String>)
    fun layoutMask(field: String, key: String, default: String)
    fun integer(field: String, key: String, default: Int)
    fun order(field: String, key: String, allowedIds: Set<String>? = null, repeats: Map<String, Int> = emptyMap())
    fun group(field: String, block: ThemeFields.() -> Unit)
}

internal val themeSettingNames: Map<String, String> by lazy {
    val names = mutableMapOf<String, String>()
    val collector = object : ThemeFields {
        private val groups = mutableListOf<String>()

        private fun record(key: String, field: String) {
            names[key] = (groups.filterNot { it == "actionButtons" } + field).joinToString(" / ")
        }
        override fun flag(field: String, key: String, default: Boolean) {
            record(key, field)
        }

        override fun number(field: String, key: String, range: ClosedFloatingPointRange<Double>, default: Float, allowDefault: Boolean) {
            record(key, field)
        }

        override fun option(field: String, key: String, default: String, values: Set<String>) {
            record(key, field)
        }

        override fun layoutMask(field: String, key: String, default: String) {
            record(key, field)
        }

        override fun integer(field: String, key: String, default: Int) {
            record(key, field)
        }

        override fun order(field: String, key: String, allowedIds: Set<String>?, repeats: Map<String, Int>) {
            record(key, field)
        }

        override fun group(field: String, block: ThemeFields.() -> Unit) {
            groups.add(field)
            try {
                block()
            } finally {
                groups.removeAt(groups.lastIndex)
            }
        }
    }
    for (schema in 1..ThemeAppearanceV3) {
        ThemeCategory.entries.forEach { collector.fields(it, schema) }
    }
    names
}

internal inline fun <reified T : Enum<T>> ThemeFields.option(
    field: String, key: String, default: T, excluded: Set<T> = emptySet(),
) = option(field, key, default.name, enumValues<T>().filterNot { it in excluded }.map { it.name }.toSet())

private inline fun <reified T : Enum<T>> ThemeFields.option(field: String, preference: Preference.Key<T>) =
    option(field, preference.key, preference.default)

private fun String.isLayoutMask(length: Int): Boolean = this.length == length && all { it == '0' || it == '1' }

internal class ImportThemeFields(content: JsonElement, private val schemaVersion: Int) : ThemeFields {
    private val remaining = (content as? JsonObject ?: error("Expected theme settings object")).toMutableMap()
    private val values = mutableMapOf<String, Any>()

    fun has(field: String): Boolean = field in remaining

    fun requireNonEmptyGroup() {
        require(remaining.isNotEmpty()) { "Expected at least one theme setting" }
    }

    fun result(): Map<String, Any> {
        require(remaining.isEmpty()) { "Unrecognized theme fields" }
        return values
    }

    override fun flag(field: String, key: String, default: Boolean) {
        val value = primitive(field)
        require(!value.isString && value.content in setOf("true", "false")) { "Invalid boolean for $field" }
        values[key] = value.content == "true"
    }

    override fun number(field: String, key: String, range: ClosedFloatingPointRange<Double>, default: Float, allowDefault: Boolean) {
        val primitive = primitive(field)
        if (allowDefault && primitive == JsonNull) {
            values[key] = UseThemeDefault
            return
        }
        require(!primitive.isString && jsonNumber.matches(primitive.content)) { "Invalid number for $field" }
        val value = primitive.content.toDoubleOrNull()
        require(value != null && value in range) { "Number outside range for $field" }
        values[key] = value.toFloat()
    }

    override fun option(field: String, key: String, default: String, values: Set<String>) {
        val value = primitive(field)
        require(value.isString) { "Invalid value for $field" }
        this.values[key] = requireNotNull(values.firstOrNull { wireName(it) == value.content }) { "Unknown value for $field" }
    }

    override fun layoutMask(field: String, key: String, default: String) {
        val value = primitive(field)
        require(value.isString && value.content.isLayoutMask(default.length)) { "Invalid layout mask for $field" }
        values[key] = value.content
    }

    override fun integer(field: String, key: String, default: Int) {
        val value = primitive(field)
        require(!value.isString) { "Invalid integer for $field" }
        values[key] = requireNotNull(runCatching { value.content.toBigDecimalOrNull()?.intValueExact() }.getOrNull())
    }

    override fun order(field: String, key: String, allowedIds: Set<String>?, repeats: Map<String, Int>) {
        val importedOrder = remaining.remove(field) as? JsonArray ?: error("Invalid order for $field")
        val order = if (schemaVersion == 1 && key in setOf(miniPlayerButtonOrderKey, playerActionBarButtonOrderKey)) {
            importedOrder.filterNot { it == JsonPrimitive("video") }
        } else importedOrder
        if (key == playerActionBarTopSlotsKey) {
            require(order.size == defaultPlayerActionBarTopSlots.size) { "Invalid seat count in $field" }
        }
        require(order.size <= (allowedIds?.size ?: maxDockOrderEntries) + repeats.values.sum() - repeats.size) { "Too many buttons in $field" }
        val names = order.map { element ->
            val value = element as? JsonPrimitive ?: error("Invalid button in $field")
            require(value.isString) { "Invalid button in $field" }
            if (allowedIds == null) {
                val id = value.content.replace('-', '_')
                require(dockPreferenceId.matches(id) && '_' !in value.content) { "Invalid dock icon ID" }
                id
            } else {
                requireNotNull(allowedIds.firstOrNull { it.replace('_', '-') == value.content }) { "Unknown button in $field" }
            }
        }
        require(names.groupingBy { it }.eachCount().all { (id, count) -> count <= (repeats[id] ?: 1) }) {
            "Duplicate entries in $field"
        }
        values[key] = JsonArray(names.map(::JsonPrimitive)).toString()
    }

    override fun group(field: String, block: ThemeFields.() -> Unit) {
        values.putAll(ImportThemeFields(requireNotNull(remaining.remove(field)), schemaVersion).apply(block).result())
    }

    private fun primitive(field: String): JsonPrimitive =
        requireNotNull(remaining.remove(field) as? JsonPrimitive) { "Missing or invalid field $field" }
}

// Dock IDs are drawable resource entry names registered across all destinations.
private val dockPreferenceId = Regex("[a-z][a-z0-9_]{0,79}")
private const val maxDockOrderEntries = 512

private val camelBoundary = Regex("([a-z0-9])([A-Z])|([A-Z])([A-Z][a-z])")
private val jsonNumber = Regex("-?(?:0|[1-9][0-9]*)(?:[.][0-9]+)?(?:[eE][+-]?[0-9]+)?")

internal fun wireName(name: String): String =
    camelBoundary.replace(name) { match ->
        val (_, lower, upper, acronym, next) = match.groupValues
        if (lower.isNotEmpty()) "$lower-$upper" else "$acronym-$next"
    }.lowercase(Locale.ROOT)
