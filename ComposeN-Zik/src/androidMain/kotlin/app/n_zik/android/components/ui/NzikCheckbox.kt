package app.n_zik.android.components.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape

@Composable
internal fun NzikCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = colorPalette()
    val tint = when {
        !enabled -> palette.textDisabled
        checked -> palette.accent
        else -> palette.textSecondary
    }
    val control = if (onCheckedChange == null) {
        Modifier
    } else {
        Modifier.size(48.dp).clip(uiRoundnessShape())
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    }
    Box(modifier.then(control), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(if (checked) R.drawable.checked_filled else R.drawable.unchecked_outline),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
