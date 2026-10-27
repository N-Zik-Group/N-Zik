package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.medium

class SyncResultDialog(
    private val activeState: MutableState<Boolean>,
    val results: List<Pair<String, Boolean>>,
    val elapsedSeconds: Long
) : InteractiveDialog {

    companion object {
        @Composable
        operator fun invoke(results: List<Pair<String, Boolean>>, elapsedSeconds: Long) = SyncResultDialog(
            activeState = remember { mutableStateOf(true) },
            results = results,
            elapsedSeconds = elapsedSeconds
        )
    }

    override var isActive: Boolean
        get() = activeState.value
        set(value) { activeState.value = value }

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.sync_now)

    override val bodyPadding: PaddingValues get() = PaddingValues(0.dp)

    @Composable
    override fun DialogBody() {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            results.forEach { (name, success) ->
                val icon = if (success) "\u2713" else "\u2717"
                val color = if (success) colorPalette().text else colorPalette().textDisabled
                BasicText(
                    text = "$icon $name",
                    style = typography().s.medium.copy(color = color)
                )
            }
            if (results.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = stringResource(R.string.sync_result_completed, elapsedSeconds),
                style = typography().s.medium.copy(color = colorPalette().textDisabled)
            )
        }
    }

    @Composable
    override fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(R.string.dialog_close),
                onClick = { hideDialog() },
                primary = true
            )
        )
    )
}
