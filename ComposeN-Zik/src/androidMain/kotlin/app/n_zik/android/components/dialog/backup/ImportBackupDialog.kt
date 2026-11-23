package app.n_zik.android.components.dialog.backup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame

object ImportBackupDialog : ContentFramedDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.backup_action_import)

    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        ImportBackupFlow(
            initialOptions = BackupContent.Database.flag,
            onComplete = ::hideDialog,
        ) { flow ->
            DialogFrame(
                onDismiss = ::hideDialog,
                title = dialogTitle,
                bodyPadding = PaddingValues(0.dp),
                footer = {
                    ButtonRow(
                        buttons = listOf(
                            DialogButton(
                                text = stringResource(R.string.common_action_cancel),
                                onClick = ::hideDialog,
                                danger = true,
                            ),
                            DialogButton(
                                text = stringResource(
                                    if (flow.includeThemes) flow.themeBackup.primaryLabelRes else R.string.common_action_import,
                                ),
                                primary = true,
                                enabled = flow.canSubmit,
                                textColor = colorPalette().textSecondary,
                                onClick = flow.submit,
                            )
                        )
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()).padding(24.dp)
                ) {
                    ImportBackupContentOptions(flow.selectedOptions, flow.selectOptions) { flow.themeBackup.Content() }
                }

                flow.dialogs()
            }
        }
    }
}
