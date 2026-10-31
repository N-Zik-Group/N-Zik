package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.ThumbnailType

/** Hidden thumbnails and the compact Essential style use a full-background presentation. */
fun playerLandscapeArtworkColumnVisible(
    playerType: PlayerType,
    thumbnailType: ThumbnailType,
    expandedPlayer: Boolean,
    showThumbnail: Boolean = true,
): Boolean = !(playerType == PlayerType.Modern &&
    (!showThumbnail || (thumbnailType == ThumbnailType.Essential && !expandedPlayer)))

/** Keeps artwork beside controls unless the selected presentation uses full-background artwork. */
@Composable
fun PlayerLandscapeColumns(
    modifier: Modifier = Modifier,
    contentInsets: WindowInsets = WindowInsets.safeDrawing,
    showArtworkColumn: Boolean = true,
    artwork: @Composable ColumnScope.() -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier.fillMaxSize().windowInsetsPadding(contentInsets), verticalAlignment = Alignment.CenterVertically) {
        if (showArtworkColumn) Column(Modifier.weight(1f).fillMaxHeight().playerLandscapeArtworkBounds(), content = artwork)
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
            content = controls)
    }
}
