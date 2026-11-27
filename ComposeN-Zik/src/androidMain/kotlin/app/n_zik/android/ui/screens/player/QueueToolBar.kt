package app.n_zik.android.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.Icon as ToolbarIcon
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.components.glass.GlassQueueToolbarActions
import app.n_zik.android.components.glass.queueToolbarBottomInset
import app.n_zik.android.components.glass.GlassQueueToolbar
import app.n_zik.android.components.glass.QueueEmbeddedMini
import app.n_zik.android.components.ui.CollectionSmartShuffleCount

object QueueToolBarState {
    var mediaItemCount by mutableIntStateOf(0)
    var recommendationCount by mutableIntStateOf(0)
    var smartShuffleLoading by mutableStateOf(false)
    internal var selectionActive by mutableStateOf(false)
    internal var dragInProgress by mutableStateOf(false)
    var buttons: List<Button> by mutableStateOf(emptyList())
    var queueArrow: ToolbarIcon? = null
    var onBarClick: () -> Unit = {}
    var isVisible by mutableIntStateOf(0)

    fun reset() {
        mediaItemCount = 0
        recommendationCount = 0
        smartShuffleLoading = false
        selectionActive = false
        dragInProgress = false
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
    val toolbarHeight = 60.dp

    GlassQueueToolbar(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.common_action_close)) { QueueToolBarState.onBarClick() }
            .height(toolbarHeight + bottomInset)
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
                    // The Glass navigation strip extends the surface, not the mini-player slot.
                    .heightIn(max = toolbarHeight)
            ) { QueueEmbeddedMini { MiniPlayer({}, {}) } }
        }

        val queueArrow = QueueToolBarState.queueArrow

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.wrapContentWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.musical_notes),
                        contentDescription = stringResource(R.string.accessibility_number_of_songs_in_queue),
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
                val recommendationCount = QueueToolBarState.recommendationCount
                val loading = QueueToolBarState.smartShuffleLoading
                CollectionSmartShuffleCount(recommendationCount > 0 || loading, loading, recommendationCount)
            }

            GlassQueueToolbarActions(
                buttons = QueueToolBarState.buttons,
                arrow = queueArrow,
            )
        }
    }
}
