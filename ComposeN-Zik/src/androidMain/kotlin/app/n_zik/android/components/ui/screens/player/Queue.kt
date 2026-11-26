package app.n_zik.android.components.ui.screens.player

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyListState
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.enums.QueueLoopType
import kotlinx.coroutines.CoroutineScope
import app.n_zik.android.ui.components.tab.toolbar.ConfirmDialog
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.DynamicColor
import app.n_zik.android.ui.components.tab.toolbar.Icon
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.discoverButtonIconRes
import app.n_zik.android.components.shuffleButtonIconRes
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.ui.NzikCheckbox
import app.n_zik.android.listentogether.rememberListenTogetherGuestLock
import app.n_zik.android.utils.discoverKey
import app.n_zik.android.utils.queueLoopTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showButtonPlayerArrowKey
import app.n_zik.android.utils.shuffleQueue
import app.n_zik.android.utils.smoothScrollToTop
import kotlinx.coroutines.launch
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import timber.log.Timber
import app.n_zik.android.core.utils.Toaster

@SuppressLint("ComposableNaming")
@Composable
fun Discover(
    isDiscoverClickable: Boolean,
    onDiscoverClick: () -> Unit
): MenuIcon = object: MenuIcon, Descriptive, DynamicColor {
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.queue_discover_title )
    override val iconId: Int get() = discoverButtonIconRes(isFirstColor)
    override val messageId: Int = R.string.queue_discover_description

    // Active state of this button
    override var isFirstColor: Boolean by rememberPreference(discoverKey, false)

    private val guestLocked = rememberListenTogetherGuestLock()
    override val isEnabled: Boolean
        get() = !guestLocked

    override val color: Color
        @Composable
        get() = if (isDiscoverClickable) super<DynamicColor>.color else super<DynamicColor>.color.copy(alpha = 0.4f)

    override fun onShortClick() {
        if (!guestLocked) onDiscoverClick()
    }
}

class Repeat private constructor(
    private val typeState: MutableState<QueueLoopType>,
    private val guestLocked: Boolean
): MenuIcon, Descriptive {

    companion object {
        @JvmStatic
        @Composable
        fun init(): Repeat = Repeat(
            rememberPreference( queueLoopTypeKey, QueueLoopType.Default ),
            rememberListenTogetherGuestLock()
        )
    }

    var type: QueueLoopType
        get() = typeState.value
        private set(value) {
            typeState.value = value
        }

    override val iconId: Int = -1   // Unused
    override val messageId: Int = R.string.player_action_repeat
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val icon: Painter
        @Composable
        get() = painterResource( type.iconId )

    override val isEnabled: Boolean
        get() = !guestLocked

    @Composable
    override fun ToolBarButton() {
        TabToolBar.Icon(
            icon,
            color,
            TabToolBar.TOOLBAR_ICON_SIZE,
            isEnabled,
            Modifier,
            this::onShortClick
        )
    }

    override fun onShortClick() { if (!guestLocked) type = type.next() }
}

@SuppressLint("ComposableNaming")
@Composable
fun ShuffleQueue(
    player: Player,
    lazyListState: LazyListState,
    coroutineScope: CoroutineScope,
    itemSelector: ItemSelector<Any>,
    selectedWindowUids: () -> Set<Any> = { itemSelector.toSet() },
): MenuIcon = object: MenuIcon, Descriptive {
    private val serviceBinder = LocalPlayerServiceBinder.current
    override val iconId: Int get() = shuffleButtonIconRes( serviceBinder )
    override val messageId: Int = R.string.player_action_shuffle
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    private val guestLocked by rememberUpdatedState(rememberListenTogetherGuestLock())
    override val isEnabled: Boolean
        get() = !guestLocked && (!itemSelector.isActive || selectedWindowUids().size > 1)

    override fun onShortClick() {
        if (!isEnabled) return
        try {
            if (itemSelector.isActive) {
                val selectedIndices = selectedQueueIndices(player.currentTimeline, selectedWindowUids())
                if (selectedIndices.size < 2) return
                val shuffled = selectedIndices.shuffled()
                val desiredOrder = if (shuffled == selectedIndices) shuffled.drop(1) + shuffled.first() else shuffled
                shuffleQueueSelection(selectedIndices, desiredOrder, player::moveMediaItem)
                Toaster.s(R.string.player_queue_shuffled_count_format, formatArgs = *arrayOf(selectedIndices.size))
            } else if (!player.shuffleQueue()) {
                return
            }
        } catch (e: Exception) {
            Timber.tag("ShuffleQueue").e(e, "Failed to shuffle queue")
            Toaster.e(R.string.player_no_song_found)
            return
        }
        serviceBinder?.triggerShuffleOkFlash()
        coroutineScope.launch { lazyListState.smoothScrollToTop() }
    }
}

