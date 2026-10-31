package app.n_zik.android.components.glass

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.uiRoundnessShape

/** Native badges keep their filled icon; Glass draws only the sharp E over its own surface. */
@Composable
fun explicitBadgeIconId(settings: GlassSettings = rememberGlassSettings()): Int =
    if (!LocalNativeQueueRows.current && (settings.usesGlassMaterial(GlassArea.ActionButtons) || settings.usesGlassContour(GlassArea.ActionButtons)))
        R.drawable.explicit_glass_glyph else R.drawable.explicit

/** Shared translation bubble with the original active tint and both native callbacks. */
@Composable
fun GlassDescriptionTranslate(
    color: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    settings: GlassSettings = rememberGlassSettings(),
    nativeContent: @Composable () -> Unit,
) {
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        GlassToolbarRow(actionCount = 1, settings = settings) {
            GlassToolbarIcon(painterResource(R.drawable.translate), color, 18.dp, true,
                onClick = onClick, onLongClick = onLongClick)
        }
    } else if (settings.usesGlassContour(GlassArea.ActionButtons)) {
        Box(Modifier.glassAreaContour(settings, GlassArea.ActionButtons, uiRoundnessShape())) { nativeContent() }
    } else nativeContent()
}

/** Adds a text-button surface while retaining the exact native label when Glass is off. */
@Composable
fun GlassDescriptionExpansion(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable () -> Unit,
) {
    if (settings.usesGlassMaterial(GlassArea.ActionButtons) || settings.usesGlassContour(GlassArea.ActionButtons)) {
        PageTextButton(onClick = onClick, modifier = modifier, shape = uiRoundnessShape(), settings = settings) {
            content()
        }
    } else {
        content()
    }
}

/** A passive badge surface: the icon remains sharp and gains no new input action. */
@Composable
fun Modifier.glassExplicitBadgeBackground(
    settings: GlassSettings = rememberGlassSettings(),
): Modifier {
    if (LocalNativeQueueRows.current) return this
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons) && !settings.usesGlassContour(GlassArea.ActionButtons)) return this
    // Transparent is the native fill, not an instruction to strip the shared Glass film.
    val interaction = remember { MutableInteractionSource() }
    // Keep a drag that starts on the badge without adding any click action.
    return retainGlassButtonGesture().glassButtonFeedback(interaction, uiRoundnessShape(),
        enabled = isGlassMotionAllowed(), retainPressUntilUp = true, drawHighlight = false)
        .glassChipBackground(Color.Transparent, uiRoundnessShape(), settings, filmColor = null)
}
