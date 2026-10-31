package app.n_zik.android.components.dialog.player

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import app.n_zik.android.components.ui.sliders.SliderControl
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.NativeSectionIconAction
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.InteractiveDialog
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.semiBold

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

    override val bodyPadding: PaddingValues get() = PaddingValues(0.dp)

    @Composable
    override fun DialogBody() {
        var lyricsOffsetState = rememberPreference("lyricsOffset_$mediaId", 0L)
        var lyricsOffset by rememberSaveable { mutableIntStateOf(lyricsOffsetState.value.toInt()) }
        var textFieldValue by rememberSaveable { mutableStateOf(lyricsOffset.toString()) }
        val palette = colorPalette()

        LaunchedEffect(lyricsOffset) {
            lyricsOffsetState.value = lyricsOffset.toLong()
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
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
                    textStyle = typography().l.bold.copy(
                        textAlign = TextAlign.Center,
                        color = palette.text
                    ),
                    modifier = Modifier.widthIn(min = 120.dp, max = 160.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = palette.text,
                        unfocusedTextColor = palette.text,
                        focusedContainerColor = palette.background1,
                        unfocusedContainerColor = palette.background1,
                        cursorColor = palette.accent,
                        focusedIndicatorColor = palette.accent,
                        unfocusedIndicatorColor = palette.textDisabled
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                BasicText(
                    text = "ms",
                    style = typography().s.medium.copy(
                        color = palette.textSecondary
                    )
                )

                if (lyricsOffset != 0) {
                    Spacer(Modifier.width(8.dp))

                    NativeSectionIconAction(
                        icon = R.drawable.refresh,
                        color = palette.accent,
                        contentDescription = stringResource(R.string.cd_reset),
                        onClick = {
                            lyricsOffset = 0
                            textFieldValue = "0"
                        },
                    )
                }
            }

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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .alpha(if (lyricsOffset <= -3000) 0.5f else 1f)
                        .clip(uiRoundnessShape())
                        .clickable(enabled = lyricsOffset > -3000) {
                            lyricsOffset = (lyricsOffset - 50).coerceIn(-3000, 3000)
                            textFieldValue = lyricsOffset.toString()
                        }
                        .size(48.dp)
                        .background(palette.background0)
                ) {
                    BasicText(
                        text = "-",
                        style = typography().xs.semiBold.copy(color = palette.text)
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

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .alpha(if (lyricsOffset >= 3000) 0.5f else 1f)
                        .clip(uiRoundnessShape())
                        .clickable(enabled = lyricsOffset < 3000) {
                            lyricsOffset = (lyricsOffset + 50).coerceIn(-3000, 3000)
                            textFieldValue = lyricsOffset.toString()
                        }
                        .size(48.dp)
                        .background(palette.background0)
                ) {
                    BasicText(
                        text = "+",
                        style = typography().xs.semiBold.copy(color = palette.text)
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
                BasicText(
                    text = "-3000ms",
                    style = typography().xxs.copy(color = palette.textSecondary)
                )
                BasicText(
                    text = "+3000ms",
                    style = typography().xxs.copy(color = palette.textSecondary)
                )
            }
        }
    }

    @Composable
    override fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(R.string.dialog_close),
                onClick = ::hideDialog,
                primary = true
            )
        )
    )
}
