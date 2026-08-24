package app.n_zik.android.components.import

import app.n_zik.android.core.database.*

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.components.ImportFromFile
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.io.FileOutputStream

class ImportDatabase private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher) {

    companion object {
        @Composable
        operator fun invoke( context: Context, onImportComplete: (() -> Unit)? = null ): ImportDatabase =
            ImportDatabase(
                rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    Timber.tag("ImportDatabase").d("File picker callback received, uri: $uri")
                    uri ?: return@rememberLauncherForActivityResult

                    CoroutineScope( Dispatchers.IO ).launch {
                        try {
                            Timber.tag("ImportDatabase").d("Starting database import...")
                            Database.checkpoint()
                            Timber.tag("ImportDatabase").d("Database checkpoint done")
                            Database.close()
                            Timber.tag("ImportDatabase").d("Database closed")

                            context.applicationContext
                                   .contentResolver
                                   .openInputStream(uri)
                                   ?.use { inStream ->
                                       val dbFile = context.getDatabasePath( Database.FILE_NAME )
                                       Timber.tag("ImportDatabase").d("Copying database to: ${dbFile.absolutePath}")
                                       FileOutputStream( dbFile ).use { outStream ->
                                           val bytes = inStream.copyTo(outStream)
                                           Timber.tag("ImportDatabase").d("Import complete, bytes written: $bytes")
                                       }
                                   } ?: Timber.tag("ImportDatabase").w("Failed to open input stream")

                            withContext(Dispatchers.Main) {
                                // Reset cookie status after import — fresh start
                                app.n_zik.android.MainApplication.cookieStatus = app.n_zik.android.MainApplication.CookieStatus.NOT_LOGGED_IN
                                context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().remove("ytCookieExpired").apply()
                                if (onImportComplete != null) {
                                    onImportComplete()
                                } else {
                                    RestartAppDialog.showDialog()
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("ImportDatabase").e(e, "Import failed")
                            withContext(Dispatchers.Main) {
                                Toaster.e("Import failed: ${e.message}")
                            }
                        }
                    }
                }
            )
    }

    override val supportedMimes: Array<String> = arrayOf(
        "application/vnd.sqlite3",
        "application/x-sqlite3",
        "application/octet-stream"
    )
}

