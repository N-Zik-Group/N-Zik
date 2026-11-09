package app.n_zik.android.components.dialog.export

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.backup.DatabaseBackup
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DatabaseExportAction(val inProgress: Boolean, val launch: () -> Unit)

/** Prepares a single export whose MIME type, name and bytes stay paired across the SAF flow. */
@Composable
internal fun rememberDatabaseExport(
    prepare: () -> DatabaseBackup.Export,
    context: Context,
    onResult: (Result<Unit>) -> Unit,
    onPickerFinished: () -> Unit = {},
): DatabaseExportAction {
    val scope = rememberCoroutineScope()
    val resultCallback by rememberUpdatedState(onResult)
    val finishCallback by rememberUpdatedState(onPickerFinished)
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val path = pendingPath ?: return@rememberLauncherForActivityResult
        pendingPath = null
        val uri = result.data?.data?.takeIf { result.resultCode == android.app.Activity.RESULT_OK }
        // The selected document and staged file belong to this write even if the UI leaves.
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            val written = runCatching {
                val export = try {
                    DatabaseBackup.resumeExport(context, path)
                } catch (error: Exception) {
                    if (uri != null) runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    throw error
                }
                export.use {
                    if (uri != null) it.writeTo(context, uri)
                    Unit
                }
            }
            if (uri != null || written.isFailure) {
                withContext(NzikDispatchers.UI) { resultCallback(written) }
            }
        }
        finishCallback()
    }

    return DatabaseExportAction(inProgress = preparing || pendingPath != null) {
        if (!preparing && pendingPath == null) {
            preparing = true
            scope.launch {
                var prepared: DatabaseBackup.Export? = null
                try {
                    // Assign before returning to the UI so cancellation during dispatch still
                    // leaves the finally block with the staging file to clean up.
                    withContext(NzikDispatchers.DATA) {
                        val export = prepare()
                        prepared = export
                        export.handOffToPicker()
                    }
                    val export = checkNotNull(prepared)
                    pendingPath = export.file.absolutePath
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(export.format.mime)
                        .putExtra(Intent.EXTRA_TITLE, "${BuildConfig.APP_NAME} ${LocalDate.now()} Database.${export.format.extension}")
                    launcher.launch(intent)
                    prepared = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    pendingPath = null
                    resultCallback(Result.failure(error))
                    finishCallback()
                } finally {
                    preparing = false
                    withContext(NonCancellable + NzikDispatchers.DATA) { prepared?.close() }
                }
            }
        }
    }
}
