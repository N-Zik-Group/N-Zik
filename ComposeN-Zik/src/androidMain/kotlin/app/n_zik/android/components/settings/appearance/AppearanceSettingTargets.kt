package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.MiniPlayerButtonsSettingsDialog
import app.it.fast4x.rimusic.utils.actionExpandedKey
import app.it.fast4x.rimusic.utils.actionspacedevenlyKey
import app.it.fast4x.rimusic.utils.albumCoverRotationKey
import app.it.fast4x.rimusic.utils.animatedGradientKey
import app.it.fast4x.rimusic.utils.backgroundProgressKey
import app.it.fast4x.rimusic.utils.blackgradientKey
import app.it.fast4x.rimusic.utils.bottomgradientKey
import app.it.fast4x.rimusic.utils.buttonzoomoutKey
import app.it.fast4x.rimusic.utils.carouselKey
import app.it.fast4x.rimusic.utils.carouselSizeKey
import app.it.fast4x.rimusic.utils.clickOnLyricsTextKey
import app.it.fast4x.rimusic.utils.controlsExpandedKey
import app.it.fast4x.rimusic.utils.coverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.disablePlayerHorizontalSwipeKey
import app.it.fast4x.rimusic.utils.playerSwipeSensitivityKey
import app.it.fast4x.rimusic.utils.shakeSensitivityThemeKey
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.fadingedgeKey
import app.it.fast4x.rimusic.utils.iconLikeTypeKey
import app.it.fast4x.rimusic.utils.miniQueueExpandedKey
import app.it.fast4x.rimusic.utils.noblurKey
import app.it.fast4x.rimusic.utils.notificationPlayerFirstIconKey
import app.it.fast4x.rimusic.utils.notificationPlayerSecondIconKey
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerControlsTypeKey
import app.it.fast4x.rimusic.utils.playerControlsColorsKey
import app.it.fast4x.rimusic.utils.playerEnableLyricsPopupMessageKey
import app.it.fast4x.rimusic.utils.playerInfoShowIconsKey
import app.it.fast4x.rimusic.utils.playerInfoTypeKey
import app.it.fast4x.rimusic.utils.playerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.playerSwapControlsWithTimelineKey
import app.it.fast4x.rimusic.utils.playerTimelineSizeKey
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.queueDurationExpandedKey
import app.it.fast4x.rimusic.utils.queueTypeKey
import app.it.fast4x.rimusic.utils.showBackgroundLyricsKey
import app.it.fast4x.rimusic.utils.showCoverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.showLyricsStateKey
import app.it.fast4x.rimusic.utils.showNextSongsInPlayerKey
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.showSkipTimeButtonsKey
import app.it.fast4x.rimusic.utils.showTopActionsBarKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.showVisualizerStateKey
import app.it.fast4x.rimusic.utils.showalbumcoverKey
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showsongsKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.showvisthumbnailKey
import app.it.fast4x.rimusic.utils.showVisualizerButtonsKey
import app.it.fast4x.rimusic.utils.blackBackgroundForVisThumbnailKey
import app.it.fast4x.rimusic.utils.statsExpandedKey
import app.it.fast4x.rimusic.utils.statsfornerdsKey
import app.it.fast4x.rimusic.utils.swipeAnimationsNoThumbnailKey
import app.it.fast4x.rimusic.utils.swipeUpQueueKey
import app.it.fast4x.rimusic.utils.tapqueueKey
import app.it.fast4x.rimusic.utils.textoutlineKey
import app.it.fast4x.rimusic.utils.thumbnailTapEnabledKey
import app.it.fast4x.rimusic.utils.thumbnailTypeKey
import app.it.fast4x.rimusic.utils.thumbnailpauseKey
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.timelineExpandedKey
import app.it.fast4x.rimusic.utils.titleExpandedKey
import app.it.fast4x.rimusic.utils.topPaddingKey
import app.it.fast4x.rimusic.utils.transparentBackgroundPlayerActionBarKey
import app.it.fast4x.rimusic.utils.transparentbarKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.it.fast4x.rimusic.utils.uiRoundnessDpKey
import app.it.fast4x.rimusic.utils.visualizerWhiteColorOptionKey
import app.it.fast4x.rimusic.utils.thumbnailSizeLDpKey
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.lyricsColorKey
import app.it.fast4x.rimusic.utils.thumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.artistThumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.lyricsCustomColorKey
import app.it.fast4x.rimusic.utils.visualizerLineThicknessKey
import app.it.fast4x.rimusic.utils.visualizerCustomColorKey

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
