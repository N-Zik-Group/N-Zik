package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.medium
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
    val hasButtons = onReset != null || onCancel != null || onConfirm != null

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
                            onCheckedChange = if (onCheckedChange != null) { { onCheckedChange(index, it) } } else null
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
                        onCheckedChange = if (onCheckedChange != null) { { onCheckedChange(index, it) } } else null
                    )
                }
            }
        }

        if (hasButtons) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (onReset != null) {
                    BasicText(
                        text = stringResource(R.string.reset),
                        style = typography().xs.medium.copy(
                            color = colorPalette().textDisabled,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .clip(uiRoundnessShape())
                            .clickable { onReset() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (onCancel != null) {
                    BasicText(
                        text = stringResource(R.string.cancel),
                        style = typography().xs.medium.copy(
                            color = colorPalette().red.copy(alpha = 0.3f),
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .clip(uiRoundnessShape())
                            .clickable { onCancel() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (onConfirm != null) {
                    BasicText(
                        text = stringResource(R.string.ok),
                        style = typography().xs.semiBold.copy(
                            color = colorPalette().onAccent,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .clip(uiRoundnessShape())
                            .background(colorPalette().accent)
                            .clickable { onConfirm() }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

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
    onCheckedChange: ((Boolean) -> Unit)? = null
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
                    modifier = Modifier.basicMarquee()
                )
                if (description != null) {
                    BasicText(
                        text = description,
                        maxLines = 1,
                        style = typography().xxs.copy(
                            color = colorPalette().textDisabled
                        ),
                        modifier = Modifier.basicMarquee()
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
