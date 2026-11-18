package app.n_zik.android.utils

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

private fun compactDurationLabel(locale: Locale, vararg units: Measure): String =
    MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*units)

internal fun secondsLabel(seconds: Int, locale: Locale): String =
    compactDurationLabel(locale, Measure(seconds, MeasureUnit.SECOND))

@Composable
internal fun secondsLabel(seconds: Int): String =
    secondsLabel(seconds, LocalConfiguration.current.locales[0])

internal fun minutesLabel(minutes: Int, locale: Locale): String =
    compactDurationLabel(locale, Measure(minutes, MeasureUnit.MINUTE))

@Composable
internal fun minutesLabel(minutes: Int): String =
    minutesLabel(minutes, LocalConfiguration.current.locales[0])

internal fun compactPlayTimeLabel(millis: Long, locale: Locale): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val measure = when {
        hours == 0L -> Measure(minutes, MeasureUnit.MINUTE)
        hours < 24 -> Measure(hours, MeasureUnit.HOUR)
        else -> Measure(hours / 24, MeasureUnit.DAY)
    }
    return compactDurationLabel(locale, measure)
}

@Composable
internal fun compactPlayTimeLabel(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(millis, locale) { compactPlayTimeLabel(millis, locale) }
}

internal fun formatAsTime(millis: Long, locale: Locale): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    val parts = buildList {
        val days = seconds / 86_400
        val hours = seconds / 3_600 % 24
        val minutes = seconds / 60 % 60
        if (days > 0) add(Measure(days, MeasureUnit.DAY))
        if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
        if (minutes > 0) add(Measure(minutes, MeasureUnit.MINUTE))
        val remainder = seconds % 60
        if (remainder > 0 || isEmpty()) add(Measure(remainder, MeasureUnit.SECOND))
    }
    return compactDurationLabel(locale, *parts.toTypedArray())
}

@Composable
internal fun formatAsTime(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(millis, locale) { formatAsTime(millis, locale) }
}
