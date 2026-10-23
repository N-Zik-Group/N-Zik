package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.uiRoundnessShape

/**
 * Generic value selector on the shared [DialogFrame]: a radio list in the n_zik radio language
 * (20dp [RadioButton], a single-line `xs.semiBold` label that marquees when it does not fit)
 * and a cancel pill in a [ButtonRow].
 *
 * [lockedValues] are rows that cannot be selected; with [lockedLabel] given, they are dimmed
 * and show the label (e.g. "Not logged in").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ValueSelectorDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    title: String,
    titleSecondary: String? = null,
    selectedValue: T,
    values: List<T>,
    onValueSelected: (T) -> Unit,
    valueText: @Composable (T) -> String = { it.toString() },
    lockedValues: Set<T> = emptySet(),
    lockedLabel: String? = null
) {
    val palette = colorPalette()

    DialogFrame(
        onDismiss = onDismiss,
        modifier = modifier,
        title = title
    ) {
        titleSecondary?.let {
            BasicText(
                text = it,
                style = typography().s.copy(
                    color = palette.textSecondary
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, false),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            values.forEach { value ->
                val isLocked = value in lockedValues

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(uiRoundnessShape())
                        .clickable(enabled = !isLocked) {
                            onDismiss()
                            onValueSelected(value)
                        }
                        .alpha(if (isLocked && lockedLabel != null) 0.4f else 1f)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    RadioButton(
                        selected = selectedValue == value,
                        enabled = !isLocked,
                        onClick = {
                            onDismiss()
                            onValueSelected(value)
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = palette.text,
                            unselectedColor = palette.textSecondary
                        ),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    BasicText(
                        text = valueText(value),
                        maxLines = 1,
                        style = typography().xs.semiBold.copy(
                            color = palette.text
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )

                    if (isLocked && lockedLabel != null) {
                        BasicText(
                            text = lockedLabel,
                            style = typography().xxs.copy(
                                color = palette.textSecondary
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ButtonRow(
            buttons = listOf(
                DialogButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    danger = true
                )
            )
        )
    }
}
