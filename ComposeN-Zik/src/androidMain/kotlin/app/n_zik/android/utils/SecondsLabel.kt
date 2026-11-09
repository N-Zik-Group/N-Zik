package app.n_zik.android.utils

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

internal fun secondsLabel(seconds: Int, locale: Locale): String =
    MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        .formatMeasures(Measure(seconds, MeasureUnit.SECOND))

@Composable
internal fun secondsLabel(seconds: Int): String =
    secondsLabel(seconds, LocalConfiguration.current.locales[0])
