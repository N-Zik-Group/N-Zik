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
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.sliders.SliderControl
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.lyricsSizeKey
import app.it.fast4x.rimusic.utils.lyricsSizeLKey
import app.it.fast4x.rimusic.utils.rememberPreference

/** Lyrics size tuning dialog on the shared [DefaultDialog]: one slider in portrait, one in landscape. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LyricsSizeDialog(
    onDismiss: () -> Unit,
    sizeValue: (Float) -> Unit,
    sizeValueL: (Float) -> Unit,
) {
    var lyricsSize by rememberPreference(lyricsSizeKey, 20f)
    var lyricsSizeL by rememberPreference(lyricsSizeLKey, 20f)
    DefaultDialog(
        onDismiss = {
            sizeValue(lyricsSize)
            sizeValueL(lyricsSizeL)
            onDismiss()
        }
    ) {

        if (!isLandscape) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        lyricsSize = 20f
                    },
                    icon = R.drawable.text,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier
                        .size(24.dp)
                )

                SliderControl(
                    state = lyricsSize,
                    onSlide = { lyricsSize = it },
                    onSlideComplete = {},
                    toDisplay = { "%.0f".format(it) },
                    stepSize = 1f,
                    range = 18f..100f
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
                        lyricsSizeL = 20f
                    },
                    icon = R.drawable.text,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier
                        .size(24.dp)
                )

                SliderControl(
                    state = lyricsSizeL,
                    onSlide = { lyricsSizeL = it },
                    onSlideComplete = {},
                    toDisplay = { "%.0f".format(it) },
                    range = 18f..100f,
                    stepSize = 1f
                )
            }
        }
    }
}
