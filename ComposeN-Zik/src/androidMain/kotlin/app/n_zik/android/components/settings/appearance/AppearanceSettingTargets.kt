package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import app.n_zik.android.utils.actionExpandedKey
import app.n_zik.android.utils.actionspacedevenlyKey
import app.n_zik.android.utils.albumCoverRotationKey
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.backgroundProgressKey
import app.n_zik.android.utils.blackgradientKey
import app.n_zik.android.utils.bottomgradientKey
import app.n_zik.android.utils.buttonzoomoutKey
import app.n_zik.android.utils.carouselKey
import app.n_zik.android.utils.carouselSizeKey
import app.n_zik.android.utils.clickOnLyricsTextKey
import app.n_zik.android.utils.controlsExpandedKey
import app.n_zik.android.utils.coverThumbnailAnimationKey
import app.n_zik.android.utils.disablePlayerHorizontalSwipeKey
import app.n_zik.android.utils.playerSwipeSensitivityKey
import app.n_zik.android.utils.shakeSensitivityThemeKey
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.effectRotationKey
import app.n_zik.android.utils.fadingedgeKey
import app.n_zik.android.utils.iconLikeTypeKey
import app.n_zik.android.utils.miniQueueExpandedKey
import app.n_zik.android.utils.noblurKey
import app.n_zik.android.utils.notificationPlayerFirstIconKey
import app.n_zik.android.utils.notificationPlayerSecondIconKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.playerControlsTypeKey
import app.n_zik.android.utils.playerControlsColorsKey
import app.n_zik.android.utils.playerEnableLyricsPopupMessageKey
import app.n_zik.android.utils.playerInfoShowIconsKey
import app.n_zik.android.utils.playerInfoTypeKey
import app.n_zik.android.utils.playerPlayButtonTypeKey
import app.n_zik.android.utils.playerSwapControlsWithTimelineKey
import app.n_zik.android.utils.playerTimelineSizeKey
import app.n_zik.android.utils.playerTimelineTypeKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.queueDurationExpandedKey
import app.n_zik.android.utils.queueTypeKey
import app.n_zik.android.utils.showBackgroundLyricsKey
import app.n_zik.android.utils.showCoverThumbnailAnimationKey
import app.n_zik.android.utils.showLyricsStateKey
import app.n_zik.android.utils.showNextSongsInPlayerKey
import app.n_zik.android.utils.showRemainingSongTimeKey
import app.n_zik.android.utils.showSkipTimeButtonsKey
import app.n_zik.android.utils.showTopActionsBarKey
import app.n_zik.android.utils.showTotalTimeQueueKey
import app.n_zik.android.utils.showVisualizerStateKey
import app.n_zik.android.utils.showalbumcoverKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.showsongsKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.showvisthumbnailKey
import app.n_zik.android.utils.showVisualizerButtonsKey
import app.n_zik.android.utils.blackBackgroundForVisThumbnailKey
import app.n_zik.android.utils.statsExpandedKey
import app.n_zik.android.utils.statsfornerdsKey
import app.n_zik.android.utils.swipeAnimationsNoThumbnailKey
import app.n_zik.android.utils.swipeUpQueueKey
import app.n_zik.android.utils.tapqueueKey
import app.n_zik.android.utils.textoutlineKey
import app.n_zik.android.utils.thumbnailTapEnabledKey
import app.n_zik.android.utils.thumbnailTypeKey
import app.n_zik.android.utils.thumbnailpauseKey
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.timelineExpandedKey
import app.n_zik.android.utils.titleExpandedKey
import app.n_zik.android.utils.topPaddingKey
import app.n_zik.android.utils.transparentBackgroundPlayerActionBarKey
import app.n_zik.android.utils.transparentbarKey
import app.n_zik.android.utils.visualizerEnabledKey
import app.n_zik.android.utils.uiRoundnessDpKey
import app.n_zik.android.utils.visualizerWhiteColorOptionKey
import app.n_zik.android.utils.thumbnailSizeLDpKey
import app.n_zik.android.utils.thumbnailSizeDpKey
import app.n_zik.android.utils.lyricsColorKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.utils.lyricsCustomColorKey
import app.n_zik.android.utils.visualizerLineThicknessKey
import app.n_zik.android.utils.visualizerCustomColorKey

