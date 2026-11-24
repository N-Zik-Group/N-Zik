package app.n_zik.android.components.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassActionGroup
import app.n_zik.android.components.glass.GlassActionGroupNativeItem
import app.n_zik.android.components.glass.GlassActionGroupItem
import app.n_zik.android.components.glass.GlassActionIconSize
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.GlassMenuTrigger
import app.n_zik.android.components.glass.LocalGlassMenuTrigger
import app.n_zik.android.components.glass.LocalGlassActionCellSize
import app.n_zik.android.components.glass.NativeSectionIconAction
import app.n_zik.android.components.navigation.header.HeaderActionCanvasSize
import app.n_zik.android.components.navigation.header.HeaderActionCellSize
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour

private val LocalSearchResultMenuTrigger = staticCompositionLocalOf<GlassMenuTriggerState?> { null }

/** Both rendering modes share action dimensions and dropdown neighbor motion. */
@Composable
fun SearchResultHeaderActions(
    showLayoutToggle: Boolean,
    onEditQuery: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val settings = rememberGlassSettings()
    val trigger = remember { GlassMenuTriggerState() }
    CompositionLocalProvider(
        LocalSearchResultMenuTrigger provides trigger,
        LocalGlassMenuTrigger provides trigger,
        LocalGlassActionCellSize provides HeaderActionCellSize,
    ) {
        val glass = settings.usesGlassMaterial(GlassArea.ActionButtons)
        val count = if (showLayoutToggle) 3 else 2
        GlassActionGroup(settings, GlassArea.ActionButtons,
            modifier = Modifier.width(HeaderActionCanvasSize * count),
            trailingTrigger = trigger, menuActionCount = count, menuTriggerIndex = 1,
            drawSurface = glass || settings.usesGlassContour(GlassArea.ActionButtons), separatorBefore = emptySet()) {
            SearchResultHeaderIcon(R.drawable.pencil, stringResource(R.string.search_title), onEditQuery)
            content()
        }
    }
}

/** Keeps the filter control anchored when its dropdown borrows the source glyph. */
@Composable
fun SearchResultHeaderIcon(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val trigger = LocalSearchResultMenuTrigger.current
    val settings = rememberGlassSettings()
    val index = when (icon) {
        R.drawable.pencil -> 0
        R.drawable.discover -> 1
        else -> 2
    }
    if (trigger != null && settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        GlassActionGroupItem(onClick = onClick, menuTrigger = trigger.takeIf { index == 1 }, groupIndex = index) {
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
    if (trigger == null) {
        nativeContent()
    } else {
        GlassActionGroupNativeItem(index) {
            if (index == 1) GlassMenuTrigger(trigger, uiRoundnessShape(), content = nativeContent)
            else nativeContent()
        }
    }
}
