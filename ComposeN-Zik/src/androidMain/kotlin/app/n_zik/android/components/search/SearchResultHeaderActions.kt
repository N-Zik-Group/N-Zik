package app.n_zik.android.components.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassActionGroup
import app.n_zik.android.components.glass.GlassActionGroupItem
import app.n_zik.android.components.glass.GlassActionGroupNativeItem
import app.n_zik.android.components.glass.GlassActionIconSize
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassMenuTrigger
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.LocalGlassActionCellSize
import app.n_zik.android.components.glass.LocalGlassMenuTrigger
import app.n_zik.android.components.glass.NativeSectionIconAction
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.header.HeaderActionCanvasSize
import app.n_zik.android.components.navigation.header.HeaderActionCellSize
import app.n_zik.android.uiRoundnessShape

@Stable
internal data class SearchResultFilterAction(
    val onOpen: () -> Unit,
    val menu: @Composable () -> Unit,
)

/** Both rendering modes share action dimensions and dropdown neighbor motion. */
@Composable
internal fun SearchResultHeaderActions(
    onEditQuery: () -> Unit,
    filter: SearchResultFilterAction? = null,
    useGrid: Boolean = false,
    onToggleLayout: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val settings = rememberGlassSettings()
    val trigger = if (filter != null) remember { GlassMenuTriggerState() } else null
    val layoutIndex = if (filter == null) 1 else 2
    val count = layoutIndex + if (onToggleLayout == null) 0 else 1
    CompositionLocalProvider(LocalGlassActionCellSize provides HeaderActionCellSize) {
        val glass = settings.usesGlassMaterial(GlassArea.ActionButtons)
        GlassActionGroup(
            settings,
            GlassArea.ActionButtons,
            modifier = modifier.width(HeaderActionCanvasSize * count),
            trailingTrigger = trigger,
            menuActionCount = count.takeIf { trigger != null },
            menuTriggerIndex = 1,
            drawSurface = glass || settings.usesGlassContour(GlassArea.ActionButtons),
            separatorBefore = emptySet(),
        ) {
            SearchResultHeaderIcon(
                icon = R.drawable.pencil,
                contentDescription = stringResource(R.string.search_action_edit_query),
                index = 0,
                onClick = onEditQuery,
            )
            if (filter != null) {
                Box {
                    SearchResultHeaderIcon(
                        icon = R.drawable.discover,
                        contentDescription = stringResource(R.string.accessibility_filter),
                        index = 1,
                        trigger = trigger,
                        onClick = filter.onOpen,
                    )
                    CompositionLocalProvider(LocalGlassMenuTrigger provides trigger) { filter.menu() }
                }
            }
            if (onToggleLayout != null) {
                SearchResultHeaderIcon(
                    icon = if (useGrid) R.drawable.sort_vertical else R.drawable.sort_grid,
                    contentDescription = stringResource(R.string.accessibility_switch_mode),
                    index = layoutIndex,
                    onClick = onToggleLayout,
                )
            }
        }
    }
}

/** Keeps the filter control anchored when its dropdown borrows the source glyph. */
@Composable
private fun SearchResultHeaderIcon(
    @DrawableRes icon: Int,
    contentDescription: String,
    index: Int,
    trigger: GlassMenuTriggerState? = null,
    onClick: () -> Unit,
) {
    val settings = rememberGlassSettings()
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        GlassActionGroupItem(onClick = onClick, menuTrigger = trigger, groupIndex = index) {
            Icon(painterResource(icon), contentDescription, Modifier.size(GlassActionIconSize), tint = colorPalette().text)
        }
        return
    }
    val nativeContent: @Composable () -> Unit = {
        NativeSectionIconAction(
            icon = icon,
            contentDescription = contentDescription,
            color = colorPalette().text,
            settings = settings,
            canvasSize = HeaderActionCanvasSize,
            onClick = onClick,
        )
    }
    GlassActionGroupNativeItem(index) {
        if (trigger != null) GlassMenuTrigger(trigger, uiRoundnessShape(), content = nativeContent)
        else nativeContent()
    }
}