@OptIn(UnstableApi::class)
internal fun selectedQueueIndices(timeline: Timeline, selectedWindowUids: Set<Any>): List<Int> {
    val window = Timeline.Window()
    return (0 until timeline.windowCount).filter { index ->
        timeline.getWindow(index, window).uid in selectedWindowUids
    }
}

internal fun shuffleQueueSelection(
    selectedIndices: List<Int>,
    desiredOrder: List<Int>,
    moveMediaItem: (Int, Int) -> Unit,
) {
    val currentOrder = selectedIndices.toMutableList()
    desiredOrder.forEachIndexed { slot, item ->
        val sourceSlot = currentOrder.indexOf(item)
        if (slot != sourceSlot) {
            val from = selectedIndices[sourceSlot]
            val to = selectedIndices[slot]
            moveMediaItem(from, to)
            // The two moves swap selected slots while preserving intervening queue positions.
            if (from > to + 1) moveMediaItem(to + 1, from)
            currentOrder[sourceSlot] = currentOrder[slot]
            currentOrder[slot] = item
        }
    }
}

@SuppressLint("ComposableNaming")
@Composable
internal fun DeleteFromQueue(
    player: Player,
    itemSelector: ItemSelector<Any>,
    selectedWindowUids: () -> Set<Any>,
): MenuIcon = object: MenuIcon, Descriptive, ConfirmDialog {
    override val confirmationTitle: String
        @Composable get() = stringResource(R.string.player_queue_action_remove)
    override val confirmationAction: String
        @Composable get() = stringResource(R.string.common_action_remove)

    override val iconId: Int = R.drawable.trash
    override val messageId: Int = R.string.player_queue_action_remove
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val dialogTitle: String
        @Composable
        get() {
            val selected = pendingRemoval
            return if (selected != null)
                stringResource(R.string.player_queue_remove_selected_confirmation_format, selected.size)
            else
                stringResource( R.string.player_queue_clear_confirmation )
        }

    private val guestLocked = rememberListenTogetherGuestLock()
    private val binder = LocalPlayerServiceBinder.current
    override val isEnabled: Boolean
        get() = !guestLocked && (!itemSelector.isActive || selectedWindowUids().isNotEmpty())

    private var pendingRemoval by remember { mutableStateOf<Set<Any>?>(null) }
    private var finishSelection by remember { mutableStateOf<(() -> Unit)?>(null) }
    override var isActive: Boolean by remember { mutableStateOf(false) }

    override fun onShortClick() {
        if (!isEnabled) return
        pendingRemoval = if (itemSelector.isActive) {
            selectedWindowUids().takeIf { it.isNotEmpty() } ?: return
        } else null
        finishSelection = itemSelector.captureSelectionCompletion()
        isActive = true
    }

    override fun onConfirm() {
        if (!isActive || guestLocked) return
        val selected = pendingRemoval
        val complete = finishSelection
        onDismiss()
        try {
            if (selected == null) {
                binder?.onExplicitQueueClear()
                player.stop()
                player.clearMediaItems()
                Toaster.s(R.string.common_delete_success)
            } else {
                val indices = selectedQueueIndices(player.currentTimeline, selected)
                if (indices.isEmpty()) return
                indices.asReversed().forEach(player::removeMediaItem)
                Toaster.s(R.string.player_queue_deleted_item_format, formatArgs = *arrayOf(indices.size.toString()))
                complete?.invoke()
            }
        } catch (e: Exception) {
            Timber.tag("Queue").e(e, "Failed to delete from queue")
        }
    }
}

@Composable
internal fun QueueSelectionCheckbox(windowUid: Any, itemSelector: ItemSelector<Any>) {
    if (itemSelector.isActive) {
        val label = stringResource(R.string.common_action_select)
        NzikCheckbox(
            checked = windowUid in itemSelector,
            onCheckedChange = { if (itemSelector.isActive) itemSelector.toggle(windowUid) { it } },
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
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

