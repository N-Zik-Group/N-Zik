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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.textCopyToClipboard
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import app.n_zik.android.components.dialog.common.Dialog

/**
 * Reads the log text for the given export [option] from [logsDir]: 0 = debug log, 1 = crash log,
 * 2 = both (each prefixed by a header, separated by a blank line). Returns null when no matching
 * file exists or the option is unknown. Blocking file IO: callers must run it off the Main thread.
 */
internal fun readLogContent(logsDir: File, option: Int): String? {
    val debugFile = File(logsDir, "N-Zik_log.txt")
    val crashFile = File(logsDir, "N-Zik_crash_log.txt")

    return when (option) {
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

/**
 * Runs [read] (blocking file IO) on [NzikDispatchers.DATA] so the log read never happens on the
 * Main thread that delivers the click / activity-result callbacks. [read] is injectable so a test
 * can observe the thread it runs on.
 */
internal suspend fun loadLogContent(
    logsDir: File,
    option: Int,
    read: (File, Int) -> String? = ::readLogContent
): String? = withContext(NzikDispatchers.DATA) { read(logsDir, option) }

/**
 * The SAF export file name for a given export [option] (0 = debug, 1 = crash, anything
 * else = both): pinned here so the dialog's launcher and the tests share one mapping.
 */
internal fun logExportFileName(option: Int): String = when (option) {
    0 -> "N-Zik_debug_log.txt"
    1 -> "N-Zik_crash_log.txt"
    else -> "N-Zik_logs.txt"
}

/**
 * Whether an export [option] is greyed out (not selectable) for the given debug-log state:
 * the "debug log" (0) and "both logs" (2) options are meaningless while debug logging is
 * off (its file is purged on disable), so only the "crash log" (1) option stays selectable.
 */
internal fun isLogExportOptionDisabled(option: Int, debugLogEnabled: Boolean): Boolean =
    option != 1 && !debugLogEnabled

object CopyLogsDialog : Dialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.export_logs)

    override var isActive: Boolean by mutableStateOf(false)

    private var selectedOption = mutableIntStateOf(0)

    /**
     * Opens the dialog with a given option pre-selected (the sheet's crash row exports
     * from the crash option; the sheet's debug row and the settings entry keep the
     * default debug option).
     */
    fun showDialogFor(option: Int) {
        selectedOption.intValue = option
        isActive = true
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val currentOption by selectedOption

        val noLogAvailable = stringResource(R.string.no_log_available)

        // Live preview of the selected option's log (re-read on every option change):
        // all three options are viewable + copyable, not just exportable — the same
        // "complete" log view the Maintenance sheet rows open
        var content by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(currentOption) {
            content = loadLogContent(context.filesDir.resolve("logs"), currentOption)
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            val option = currentOption
            coroutineScope.launch {
                val content = loadLogContent(context.filesDir.resolve("logs"), option)
                if (content == null) {
                    Toaster.w(noLogAvailable)
                    return@launch
                }
                withContext(NzikDispatchers.DATA) {
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
        }

        val debugLogLabel = stringResource(R.string.export_debug_log)
        val debugLogDescription = stringResource(R.string.export_debug_log_description)
        val crashLogLabel = stringResource(R.string.export_crash_log)
        val crashLogDescription = stringResource(R.string.export_crash_log_description)
        val bothLabel = stringResource(R.string.export_both_logs)
        val bothDescription = stringResource(R.string.export_both_logs_description)

        // "Debug log" and "Both logs" are greyed out while debug logging is off (their
        // debug content doesn't exist then): only the crash log option stays selectable,
        // and a stale selection on a now-disabled option falls back to it
        val debugLogEnabled by rememberPreference(logDebugEnabledKey, false)
        LaunchedEffect(currentOption, debugLogEnabled) {
            if (isLogExportOptionDisabled(currentOption, debugLogEnabled)) {
                selectedOption.intValue = 1
            }
        }

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
                    val optionDisabled = isLogExportOptionDisabled(index, debugLogEnabled)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .alpha(if (optionDisabled) 0.4f else 1f)
                            .clickable(enabled = !optionDisabled) { selectedOption.intValue = index }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentOption == index && !optionDisabled,
                            onClick = { if (!optionDisabled) selectedOption.intValue = index },
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

            // The selected option's log content, scrollable — the "complete" view
            // (empty file / no file answers the standard "Log unavailable" sentence)
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

            // Copy + Export side by side (the crash/debug log dialog pattern): the
            // export re-reads the file at click time so a log written after the
            // preview never exports stale content
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
                        contentColor = colorPalette().textSecondary
                    ),
                    shape = uiRoundnessShape()
                ) {
                    Text(
                        text = stringResource(R.string.maintenance_copy),
                        style = typography().s.medium
                    )
                }
                Spacer(modifier = Modifier.padding(end = 8.dp))
                Button(
                    onClick = {
                        val option = currentOption
                        coroutineScope.launch {
                            val content = loadLogContent(context.filesDir.resolve("logs"), option)
                            if (content == null) {
                                Toaster.w(noLogAvailable)
                            } else {
                                launcher.launch(logExportFileName(option))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
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
}
