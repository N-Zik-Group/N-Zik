package app.n_zik.android.components.settings.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import app.n_zik.android.components.glass.backdrop.Backdrop

internal val SettingsFooterMiniPlayerGap = 6.dp

internal class SettingsCardSlots {
    val content = mutableStateMapOf<Int, State<@Composable () -> Unit>>()
    val titles = mutableStateListOf<Int>()
    val order = mutableMapOf<Int, Int>()
}

internal val LocalSettingsCardSlots = staticCompositionLocalOf<SettingsCardSlots?> { null }
internal val LocalSettingsFooterHeight = staticCompositionLocalOf<(String, Int) -> Unit> { { _, _ -> } }

/** Keeps restart actions composed outside the lazy viewport. */
@Composable
internal fun SettingsPinnedFooter(
    bottomSpacer: Dp,
    contentPadding: PaddingValues = PaddingValues(bottom = bottomSpacer),
    backdrop: Backdrop? = null,
    onVisibilityChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    SettingsBottomPanel(
        backdrop = backdrop,
    ) {
        Column(
            Modifier.layout { measurable, constraints ->
                val left = contentPadding.calculateLeftPadding(layoutDirection).roundToPx()
                val right = contentPadding.calculateRightPadding(layoutDirection).roundToPx()
                val top = contentPadding.calculateTopPadding().roundToPx()
                val bottom = contentPadding.calculateBottomPadding().roundToPx()
                val child = measurable.measure(constraints.offset(-left - right, -top - bottom))
                val hasContent = child.height > 0
                // The footer and the background behind Android's navigation buttons collapse together.
                layout(
                    (child.width + left + right).coerceIn(constraints.minWidth, constraints.maxWidth),
                    (if (hasContent) child.height + top + bottom else 0)
                        .coerceIn(constraints.minHeight, constraints.maxHeight),
                ) {
                    if (hasContent) child.place(left, top)
                }
            }.onSizeChanged {
                val hasContent = it.height > 0
                if (visible != hasContent) {
                    visible = hasContent
                    onVisibilityChanged(hasContent)
                }
            },
        ) { content() }
    }
}

/** Defers card composition while its source keeps ownership of shared state and dialog hosts. */
@Composable
internal fun SettingsCardSlot(
    titleRes: Int,
    order: Int? = null,
    modifier: @Composable () -> Modifier = { Modifier },
    content: @Composable BoxScope.() -> Unit,
) {
    val slots = LocalSettingsCardSlots.current
    if (slots == null) {
        Box(modifier(), content = content)
        return
    }
    val latest = rememberUpdatedState<@Composable () -> Unit> {
        Box(modifier(), content = content)
    }
    DisposableEffect(slots, titleRes, order) {
        slots.content[titleRes] = latest
        if (order != null) slots.order[titleRes] = order
        val insertion = if (order == null) -1 else slots.titles.indexOfFirst { (slots.order[it] ?: Int.MAX_VALUE) > order }
        if (insertion < 0) slots.titles.add(titleRes) else slots.titles.add(insertion, titleRes)
        onDispose {
            if (slots.content[titleRes] === latest) {
                slots.content.remove(titleRes)
                slots.titles.remove(titleRes)
                slots.order.remove(titleRes)
            }
        }
    }
}
