package app.n_zik.android.components.settings.common

import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.Dialog
import app.n_zik.android.uiRoundnessShape
import kotlinx.coroutines.delay

internal val SettingsTargetHighlighted = SemanticsPropertyKey<Boolean>("SettingsTargetHighlighted")
internal val SettingsTargetId = SemanticsPropertyKey<String>("SettingsTargetId")

internal class SettingsTargetRegistry {
    val targets = mutableStateMapOf<String, BringIntoViewRequester>()
    var highlighted by mutableStateOf<String?>(null)
    var nestedTarget by mutableStateOf<SettingsEntryTarget?>(null)
    private var pulseGeneration = 0

    suspend fun blink(id: String) {
        val generation = ++pulseGeneration
        try {
            repeat(4) {
                highlighted = id
                delay(400)
                highlighted = null
                delay(200)
            }
        } finally {
            if (generation == pulseGeneration) highlighted = null
        }
    }
}

internal val LocalSettingsTargets = staticCompositionLocalOf<SettingsTargetRegistry?> { null }
internal val LocalSettingsResetScope = staticCompositionLocalOf<Int?> { null }

/** Keeps a search request inside its owning dialog and consumes it on dismissal. */
@Composable
internal fun SettingsDialogSearchScope(dialog: Dialog, content: @Composable () -> Unit) {
    val registry = LocalSettingsTargets.current
    val target = registry?.nestedTarget
    val ownsRequest = target?.nestedDestination?.let { settingsNestedDialog(it.dialogId) === dialog } == true
    LaunchedEffect(target?.id, dialog.isActive) {
        if (ownsRequest && !dialog.isActive && registry?.nestedTarget === target) {
            registry?.nestedTarget = null
        }
    }
    CompositionLocalProvider(LocalSettingsTargets provides registry.takeIf { ownsRequest }) {
        content()
    }
}

/** A stable target on the actual setting row; never requests input focus or changes preferences. */
@Composable
fun Modifier.settingsSearchTarget(id: String): Modifier {
    val registry = LocalSettingsTargets.current ?: return this
    val requester = remember(id) { BringIntoViewRequester() }
    DisposableEffect(registry, id) {
        onDispose { if (registry.targets[id] === requester) registry.targets.remove(id) }
    }
    val highlight = if (registry.highlighted == id) colorPalette().accent.copy(alpha = 0.22f) else Color.Transparent
    val shape = uiRoundnessShape()
    val painted = if (id.startsWith("card.")) this else clip(shape).background(highlight, shape)
    return painted.testTag("settings.target.$id")
        .semantics {
            this[SettingsTargetId] = id
            this[SettingsTargetHighlighted] = registry.highlighted == id
        }
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            if (coordinates.size.width > 0 && coordinates.size.height > 0) registry.targets[id] = requester
            else if (registry.targets[id] === requester) registry.targets.remove(id)
        }
}
