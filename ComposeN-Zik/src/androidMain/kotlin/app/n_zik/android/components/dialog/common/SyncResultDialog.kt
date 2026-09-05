package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.medium
import app.n_zik.android.components.dialog.common.InteractiveDialog

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

    @Composable
    override fun DialogBody() {
        Column {
            results.forEach { (name, success) ->
                val icon = if (success) "\u2713" else "\u2717"
                val color = if (success) colorPalette().text else colorPalette().textDisabled
                BasicText(
                    text = "$icon $name",
                    style = typography().s.medium.copy(color = color)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = stringResource(R.string.sync_result_completed, elapsedSeconds),
                style = typography().s.medium.copy(color = colorPalette().textDisabled)
            )
        }
    }

    @Composable
    override fun Buttons() {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Button(
                onClick = { hideDialog() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette().accent,
                    contentColor = colorPalette().textSecondary
                ),
                shape = uiRoundnessShape()
            ) {
                InteractiveDialog.ConfirmButton(onConfirm = { hideDialog() })
            }
        }
    }
}
