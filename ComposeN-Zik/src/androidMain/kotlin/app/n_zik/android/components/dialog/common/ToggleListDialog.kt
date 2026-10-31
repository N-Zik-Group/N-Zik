package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import app.n_zik.android.components.glass.GlassDialogIconButton as IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.glassDialogButtonBackground
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

data class ToggleItem(
    val id: String,
    val iconRes: Int,
    val label: String,
    val preferenceKey: String,
    val defaultValue: Boolean,
    val description: String? = null
)

/** Checkbox list keyed by unique [ToggleItem.id], with optional drag and unlocked-row actions. */
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
    isRowActionEnabled: (Int) -> Boolean = { true },
    extraFooterButton: DialogButton? = null,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    frameTitle: String? = null,
    onDismiss: (() -> Unit)? = null,
    bodyHeader: @Composable ColumnScope.() -> Unit = {}
) {
    val nestedTarget = LocalSettingsTargets.current?.nestedTarget
    val nestedItemId = nestedTarget?.nestedDestination?.itemId
    val itemIds = items.map { it.id }
    LaunchedEffect(nestedTarget?.id, itemIds, lazyListState) {
        val index = itemIds.indexOf(nestedItemId)
        if (index >= 0) lazyListState.scrollToItem(index)
    }
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

    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = contentHeight)
                .clipToBounds()
                .testTag("toggleListDialogContent")
        ) {
            bodyHeader()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clipToBounds()
                    .testTag("toggleListDialogList"),
                contentPadding = PaddingValues(vertical = 12.dp),
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
                                modifier = Modifier.testTag("toggleListDialogItem.$index")
                                    .then(if (item.id == nestedItemId && nestedTarget != null) Modifier.settingsSearchTarget(nestedTarget.id) else Modifier),
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
                                onRowAction = if (onRowAction != null && !isLocked && isRowActionEnabled(index)) { { onRowAction(index) } } else null
                            )
                        }
                    } else {
                        ToggleRow(
                            modifier = Modifier.testTag("toggleListDialogItem.$index")
                                .then(if (item.id == nestedItemId && nestedTarget != null) Modifier.settingsSearchTarget(nestedTarget.id) else Modifier),
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
                            onRowAction = if (onRowAction != null && !isLocked && isRowActionEnabled(index)) { { onRowAction(index) } } else null
                        )
                    }
                }
            }



        }
    }
    val footer: (@Composable ColumnScope.() -> Unit)? = if (hasButtons) ({
        ButtonRow(
            buttons = buildList {
                extraFooterButton?.let { add(it) }
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
                            text = stringResource(if (checkedStatesOverride != null || onConfirm != null) R.string.cancel else R.string.dialog_close),
                            onClick = cancel,
                            danger = true
                        )
                    )
                }
                onConfirm?.let { confirm ->
                    add(
                        DialogButton(
                            text = stringResource(R.string.dialog_apply),
                            onClick = confirm,
                            primary = true
                        )
                    )
                }
            }
        )
    }) else null

    if (onDismiss != null) {
        DialogFrame(
            onDismiss = onDismiss,
            title = frameTitle,
            footer = footer,
            bodyPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
            content = body
        )
    } else {
        Column(modifier = modifier.fillMaxWidth().height(contentHeight)) {
            Box(Modifier.weight(1f, fill = false)) { Column(content = body) }
            footer?.let {
                Spacer(Modifier.height(8.dp))
                it()
                Spacer(Modifier.height(8.dp))
            }
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
    onRowAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 35.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(uiRoundnessShape())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    enabled = enabled,
                    onClick = {
                        if (enabled) {
                            if (useLocalState) {
                                onCheckedChange?.invoke(!isChecked)
                            } else {
                                val prefs = ctx.preferences
                                prefs.edit().putBoolean(preferenceKey, !isChecked).apply()
                            }
                        }
                    }
                )
                .padding(vertical = 6.dp),
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
                    .glassDialogButtonBackground(
                        backgroundColor = Color.Transparent,
                        shape = uiRoundnessShape(),
                        filmColor = surfaceColor(colorPalette().background2)
                    )
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
