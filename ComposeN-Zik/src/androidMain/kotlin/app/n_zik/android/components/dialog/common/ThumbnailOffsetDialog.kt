package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.sliders.SliderControl
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.VinylSizeKey
import app.it.fast4x.rimusic.utils.expandedplayerKey
import app.it.fast4x.rimusic.utils.fadingedgeKey
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showCoverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.thumbnailFadeExKey
import app.it.fast4x.rimusic.utils.thumbnailFadeKey
import app.it.fast4x.rimusic.utils.thumbnailSpacingKey
import app.it.fast4x.rimusic.utils.thumbnailSpacingLKey

/** Thumbnail layout tuning dialog on the shared [DefaultDialog]: image cover size, edge fade and spacing sliders. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ThumbnailOffsetDialog(
    onDismiss: () -> Unit,
    spacingValue: (Float) -> Unit,
    spacingValueL: (Float) -> Unit,
    fadeValue: (Float) -> Unit,
    fadeValueEx: (Float) -> Unit,
    imageCoverSizeValue: (Float) -> Unit
) {
    val defaultFade = 5f
    val defaultSpacing = 0f
    val defaultImageCoverSize = 50f
    var thumbnailSpacing by rememberPreference(thumbnailSpacingKey, defaultSpacing)
    var thumbnailSpacingL by rememberPreference(thumbnailSpacingLKey, defaultSpacing)
    var thumbnailFade by rememberPreference(thumbnailFadeKey, defaultFade)
    var thumbnailFadeEx by rememberPreference(thumbnailFadeExKey, defaultFade)
    var fadingedge by rememberPreference(fadingedgeKey, false)
    var imageCoverSize by rememberPreference(VinylSizeKey, defaultImageCoverSize)
    val showCoverThumbnailAnimation by rememberPreference(showCoverThumbnailAnimationKey, false)
    val expandedplayer by rememberPreference(expandedplayerKey, false)
    DefaultDialog(
        onDismiss = {
            spacingValue(thumbnailSpacing)
            spacingValueL(thumbnailSpacingL)
            fadeValue(thumbnailFade)
            fadeValueEx(thumbnailFadeEx)
            imageCoverSizeValue(imageCoverSize)
            onDismiss()
        }
    ) {
        if (showCoverThumbnailAnimation) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        imageCoverSize = defaultImageCoverSize
                    },
                    icon = R.drawable.album,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier
                        .size(24.dp)
                )

                SliderControl(
                    state = imageCoverSize,
                    onSlide = { imageCoverSize = it },
                    onSlideComplete = {
                        imageCoverSizeValue(imageCoverSize)
                    },
                    stepSize = 5f,
                    range = 50f..100f
                )
            }
        }

        if (fadingedge && !isLandscape) {
            if (expandedplayer) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            thumbnailFadeEx = defaultFade
                        },
                        icon = R.drawable.droplet,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(24.dp)
                    )

                    SliderControl(
                        state = thumbnailFadeEx,
                        onSlide = { thumbnailFadeEx = it },
                        onSlideComplete = {},
                        toDisplay = { "%.0f".format(it) },
                        stepSize = 1f,
                        range = 0f..10f
                    )

                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            thumbnailFade = defaultFade
                        },
                        icon = R.drawable.droplet,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(24.dp)
                    )

                    SliderControl(
                        state = thumbnailFade,
                        onSlide = { thumbnailFade = it },
                        onSlideComplete = {},
                        toDisplay = { "%.0f".format(it) },
                        stepSize = 1f,
                        range = 0f..10f
                    )
                }
            }
        }
        if (expandedplayer && !isLandscape) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        thumbnailSpacing = defaultSpacing
                    },
                    icon = R.drawable.burger,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier
                        .size(24.dp)
                )

                SliderControl(
                    state = thumbnailSpacing,
                    onSlide = { thumbnailSpacing = it },
                    onSlideComplete = {},
                    toDisplay = { "%.0f".format(it) },
                    range = -50f..50f,
                    stepSize = 1f
                )
            }
        }
        if (isLandscape) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        thumbnailSpacingL = defaultSpacing
                    },
                    icon = R.drawable.burger,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(90f)
                )

                SliderControl(
                    state = thumbnailSpacingL,
                    onSlide = { thumbnailSpacingL = it },
                    onSlideComplete = {},
                    toDisplay = { "%.0f".format(it) },
                    range = -50f..50f,
                    stepSize = 1f
                )
            }
        }
    }
}
