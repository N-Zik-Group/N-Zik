package app.n_zik.android.components.settings.appearance

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
import androidx.compose.material3.TextButton
import app.it.fast4x.rimusic.utils.thumbnailSizeLDpKey
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

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceCoverCard(search: Search) {
    var showthumbnail by rememberPreference(showthumbnailKey, true)
    var expandedplayer by rememberPreference(expandedplayerKey, false)
    var thumbnailSizeDp by rememberPreference(thumbnailSizeDpKey, 90f)
    var thumbnailSizeLDp by rememberPreference(thumbnailSizeLDpKey, 0f)
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
    val searchCtx_0_2 = search.inputValue.isBlank() || stringResource(R.string.cover_and_thumbnail).contains(search.inputValue, true) || stringResource(R.string.show_thumbnail).contains(search.inputValue, true) || stringResource(R.string.swipe_Animation_No_Thumbnail).contains(search.inputValue, true) || stringResource(R.string.te_slide_vertical).contains(search.inputValue, true) || stringResource(R.string.te_fade).contains(search.inputValue, true) || stringResource(R.string.te_scale).contains(search.inputValue, true) || stringResource(R.string.carousel).contains(search.inputValue, true) || stringResource(R.string.vt_circular).contains(search.inputValue, true) || stringResource(R.string.fadingedge).contains(search.inputValue, true) || stringResource(R.string.carouselsize).contains(search.inputValue, true) || stringResource(R.string.thumbnailpause).contains(search.inputValue, true) || stringResource(R.string.show_lyrics_thumbnail).contains(search.inputValue, true) || stringResource(R.string.showvisthumbnail).contains(search.inputValue, true) || stringResource(R.string.show_cover_thumbnail_animation).contains(search.inputValue, true) || stringResource(R.string.cover_thumbnail_animation_type).contains(search.inputValue, true) || stringResource(R.string.player_thumbnail_size).contains(search.inputValue, true) || stringResource(R.string.thumbnail_roundness).contains(search.inputValue, true) || stringResource(R.string.artist_roundness).contains(search.inputValue, true) || stringResource(R.string.noblur).contains(search.inputValue, true) || stringResource(R.string.statsfornerdsplayer).contains(search.inputValue, true)
    AnimatedVisibility(
                visible = searchCtx_0_2,
                enter = settingsCardEnter,
                exit = settingsCardExit
            ) {
        SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.cover"),
            title = stringResource(R.string.cover_and_thumbnail),
            icon = R.drawable.album,
            content = {
            if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) {
                if (search.inputValue.isBlank() || stringResource(R.string.show_thumbnail).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.show_thumbnail).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.image,
                            title = stringResource(R.string.show_thumbnail),
                            text = "",
                            isChecked = showthumbnail,
                            onCheckedChange = {showthumbnail = it},
                        )
                    }
            }
            AnimatedVisibility(visible = !showthumbnail && playerType == PlayerType.Modern && !isLandscape) {
                if (search.inputValue.isBlank() || stringResource(R.string.swipe_Animation_No_Thumbnail).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.swipe_Animation_No_Thumbnail).contains(search.inputValue, true)) {
                        OtherEnumValueSelectorSettingsEntry(
                        icon = R.drawable.gesture,
                            title = stringResource(R.string.swipe_Animation_No_Thumbnail),
                            selectedValue = swipeAnimationNoThumbnail,
                            onValueSelected = { swipeAnimationNoThumbnail = it },
                            valueText = {
                                when (it) {
                                    SwipeAnimationNoThumbnail.Sliding -> stringResource(R.string.te_slide_vertical)
                                    SwipeAnimationNoThumbnail.Fade -> stringResource(R.string.te_fade)
                                    SwipeAnimationNoThumbnail.Scale -> stringResource(R.string.te_scale)
                                    SwipeAnimationNoThumbnail.Carousel -> stringResource(R.string.carousel)
                                    SwipeAnimationNoThumbnail.Circle -> stringResource(R.string.vt_circular)
                                }
                            },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )
                    }
            }
            AnimatedVisibility(visible = showthumbnail) {
                Column {
                    if (playerType == PlayerType.Modern) {
                        if (search.inputValue.isBlank() || stringResource(R.string.fadingedge).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.fadingedge).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                        icon = R.drawable.drop_blur,
                                    title = stringResource(R.string.fadingedge),
                                    text = "",
                                    isChecked = fadingedge,
                                    onCheckedChange = { fadingedge = it },
                                    modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                                )
                            }
                    }

                    if (playerType == PlayerType.Modern && !isLandscape && (expandedplayertoggle || expandedplayer)) {
                        if (search.inputValue.isBlank() || stringResource(R.string.carousel).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.carousel).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                        icon = R.drawable.images_sharp,
                                    title = stringResource(R.string.carousel),
                                    text = "",
                                    isChecked = carousel,
                                    onCheckedChange = { carousel = it },
                                    modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                                )
                            }

                        if (search.inputValue.isBlank() || stringResource(R.string.carouselsize).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.carouselsize).contains(search.inputValue, true)) {
                                OtherEnumValueSelectorSettingsEntry(
                        icon = R.drawable.resize,
                                    title = stringResource(R.string.carouselsize),
                                    selectedValue = carouselSize,
                                    onValueSelected = { carouselSize = it },
                                    valueText = { it.text },
                                    modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                                )
                            }
                    }
                    if (playerType == PlayerType.Essential) {

                        if (search.inputValue.isBlank() || stringResource(R.string.thumbnailpause).contains(
                                search.inputValue,
                                true
                            )
                        )
                            if (search.inputValue.isBlank() || stringResource(R.string.thumbnailpause).contains(search.inputValue, true)) {
                                OtherSwitchSettingEntry(
                        icon = R.drawable.pause,
                                    title = stringResource(R.string.thumbnailpause),
                                    text = "",
                                    isChecked = thumbnailpause,
                                    onCheckedChange = { thumbnailpause = it },
                                    modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                                )
                            }

                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.crop_video_thumbnails).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            icon = R.drawable.images_sharp,
                            title = stringResource(R.string.crop_video_thumbnails),
                            text = "",
                            isChecked = cropVideoThumbnails,
                            onCheckedChange = { cropVideoThumbnails = it },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.show_cover_thumbnail_animation).contains(
                            search.inputValue,
                            true
                        )
                    ) {
                        if (search.inputValue.isBlank() || stringResource(R.string.show_cover_thumbnail_animation).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                        icon = R.drawable.images_sharp,
                                title = stringResource(R.string.show_cover_thumbnail_animation),
                                text = "",
                                isChecked = showCoverThumbnailAnimation,
                                onCheckedChange = { showCoverThumbnailAnimation = it },
                                modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                            )
                        }
                        AnimatedVisibility(visible = showCoverThumbnailAnimation) {
                            Column {
                                if (search.inputValue.isBlank() || stringResource(R.string.cover_thumbnail_animation_type).contains(search.inputValue, true)) {
                                    OtherEnumValueSelectorSettingsEntry(
                        icon = R.drawable.images_sharp,
                                        title = stringResource(R.string.cover_thumbnail_animation_type),
                                        selectedValue = coverThumbnailAnimation,
                                        onValueSelected = { coverThumbnailAnimation = it },
                                        valueText = { it.text },
                                        modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 50.dp else 25.dp)
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
                                        icon = R.drawable.resize,
                                        title = stringResource(R.string.player_thumbnail_size),
                                        text = "",
                                        state = thumbnailSizeLDp,
                                        range = 0f..100f,
                                        stepSize = 0f,
                                        defaultValue = 100f,
                                        drawValuePoints = true,
                                        isIntegerOnly = true,
                                        onSlide = { thumbnailSizeLDp = kotlin.math.round(it) },
                                        toDisplay = { it.toInt().toString() },
                                        trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).border(width = 1.dp, color = colorPalette().accent, shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))), contentAlignment = Alignment.Center) { Box(modifier = Modifier.padding(((100f - thumbnailSizeLDp) * 0.15f).dp).fillMaxSize().background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)))) } } }
                                    )

                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    listOf(0f to "Min", 25f to "25%", 50f to "50%", 75f to "75%", 100f to "Max").forEach { (v, label) ->
                                        val isSelected = thumbnailSizeLDp.toInt() == v.toInt()
                                        TextButton(
                                            onClick = { thumbnailSizeLDp = v },
                                            shape = uiRoundnessShape(),
                                            colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                color = if (isSelected) colorPalette().accent else colorPalette().text,
                                                fontWeight = if (isSelected) FontWeight.Bold else null
                                            )
                                        }
                                    }
                                }
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
                                        icon = R.drawable.resize,
                                        title = stringResource(R.string.player_thumbnail_size),
                                        text = "",
                                        state = thumbnailSizeDp,
                                        range = 0f..100f,
                                        stepSize = 0f,
                                        defaultValue = 90f,
                                        drawValuePoints = true,
                                        isIntegerOnly = true,
                                        onSlide = { thumbnailSizeDp = kotlin.math.round(it) },
                                        toDisplay = { it.toInt().toString() },
                                        trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).border(width = 1.dp, color = colorPalette().accent, shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))), contentAlignment = Alignment.Center) { Box(modifier = Modifier.padding(((100f - thumbnailSizeDp) * 0.15f).dp).fillMaxSize().background(color = colorPalette().accent.copy(alpha = 0.5f), shape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f)))) } } }
                                    )

                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    listOf(0f to "Min", 25f to "25%", 50f to "50%", 75f to "75%", 100f to "Max").forEach { (v, label) ->
                                        val isSelected = thumbnailSizeDp.toInt() == v.toInt()
                                        TextButton(
                                            onClick = { thumbnailSizeDp = v },
                                            shape = uiRoundnessShape(),
                                            colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                color = if (isSelected) colorPalette().accent else colorPalette().text,
                                                fontWeight = if (isSelected) FontWeight.Bold else null
                                            )
                                        }
                                    }
                                }

                            }
                            }
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.thumbnail_roundness).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.thumbnail_roundness).contains(search.inputValue, true)) {
                            Column(modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)) {
                                SliderSettingsEntry(
                                    icon = R.drawable.image,
                                    title = stringResource(R.string.thumbnail_roundness),
                                    text = "",
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

                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(0f to "0", 12f to "12", 24f to "24", 36f to "36", 48f to "Max").forEach { (v, label) ->
                                    val isSelected = thumbnailRoundnessDp.toInt() == v.toInt()
                                    TextButton(
                                        onClick = { thumbnailRoundnessDp = v },
                                        shape = uiRoundnessShape(),
                                        colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = if (isSelected) colorPalette().accent else colorPalette().text,
                                            fontWeight = if (isSelected) FontWeight.Bold else null
                                        )
                                    }
                                }
                            }
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
                                    icon = R.drawable.image,
                                    title = stringResource(R.string.artist_roundness),
                                    text = "",
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

                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(0f to "0", 12f to "12", 24f to "24", 36f to "36", 48f to "Max").forEach { (v, label) ->
                                    val isSelected = artistThumbnailRoundnessDp.toInt() == v.toInt()
                                    TextButton(
                                        onClick = { artistThumbnailRoundnessDp = v },
                                        shape = uiRoundnessShape(),
                                        colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = if (isSelected) colorPalette().accent else colorPalette().text,
                                            fontWeight = if (isSelected) FontWeight.Bold else null
                                        )
                                    }
                                }
                            }
                            }
                        }

                    if (search.inputValue.isBlank() || stringResource(R.string.ui_roundness).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.ui_roundness).contains(search.inputValue, true)) {
                            SliderSettingsEntry(
                                icon = R.drawable.ui,
                                title = stringResource(R.string.ui_roundness),
                                text = "",
                                state = uiRoundnessDp,
                                range = 0f..40f,
                                stepSize = 0f,
                                defaultValue = 25f,
                                drawValuePoints = true,
                                isIntegerOnly = true,
                                onSlide = { uiRoundnessDp = kotlin.math.round(it) },
                                toDisplay = { it.toInt().toString() },
                                trailingContent = @Composable { Row(verticalAlignment = Alignment.CenterVertically) { Spacer(modifier = Modifier.size(36.dp).background(color = colorPalette().accent.copy(alpha = 0.5f), shape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f))).border(width = 1.dp, color = colorPalette().accent, shape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f)))) } },
                                modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                            )

                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf(0f to "0", 10f to "10", 20f to "20", 30f to "30", 40f to "Max").forEach { (v, label) ->
                                    val isSelected = uiRoundnessDp.toInt() == v.toInt()
                                    TextButton(
                                        onClick = { uiRoundnessDp = v },
                                        shape = uiRoundnessShape(),
                                        colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = if (isSelected) colorPalette().accent else colorPalette().text,
                                            fontWeight = if (isSelected) FontWeight.Bold else null
                                        )
                                    }
                                }
                            }
                        }
                }
            }

            if (!showthumbnail) {
                if (search.inputValue.isBlank() || stringResource(R.string.noblur).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.noblur).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.eye_off,
                            title = stringResource(R.string.noblur),
                            text = "",
                            isChecked = noblur,
                            onCheckedChange = { noblur = it }
                        )
                    }


            }

            if (!(showthumbnail && playerType == PlayerType.Essential)){
                if (search.inputValue.isBlank() || stringResource(R.string.statsfornerdsplayer).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.statsfornerdsplayer).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        icon = R.drawable.stats_chart,
                            title = stringResource(R.string.statsfornerdsplayer),
                            text = "",
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
