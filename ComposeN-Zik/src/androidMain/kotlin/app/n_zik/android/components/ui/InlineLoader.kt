package app.n_zik.android.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassIndicatorSilhouette
import app.n_zik.android.components.glass.rememberGlassSettings

/** App loader for cards and dialogs without the full-page navigation spacer. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InlineLoader(modifier: Modifier = Modifier, size: Dp = 100.dp) {
    val accent = colorPalette().accent
    GlassIndicatorSilhouette(
        rememberGlassSettings(), GlassArea.LoadingIndicators, accent, modifier,
    ) { glyph ->
        LoadingIndicator(color = accent, modifier = glyph.size(size))
    }
}

/** Adapts the Material spinner without changing its native animation or dimensions. */
@Composable
internal fun InlineCircularLoader(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) {
    GlassIndicatorSilhouette(
        rememberGlassSettings(), GlassArea.LoadingIndicators, color, modifier,
    ) { glyph ->
        CircularProgressIndicator(modifier = glyph, color = color, strokeWidth = strokeWidth)
    }
}

/** Centers the shared loading indicator and accounts for floating bottom navigation. */
@Composable
internal fun FullPageLoader(size: Dp = 100.dp, modifier: Modifier = Modifier.fillMaxWidth()) {
    val bottomPadding = if (NavigationBarPosition.BottomFloating.isCurrent()) Dimensions.bottomSpacer else 0.dp
    Box(modifier.padding(bottom = bottomPadding), contentAlignment = Alignment.Center) {
        InlineLoader(size = size)
    }
}
