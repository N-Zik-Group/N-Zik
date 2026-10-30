package app.n_zik.android.components.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassToolbarIcon
import app.n_zik.android.components.glass.GlassActionGroup
import app.n_zik.android.components.glass.GlassActionGroupNativeItem
import app.n_zik.android.components.glass.GlassActionGroupItem
import app.n_zik.android.components.glass.GlassActionCellWidth
import app.n_zik.android.components.glass.GlassActionIconSize
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.GlassMenuTrigger
import app.n_zik.android.components.glass.LocalGlassMenuTrigger
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial

private val LocalSearchResultMenuTrigger = staticCompositionLocalOf<GlassMenuTriggerState?> { null }

/** Both rendering modes share neighbor motion while native actions keep their original controls. */
@Composable
fun SearchResultHeaderActions(
    showLayoutToggle: Boolean,
    onEditQuery: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val settings = rememberGlassSettings()
    val trigger = remember { GlassMenuTriggerState() }
    CompositionLocalProvider(LocalSearchResultMenuTrigger provides trigger, LocalGlassMenuTrigger provides trigger) {
        val glass = settings.usesGlassMaterial(GlassArea.ActionButtons)
        val count = if (showLayoutToggle) 3 else 2
        GlassActionGroup(settings, GlassArea.ActionButtons,
            modifier = if (glass) Modifier.width(GlassActionCellWidth * count) else Modifier,
            trailingTrigger = trigger, menuActionCount = count, menuTriggerIndex = 1,
            drawSurface = glass, separatorBefore = emptySet()) {
            SearchResultHeaderIcon(R.drawable.pencil, stringResource(R.string.search), onEditQuery) {
                IconButton(onClick = onEditQuery) {
                    Icon(painterResource(R.drawable.pencil), stringResource(R.string.search), tint = colorPalette().text)
                }
            }
            content()
        }
    }
}

/** Uses the shared toolbar gesture and icon renderer, with the caller's exact native fallback. */
@Composable
fun SearchResultHeaderIcon(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    nativeContent: @Composable () -> Unit,
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
    if (trigger != null) {
        GlassActionGroupNativeItem(index) {
            if (icon == R.drawable.discover) GlassMenuTrigger(trigger, CircleShape, content = nativeContent)
            else nativeContent()
        }
        return
    }
    if (!GlassToolbarIcon(
            icon = painterResource(icon),
            tint = colorPalette().text,
            size = 24.dp,
            enabled = true,
            onClick = onClick,
            contentDescription = contentDescription,
        )) {
        nativeContent()
    }
}
