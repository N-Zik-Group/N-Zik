package app.n_zik.android.components.extensions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.typography

/**
 * Module info dialog (spec-ffmpeg-extension-module): title + version/catalog
 * text block + a single OK pill on the shared [DialogFrame].
 */
@Composable
fun ModuleInfoDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit,
) {
    DialogFrame(onDismiss = onDismiss, title = title) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Scrollable content so the banner never pushes the button row out
            // of the frame (CrashLogDialog pattern)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                BasicText(
                    text = content,
                    style = typography().xs.copy(color = colorPalette().textSecondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(R.string.ok),
                        onClick = onDismiss,
                        primary = true,
                    ),
                ),
            )
        }
    }
}
