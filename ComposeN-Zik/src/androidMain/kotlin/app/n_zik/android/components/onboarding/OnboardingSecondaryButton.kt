package app.n_zik.android.components.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.glass.PageTextButton
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape

/** Permission actions keep their native variant and replace its surface only in Glass. */
@Composable
internal fun OnboardingSecondaryButton(
    onClick: () -> Unit,
    outlined: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val settings = rememberGlassSettings()
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    val shape = if (material) uiRoundnessShape() else ButtonDefaults.shape
    if (outlined && !material) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.glassAreaContour(settings, GlassArea.ActionButtons, shape),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorPalette().text),
            border = if (settings.usesGlassContour(GlassArea.ActionButtons)) null
                else BorderStroke(1.dp, colorPalette().textSecondary),
            content = content,
        )
    } else if (outlined) {
        // PageButton keeps Material input and uses one shared Glass highlight.
        PageButton(
            onClick = onClick,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorPalette().text),
            border = BorderStroke(1.dp, colorPalette().textSecondary),
            elevation = null,
            settings = settings,
            content = content,
        )
    } else {
        PageTextButton(
            onClick = onClick,
            shape = shape,
            colors = ButtonDefaults.buttonColors(contentColor = colorPalette().text),
            settings = settings,
            content = content,
        )
    }
}
