package app.n_zik.android.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassActionCellWidth
import app.n_zik.android.components.glass.GlassActionCellHeight
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStandaloneActionSize
import app.n_zik.android.components.glass.GlassToolbarIcon
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.Clickable
import app.n_zik.android.ui.components.tab.toolbar.EllipsisMenuComponent
import app.n_zik.android.ui.components.tab.toolbar.Icon
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.core.utils.Toaster

private val PrimaryGap = 8.dp
private val CollectionToolbarMaxWidth = 356.dp
private val CollectionActionTouchSize = 48.dp

private fun primaryCellWidth(width: Dp, bookmarkWidth: Dp, hasActions: Boolean, actionSize: Dp): Dp =
    if (hasActions) bookmarkWidth.coerceAtMost(width - minOf(actionSize + PrimaryGap, width / 2))
    else bookmarkWidth.coerceAtMost(width)

/** Keeps the primary control first and distributes the remaining seats inside the playlist width. */
@Composable
internal fun CollectionToolbar(
    bookmark: @Composable () -> Unit,
    actions: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    bookmarkWidth: Dp = GlassStandaloneActionSize,
    trailingSeparator: Boolean = false,
    showBookmark: Boolean = true,
    actionSize: Dp = GlassActionCellHeight,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxRowWidth = maxWidth.coerceAtMost(CollectionToolbarMaxWidth)
        val primarySize = maxOf(bookmarkWidth, actionSize)
        val singleActionWidth = maxOf(GlassActionCellWidth, actionSize)
        val width = when {
            !showBookmark || actions.size > 1 -> maxRowWidth
            actions.isEmpty() -> minOf(maxRowWidth, primarySize)
            else -> minOf(maxRowWidth, primarySize + PrimaryGap + singleActionWidth)
        }
        val primaryWidth = if (showBookmark) primaryCellWidth(width, primarySize, actions.isNotEmpty(), actionSize) else 0.dp
        val gap = minOf(PrimaryGap, width / 4)
        val inlineWidth = if (showBookmark) (width - primaryWidth - gap).coerceAtLeast(0.dp) else width
        Row(Modifier.width(width), horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically) {
            if (showBookmark) {
                if (bookmarkWidth == GlassStandaloneActionSize) {
                    GlassToolbarRow(1, Modifier.width(primaryWidth), settings, compact = true, cellHeight = actionSize) { bookmark() }
                } else Box(Modifier.width(primaryWidth), contentAlignment = Alignment.Center) { bookmark() }
            }
            if (actions.isNotEmpty()) {
                Box(Modifier.width(inlineWidth), contentAlignment = Alignment.Center) {
                    val groupWidth = if (actions.size == 1) minOf(inlineWidth, singleActionWidth) else inlineWidth
                    GlassToolbarRow(actions.size, Modifier.width(groupWidth), settings,
                        expandToWidth = true, compact = true, trailingSeparator = trailingSeparator, cellHeight = actionSize) {
                        actions.forEach { action -> action() }
                    }
                }
            }
        }
    }
}

/** Keeps the same action cells when disabled. The bookmark owns its own availability. */
@Composable
internal fun CollectionButtonToolbar(
    bookmark: @Composable () -> Unit,
    buttons: List<Button>,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    bookmarkWidth: Dp = GlassStandaloneActionSize,
    enabled: Boolean = true,
    showBookmark: Boolean = true,
    menuButtons: Set<Button> = emptySet(),
    overflowEnabled: Boolean = enabled,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth.coerceAtMost(CollectionToolbarMaxWidth)
        val gap = minOf(PrimaryGap, width / 4)
        val primaryWidth = primaryCellWidth(width, maxOf(bookmarkWidth, CollectionActionTouchSize),
            buttons.isNotEmpty(), CollectionActionTouchSize)
        val inlineWidth = if (showBookmark) width - primaryWidth - gap else width
        val capacity = (inlineWidth / CollectionActionTouchSize).toInt().coerceAtLeast(1)
        val (visibleButtons, pendingOverflow) = collectionToolbarPlacement(buttons, menuButtons, capacity)
        val currentOverflow by rememberUpdatedState(pendingOverflow)
        val canOpenOverflow = overflowEnabled || (enabled && pendingOverflow.any { (it as? Icon)?.isEnabled == true })
        val actions = visibleButtons.map { button ->
            @Composable {
                if (enabled) button.ToolBarButton()
                else if (button is Icon) TabToolBar.Icon(
                    icon = button.icon,
                    tint = colorPalette().textDisabled,
                    size = button.sizeDp,
                    enabled = false,
                    modifier = button.modifier,
                    contentDescription = (button as? MenuIcon)?.menuIconTitle,
                    onClick = {},
                )
            }
        }
        val overflowAction: @Composable () -> Unit = {
            val menu = if (canOpenOverflow) EllipsisMenuComponent.init { currentOverflow } else null
            val palette = colorPalette()
            GlassToolbarIcon(
                icon = painterResource(R.drawable.ellipsis_horizontal),
                tint = if (canOpenOverflow) palette.text else palette.textDisabled,
                size = TabToolBar.TOOLBAR_ICON_SIZE,
                enabled = canOpenOverflow,
                contentDescription = stringResource(R.string.common_menu_label),
                onClick = { menu?.onShortClick() },
            )
        }
        val toolbarActions = if (pendingOverflow.isNotEmpty()) actions + overflowAction else actions
        CollectionToolbar(bookmark, toolbarActions, settings = settings, bookmarkWidth = bookmarkWidth,
            trailingSeparator = pendingOverflow.isNotEmpty(),
            showBookmark = showBookmark, actionSize = CollectionActionTouchSize)
    }
}

internal fun <T> collectionToolbarPlacement(
    buttons: List<T>, menuButtons: Set<T>, capacity: Int,
): Pair<List<T>, List<T>> {
    val inline = buttons.filterNot(menuButtons::contains)
    if (menuButtons.isEmpty() && inline.size <= capacity) return inline to emptyList()
    val visible = inline.take(capacity - 1)
    return visible to buttons.filterNot(visible.toSet()::contains)
}

/** One control supplies its label and action to the toolbar and to either overflow layout. */
@Composable
internal fun collectionToolbarAction(
    action: ToolbarAction,
    enabled: Boolean = true,
    icon: Int = action.icon,
    label: Int = action.label,
    selected: Boolean = false,
    dismissMenu: Boolean = true,
    onClick: () -> Unit = {},
): MenuIcon {
    val menuState = LocalMenuState.current
    return object : MenuIcon, Clickable {
        override val iconId = icon
        override val isEnabled = enabled
        override val color: Color
            @Composable get() = when {
                !isEnabled -> colorPalette().textDisabled
                selected -> colorPalette().accent
                else -> colorPalette().text
            }
        override val menuIconTitle: String
            @Composable get() = stringResource(label)

        override fun onShortClick() {
            if (!isEnabled) return
            if (dismissMenu && menuState.isDisplayed) menuState.hide()
            onClick()
        }

        override fun onLongClick() {
            Toaster.i(label)
        }
    }
}
