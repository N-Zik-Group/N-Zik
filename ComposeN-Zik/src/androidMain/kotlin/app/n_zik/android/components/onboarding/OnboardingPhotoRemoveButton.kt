package app.n_zik.android.components.onboarding

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import app.n_zik.android.components.ui.UiIconButton as IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.PageTextButton
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape

/** Photo action retains its native icon button when the Glass material is disabled. */
@Composable
internal fun OnboardingPhotoRemoveButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val settings = rememberGlassSettings()
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        PageTextButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = uiRoundnessShape(),
            contentPadding = PaddingValues(0.dp),
            settings = settings,
        ) { content() }
    } else {
        IconButton(onClick = onClick, content = content)
    }
}
