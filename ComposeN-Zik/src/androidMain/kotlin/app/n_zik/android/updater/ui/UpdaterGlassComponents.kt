package app.n_zik.android.updater.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import app.n_zik.android.components.glass.extendGlassPastScreenEdge
import app.n_zik.android.components.glass.GlassScreenEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import app.n_zik.android.components.glass.PageButtonFeedback
import app.n_zik.android.components.glass.glassChipBackground
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
import app.n_zik.android.components.glass.GlassMaterialContainerScope
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
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

/** Preserve each native card's dimensions and colours; share SettingsCards optics. */
@Composable
internal fun UpdaterCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = uiRoundnessShape(),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val settings = rememberGlassSettings()
    UpdaterPressFeedback(onClick = onClick, allowJelly = false) { feedback ->
        val cardModifier = modifier.then(feedback)
        if (!settings.usesGlassMaterial(GlassArea.SettingsCards) &&
            !settings.usesGlassContour(GlassArea.SettingsCards) &&
            !settings.usesGlassMaterial(GlassArea.ActionButtons)) {
            Card(modifier = cardModifier, shape = shape, colors = colors, elevation = elevation, content = content)
        } else {
            SettingsCardSurface(
                modifier = cardModifier,
                shape = shape,
                containerColor = colors.containerColor,
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
    UpdaterPressFeedback(onClick, onLongClick) { feedback ->
        Box(modifier.glassChipBackground(Color.Transparent, shape).then(feedback),
            contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun UpdaterPressFeedback(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    allowJelly: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    if (onClick == null) {
        content(Modifier)
        return
    }
    val settings = rememberGlassSettings()
    val shape = uiRoundnessShape()
    // Scrollable accordions use cancellable row feedback, never retained button gestures.
    val material = allowJelly && settings.usesGlassMaterial(GlassArea.ActionButtons)
    PageButtonFeedback(material, true, shape, null, settings) { feedback, source ->
        content((if (material) Modifier else Modifier.clip(shape)).then(feedback).combinedClickable(
            interactionSource = source,
            indication = if (source == null) LocalIndication.current else null,
            onClick = onClick,
            onLongClick = onLongClick,
        ))
    }
}

/** Fixed settings action pane; its only rim at the bottom sits outside the window. */
@Composable
internal fun UpdaterBottomPanel(
    modifier: Modifier = Modifier,
    backdrop: app.n_zik.android.components.glass.backdrop.Backdrop? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val shape = app.n_zik.android.components.navigation.nav.bottomNavigationBarShape(uiRoundnessShape())
    val settings = rememberGlassSettings()
    if (settings.usesGlassMaterial(GlassArea.SettingsCards)) {
        GlassMaterialContainerScope(Modifier, material = { exported ->
            Modifier.glassMaterial(
                settings = settings.forArea(GlassArea.SettingsCards), shape = shape,
                ownBackdrop = backdrop, filmColor = colorPalette().background0,
                exportedBackdrop = exported, drawShadow = false,
            )
        }) { skin ->
            Box(Modifier.fillMaxWidth()) {
                // Record/export only the pane, leaving button springs outside its clip.
                Box(Modifier.matchParentSize().extendGlassPastScreenEdge(GlassScreenEdge.Bottom).then(skin))
                Box(modifier, content = content)
            }
        }
        return
    }
    SettingsCardSurface(
        modifier = Modifier.fillMaxWidth().extendGlassPastScreenEdge(GlassScreenEdge.Bottom),
        shape = shape,
        containerColor = colorPalette().background0,
        backgroundColor = colorPalette().background0,
        shadowColor = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box(modifier, content = content)
    }
}
