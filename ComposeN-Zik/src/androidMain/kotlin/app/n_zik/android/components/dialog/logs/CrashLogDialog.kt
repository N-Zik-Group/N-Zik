package app.n_zik.android.components.dialog.logs

import androidx.compose.foundation.layout.PaddingValues
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.it.fast4x.rimusic.utils.textCopyToClipboard
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers

/**
 * The Maintenance sheet's "Last crash" row viewer (the [CopyLogsDialog] pattern,
 * crash-only): shows the full crash log file (scrollable) with a Copy button
 * (clipboard) and the same SAF Export as the logs dialog. The single [Render] host
 * lives in the persistent header (ActionBar.kt), next to [CopyLogsDialog]'s host —
 * Render() is a singleton per dialog object, so a second host anywhere would stack
 * two dialogs on the same showDialog() activation.
 */
object CrashLogDialog : ContentFramedDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.maintenance_last_crash)

    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var content by remember { mutableStateOf<String?>(null) }

        val noLogAvailable = stringResource(R.string.no_log_available)

        // The crash file is re-read on every open (the dialog re-composes on activation)
        LaunchedEffect(Unit) {
            content = loadLogContent(context.filesDir.resolve("logs"), 1)
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            val text = content ?: return@rememberLauncherForActivityResult
            coroutineScope.launch {
                withContext(NzikDispatchers.DATA) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            outStream.write(text.toByteArray())
                            Timber.tag("CrashLogDialog").d("Crash log exported")
                        }
                    } catch (e: Exception) {
                        Timber.tag("CrashLogDialog").e(e, "Failed to export the crash log")
                    }
                }
            }
        }

        DialogFrame(
            onDismiss = ::hideDialog,
            title = dialogTitle,
            bodyPadding = PaddingValues(0.dp),
        footer = {
                ButtonRow(
                    buttons = listOf(
                        DialogButton(
                            text = stringResource(R.string.maintenance_copy),
                            primary = true,
                            textColor = colorPalette().textSecondary,
                            onClick = {
                                val text = content
                                if (text.isNullOrBlank()) {
                                    Toaster.w(noLogAvailable)
                                } else {
                                    textCopyToClipboard(text, context)
                                }
                            },
                        ),
                        DialogButton(
                            text = stringResource(R.string.export),
                            primary = true,
                            textColor = colorPalette().textSecondary,
                            onClick = {
                                val text = content
                                if (text.isNullOrBlank()) {
                                    Toaster.w(noLogAvailable)
                                } else {
                                    launcher.launch("N-Zik_crash_log.txt")
                                }
                            },
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
                val text = content
                if (text.isNullOrBlank()) {
                    Text(
                        text = noLogAvailable,
                        style = typography().xs,
                        color = colorPalette().textSecondary,
                    )
                } else {
                    Text(
                        text = text,
                        style = typography().xxs,
                        color = colorPalette().text,
                    )
                }
            }
        }
    }
}
