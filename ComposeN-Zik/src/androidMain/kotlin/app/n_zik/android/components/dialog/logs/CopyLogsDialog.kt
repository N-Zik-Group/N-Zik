package app.n_zik.android.components.dialog.logs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import app.n_zik.android.components.dialog.common.Dialog

object CopyLogsDialog : Dialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.export_logs)

    override var isActive: Boolean by mutableStateOf(false)

    private var selectedOption = mutableIntStateOf(0)

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val currentOption by selectedOption

        val noLogAvailable = stringResource(R.string.no_log_available)

        fun readLogContent(): String? {
            val debugFile = File(context.filesDir.resolve("logs"), "N-Zik_log.txt")
            val crashFile = File(context.filesDir.resolve("logs"), "N-Zik_crash_log.txt")

            return when (currentOption) {
                0 -> {
                    if (debugFile.exists()) debugFile.readText() else null
                }
                1 -> {
                    if (crashFile.exists()) crashFile.readText() else null
                }
                2 -> {
                    val texts = mutableListOf<String>()
                    if (debugFile.exists()) {
                        texts.add("=== DEBUG LOG ===\n${debugFile.readText()}")
                    }
                    if (crashFile.exists()) {
                        texts.add("=== CRASH LOG ===\n${crashFile.readText()}")
                    }
                    if (texts.isNotEmpty()) texts.joinToString("\n\n") else null
                }
                else -> null
            }
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            val content = readLogContent()
            if (content == null) {
                Toaster.w(noLogAvailable)
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        outStream.write(content.toByteArray())
                        Timber.tag("CopyLogsDialog").d("Logs exported successfully")
                    }
                } catch (e: Exception) {
                    Timber.tag("CopyLogsDialog").e(e, "Failed to export logs")
                }
            }
        }

        fun getExportFileName(): String {
            return when (currentOption) {
                0 -> "N-Zik_debug_log.txt"
                1 -> "N-Zik_crash_log.txt"
                else -> "N-Zik_logs.txt"
            }
        }

        val debugLogLabel = stringResource(R.string.export_debug_log)
        val debugLogDescription = stringResource(R.string.export_debug_log_description)
        val crashLogLabel = stringResource(R.string.export_crash_log)
        val crashLogDescription = stringResource(R.string.export_crash_log_description)
        val bothLabel = stringResource(R.string.export_both_logs)
        val bothDescription = stringResource(R.string.export_both_logs_description)

        val options = listOf(
            Triple(R.drawable.copy, debugLogLabel, debugLogDescription),
            Triple(R.drawable.copy, crashLogLabel, crashLogDescription),
            Triple(R.drawable.copy, bothLabel, bothDescription)
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                options.forEachIndexed { index, (_, title, description) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clickable { selectedOption.intValue = index }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentOption == index,
                            onClick = { selectedOption.intValue = index },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorPalette().text,
                                unselectedColor = colorPalette().textSecondary
                            ),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = typography().xs.semiBold,
                                color = colorPalette().text
                            )
                            Text(
                                text = description,
                                style = typography().xxs,
                                color = colorPalette().textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val content = readLogContent()
                    if (content == null) {
                        Toaster.w(noLogAvailable)
                    } else {
                        launcher.launch(getExportFileName())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette().accent,
                    contentColor = colorPalette().textSecondary
                ),
                shape = uiRoundnessShape()
            ) {
                Text(
                    text = stringResource(R.string.export),
                    style = typography().s.medium
                )
            }
        }
    }
}
