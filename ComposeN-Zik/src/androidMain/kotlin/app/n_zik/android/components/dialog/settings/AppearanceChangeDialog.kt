package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.ConfirmDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.typography

object AppearanceChangeDialog: ConfirmDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.title_appearance_changes)

    override var isActive: Boolean by mutableStateOf(false)

    override fun hideDialog() {
        isActive = false
    }

    override fun onConfirm() {
        isActive = false
    }

    @Composable
    override fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(R.string.confirm),
                onClick = ::onConfirm,
                primary = true
            )
        )
    )

    @Composable
    override fun DialogBody() {
        BasicText(
            text = stringResource(R.string.appearance_changes_message),
            style = typography().s.copy(
                color = colorPalette().text,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(vertical = 20.dp)
        )
    }
} 