/** Stable local inventory; global search activation is a separate change. */
internal object AppearanceSettingTargets {
    val cards = listOf(
        AppearanceCardTargets("appearance.card.presets", listOf(playerTypeKey, queueTypeKey, thumbnailTypeKey, playerInfoTypeKey, playerInfoShowIconsKey, playerControlsTypeKey, iconLikeTypeKey), listOf("appearancePreset")),
        AppearanceCardTargets("appearance.card.cover", listOf(showthumbnailKey, thumbnailSizeDpKey, thumbnailSizeLDpKey, thumbnailRoundnessDpKey, artistThumbnailRoundnessDpKey, uiRoundnessDpKey, thumbnailpauseKey, cropVideoThumbnailsKey, statsfornerdsKey, noblurKey, fadingedgeKey, carouselKey, carouselSizeKey, showCoverThumbnailAnimationKey, coverThumbnailAnimationKey, swipeAnimationsNoThumbnailKey), listOf()),
        AppearanceCardTargets("appearance.card.timeline", listOf(transparentbarKey, playerPlayButtonTypeKey, playerTimelineTypeKey, playerTimelineSizeKey, playerControlsColorsKey, iconLikeTypeKey, playerSwapControlsWithTimelineKey, buttonzoomoutKey), listOf("MiniPlayerButtonsSettingsDialog")),
        AppearanceCardTargets("appearance.card.colors", listOf(blackgradientKey, bottomgradientKey, textoutlineKey, showTotalTimeQueueKey, showRemainingSongTimeKey, showSkipTimeButtonsKey, playerBackgroundColorsKey, animatedGradientKey, shakeSensitivityThemeKey, albumCoverRotationKey), listOf()),
        AppearanceCardTargets("appearance.card.behavior", listOf(disablePlayerHorizontalSwipeKey, playerSwipeSensitivityKey, disableScrollingTextKey, visualizerEnabledKey, visualizerWhiteColorOptionKey, visualizerCustomColorKey, visualizerLineThicknessKey, effectRotationKey, backgroundProgressKey, showNextSongsInPlayerKey, showTopActionsBarKey, showvisthumbnailKey, blackBackgroundForVisThumbnailKey, showVisualizerButtonsKey, showsongsKey, showalbumcoverKey, showVisualizerStateKey, topPaddingKey), listOf()),
        AppearanceCardTargets("appearance.card.lyrics", listOf(showlyricsthumbnailKey, thumbnailTapEnabledKey, clickOnLyricsTextKey, lyricsColorKey, lyricsCustomColorKey, showBackgroundLyricsKey, playerEnableLyricsPopupMessageKey, showLyricsStateKey), listOf()),
        AppearanceCardTargets("appearance.card.actionBar", listOf(transparentBackgroundPlayerActionBarKey, actionspacedevenlyKey, tapqueueKey, swipeUpQueueKey), listOf("PlayerActionBarSettingsDialog")),
        AppearanceCardTargets("appearance.card.fullscreenLyrics", listOf(queueDurationExpandedKey, titleExpandedKey, timelineExpandedKey, controlsExpandedKey, miniQueueExpandedKey, statsExpandedKey, actionExpandedKey), listOf()),
        AppearanceCardTargets("appearance.card.notification", listOf(notificationPlayerFirstIconKey, notificationPlayerSecondIconKey), listOf()),
        AppearanceCardTargets("appearance.card.reset", listOf(), listOf("applyAppearanceDefaults")),
    )
    val ids = cards.flatMap { card ->
        listOf(card.id) + card.preferenceKeys.map { "${card.id}.setting.$it" } +
            card.actions.map { "${card.id}.action.$it" }
    }
}

internal data class AppearanceCardTargets(
    val id: String,
    val preferenceKeys: List<String>,
    val actions: List<String>,
)
