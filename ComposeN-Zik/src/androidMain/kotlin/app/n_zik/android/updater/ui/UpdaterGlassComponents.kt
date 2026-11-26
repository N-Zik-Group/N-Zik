package app.n_zik.android.updater.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import app.n_zik.android.components.glass.PageButtonFeedback
import app.n_zik.android.components.glass.glassChipBackground
import app.n_zik.android.components.glass.glassActionFilmColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassScope
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.uiRoundnessShape

/** Updater is a settings destination, including its bottom actions and overlays. */
@Composable
internal fun UpdaterGlassScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassScope provides GlassScope.SettingsProfiles, content = content)
}

internal fun GlassSettings.usesSingleUpdaterDownloadSurface(): Boolean =
    usesGlassMaterial(GlassArea.SettingsCards) || usesGlassContour(GlassArea.SettingsCards) ||
        usesGlassMaterial(GlassArea.ActionButtons) || usesGlassContour(GlassArea.ActionButtons)

/** Preserve each native card's dimensions and colours; share SettingsCards optics. */
@Composable
internal fun UpdaterCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = uiRoundnessShape(),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    drawSurface: Boolean = true,
    glassContainerColor: Color = colors.containerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!drawSurface) {
        Column(modifier, content = content)
        return
    }
    val settings = rememberGlassSettings()
    UpdaterPressFeedback(onClick) { feedback ->
        val cardModifier = modifier.then(feedback)
        if (!settings.usesGlassMaterial(GlassArea.SettingsCards) &&
            !settings.usesGlassContour(GlassArea.SettingsCards) &&
            !settings.usesGlassMaterial(GlassArea.ActionButtons)) {
            Card(modifier = cardModifier, shape = shape, colors = colors, elevation = elevation, content = content)
        } else {
            SettingsCardSurface(
                modifier = cardModifier,
                shape = shape,
                containerColor = if (settings.usesGlassMaterial(GlassArea.SettingsCards)) glassContainerColor
                    else colors.containerColor,
                backgroundColor = colorPalette().background0,
                shadowColor = Color.Transparent,
                shadowElevation = 0.dp,
                settings = settings,
                content = content,
            )
        }
    }
}

/** Card-sized action with shared material, contour, ripple and press motion. */
@Composable
internal fun UpdaterActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = uiRoundnessShape(),
    colors: CardColors = CardDefaults.cardColors(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.containerColor),
        elevation = null,
        border = border,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(content = content)
    }
}

/** The long click keeps translation language selection on the original touch target. */
@Composable
internal fun UpdaterIconAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = uiRoundnessShape()
    val settings = rememberGlassSettings()
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    PageButtonFeedback(material, true, shape, null, settings, animateContentLayer = true) { feedback, source ->
        val surface = Modifier.glassChipBackground(
            Color.Transparent, shape, settings, filmColor = glassActionFilmColor(),
        )
        val click = Modifier.combinedClickable(
            interactionSource = source,
            indication = if (source == null) LocalIndication.current else null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        // Keep input outside the animated layer while the chip and glyph move together.
        val action = if (material) click.then(feedback).then(surface)
            else surface.clip(shape).then(feedback).then(click)
        Box(modifier.then(action), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun UpdaterPressFeedback(
    onClick: (() -> Unit)?,
    content: @Composable (Modifier) -> Unit,
) {
    if (onClick == null) {
        content(Modifier)
        return
    }
    val settings = rememberGlassSettings()
    val shape = uiRoundnessShape()
    // Scrollable accordions use cancellable row feedback, never retained button gestures.
    PageButtonFeedback(false, true, shape, null, settings) { feedback, source ->
        content(Modifier.clip(shape).then(feedback).combinedClickable(
            interactionSource = source,
            indication = if (source == null) LocalIndication.current else null,
            onClick = onClick,
        ))
    }
}
