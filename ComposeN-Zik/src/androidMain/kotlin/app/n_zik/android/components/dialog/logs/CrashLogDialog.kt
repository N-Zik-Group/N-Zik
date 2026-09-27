package app.n_zik.android.components.dialog.logs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.textCopyToClipboard
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.Dialog
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The Maintenance sheet's "Last crash" row viewer (the [CopyLogsDialog] pattern,
 * crash-only): shows the full crash log file (scrollable) with a Copy button
 * (clipboard) and the same SAF Export as the logs dialog. The single [Render] host
 * lives in the persistent header (ActionBar.kt), next to [CopyLogsDialog]'s host —
 * Render() is a singleton per dialog object, so a second host anywhere would stack
 * two dialogs on the same showDialog() activation.
 */
object CrashLogDialog : Dialog {

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

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val text = content
                        if (text.isNullOrBlank()) {
                            Toaster.w(noLogAvailable)
                        } else {
                            textCopyToClipboard(text, context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary,
                    ),
                    shape = uiRoundnessShape(),
                ) {
                    Text(
                        text = stringResource(R.string.maintenance_copy),
                        style = typography().s.medium,
                    )
                }
                Spacer(modifier = Modifier.padding(end = 8.dp))
                Button(
                    onClick = {
                        val text = content
                        if (text.isNullOrBlank()) {
                            Toaster.w(noLogAvailable)
                        } else {
                            launcher.launch("N-Zik_crash_log.txt")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary,
                    ),
                    shape = uiRoundnessShape(),
                ) {
                    Text(
                        text = stringResource(R.string.export),
                        style = typography().s.medium,
                    )
                }
            }
        }
    }
}
