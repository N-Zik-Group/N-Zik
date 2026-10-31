package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassChipBackground
import app.n_zik.android.components.glass.glassMoodCardBackground
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.PageButtonFeedback
import app.n_zik.android.components.ui.screens.profiles.ProfileAvatarFrame
import app.n_zik.android.components.ui.screens.rewind.rewindCaptureMode
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import app.n_zik.android.uiRoundnessShape

/** Live actions follow UI roundness; exported story frames retain their authored shape. */
@Composable
internal fun rewindActionShape(captureShape: Shape): Shape =
    if (rewindCaptureMode.value || rewindShareCaptureActive.value) captureShape else uiRoundnessShape()

/** Uses the shared card material live and the original solid fill in exported frames. */
@Composable
internal fun Modifier.rewindCardBackground(
    color: Color, shape: Shape, settings: GlassSettings = rememberGlassSettings(),
): Modifier =
    if (rewindCaptureMode.value || rewindShareCaptureActive.value) background(color, shape)
    else glassMoodCardBackground(rewindColors.value.ink, color, shape, settings)

/** Applies the shared action material without changing capture colours or bounds. */
@Composable
internal fun Modifier.rewindActionBackground(
    color: Color, shape: Shape, settings: GlassSettings = rememberGlassSettings(),
): Modifier =
    if (rewindCaptureMode.value || rewindShareCaptureActive.value) background(color, shape)
    else glassChipBackground(color, shape, settings)

/** Preserves the outlined pill's native appearance and all capture pixels. */
@Composable
internal fun Modifier.rewindOutlinedActionBackground(
    color: Color, border: Color, shape: Shape, settings: GlassSettings = rememberGlassSettings(),
): Modifier =
    if (rewindCaptureMode.value || rewindShareCaptureActive.value ||
        (!settings.usesGlassMaterial(GlassArea.ActionButtons) &&
            !settings.usesGlassContour(GlassArea.ActionButtons))) background(color, shape).border(1.dp, border, shape)
    else glassChipBackground(color, shape, settings)

/** Keeps collage photographs edge-to-edge beneath their material contour. */
@Composable
internal fun Modifier.rewindCollageFrame(
    color: Color, shape: Shape, settings: GlassSettings = rememberGlassSettings(),
): Modifier =
    if (rewindCaptureMode.value || rewindShareCaptureActive.value) this
    // A border draws after the photographs; a material behind their opaque pixels is invisible.
    // Keep the collage sharp instead of layering another tinted surface over the artwork.
    else glassAreaContour(settings, GlassArea.MoodCards, shape)

/** Live controls share the same pane motion; exported frames retain their original pixels. */
@Composable
internal fun RewindActionFeedback(
    shape: Shape,
    area: GlassArea = GlassArea.ActionButtons,
    settings: GlassSettings = rememberGlassSettings(),
    nativeIndicationEnabled: Boolean = true,
    content: @Composable (Modifier, MutableInteractionSource?) -> Unit,
) {
    val material = settings.usesGlassMaterial(area)
    if (rewindCaptureMode.value || rewindShareCaptureActive.value || (!material && !nativeIndicationEnabled)) {
        content(Modifier, null)
    } else PageButtonFeedback(material, true, shape, null, content = content)
}

/** Shares the profile edge treatment while leaving exported avatars untouched. */
@Composable
internal fun RewindProfileAvatarFrame(size: Dp, content: @Composable () -> Unit) {
    if (rewindCaptureMode.value || rewindShareCaptureActive.value) content()
    else ProfileAvatarFrame(size = size, area = GlassArea.MoodCards, content = content)
}
