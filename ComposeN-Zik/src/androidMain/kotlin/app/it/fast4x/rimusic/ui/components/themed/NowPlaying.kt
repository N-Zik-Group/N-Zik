package app.it.fast4x.rimusic.ui.components.themed

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.ui.components.MusicAnimation
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.n_zik.android.colorPalette

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingSongIndicator (
    mediaId: String,
    // Player (not ExoPlayer): the guarded Listen Together facade is a plain Player — this
    // indicator only reads currentMediaItem (spec-listen-together-guest-lock-hardening).
    player: Player?,
    containerSize: Dp = Dimensions.thumbnails.song
) {

    if (player?.currentMediaItem?.mediaId != mediaId) return

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
//            .background(
//                color = Color.Black.copy(alpha = 0.25f),
//                shape = thumbnailShape()
//            )
            .size(containerSize)
    ) {
            MusicAnimation(
                color = colorPalette().onOverlay,
                modifier = Modifier
                    .fillMaxHeight(0.5f)
            )
    }

}


