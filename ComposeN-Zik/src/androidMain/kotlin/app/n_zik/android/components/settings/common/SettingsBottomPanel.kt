package app.n_zik.android.components.settings.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassMaterialContainerScope
import app.n_zik.android.components.glass.GlassScreenEdge
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassMaterialSource
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.extendGlassPastScreenEdge
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.nav.bottomNavigationBarShape
import app.n_zik.android.uiRoundnessShape

/** Fixed settings action pane; its only rim at the bottom sits outside the window. */
@Composable
internal fun SettingsBottomPanel(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = bottomNavigationBarShape(uiRoundnessShape())
    val settings = rememberGlassSettings()
    if (settings.usesGlassMaterial(GlassArea.SettingsCards)) {
        GlassMaterialContainerScope(Modifier, materialSource = backdrop ?: LocalGlassBackdrop.current, material = { exported ->
            Modifier.glassMaterial(
                settings = settings.forArea(GlassArea.SettingsCards), shape = shape,
                ownBackdrop = backdrop, filmColor = colorPalette().background0,
                exportedBackdrop = exported, drawShadow = false,
            )
        }) { skin ->
            Box(Modifier.fillMaxWidth()) {
                // Record/export only the pane, leaving button springs outside its clip.
                Box(Modifier.matchParentSize().extendGlassPastScreenEdge(GlassScreenEdge.Bottom).then(skin))
                CompositionLocalProvider(LocalGlassMaterialSource provides LocalGlassContainerBackdrop.current) {
                    Box(modifier, content = content)
                }
            }
        }
        return
    }
    SettingsCardSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        containerColor = colorPalette().background0,
        backgroundColor = colorPalette().background0,
        shadowColor = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box(modifier, content = content)
    }
}
