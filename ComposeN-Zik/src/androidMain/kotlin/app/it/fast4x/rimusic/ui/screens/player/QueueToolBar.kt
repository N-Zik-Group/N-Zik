package app.it.fast4x.rimusic.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Icon as ToolbarIcon
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.isLandscape
import app.n_zik.android.topUiRoundnessShape
import app.n_zik.android.components.glass.GlassQueueToolbarActions
import app.n_zik.android.components.glass.queueToolbarBottomInset
import app.n_zik.android.components.glass.GlassQueueToolbar
import app.n_zik.android.components.glass.QueueEmbeddedMini

object QueueToolBarState {
    var mediaItemCount by mutableIntStateOf(0)
    var buttons: List<Button> = emptyList()
    var queueArrow: ToolbarIcon? = null
    var onBarClick: () -> Unit = {}
    var isVisible by mutableIntStateOf(0)

    fun reset() {
        mediaItemCount = 0
        buttons = emptyList()
        queueArrow = null
        onBarClick = {}
        isVisible = 0
    }
}

@SuppressLint("SuspiciousIndentation")
@Composable
fun QueueToolBar(
    modifier: Modifier = Modifier
) {
    val bottomInset = queueToolbarBottomInset()

    GlassQueueToolbar(
        modifier = modifier
            .fillMaxWidth()
            .clickable { QueueToolBarState.onBarClick() }
            .height(60.dp + bottomInset)
    ) {
        if (!isLandscape) {
            val miniPlayerOffset = Dimensions.miniPlayerHeight
            val searchBarHeight = 96.dp
            // Eased so the mini-player glides instead of jumping when the
            // queue search bar toggles (the offset shifts by a full bar)
            val yOffset by animateDpAsState(
                targetValue = if (QueueToolBarState.isVisible > 0) -(miniPlayerOffset + searchBarHeight) else -miniPlayerOffset,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "queueMiniPlayerYOffset"
            )

            Box(
                Modifier.offset(0.dp, yOffset)
                    .align(Alignment.TopCenter)
            ) { QueueEmbeddedMini { MiniPlayer({}, {}) } }
        }

        val queueArrow = QueueToolBarState.queueArrow

        if (queueArrow != null && !queueArrow.isEnabled)
            Image(
                painter = painterResource(R.drawable.horizontal_bold_line_rounded),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier
                    .absoluteOffset(0.dp, (-10).dp)
                    .align(Alignment.TopCenter)
                    .size(30.dp)
            )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .height(TabToolBar.TOOLBAR_ICON_SIZE)
                    .wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.musical_notes),
                    contentDescription = stringResource(R.string.cd_number_of_songs_in_queue),
                    tint = colorPalette().text,
                    modifier = Modifier.padding(end = 2.dp)
                )
                BasicText(
                    text = QueueToolBarState.mediaItemCount.toString(),
                    style = TextStyle(
                        color = colorPalette().text,
                        fontStyle = typography().l.fontStyle
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            GlassQueueToolbarActions(
                buttons = QueueToolBarState.buttons,
                arrow = queueArrow,
            )
        }
    }
}
