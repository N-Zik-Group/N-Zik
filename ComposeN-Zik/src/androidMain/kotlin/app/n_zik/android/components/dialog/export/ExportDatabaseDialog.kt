package app.n_zik.android.components.dialog.export

import app.n_zik.android.core.database.*

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.FileInputStream

class ExportDatabaseDialog private constructor(
    private val launcher: ManagedActivityResultLauncher<String, Uri?>
) {
    companion object {
        @Composable
        operator fun invoke(context: Context): ExportDatabaseDialog {
            val coroutineScope = rememberCoroutineScope()
            return ExportDatabaseDialog(
                rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/vnd.sqlite3")
                ) { uri ->
                    Timber.tag("ExportDatabaseDialog").d("File picker callback received, uri: $uri")
                    uri ?: return@rememberLauncherForActivityResult
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            Timber.tag("ExportDatabaseDialog").d("Starting database export...")
                            Database.checkpoint()
                            Timber.tag("ExportDatabaseDialog").d("Database checkpoint done")
                            context.applicationContext
                                .contentResolver
                                .openOutputStream(uri)
                                ?.use { outStream ->
                                    val dbFile = context.getDatabasePath(Database.FILE_NAME)
                                    Timber.tag("ExportDatabaseDialog").d("Copying database from: ${dbFile.absolutePath}")
                                    FileInputStream(dbFile).use { inStream ->
                                        val bytes = inStream.copyTo(outStream)
                                        Timber.tag("ExportDatabaseDialog").d("Export complete, bytes written: $bytes")
                                    }
                                } ?: Timber.tag("ExportDatabaseDialog").w("Failed to open output stream")
                        } catch (e: Exception) {
                            Timber.tag("ExportDatabaseDialog").e(e, "Export failed")
                        }
                    }
                }
            )
        }
    }

    fun export() {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val fileName = "${BuildConfig.APP_NAME} $date Database"
        Timber.tag("ExportDatabaseDialog").d("Launching file picker with name: $fileName.sqlite")
        launcher.launch("$fileName.sqlite")
    }
}
