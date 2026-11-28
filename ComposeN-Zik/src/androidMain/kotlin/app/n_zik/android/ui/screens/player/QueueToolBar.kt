package app.n_zik.android.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.Icon as ToolbarIcon
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.components.glass.GlassQueueToolbarActions
import app.n_zik.android.components.glass.queueToolbarBottomInset
import app.n_zik.android.components.glass.GlassQueueToolbar
import app.n_zik.android.components.glass.QueueEmbeddedMini
import app.n_zik.android.components.ui.InlineCircularLoader

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
                QueueToolbarCount(
                    R.drawable.musical_notes,
                    stringResource(R.string.accessibility_number_of_songs_in_queue),
                    QueueToolBarState.mediaItemCount,
                )
                val recommendationCount = QueueToolBarState.recommendationCount
                val loading = QueueToolBarState.smartShuffleLoading
                AnimatedVisibility(recommendationCount > 0 || loading) {
                    QueueToolbarCount(
                        R.drawable.smart_shuffle,
                        stringResource(R.string.recommendations_action_smart_shuffle),
                        recommendationCount,
                        loading,
                    )
                }
            }

            GlassQueueToolbarActions(
                buttons = QueueToolBarState.buttons,
                arrow = queueArrow,
            )
        }
    }
}

@Composable
private fun QueueToolbarCount(icon: Int, label: String, count: Int, loading: Boolean = false) {
    val color = colorPalette().text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        if (loading) {
            InlineCircularLoader(Modifier.size(18.dp), color = color)
        } else {
            BasicText(
                count.toString(),
                style = typography().s.copy(color = color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
