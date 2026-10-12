package app.n_zik.android.components.dialog.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.n_zik.android.components.ui.sliders.SliderControl
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.InteractiveDialog

class ShowOffsetDialog private constructor(
    activeState: MutableState<Boolean>,
    private val mediaId: String
) : InteractiveDialog {

    companion object {
        @Composable
        operator fun invoke(mediaId: String): ShowOffsetDialog {
            return ShowOffsetDialog(remember { mutableStateOf(false) }, mediaId)
        }
    }

    override var isActive: Boolean by activeState

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.lyrics_offset)

    fun onShortClick() = showDialog()

    @Composable
    override fun DialogBody() {
        var lyricsOffsetState = rememberPreference("lyricsOffset_$mediaId", 0L)
        var lyricsOffset by rememberSaveable { mutableIntStateOf(lyricsOffsetState.value.toInt()) }
        var textFieldValue by rememberSaveable { mutableStateOf(lyricsOffset.toString()) }

        LaunchedEffect(lyricsOffset) {
            lyricsOffsetState.value = lyricsOffset.toLong()
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = colorPalette().accent
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = textFieldValue,
                    onValueChange = { newText ->
                        val sanitized = newText.filter {
                            it.isDigit() || (it == '-' && newText.indexOf('-') == 0)
                        }

                        val limited = if (sanitized.startsWith('-')) {
                            sanitized.take(6)
                        } else {
                            sanitized.take(5)
                        }

                        textFieldValue = limited

                        when {
                            limited.isEmpty() -> {
                                lyricsOffset = 0
                                textFieldValue = "0"
                            }

                            limited == "-" -> {
                            }

                            else -> {
                                limited.toIntOrNull()?.let { parsedValue ->
                                    val clampedValue = parsedValue.coerceIn(-9999, 9999)
                                    lyricsOffset = clampedValue

                                    if (parsedValue != clampedValue) {
                                        textFieldValue = clampedValue.toString()
                                    }

                                    if (clampedValue == 0 && limited.startsWith('-')) {
                                        textFieldValue = "0"
                                    }
                                }
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.widthIn(min = 120.dp, max = 160.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = colorPalette().text,
                        unfocusedTextColor = colorPalette().text,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = colorPalette().accent,
                        focusedIndicatorColor = colorPalette().accent,
                        unfocusedIndicatorColor = colorPalette().textDisabled,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = colorPalette().red
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "ms",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorPalette().textSecondary,
                    fontWeight = FontWeight.Medium
                )

                if (lyricsOffset != 0) {
                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            lyricsOffset = 0
                            textFieldValue = "0"
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.refresh),
                            tint = colorPalette().accent,
                            contentDescription = stringResource(R.string.cd_reset)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        lyricsOffset = (lyricsOffset - 50).coerceIn(-3000, 3000)
                        textFieldValue = lyricsOffset.toString()
                    }
                ) {
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colorPalette().text
                    )
                }

                SliderControl(
                    state = lyricsOffset.toFloat(),
                    onSlide = { newValue ->
                        val rounded = (newValue / 100).toInt() * 100
                        lyricsOffset = rounded
                        textFieldValue = rounded.toString()
                    },
                    range = -3000f..3000f,
                    stepSize = 100f,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        lyricsOffset = (lyricsOffset + 50).coerceIn(-3000, 3000)
                        textFieldValue = lyricsOffset.toString()
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        tint = colorPalette().text,
                        contentDescription = stringResource(R.string.cd_increase)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
            ) {
                Text(
                    text = "-3000ms",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorPalette().textSecondary
                )
                Text(
                    text = "+3000ms",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorPalette().textSecondary
                )
            }
        }
    }

    @Composable
    override fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(R.string.confirm),
                onClick = ::hideDialog,
                primary = true
            )
        )
    )
}

