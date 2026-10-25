package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import android.content.Context

data class ToggleItem(
    val id: String,
    val iconRes: Int,
    val label: String,
    val preferenceKey: String,
    val defaultValue: Boolean,
    val description: String? = null
)

/**
 * Checkbox list with optional drag reorder. Rows are keyed by [ToggleItem.id], so
 * callers must pass unique ids. [onRowAction] (optional) renders a per-row remove icon
 * before the drag handle; it is hidden for the ids in [lockedCheckedIds].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToggleListDialog(
    items: List<ToggleItem>,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    reorderableState: ReorderableLazyListState? = null,
    contentHeight: Dp = 480.dp,
    enforceMinOneChecked: Boolean = false,
    maxChecked: Int = Int.MAX_VALUE,
    pinnedItemCount: Int = 0,
    lockedCheckedIds: Set<String> = emptySet(),
    checkedStatesOverride: List<Boolean>? = null,
    onCheckedChange: ((Int, Boolean) -> Unit)? = null,
    onRowAction: ((Int) -> Unit)? = null,
    extraFooterButton: DialogButton? = null,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null
) {
    val effectiveCheckedStates = checkedStatesOverride?.let { override ->
        items.mapIndexed { index, item ->
            if (item.id in lockedCheckedIds) true else override[index]
        }
    } ?: items.map { item ->
        if (item.id in lockedCheckedIds) true
        else key(item.id) {
            val isChecked by rememberPreference(item.preferenceKey, item.defaultValue)
            isChecked
        }
    }
    val checkedCount = effectiveCheckedStates.count { it }
    val hasButtons =
        onReset != null || onCancel != null || onConfirm != null || extraFooterButton != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = lazyListState
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.id }
            ) { index, item ->
                val isLocked = item.id in lockedCheckedIds
                val isChecked = effectiveCheckedStates[index]
                val isLastChecked = enforceMinOneChecked && isChecked && checkedCount <= 1
                val isMaxReached = !isChecked && checkedCount >= maxChecked
                val enabled = !isLocked && !isLastChecked && !isMaxReached

                val isPinned = index < pinnedItemCount

                if (reorderableState != null && !isPinned) {
                    ReorderableItem(reorderableState, key = item.id) { isDragging ->
                        ToggleRow(
                            icon = item.iconRes,
                            label = item.label,
                            isChecked = isChecked,
                            preferenceKey = item.preferenceKey,
                            showReorder = true,
                            isDragging = isDragging,
                            dragModifier = Modifier.draggableHandle(),
                            description = item.description,
                            enabled = enabled,
                            useLocalState = checkedStatesOverride != null,
                            onCheckedChange = if (onCheckedChange != null) { { onCheckedChange(index, it) } } else null,
                            onRowAction = if (onRowAction != null && !isLocked) { { onRowAction(index) } } else null
                        )
                    }
                } else {
                    ToggleRow(
                        icon = item.iconRes,
                        label = item.label,
                        isChecked = isChecked,
                        preferenceKey = item.preferenceKey,
                        showReorder = false,
                        isDragging = false,
                        dragModifier = Modifier,
                        description = item.description,
                        enabled = enabled,
                        useLocalState = checkedStatesOverride != null,
                        onCheckedChange = if (onCheckedChange != null) { { onCheckedChange(index, it) } } else null,
                        onRowAction = if (onRowAction != null && !isLocked) { { onRowAction(index) } } else null
                    )
                }
            }
        }

        if (hasButtons) {
            Spacer(modifier = Modifier.height(8.dp))

            ButtonRow(
                buttons = buildList {
                    // Optional dialog-specific pill (e.g. the burger dialog's "Add
                    // separator"), rendered leftmost so the standard Reset/Cancel/OK
                    // trio keeps its order and styling.
                    extraFooterButton?.let { extra ->
                        add(extra)
                    }
                    onReset?.let { reset ->
                        add(
                            DialogButton(
                                text = stringResource(R.string.reset),
                                onClick = reset
                            )
                        )
                    }
                    onCancel?.let { cancel ->
                        add(
                            DialogButton(
                                text = stringResource(R.string.cancel),
                                onClick = cancel,
                                danger = true
                            )
                        )
                    }
                    onConfirm?.let { confirm ->
                        add(
                            DialogButton(
                                text = stringResource(R.string.ok),
                                onClick = confirm,
                                primary = true
                            )
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    icon: Int,
    label: String,
    isChecked: Boolean,
    preferenceKey: String,
    showReorder: Boolean,
    isDragging: Boolean,
    dragModifier: Modifier,
    description: String? = null,
    enabled: Boolean = true,
    useLocalState: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onRowAction: (() -> Unit)? = null
) {
    val ctx = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 35.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(uiRoundnessShape())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (enabled) {
                            if (useLocalState) {
                                onCheckedChange?.invoke(!isChecked)
                            } else {
                                val prefs = ctx.getSharedPreferences("preferences", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean(preferenceKey, !isChecked).apply()
                            }
                        }
                    }
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) colorPalette().accent else colorPalette().textDisabled,
                modifier = Modifier.size(22.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                BasicText(
                    text = label,
                    maxLines = 1,
                    style = typography().xs.semiBold.copy(
                        color = if (isDragging) colorPalette().accent
                               else if (enabled) colorPalette().text
                               else colorPalette().textDisabled
                    ),
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                if (description != null) {
                    BasicText(
                        text = description,
                        maxLines = 1,
                        style = typography().xxs.copy(
                            color = colorPalette().textDisabled
                        ),
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
            }

            Icon(
                painter = painterResource(if (isChecked) R.drawable.checked_filled else R.drawable.unchecked_outline),
                contentDescription = null,
                tint = if (!enabled) colorPalette().textDisabled else if (isChecked) colorPalette().accent else colorPalette().textDisabled,
                modifier = Modifier.size(20.dp)
            )
        }

        if (onRowAction != null) {
            IconButton(
                onClick = { onRowAction() },
                modifier = Modifier
                    .size(24.dp)
                    .padding(4.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.delete),
                    tint = colorPalette().textDisabled,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (showReorder) {
            Icon(
                painter = painterResource(app.n_zik.android.R.drawable.reorder),
                contentDescription = null,
                tint = if (isDragging) colorPalette().accent else colorPalette().textDisabled,
                modifier = Modifier
                    .size(24.dp)
                    .padding(4.dp)
                    .then(dragModifier)
            )
        }
    }
}
