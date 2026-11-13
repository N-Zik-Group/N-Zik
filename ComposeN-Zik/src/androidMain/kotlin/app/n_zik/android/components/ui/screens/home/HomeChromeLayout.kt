package app.n_zik.android.components.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import app.n_zik.android.colorPalette

private enum class HomeChromeSlot { Header, Content }

/**
 * Measure the dynamic header before composing its scroll owner. The first content
 * frame therefore receives the real reserve, rather than correcting a zero guess
 * after onSizeChanged. Content still owns the complete, stationary viewport.
 */
@Composable
internal fun HomeChromeLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val captureBackground = LocalSharedHomeHeaderGlass.current != null
    val background = colorPalette().background0
    SubcomposeLayout(modifier) { constraints ->
        val headerPlaceables = subcompose(HomeChromeSlot.Header) {
            Box(Modifier.fillMaxWidth()) { header() }
        }.map { it.measure(constraints.copy(minHeight = 0)) }
        val contentPlaceables = subcompose(HomeChromeSlot.Content) {
            Box(
                Modifier.fillMaxSize().sharedHomeGlassContent()
                    .then(if (captureBackground) Modifier.background(background) else Modifier),
                content = content,
            )
        }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceables.forEach { it.placeRelative(0, 0) }
            headerPlaceables.forEach { it.placeRelative(0, 0) }
        }
    }
}
