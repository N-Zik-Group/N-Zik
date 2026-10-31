package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.glassButtonFeedback
import app.n_zik.android.components.glass.isGlassMotionAllowed
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.retainGlassButtonGesture
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.uiRoundnessShape

/** The bubble, feedback and single seek owner share the original reserved glyph footprint. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun PlayerSkipTimeGlass(
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTime),
    onClick: () -> Unit = {},
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val settings = settings.inScope(GlassScope.PlayerTime)
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    val contour = settings.usesGlassContour(GlassArea.ActionButtons)
    val bubble = material || contour
    val shape = uiRoundnessShape()
    val interaction = remember { MutableInteractionSource() }
    val interactive = material && isGlassMotionAllowed()
    val backdrop = LocalGlassContainerBackdrop.current
    Box {
        Box(Modifier
            .then(if (bubble) Modifier.playerInformationGlassVisualBounds()
                .testTag("player.skipTime.material") else Modifier)
            .then(if (interactive) Modifier else Modifier.clip(shape))
            .retainGlassButtonGesture(settings.enabled)
            .combinedClickable(interactionSource = interaction,
                indication = if (interactive) null else LocalIndication.current,
                role = Role.Button, onClick = onClick, onClickLabel = onClickLabel,
                onDoubleClick = onDoubleClick, onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel)) {
            Box(Modifier.glassButtonFeedback(interaction, shape, interactive, retainPressUntilUp = true)
                .then(if (material) Modifier.glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
                    ownBackdrop = backdrop, separateWindow = backdrop == null)
                else if (contour) Modifier.glassAreaContour(settings, GlassArea.ActionButtons, shape)
                else Modifier)) {
                // Keep the native 20dp slot; Glass alone gives its glyph 3dp of visual inset.
                Box(Modifier.graphicsLayer {
                    scaleX = if (material) .7f else 1f
                    scaleY = if (material) .7f else 1f
                }) { content() }
            }
        }
    }
}

/** Content-width capsule around the queue duration, without moving its text or adjacent controls. */
@Composable
fun PlayerQueueDurationGlass(
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTime),
    content: @Composable () -> Unit,
) = PlayerTimeGlassSurface(settings, uiRoundnessShape(), 8.dp, 3.dp,
    "player.queueDuration.material", content)

@Composable
private fun PlayerTimeGlassSurface(
    settings: GlassSettings,
    shape: Shape,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    tag: String,
    content: @Composable () -> Unit,
) {
    val settings = settings.inScope(GlassScope.PlayerTime)
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    val contour = settings.usesGlassContour(GlassArea.ActionButtons)
    if (!material && !contour) {
        content()
        return
    }
    val backdrop = LocalGlassContainerBackdrop.current
    Box {
        // Only the passive surface overflows; original content alone determines row measurement.
        Box(Modifier.matchParentSize().layout { measurable, constraints ->
            val x = horizontalPadding.roundToPx()
            val y = verticalPadding.roundToPx()
            val surface = measurable.measure(Constraints.fixed(
                constraints.maxWidth + 2 * x, constraints.maxHeight + 2 * y))
            layout(constraints.maxWidth, constraints.maxHeight) { surface.place(-x, -y) }
        }.playerInformationGlassVisualBounds().testTag(tag).then(if (material)
            Modifier.glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
                ownBackdrop = backdrop, separateWindow = backdrop == null)
            else Modifier.glassAreaContour(settings, GlassArea.ActionButtons, shape)))
        content()
    }
}
