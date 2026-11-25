package app.n_zik.android.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassActionCellWidth
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStandaloneActionSize
import app.n_zik.android.components.glass.GlassToolbarIcon
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.EllipsisMenuComponent
import app.n_zik.android.ui.components.tab.toolbar.Icon

private val PrimaryGap = 8.dp
private val CollectionToolbarMaxWidth = 356.dp
// The reference online playlist has eight inline seats, including More.
private val PlaylistCellWidth = (CollectionToolbarMaxWidth - GlassStandaloneActionSize - PrimaryGap) / 8

private fun primaryCellWidth(width: Dp, bookmarkWidth: Dp, hasActions: Boolean): Dp =
    if (hasActions) bookmarkWidth.coerceAtMost(width - minOf(PlaylistCellWidth + PrimaryGap, width / 2))
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
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxRowWidth = maxWidth.coerceAtMost(CollectionToolbarMaxWidth)
        val width = when {
            !showBookmark || actions.size > 1 -> maxRowWidth
            actions.isEmpty() -> minOf(maxRowWidth, bookmarkWidth)
            else -> minOf(maxRowWidth, bookmarkWidth + PrimaryGap + GlassActionCellWidth)
        }
        val primaryWidth = if (showBookmark) primaryCellWidth(width, bookmarkWidth, actions.isNotEmpty()) else 0.dp
        val gap = minOf(PrimaryGap, width / 4)
        val inlineWidth = if (showBookmark) (width - primaryWidth - gap).coerceAtLeast(0.dp) else width
        Row(Modifier.width(width), horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically) {
            if (showBookmark) {
                if (bookmarkWidth == GlassStandaloneActionSize) {
                    GlassToolbarRow(1, Modifier.width(primaryWidth), settings, compact = true) { bookmark() }
                } else Box(Modifier.width(primaryWidth), contentAlignment = Alignment.Center) { bookmark() }
            }
            if (actions.isNotEmpty()) {
                Box(Modifier.width(inlineWidth), contentAlignment = Alignment.Center) {
                    val groupWidth = if (actions.size == 1) minOf(inlineWidth, GlassActionCellWidth) else inlineWidth
                    GlassToolbarRow(actions.size, Modifier.width(groupWidth), settings,
                        expandToWidth = true, compact = true, trailingSeparator = trailingSeparator) {
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
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth.coerceAtMost(CollectionToolbarMaxWidth)
        val gap = minOf(PrimaryGap, width / 4)
        val inlineWidth = if (showBookmark) width - primaryCellWidth(width, bookmarkWidth, buttons.isNotEmpty()) - gap else width
        val roundingAllowance = with(LocalDensity.current) { .5f.toDp() }
        val capacity = ((inlineWidth + roundingAllowance) / PlaylistCellWidth).toInt().coerceAtLeast(1)
        val overflow = buttons.size > capacity
        val visibleCount = if (overflow) capacity - 1 else buttons.size
        val actions = buttons.take(visibleCount).map { button ->
            @Composable {
                if (enabled) button.ToolBarButton()
                else if (button is Icon) TabToolBar.Icon(
                    icon = button.icon,
                    tint = colorPalette().textDisabled,
                    size = button.sizeDp,
                    enabled = false,
                    modifier = button.modifier,
                    onClick = {},
                )
            }
        }
        val overflowAction: @Composable () -> Unit = {
            val menu = if (enabled) EllipsisMenuComponent.init { buttons.drop(visibleCount) } else null
            val palette = colorPalette()
            GlassToolbarIcon(
                icon = painterResource(R.drawable.ellipsis_horizontal),
                tint = if (enabled) palette.text else palette.textDisabled,
                size = TabToolBar.TOOLBAR_ICON_SIZE,
                enabled = enabled,
                contentDescription = stringResource(R.string.common_menu_label),
                onClick = { menu?.onShortClick() },
            )
        }
        val toolbarActions = if (overflow) actions + overflowAction else actions
        CollectionToolbar(bookmark, toolbarActions, settings = settings, bookmarkWidth = bookmarkWidth,
            trailingSeparator = overflow,
            showBookmark = showBookmark)
    }
}
