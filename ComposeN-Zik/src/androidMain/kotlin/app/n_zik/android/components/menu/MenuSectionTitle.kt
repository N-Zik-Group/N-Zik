package app.n_zik.android.components.menu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.TextContrast
import app.n_zik.android.components.glass.surfaceGlyphColor
import app.n_zik.android.typography
import app.n_zik.android.utils.semiBold

@Composable
internal fun MenuSectionTitle(title: String) {
    BasicText(
        text = title,
        style = typography().xxs.semiBold.copy(
            color = surfaceGlyphColor(colorPalette().accent, minimum = TextContrast),
            textAlign = TextAlign.Start,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).menuSectionHeading(),
    )
}
