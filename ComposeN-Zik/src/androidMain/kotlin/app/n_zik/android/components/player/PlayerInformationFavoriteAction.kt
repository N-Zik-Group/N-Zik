package app.n_zik.android.components.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import app.n_zik.android.components.glass.glassFilmColor
import app.n_zik.android.components.glass.surfaceGlyphColor
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import app.n_zik.android.utils.getDislikedIcon
import app.n_zik.android.utils.getLikedIcon
import app.n_zik.android.utils.getUnlikedIcon

@Composable
internal fun playerFavoriteIcon(likedAt: Long?): Int = when {
    likedAt == -1L -> getDislikedIcon()
    likedAt != null && likedAt > 0L -> getLikedIcon()
    else -> getUnlikedIcon()
}

/** The current palette supplies the tint of each drawable throughout its transition. */
@Composable
internal fun PlayerFavoriteGlyph(
    @DrawableRes icon: Int,
    favoriteColor: Color,
    dislikedColor: Color,
    modifier: Modifier,
    description: String? = null,
) {
    val semantics = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    Crossfade(icon, modifier.then(semantics), animationSpec = tween(400), label = "playerFavoriteGlyph") { glyph ->
        val color = if (glyph == R.drawable.heart_dislike) dislikedColor else favoriteColor
        Image(painterResource(glyph), null, colorFilter = ColorFilter.tint(color),
            modifier = Modifier.fillMaxSize().testTag("player.favoriteGlyph.$glyph"))
    }
}

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
    dislikedColor: Color? = null,
) {
    val scopedSettings = settings.inScope(GlassScope.PlayerFavorite)
    val material = scopedSettings.usesGlassMaterial(GlassArea.ActionButtons)
    val contour = scopedSettings.usesGlassContour(GlassArea.ActionButtons)
    val optical = material || contour
    val plate = if (material) glassFilmColor(scopedSettings.forArea(GlassArea.ActionButtons), null, menu = false) else null
    val foreground = if (dislikedColor == null) surfaceGlyphColor(color, plate) else color
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
            val description = stringResource(R.string.settings_player_favorite_button_style_label)
            val glyphModifier = Modifier.size(24.dp).testTag("player.informationFavorite.glyph")
            if (dislikedColor == null) {
                Image(painterResource(icon), description, colorFilter = ColorFilter.tint(foreground),
                    modifier = glyphModifier)
            } else {
                PlayerFavoriteGlyph(icon, color, dislikedColor, glyphModifier, description)
            }
        }
    }
}
