package app.n_zik.android.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassChipBackground
import app.n_zik.android.components.glass.rememberGlassSettings

/** Applies the shared Glass fill and rim to an icon container, preserving its native fill. */
@Composable
fun Modifier.iconBadgeBackground(
    color: Color,
    shape: Shape,
    settings: GlassSettings = rememberGlassSettings(),
): Modifier = glassChipBackground(color, shape, settings)
