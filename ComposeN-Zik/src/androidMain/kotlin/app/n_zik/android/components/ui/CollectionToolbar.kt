package app.n_zik.android.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.EllipsisMenuComponent
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStandaloneActionSize
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial

internal val CollectionActionCellWidth = 44.dp
private val BookmarkGap = 8.dp

/** Width belongs to the whole group, including its separate bookmark seat. */
internal fun collectionActionCellWidth(availableWidth: Dp, actionCount: Int,
    bookmarkWidth: Dp = GlassStandaloneActionSize,
    actionCellWidth: Dp = CollectionActionCellWidth): Dp =
    if (actionCount <= 0) 0.dp else
        ((availableWidth - bookmarkWidth - BookmarkGap).coerceAtLeast(0.dp) / actionCount)
            .coerceAtMost(actionCellWidth)

/** All inline actions stay visible; the action list is also the source of its cell count. */
@Composable
internal fun CollectionToolbar(
    bookmark: @Composable () -> Unit,
    actions: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    bookmarkWidth: Dp = GlassStandaloneActionSize,
    actionCellWidth: Dp = CollectionActionCellWidth,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val cellWidth = collectionActionCellWidth(maxWidth, actions.size, bookmarkWidth, actionCellWidth)
        Row(horizontalArrangement = Arrangement.spacedBy(BookmarkGap),
            verticalAlignment = Alignment.CenterVertically) {
            if (bookmarkWidth == GlassStandaloneActionSize) {
                GlassToolbarRow(1, Modifier.width(bookmarkWidth), settings,
                    compact = true) { bookmark() }
            } else Box(Modifier.width(bookmarkWidth), contentAlignment = Alignment.Center) { bookmark() }
            GlassToolbarRow(actions.size, Modifier.width(cellWidth * actions.size), settings,
                expandToWidth = true, compact = true) {
                actions.forEach { action -> action() }
            }
        }
    }
}

/** Album editing controls retain their overflow menu and use the same visible action cells. */
@Composable
internal fun CollectionButtonToolbar(
    bookmark: @Composable () -> Unit,
    buttons: List<Button>,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    bookmarkWidth: Dp = GlassStandaloneActionSize,
    originalBookmarkGap: Dp = 15.dp,
    originalNativeBookmarkWidth: Dp = bookmarkWidth,
    actionCellWidth: Dp = CollectionActionCellWidth,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Preserve the original .8f toolbar measurement and overflow policy.
        // Its position callback runs inside padding, then subtracts padding again.
        // Compact glyph seats change presentation only, never the visible actions.
        val originalBookmarkWidth = if (settings.usesGlassMaterial(GlassArea.ActionButtons)) bookmarkWidth
            else originalNativeBookmarkWidth
        val density = LocalDensity.current
        // fillMaxWidth rounds its fraction in pixels before the width callback sees it.
        val originalToolbarWidth = with(density) {
            ((constraints.maxWidth - originalBookmarkWidth.roundToPx() - originalBookmarkGap.roundToPx())
                .coerceAtLeast(0) * .8f).roundToInt().toDp()
        }
        val availableWidth = (originalToolbarWidth - TabToolBar.HORIZONTAL_PADDING * 4).coerceAtLeast(0.dp)
        val capacity = (availableWidth / (TabToolBar.TOOLBAR_ICON_SIZE + 15.dp)).toInt()
        val visible = if (capacity == 0) emptyList() else if (buttons.size <= capacity) buttons else {
            val visibleCount = capacity - 1
            buttons.take(visibleCount) + EllipsisMenuComponent.init { buttons.drop(visibleCount) }
        }
        val actions = visible.map { button -> @Composable { button.ToolBarButton() } }
        CollectionToolbar(bookmark, actions, settings = settings, bookmarkWidth = bookmarkWidth,
            actionCellWidth = actionCellWidth)
    }
}
