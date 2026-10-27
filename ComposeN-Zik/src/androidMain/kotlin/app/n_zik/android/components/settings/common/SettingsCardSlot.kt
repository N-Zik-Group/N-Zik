package app.n_zik.android.components.settings.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp

internal const val SettingsSourceFooter = -1

internal class SettingsCardSlots {
    val content = mutableStateMapOf<Int, State<@Composable () -> Unit>>()
    val titles = mutableStateListOf<Int>()
    val order = mutableMapOf<Int, Int>()
}

internal val LocalSettingsCardSlots = staticCompositionLocalOf<SettingsCardSlots?> { null }

/** Keeps restart actions composed outside the lazy viewport and above the player inset. */
@Composable
internal fun SettingsPinnedFooter(bottomSpacer: Dp, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    Column {
        Column(Modifier.onSizeChanged { visible = it.height > 0 }) { content() }
        if (visible) Spacer(Modifier.height(bottomSpacer))
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
