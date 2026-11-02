package app.n_zik.android.components.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassMenuEntry
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.LocalGlassMenuTrigger
import app.n_zik.android.components.glass.currentGlassOverlayBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial

/** Uses the app overlay when possible; separate windows retain the shared fallback. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    containerColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    trigger: GlassMenuTriggerState? = LocalGlassMenuTrigger.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val menuShape = remember(shape) { straightBottomMenuShape(shape) }
    val settings = rememberGlassSettings()
    val host = LocalGlassMenuHost.current
    val overlayBackdrop = currentGlassOverlayBackdrop()
    val id = remember { Any() }
    val locals = currentCompositionLocalContext
    val currentContent = rememberUpdatedState(content)
    val currentDismiss = rememberUpdatedState(onDismissRequest)
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val glassEnabled = settings.usesGlassMaterial(GlassArea.Dropdowns)
    val originalContainerColor = if (containerColor.isSpecified) containerColor else MenuDefaults.containerColor
    if (host != null) {
        Box(Modifier.size(0.dp).onGloballyPositioned { coordinates ->
            val parent = coordinates.parentLayoutCoordinates ?: coordinates
            val position = parent.positionInRoot()
            val next = Rect(position.x, position.y, position.x + parent.size.width, position.y + parent.size.height)
            if (anchor != next) anchor = next
        })
        val position = trigger?.bounds?.takeUnless { it == Rect.Zero } ?: anchor
        SideEffect {
            if (expanded && position != null) {
                host.show(GlassMenuEntry(
                    id = id,
                    anchor = position,
                    settings = settings.forArea(GlassArea.Dropdowns),
                    modifier = modifier,
                    onDismiss = { currentDismiss.value() },
                    content = { currentContent.value(this) },
                    trigger = trigger,
                    shape = menuShape,
                    containerColor = originalContainerColor,
                    backdrop = overlayBackdrop,
                    locals = locals,
                ))
            } else host.hide(id)
        }
        DisposableEffect(host, id) { onDispose { host.hide(id) } }
        return
    }
    SideEffect { host?.remove(id) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = if (glassEnabled) Color.Transparent else originalContainerColor,
        modifier = if (glassEnabled) modifier.glassMaterial(
            settings.forArea(GlassArea.Dropdowns), menuShape, separateWindow = true, menu = true, filmColor = originalContainerColor,
        ) else modifier.glassAreaContour(settings, GlassArea.Dropdowns, menuShape),
        shape = menuShape,
        content = content,
    )
}
