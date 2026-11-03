package app.n_zik.android.components.settings.appearance


import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.CarouselSize
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.SwipeAnimationNoThumbnail
import app.it.fast4x.rimusic.enums.ThumbnailCoverType
import app.it.fast4x.rimusic.utils.carouselKey
import app.it.fast4x.rimusic.utils.carouselSizeKey
import app.it.fast4x.rimusic.utils.coverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.expandedplayerKey
import app.it.fast4x.rimusic.utils.expandedplayertoggleKey
import app.it.fast4x.rimusic.utils.fadingedgeKey
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.noblurKey
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showCoverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.statsfornerdsKey
import app.it.fast4x.rimusic.utils.swipeAnimationsNoThumbnailKey
import app.it.fast4x.rimusic.utils.thumbnailpauseKey
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.n_zik.android.components.tab.Search
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.utils.uiRoundnessDpKey
import androidx.compose.foundation.shape.CircleShape
import app.n_zik.android.components.glass.PageTextButton as TextButton
import app.n_zik.android.components.player.rememberPlayerLandscapeArtworkSize
import app.n_zik.android.uiRoundnessShape
import androidx.compose.foundation.layout.Row
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import app.it.fast4x.rimusic.utils.thumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.artistThumbnailRoundnessDpKey
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.core.settings.DEFAULT_THUMBNAIL_SIZE_DP

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
    var artistThumbnailRoundnessDp by rememberPreference(artistThumbnailRoundnessDpKey, 48f)
    var uiRoundnessDp by rememberPreference(uiRoundnessDpKey, 25f)
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )
    var thumbnailpause by rememberPreference(thumbnailpauseKey, true)
    var cropVideoThumbnails by rememberPreference(cropVideoThumbnailsKey, true)
    var statsfornerds by rememberPreference(statsfornerdsKey, false)
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    var noblur by rememberPreference(noblurKey, true)
    var fadingedge by rememberPreference(fadingedgeKey, false)
    var carousel by rememberPreference(carouselKey, true)
    var carouselSize by rememberPreference(carouselSizeKey, CarouselSize.Biggest)
    var showCoverThumbnailAnimation by rememberPreference(showCoverThumbnailAnimationKey, false)
    var coverThumbnailAnimation by rememberPreference(coverThumbnailAnimationKey, ThumbnailCoverType.Vinyl)
    var swipeAnimationNoThumbnail by rememberPreference(swipeAnimationsNoThumbnailKey, SwipeAnimationNoThumbnail.Sliding)
    val searchCtx_0_2 = search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_section_title).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_visible_label).contains(search.inputValue, true) || stringResource(R.string.player_artwork_hidden_swipe_animation).contains(search.inputValue, true) || stringResource(R.string.settings_transition_slide_vertical).contains(search.inputValue, true) || stringResource(R.string.settings_transition_fade).contains(search.inputValue, true) || stringResource(R.string.settings_transition_scale).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_carousel_label).contains(search.inputValue, true) || stringResource(R.string.player_artwork_transition_circular).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_fading_edge_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_expanded_artwork_size_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_shrink_when_paused_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_artwork_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_visualizer_artwork_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_animation_enabled_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_animation_style_label).contains(search.inputValue, true) || stringResource(R.string.player_thumbnail_size).contains(search.inputValue, true) || stringResource(R.string.settings_player_artwork_corner_radius_label).contains(search.inputValue, true) || stringResource(R.string.artist_roundness).contains(search.inputValue, true) || stringResource(R.string.settings_player_background_blur_disabled_without_lyrics_label).contains(search.inputValue, true) || stringResource(R.string.player_artwork_technical_info_title).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_0_2,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
        SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.cover"),
            title = stringResource(R.string.settings_player_artwork_section_title),
            icon = R.drawable.album,
            content = {
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
            AnimatedVisibility(visible = showthumbnail) {
                Column {
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

                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_crop_video_artwork_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            icon = R.drawable.images_sharp,
                            title = stringResource(R.string.settings_player_crop_video_artwork_label),
                            text = stringResource(R.string.player_artwork_crop_video_thumbnails_description),
                            isChecked = cropVideoThumbnails,
                            onCheckedChange = { cropVideoThumbnails = it },
                            modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.cover_and_thumbnail.crop_video_thumbnails")
                        )
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_artwork_animation_enabled_label).contains(
                            search.inputValue,
                            true
                        )
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

                    if (search.inputValue.isBlank() || stringResource(R.string.artist_roundness).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.artist_roundness).contains(search.inputValue, true)) {
                            Column(modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)) {
                                SliderSettingsEntry(
                                    modifier = Modifier.settingsSearchTarget("appearance.cover_and_thumbnail.artist_roundness"),
                                    icon = R.drawable.image,
                                    title = stringResource(R.string.artist_roundness),
                                    text = stringResource(R.string.interface_artist_image_roundness_description),
                                    state = artistThumbnailRoundnessDp,
                                    range = 0f..48f,
                                    stepSize = 0f,
                                    defaultValue = 48f,
                                    drawValuePoints = true,
                                    isIntegerOnly = true,
                                    onSlide = { artistThumbnailRoundnessDp = kotlin.math.round(it) },
                                    toDisplay = { it.toInt().toString() },
                                    trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Spacer(modifier = Modifier.size(36.dp).background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f))).border(width = 1.dp, color = colorPalette().accent, shape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f)))) } }
                                )


                            }
                        }

                    if (search.inputValue.isBlank() || stringResource(R.string.settings_interface_corner_radius_label).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_interface_corner_radius_label).contains(search.inputValue, true)) {
                            SliderSettingsEntry(
                                icon = R.drawable.ui,
                                title = stringResource(R.string.settings_interface_corner_radius_label),
                                text = stringResource(R.string.interface_corner_roundness_description),
                                state = uiRoundnessDp,
                                range = 0f..40f,
                                stepSize = 0f,
                                defaultValue = 25f,
                                drawValuePoints = true,
                                isIntegerOnly = true,
                                onSlide = { uiRoundnessDp = kotlin.math.round(it) },
                                toDisplay = { it.toInt().toString() },
                                trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Spacer(modifier = Modifier.size(36.dp).background(color = colorPalette().accent.copy(alpha = 0.5f), shape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f))).border(width = 1.dp, color = colorPalette().accent, shape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f)))) } },
                                modifier = (Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)).settingsSearchTarget("appearance.cover_and_thumbnail.ui_roundness")
                            )


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
            }
        )
            /* Removed Spacer */
            }
}
