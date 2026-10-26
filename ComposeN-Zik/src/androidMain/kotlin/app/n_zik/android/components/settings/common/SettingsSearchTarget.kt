package app.n_zik.android.components.settings.common

import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import app.n_zik.android.colorPalette

internal val SettingsTargetHighlighted = SemanticsPropertyKey<Boolean>("SettingsTargetHighlighted")

internal class SettingsTargetRegistry {
    val targets = mutableStateMapOf<String, BringIntoViewRequester>()
    var highlighted by mutableStateOf<String?>(null)
}

internal val LocalSettingsTargets = staticCompositionLocalOf<SettingsTargetRegistry?> { null }
internal val LocalSettingsResetScope = staticCompositionLocalOf<Int?> { null }

/** A stable target on the actual setting row; never requests input focus or changes preferences. */
@Composable
fun Modifier.settingsSearchTarget(id: String): Modifier {
    val registry = LocalSettingsTargets.current ?: return this
    val requester = remember(id) { BringIntoViewRequester() }
    DisposableEffect(registry, id) {
        onDispose { if (registry.targets[id] === requester) registry.targets.remove(id) }
    }
    val highlight = if (registry.highlighted == id) colorPalette().accent.copy(alpha = 0.22f) else Color.Transparent
    return this.testTag("settings.target.$id")
        .semantics { this[SettingsTargetHighlighted] = registry.highlighted == id }
        .background(highlight)
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            if (coordinates.size.width > 0 && coordinates.size.height > 0) registry.targets[id] = requester
            else if (registry.targets[id] === requester) registry.targets.remove(id)
        }
}
