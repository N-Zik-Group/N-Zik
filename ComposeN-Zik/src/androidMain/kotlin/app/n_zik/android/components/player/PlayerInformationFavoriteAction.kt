package app.n_zik.android.components.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.n_zik.android.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStandaloneActionSize
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassButtonFeedback
import app.n_zik.android.components.glass.glassContentCaptureEnabled
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.retainGlassButtonGesture
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape

/** Capture only the title sibling, so the favorite refracts text without recording itself. */
@Composable
fun PlayerInformationFavoriteBackdrop(
    active: Boolean,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerFavorite),
    content: @Composable (titleModifier: Modifier, favoriteBackdrop: Backdrop?) -> Unit,
) {
    val parent = LocalGlassContainerBackdrop.current
    val scoped = settings.inScope(GlassScope.PlayerFavorite)
    if (!active || !scoped.usesGlassMaterial(GlassArea.ActionButtons) || !glassContentCaptureEnabled(scoped)) {
        content(Modifier, parent)
        return
    }
    val title = rememberLayerBackdrop()
    val source = if (parent == null) title else rememberCombinedBackdrop(parent, title)
    content(Modifier.layerBackdrop(title), source)
}

/** Center the shared compact action canvas on the native24dp metadata seat. */
@Composable
fun PlayerInformationFavoriteAction(
    @DrawableRes icon: Int,
    color: Color,
    onClick: () -> Unit,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerFavorite),
    ownBackdrop: Backdrop? = LocalGlassContainerBackdrop.current,
) {
    val scopedSettings = settings.inScope(GlassScope.PlayerFavorite)
    val material = scopedSettings.usesGlassMaterial(GlassArea.ActionButtons)
    val contour = scopedSettings.usesGlassContour(GlassArea.ActionButtons)
    val optical = material || contour
    val interaction = remember { MutableInteractionSource() }
    val shape = uiRoundnessShape()
    val backdrop = ownBackdrop
    Box(Modifier.layout { measurable, constraints ->
        val canvas = measurable.measure(constraints)
        // A larger touch/feedback canvas must not increase the title row or its gap to the artist.
        val seatHeight = constraints.constrainHeight(24.dp.roundToPx())
        layout(canvas.width, seatHeight) {
            canvas.placeRelative(0, (seatHeight - canvas.height) / 2)
        }
    }.size(GlassStandaloneActionSize).playerInformationGlassVisualBounds()
        .glassButtonFeedback(interaction, shape, enabled = optical,
            retainPressUntilUp = true, settings = scopedSettings, clipContent = false),
        contentAlignment = Alignment.Center) {
        if (material || contour) {
            Box(Modifier.matchParentSize().testTag("player.informationFavorite.surface")
                .then(if (material) Modifier.glassMaterial(scopedSettings.forArea(GlassArea.ActionButtons),
                    shape, ownBackdrop = backdrop, separateWindow = backdrop == null)
                else Modifier.glassAreaContour(scopedSettings, GlassArea.ActionButtons, shape)))
        }
        Box(Modifier.matchParentSize().testTag("player.informationFavorite")
            .clip(shape).retainGlassButtonGesture(optical)
            .clickable(interactionSource = interaction,
                indication = if (optical) null else ripple(),
                role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), stringResource(R.string.icon_like_button), colorFilter = ColorFilter.tint(color),
                modifier = Modifier.size(24.dp).testTag("player.informationFavorite.glyph"))
        }
    }
}
