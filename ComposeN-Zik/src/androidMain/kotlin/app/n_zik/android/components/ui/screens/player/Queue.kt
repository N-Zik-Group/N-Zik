package app.n_zik.android.components.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.LazyListState
import androidx.media3.common.Player
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.QueueLoopType
import kotlinx.coroutines.CoroutineScope
import app.it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Icon
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.components.tab.ItemSelector
import app.it.fast4x.rimusic.utils.discoverKey
import app.it.fast4x.rimusic.utils.queueLoopTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showButtonPlayerArrowKey
import app.it.fast4x.rimusic.utils.shuffleQueue
import app.it.fast4x.rimusic.utils.smoothScrollToTop
import kotlinx.coroutines.launch
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar

@SuppressLint("ComposableNaming")
@Composable
fun Discover(
    isDiscoverClickable: Boolean,
    onDiscoverClick: () -> Unit
): MenuIcon = object: MenuIcon, Descriptive, DynamicColor {
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.discover )
    override val iconId: Int = R.drawable.discover
    override val messageId: Int = R.string.discoverinfo

    // Active state of this button
    override var isFirstColor: Boolean by rememberPreference(discoverKey, false)
    
    override val isEnabled: Boolean = true

    override val color: androidx.compose.ui.graphics.Color
        @Composable
        get() = if (isDiscoverClickable) super<DynamicColor>.color else super<DynamicColor>.color.copy(alpha = 0.4f)

    override fun onShortClick() {
        onDiscoverClick()
    }
}

class Repeat private constructor(
    private val typeState: MutableState<QueueLoopType>
): MenuIcon, Descriptive {

    companion object {
        @JvmStatic
        @Composable
        fun init(): Repeat = Repeat(
            rememberPreference( queueLoopTypeKey, QueueLoopType.Default )
        )
    }

    var type: QueueLoopType
        get() = typeState.value
        private set(value) {
            typeState.value = value
        }

    override val iconId: Int = -1   // Unused
    override val messageId: Int = R.string.repeat
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val icon: Painter
        @Composable
        get() = painterResource( type.iconId )

    @Composable
    override fun ToolBarButton() {
        TabToolBar.Icon(
            icon,
            color,
            TabToolBar.TOOLBAR_ICON_SIZE,
            true,
            androidx.compose.ui.Modifier,
            this::onShortClick
        )
    }

    override fun onShortClick() { type = type.next() }
}

@SuppressLint("ComposableNaming")
@Composable
fun ShuffleQueue(
    player: Player,
    lazyListState: LazyListState,
    coroutineScope: CoroutineScope,
    itemSelector: ItemSelector<Song>? = null
): MenuIcon = object: MenuIcon, Descriptive {
    override val iconId: Int = R.drawable.shuffle
    override val messageId: Int = R.string.shuffle
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        coroutineScope.launch {
            lazyListState.smoothScrollToTop()
        }.invokeOnCompletion {
            if (itemSelector != null && itemSelector.isNotEmpty()) {
                val selectedIds = itemSelector.map { it.id }.toSet()
                val selectedIndices = mutableListOf<Int>()
                for (i in 0 until player.mediaItemCount) {
                    val item = player.getMediaItemAt(i)
                    if (item.mediaId in selectedIds) {
                        selectedIndices.add(i)
                    }
                }
                selectedIndices.sort()
                
                if (selectedIndices.isNotEmpty()) {
                    val selectedMediaItems = selectedIndices.map { player.getMediaItemAt(it) }
                    val shuffled = selectedMediaItems.shuffled()
                    selectedIndices.reversed().forEach { player.removeMediaItem(it) }
                    val insertAt = selectedIndices.minOrNull() ?: 0
                    shuffled.forEachIndexed { index, mediaItem ->
                        player.addMediaItem(insertAt + index, mediaItem)
                    }
                }
            } else {
                player.shuffleQueue()
            }
        }
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun DeleteFromQueue(
    onDeleteConfirm: ConfirmDialog.() -> Unit
): MenuIcon = object: MenuIcon, Descriptive, ConfirmDialog {
    override val iconId: Int = R.drawable.trash
    override val messageId: Int = R.string.remove_from_queue
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.clean_queue_confirm)

    override var isActive: Boolean by rememberSaveable { mutableStateOf(false) }

    override fun onShortClick() { isActive = !isActive }

    override fun onConfirm() = onDeleteConfirm()
}

@SuppressLint("ComposableNaming")
@Composable
fun QueueArrow(
    onShortClick: () -> Unit
): Icon = object: Icon {
    override val isEnabled: Boolean by rememberPreference( showButtonPlayerArrowKey, true )
    override val iconId: Int = R.drawable.chevron_down

    override fun onShortClick() = onShortClick()
}

