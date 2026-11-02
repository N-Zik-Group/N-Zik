package app.n_zik.android.components.menu.info

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassMaterialSource
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.menu.LocalMenuSheetSurface
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.uiRoundnessShape

/** The existing media statistics card shares the Details card policy and native layout. */
@Composable
fun InformationStatsCard(
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(GlassScope.Details),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = colorPalette()
    val bodyColor = informationMenuBodyColor()
    val body = rememberCanvasBackdrop { drawRect(bodyColor) }
    val menu = LocalGlassContainerBackdrop.current
    val opticalMenu = LocalMenuSheetSurface.current &&
        rememberGlassSettings().usesGlassMaterial(GlassArea.Menus)
    // The information body paints its band outside this card, after the shared menu material.
    val source = if (opticalMenu && menu != null) rememberCombinedBackdrop(menu, body) else body
    CompositionLocalProvider(
        LocalGlassContainerBackdrop provides source,
        LocalGlassMaterialSource provides source,
    ) {
        SettingsCardSurface(
            modifier = modifier,
            shape = uiRoundnessShape(),
            containerColor = palette.accent.copy(alpha = .2f),
            backgroundColor = palette.background0,
            shadowColor = Color.Transparent,
            shadowElevation = 0.dp,
            settings = settings.inScope(GlassScope.Details),
            content = content,
        )
    }
}
