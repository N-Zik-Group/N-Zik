package app.n_zik.android.components.dialog.updater

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.BuildConfig

/**
 * Major update warning, on the shared [DialogFrame]:
 * alert header, message and a single confirm [ButtonRow] pill.
 */
object MajorUpdateWarningDialog {
    var isActive: Boolean by mutableStateOf(false)

    @Composable
    fun Render(onConfirm: () -> Unit) {
        if (!isActive) return

        DialogFrame(onDismiss = {
            isActive = false
            onConfirm()
        }) {
            // Header
            Icon(
                painter = painterResource(R.drawable.alert),
                contentDescription = null,
                tint = colorPalette().red,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = stringResource(R.string.major_update_warning_title, BuildConfig.VERSION_NAME),
                style = typography().l.bold.copy(color = colorPalette().text)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Message
            BasicText(
                text = stringResource(R.string.major_update_warning_message),
                style = typography().s.semiBold.copy(color = colorPalette().text),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(R.string.major_update_warning_button),
                        icon = R.drawable.checkmark,
                        onClick = {
                            isActive = false
                            onConfirm()
                        },
                        primary = true
                    )
                )
            )
        }
    }
}
