package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.sliders.CircularSlider
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.formatAsDuration
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.uiRoundnessShape

private const val MINUTES_TO_MILLIS = 60 * 1000L
private const val STEP_MINUTES = 5
private const val MAX_SLIDER_VALUE = 120f

/**
 * Sleep timer dialog on the shared [DialogFrame]. While a timer is running
 * it renders as a [ConfirmationDialog] (no / stop); otherwise a stepper or
 * a circular slider and a cancel / confirm [ButtonRow].
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun SleepTimerDialog(
    sleepTimerMillisLeft: Long?,
    timeRemaining: Long,
    onDismiss: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onStartSleepTimer: (Long) -> Unit
) {
    if (sleepTimerMillisLeft != null) {
        ConfirmationDialog(
            text = stringResource(R.string.stop_sleep_timer),
            cancelText = stringResource(R.string.no),
            confirmText = stringResource(R.string.stop),
            onDismiss = onDismiss,
            onConfirm = {
                onCancelSleepTimer()
                onDismiss()
            }
        )
    } else {
        val palette = colorPalette()
        DialogFrame(
            onDismiss = onDismiss,
            title = stringResource(R.string.set_sleep_timer)
        ) {
            var showCircularSlider by remember { mutableStateOf(false) }
            var amount by remember { mutableStateOf(1) }

            // The frame body is non-scrollable and capped at 80 % of the
            // screen height (canon): the stepper / slider area scrolls inside

            // this column so the confirm / cancel pills stay reachable on
            // short screens.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 16.dp,
                        alignment = Alignment.CenterHorizontally
                    ),
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .fillMaxWidth()
                ) {
                    val sleepTimeMillis = amount * STEP_MINUTES * MINUTES_TO_MILLIS

                    if (!showCircularSlider) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .alpha(if (amount <= 1) 0.5f else 1f)
                                .clip(uiRoundnessShape())
                                .clickable(enabled = amount > 1) { amount-- }
                                .size(48.dp)
                                .background(palette.background0)
                        ) {
                            BasicText(
                                text = "-",
                                style = typography().xs.semiBold.copy(color = palette.text)
                            )
                        }

                        Box(contentAlignment = Alignment.Center) {
                            BasicText(
                                text = stringResource(
                                    R.string.left,
                                    formatAsDuration(sleepTimeMillis)
                                ),
                                style = typography().s.semiBold.copy(color = palette.text),
                                modifier = Modifier.clip(uiRoundnessShape()).clickable {
                                    showCircularSlider = !showCircularSlider
                                }
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .alpha(if (amount >= MAX_SLIDER_VALUE.toInt()) 0.5f else 1f)
                                .clip(uiRoundnessShape())
                                .clickable(enabled = amount < MAX_SLIDER_VALUE.toInt()) { amount++ }
                                .size(48.dp)
                                .background(palette.background0)
                        ) {
                            BasicText(
                                text = "+",
                                style = typography().xs.semiBold.copy(color = palette.text)
                            )
                        }

                    } else {
                        CircularSlider(
                            stroke = 40f,
                            thumbColor = palette.accent,
                            text = formatAsDuration(sleepTimeMillis),
                            modifier = Modifier
                                .testTag("sleepTimerSlider")
                                .fillMaxWidth()
                                .sizeIn(maxWidth = 300.dp, maxHeight = 300.dp)
                                .aspectRatio(1f),
                            onChange = { percentage ->
                                amount = (percentage * MAX_SLIDER_VALUE).toInt()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (timeRemaining > 0) {
                    // The legacy dialog offered this as a secondary button —
                    // restored as a single pill in the shared button language.
                    ButtonRow(
                        buttons = listOf(
                            DialogButton(
                                text = stringResource(R.string.set_to) + " "
                                        + formatAsDuration(timeRemaining)
                                        + " " + stringResource(R.string.end_of_song),
                                onClick = {
                                    onStartSleepTimer(timeRemaining)
                                    onDismiss()
                                }
                            )
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        danger = true
                    ),
                    DialogButton(
                        text = stringResource(R.string.confirm),
                        enabled = amount > 0,
                        onClick = {
                            onStartSleepTimer(amount * STEP_MINUTES * MINUTES_TO_MILLIS)
                            onDismiss()
                        },
                        primary = true
                    )
                )
            )
        }
    }
}
