package app.n_zik.android.components.player

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.n_zik.android.components.glass.blockGlassCoveredInput

/** Geometry belonging to an unplaced page must not obstruct player toasts. */
val LocalPlayerCoveredPage = staticCompositionLocalOf { false }

/**
 * Keep navigation and its remembered state mounted, but stop placement and drawing while a
 * fully expanded player covers it. The caller clears [covered] as soon as collapse starts,
 * so the page is placed again before the player exposes it. Visible player content is a sibling.
 */
@Composable
fun PlayerCoveredPage(
    covered: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var pageHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(covered) {
        if (covered && pageHasFocus) focusManager.clearFocus(force = true)
    }
    CompositionLocalProvider(LocalPlayerCoveredPage provides covered) {
        Layout(
            content = content,
            modifier = modifier
                .blockGlassCoveredInput(covered)
                .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier)
                .onFocusChanged { pageHasFocus = it.hasFocus }
                .focusProperties { onEnter = { if (covered) cancelFocusChange() } }
                .focusGroup(),
        ) { measurables, constraints ->
            val children = measurables.map { it.measure(constraints) }
            val width = children.maxOfOrNull { it.width } ?: constraints.minWidth
            val height = children.maxOfOrNull { it.height } ?: constraints.minHeight
            layout(width, height) {
                if (!covered) children.forEach { it.placeRelative(0, 0) }
            }
        }
    }
}
