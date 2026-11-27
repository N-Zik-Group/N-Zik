package app.n_zik.android.components.settings.common

import java.text.NumberFormat
import kotlin.math.round
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import app.n_zik.android.R
import app.n_zik.android.components.player.clampCoverDimmingOpacity

/** Displays and edits a profile's cover opacity as a whole-number percentage. */
@Composable
internal fun CoverDimmingSlider(
    title: String,
    opacity: Float,
    defaultOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { NumberFormat.getPercentInstance(locale) }
    SliderSettingsEntry(
        title = title,
        text = "",
        icon = R.drawable.images_sharp,
        state = clampCoverDimmingOpacity(opacity, defaultOpacity),
        range = 0f..1f,
        onSlide = { onOpacityChange(round(it * 100f) / 100f) },
        toDisplay = { formatter.format(it.toDouble()) },
        defaultValue = defaultOpacity,
        stepSize = 0f,
        isIntegerOnly = true,
        inputScale = 100f,
        modifier = modifier,
    )
}
