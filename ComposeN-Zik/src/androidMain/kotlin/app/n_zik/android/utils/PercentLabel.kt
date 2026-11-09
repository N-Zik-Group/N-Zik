package app.n_zik.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat

/** Formats the already rounded slider or progress value using the active app locale. */
@Composable
internal fun percentLabel(percent: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { NumberFormat.getPercentInstance(locale) }
    return formatter.format(percent / 100.0)
}
