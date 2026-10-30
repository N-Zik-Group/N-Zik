package app.n_zik.android.components.ui.screens.rewind.slides

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import app.n_zik.android.components.ui.screens.profiles.ProfileAvatarFrame
import app.n_zik.android.components.ui.screens.rewind.rewindCaptureMode
import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive

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
        !settings.usesGlassMaterial(GlassArea.ActionButtons)) background(color, shape).border(1.dp, border, shape)
    else glassChipBackground(color, shape, settings)

/** Keeps collage photographs edge-to-edge beneath their material contour. */
@Composable
internal fun Modifier.rewindCollageFrame(
    color: Color, shape: Shape, settings: GlassSettings = rememberGlassSettings(),
): Modifier =
    if (rewindCaptureMode.value || rewindShareCaptureActive.value) this
    else if (settings.usesGlassMaterial(GlassArea.MoodCards))
        rewindCardBackground(color, shape, settings)
    else glassAreaContour(settings, GlassArea.MoodCards, shape)

/** Shares the profile edge treatment while leaving exported avatars untouched. */
@Composable
internal fun RewindProfileAvatarFrame(size: Dp, content: @Composable () -> Unit) {
    if (rewindCaptureMode.value || rewindShareCaptureActive.value) content()
    else ProfileAvatarFrame(size = size, area = GlassArea.MoodCards, content = content)
}
