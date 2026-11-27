package app.n_zik.android.components.settings.appearance


import app.n_zik.android.components.settings.common.SearchableSettingsSectionHeading
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.enums.CarouselSize
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.enums.SwipeAnimationNoThumbnail
import app.n_zik.android.enums.ThumbnailCoverType
import app.n_zik.android.utils.carouselKey
import app.n_zik.android.utils.carouselSizeKey
import app.n_zik.android.utils.coverThumbnailAnimationKey
import app.n_zik.android.utils.expandedplayerKey
import app.n_zik.android.utils.expandedplayertoggleKey
import app.n_zik.android.utils.fadingedgeKey
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.noblurKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showCoverThumbnailAnimationKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.DEFAULT_HOLD_TO_SPEED_UP_RATE
import app.n_zik.android.utils.HOLD_TO_SPEED_UP_RATE_RANGE
import app.n_zik.android.utils.holdToSpeedUpKey
import app.n_zik.android.utils.holdToSpeedUpRateKey
import app.n_zik.android.utils.statsfornerdsKey
import app.n_zik.android.utils.swipeAnimationsNoThumbnailKey
import app.n_zik.android.utils.thumbnailpauseKey
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.ui.styling.BoundedCornerSize
import androidx.compose.foundation.shape.CircleShape
import app.n_zik.android.components.player.rememberPlayerLandscapeArtworkSize
import androidx.compose.foundation.layout.Row
import app.n_zik.android.utils.thumbnailSizeDpKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import androidx.compose.foundation.shape.RoundedCornerShape
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.core.settings.DEFAULT_THUMBNAIL_SIZE_DP
import kotlin.math.round

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceCoverCard(search: Search) {
    var showthumbnail by rememberPreference(showthumbnailKey, true)
    var expandedplayer by rememberPreference(expandedplayerKey, false)
    var thumbnailSizeDp by rememberPreference(thumbnailSizeDpKey, DEFAULT_THUMBNAIL_SIZE_DP)
    var thumbnailSizeLDp by rememberPlayerLandscapeArtworkSize()
    var expandedplayertoggle by rememberPreference(expandedplayertoggleKey, false)
    var thumbnailRoundnessDp by rememberPreference(thumbnailRoundnessDpKey, 12f)
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )
    var thumbnailpause by rememberPreference(thumbnailpauseKey, true)
    var cropVideoThumbnails by rememberPreference(cropVideoThumbnailsKey, true)
    var statsfornerds by rememberPreference(statsfornerdsKey, false)
    var holdToSpeedUp by rememberPreference(holdToSpeedUpKey, false)
    var holdToSpeedUpRate by rememberPreference(holdToSpeedUpRateKey, DEFAULT_HOLD_TO_SPEED_UP_RATE)
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    var noblur by rememberPreference(noblurKey, true)
    var fadingedge by rememberPreference(fadingedgeKey, false)
    var carousel by rememberPreference(carouselKey, true)
    var carouselSize by rememberPreference(carouselSizeKey, CarouselSize.Biggest)
    var showCoverThumbnailAnimation by rememberPreference(showCoverThumbnailAnimationKey, false)
    var coverThumbnailAnimation by rememberPreference(coverThumbnailAnimationKey, ThumbnailCoverType.Vinyl)
    var swipeAnimationNoThumbnail by rememberPreference(swipeAnimationsNoThumbnailKey, SwipeAnimationNoThumbnail.Sliding)
    val searchCtx_0_2 = search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_section_title).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_visible_label).contains(search.inputValue, true) || stringResource(R.string.player_artwork_hidden_swipe_animation).contains(search.inputValue, true) || stringResource(R.string.settings_transition_slide_vertical).contains(search.inputValue, true) || stringResource(R.string.settings_transition_fade).contains(search.inputValue, true) || stringResource(R.string.settings_transition_scale).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_carousel_label).contains(search.inputValue, true) || stringResource(R.string.player_artwork_transition_circular).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_fading_edge_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_expanded_artwork_size_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_shrink_when_paused_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_animation_enabled_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_animation_style_label).contains(search.inputValue, true) || stringResource(R.string.player_thumbnail_size).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_corner_radius_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_background_blur_disabled_without_lyrics_label).contains(search.inputValue, true) || stringResource(R.string.player_artwork_technical_info_title).contains(search.inputValue, true)
    val matchesVideoCrop = search.inputValue.isBlank() ||
        stringResource(R.string.settings_player_crop_video_artwork_label).contains(search.inputValue, true)
    val holdToSpeedUpTitle = stringResource(R.string.player_artwork_hold_to_speed_up_title)
    val boostSpeedTitle = stringResource(R.string.player_artwork_hold_to_speed_up_rate_title)
    val matchesSpeedBoost = holdToSpeedUpTitle.contains(search.inputValue, ignoreCase = true) ||
        boostSpeedTitle.contains(search.inputValue, ignoreCase = true)
    AnimatedVisibility(
                visible = searchCtx_0_2 || matchesVideoCrop || matchesSpeedBoost,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
        SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.cover"),
            title = stringResource(R.string.settings_player_artwork_section_title),
            icon = R.drawable.album,
            content = {
                if (search.inputValue.isBlank()) SearchableSettingsSectionHeading(R.string.settings_group_artwork_layout, "appearance.cover_and_thumbnail.section.layout", showDivider = false)
            if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) {
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_visible_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_visible_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.cover_and_thumbnail.show_thumbnail"),
                        icon = R.drawable.image,
                            title = stringResource(R.string.settings_player_artwork_visible_label),
                            text = stringResource(R.string.player_artwork_show_thumbnail_description),
                            isChecked = showthumbnail,
                            onCheckedChange = {showthumbnail = it},
                        )
                    }
            }
            AnimatedVisibility(visible = !showthumbnail && playerType == PlayerType.Modern && !isLandscape) {

                if (search.inputValue.isBlank() || stringResource(R.string.player_artwork_hidden_swipe_animation).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.player_artwork_hidden_swipe_animation).contains(search.inputValue, true)) {
                        OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_artwork_hidden_swipe_animation_description), when (swipeAnimationNoThumbnail) {
                                    SwipeAnimationNoThumbnail.Sliding -> stringResource(R.string.settings_transition_slide_vertical)
                                    SwipeAnimationNoThumbnail.Fade -> stringResource(R.string.settings_transition_fade)
                                    SwipeAnimationNoThumbnail.Scale -> stringResource(R.string.settings_transition_scale)
                                    SwipeAnimationNoThumbnail.Carousel -> stringResource(R.string.settings_player_artwork_carousel_label)
                                    SwipeAnimationNoThumbnail.Circle -> stringResource(R.string.player_artwork_transition_circular)
                                }),
                        icon = R.drawable.gesture,
                            title = stringResource(R.string.player_artwork_hidden_swipe_animation),
                            selectedValue = swipeAnimationNoThumbnail,
                            onValueSelected = { swipeAnimationNoThumbnail = it },
                            valueText = {
                                when (it) {
                                    SwipeAnimationNoThumbnail.Sliding -> stringResource(R.string.settings_transition_slide_vertical)
                                    SwipeAnimationNoThumbnail.Fade -> stringResource(R.string.settings_transition_fade)
                                    SwipeAnimationNoThumbnail.Scale -> stringResource(R.string.settings_transition_scale)
                                    SwipeAnimationNoThumbnail.Carousel -> stringResource(R.string.settings_player_artwork_carousel_label)
                                    SwipeAnimationNoThumbnail.Circle -> stringResource(R.string.player_artwork_transition_circular)
                                }
                            },
                            modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.cover_and_thumbnail.swipe_Animation_No_Thumbnail")
                        )
                    }
            }

            // This preference also controls lists, widgets and external media artwork.
            if (matchesVideoCrop) {
                OtherSwitchSettingEntry(
                    icon = R.drawable.images_sharp,
                    title = stringResource(R.string.settings_player_crop_video_artwork_label),
                    text = stringResource(R.string.player_artwork_crop_video_thumbnails_description),
                    isChecked = cropVideoThumbnails,
                    onCheckedChange = { cropVideoThumbnails = it },
                    modifier = Modifier.settingsSearchTarget("appearance.cover_and_thumbnail.crop_video_thumbnails")
                )
            }

            AnimatedVisibility(visible = showthumbnail) {
                Column {

                    if (isLandscape) {
                        if (search.inputValue.isBlank() || stringResource(R.string.player_thumbnail_size).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.player_thumbnail_size).contains(search.inputValue, true)) {
                                Column(modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)) {
                                    SliderSettingsEntry(
                                        modifier = Modifier.settingsSearchTarget("appearance.cover_and_thumbnail.player_thumbnail_size"),
                                        icon = R.drawable.resize,
                                        title = stringResource(R.string.player_thumbnail_size),
                                        text = stringResource(R.string.player_artwork_size_description),
                                        state = thumbnailSizeLDp,
                                        range = 0f..100f,
                                        stepSize = 0f,
                                        defaultValue = thumbnailSizeDp,
                                        drawValuePoints = true,
                                        isIntegerOnly = true,
                                        onSlide = { thumbnailSizeLDp = kotlin.math.round(it) },
                                        toDisplay = { it.toInt().toString() },
                                        trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).border(width = 1.dp, color = colorPalette().accent, shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))), contentAlignment = Alignment.Center) { Box(modifier = Modifier.padding(((100f - thumbnailSizeLDp) * 0.15f).dp).fillMaxSize().background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)))) } } }
                                    )


                                }
                            }
                    } else {
                        if (search.inputValue.isBlank() || stringResource(R.string.player_thumbnail_size).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.player_thumbnail_size).contains(search.inputValue, true)) {
                                Column(modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)) {
                                    SliderSettingsEntry(
                                        modifier = Modifier.settingsSearchTarget("appearance.cover_and_thumbnail.player_thumbnail_size"),
                                        icon = R.drawable.resize,
                                        title = stringResource(R.string.player_thumbnail_size),
                                        text = stringResource(R.string.player_artwork_size_description),
                                        state = thumbnailSizeDp,
                                        range = 0f..100f,
                                        stepSize = 0f,
                                        defaultValue = DEFAULT_THUMBNAIL_SIZE_DP,
                                        drawValuePoints = true,
                                        isIntegerOnly = true,
                                        onSlide = { thumbnailSizeDp = kotlin.math.round(it) },
                                        toDisplay = { it.toInt().toString() },
                                        trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).border(width = 1.dp, color = colorPalette().accent, shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))), contentAlignment = Alignment.Center) { Box(modifier = Modifier.padding(((100f - thumbnailSizeDp) * 0.15f).dp).fillMaxSize().background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)))) } } }
                                    )



                            }
                            }
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_corner_radius_label).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_corner_radius_label).contains(search.inputValue, true)) {
                            Column(modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)) {
                                SliderSettingsEntry(
                                    modifier = Modifier.settingsSearchTarget("appearance.cover_and_thumbnail.thumbnail_roundness"),
                                    icon = R.drawable.image,
                                    title = stringResource(R.string.settings_player_artwork_corner_radius_label),
                                    text = stringResource(R.string.player_artwork_corner_roundness_description),
                                    state = thumbnailRoundnessDp,
                                    range = 0f..48f,
                                    stepSize = 0f,
                                    defaultValue = 12f,
                                    drawValuePoints = true,
                                    isIntegerOnly = true,
                                    onSlide = { thumbnailRoundnessDp = kotlin.math.round(it) },
                                    toDisplay = { it.toInt().toString() },
                                    trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Spacer(modifier = Modifier.size(36.dp).background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))).border(width = 1.dp, color = colorPalette().accent, shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)))) } }
                                )


                            }
                        }

                }
            }

                    if (search.inputValue.isBlank()) SearchableSettingsSectionHeading(R.string.settings_group_artwork_motion, "appearance.cover_and_thumbnail.section.motion")

                    if (playerType == PlayerType.Modern && !isLandscape && (expandedplayertoggle || expandedplayer)) {
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_carousel_label).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_carousel_label).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                        icon = R.drawable.images_sharp,
                                    title = stringResource(R.string.settings_player_artwork_carousel_label),
                                    text = stringResource(R.string.player_artwork_carousel_description),
                                    isChecked = carousel,
                                    onCheckedChange = { carousel = it },
                                    modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.cover_and_thumbnail.carousel")
                                )
                            }

                        if (search.inputValue.isBlank() || stringResource(R.string.settings_player_expanded_artwork_size_label).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_expanded_artwork_size_label).contains(search.inputValue, true)) {
                                OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_artwork_carousel_size_description), carouselSize.text),
                        icon = R.drawable.resize,
                                    title = stringResource(R.string.settings_player_expanded_artwork_size_label),
                                    selectedValue = carouselSize,
                                    onValueSelected = { carouselSize = it },
                                    valueText = { it.text },
                                    modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.cover_and_thumbnail.carouselsize")
                                )
                            }
                    }

                    if (playerType == PlayerType.Modern) {
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_fading_edge_label).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_fading_edge_label).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                        icon = R.drawable.drop_blur,
                                    title = stringResource(R.string.settings_player_artwork_fading_edge_label),
                                    text = stringResource(R.string.player_artwork_edge_fade_description),
                                    isChecked = fadingedge,
                                    onCheckedChange = { fadingedge = it },
                                    modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.cover_and_thumbnail.fadingedge")
                                )
                            }
                    }

                    if (playerType == PlayerType.Essential) {

                        if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_shrink_when_paused_label).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_shrink_when_paused_label).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                        icon = R.drawable.pause,
                                    title = stringResource(R.string.settings_player_artwork_shrink_when_paused_label),
                                    text = stringResource(R.string.player_artwork_shrink_when_paused_description),
                                    isChecked = thumbnailpause,
                                    onCheckedChange = { thumbnailpause = it },
                                    modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.cover_and_thumbnail.thumbnailpause")
                                )
                            }

                    }

                    if (showthumbnail && (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_animation_enabled_label).contains(
                            search.inputValue,
                            true
                        ))
                    ) {
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_animation_enabled_label).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                        icon = R.drawable.images_sharp,
                                title = stringResource(R.string.settings_player_artwork_animation_enabled_label),
                                text = stringResource(R.string.player_artwork_animation_enabled_description),
                                isChecked = showCoverThumbnailAnimation,
                                onCheckedChange = { showCoverThumbnailAnimation = it },
                                modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.cover_and_thumbnail.show_cover_thumbnail_animation")
                            )
                        }
                        AnimatedVisibility(visible = showCoverThumbnailAnimation) {
                            Column {
                                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_animation_style_label).contains(search.inputValue, true)) {
                                    OtherEnumValueSelectorSettingsEntry(
                        text = stringResource(R.string.settings_description_with_value, stringResource(R.string.player_artwork_animation_style_description), coverThumbnailAnimation.text),
                        icon = R.drawable.images_sharp,
                                        title = stringResource(R.string.settings_player_artwork_animation_style_label),
                                        selectedValue = coverThumbnailAnimation,
                                        onValueSelected = { coverThumbnailAnimation = it },
                                        valueText = { it.text },
                                        modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 50.dp else 25.dp)).settingsSearchTarget("appearance.cover_and_thumbnail.cover_thumbnail_animation_type")
                                    )
                                }
                            }
                        }
                    }

            if (!showthumbnail) {
                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_background_blur_disabled_without_lyrics_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_background_blur_disabled_without_lyrics_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.cover_and_thumbnail.noblur"),
                        icon = R.drawable.eye_off,
                            title = stringResource(R.string.settings_player_background_blur_disabled_without_lyrics_label),
                            text = stringResource(R.string.player_artwork_blur_disabled_description),
                            isChecked = noblur,
                            onCheckedChange = { noblur = it }
                        )
                    }


            }

            if (!(showthumbnail && playerType == PlayerType.Essential)){
                if (search.inputValue.isBlank() || stringResource(R.string.player_artwork_technical_info_title).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.player_artwork_technical_info_title).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.cover_and_thumbnail.statsfornerdsplayer"),
                        icon = R.drawable.stats_chart,
                            title = stringResource(R.string.player_artwork_technical_info_title),
                            text = stringResource(R.string.player_artwork_technical_info_description),
                            isChecked = statsfornerds,
                            onCheckedChange = { statsfornerds = it }
                        )
                    }
            }

            if (matchesSpeedBoost) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.cover_and_thumbnail.hold_to_speed_up"),
                    icon = R.drawable.play_forward,
                    title = holdToSpeedUpTitle,
                    text = stringResource(R.string.player_artwork_hold_to_speed_up_description),
                    isChecked = holdToSpeedUp,
                    onCheckedChange = { holdToSpeedUp = it }
                )
                AnimatedVisibility(visible = holdToSpeedUp) {
                    SliderSettingsEntry(
                        modifier = Modifier
                            .padding(start = 25.dp)
                            .settingsSearchTarget("appearance.cover_and_thumbnail.hold_to_speed_up_rate"),
                        icon = R.drawable.slow_motion,
                        title = boostSpeedTitle,
                        text = stringResource(R.string.player_artwork_hold_to_speed_up_rate_description),
                        state = holdToSpeedUpRate.takeIf { it in HOLD_TO_SPEED_UP_RATE_RANGE }
                            ?: DEFAULT_HOLD_TO_SPEED_UP_RATE,
                        range = HOLD_TO_SPEED_UP_RATE_RANGE,
                        defaultValue = DEFAULT_HOLD_TO_SPEED_UP_RATE,
                        stepSize = 0.1f,
                        onSlide = { holdToSpeedUpRate = round(it * 10f) / 10f },
                        toDisplay = { stringResource(R.string.player_playback_speed_value, it) },
                        snapToSteps = true,
                        drawStepPoints = false
                    )
                }
            }
            }
        )
            /* Removed Spacer */
            }
}
