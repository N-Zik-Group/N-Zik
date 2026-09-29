package app.n_zik.android.components.import

import app.n_zik.android.core.database.*

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.components.ImportFromFile
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.core.rewind.RewindPostImportRegenerationWorker
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class ImportDatabase private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher) {

    companion object {

        /**
         * Imports the database at [uri] into [profile]'s data file.
         *
         * For the active profile the live singleton is checkpointed and closed before
         * the replacement, and the post-import side effects (rewind regeneration)
         * run. For any other profile only the file is replaced — the running app keeps
         * its own database, so nothing else is touched. Throws when the source stream
         * cannot be opened (the caller surfaces the failure).
         */
        suspend fun importTo(context: Context, uri: Uri, profile: String) {
            val isActive = profile == getActiveProfile(context)
            Timber.tag("ImportDatabase").d("Importing database into profile '$profile' (active: $isActive)")
            if (isActive) {
                Database.checkpoint()
                Timber.tag("ImportDatabase").d("Database checkpoint done")
                Database.close()
                Timber.tag("ImportDatabase").d("Database closed")
            }

            // Delete WAL and SHM files to prevent conflicts with imported database
            val dbFile = context.getDatabasePath(Database.fileNameForProfile(profile))
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            if (walFile.exists()) {
                walFile.delete()
                Timber.tag("ImportDatabase").d("Deleted WAL file")
            }
            if (shmFile.exists()) {
                shmFile.delete()
                Timber.tag("ImportDatabase").d("Deleted SHM file")
            }

            val inStream = context.applicationContext
                .contentResolver
                .openInputStream(uri)
                ?: error("Failed to open input stream for $uri")
            // The copy goes to a temp file that is then atomically renamed over the
            // target: a mid-copy failure leaves the existing database intact instead
            // of truncated (the target is only open by this process when the profile
            // is the active one, and it was closed above).
            val tmp = File(dbFile.parentFile, dbFile.name + ".tmp")
            try {
                inStream.use { input ->
                    FileOutputStream(tmp).use { outStream ->
                        val bytes = input.copyTo(outStream)
                        Timber.tag("ImportDatabase").d("Import complete, target: ${dbFile.absolutePath}, bytes written: $bytes")
                    }
                }
            } catch (e: Exception) {
                // The copy failed: the target database is intact, remove the partial temp.
                tmp.delete()
                throw e
            }
            // renameTo replaces the destination on Android (POSIX rename) but fails
            // when it exists on the JVM file systems: delete-then-rename fallback,
            // same pattern as writeProfileEntries.
            val replaced = tmp.renameTo(dbFile) || (dbFile.delete() && tmp.renameTo(dbFile))
            if (!replaced) {
                tmp.delete()
                error("Database import failed: could not replace ${dbFile.name}")
            }

            if (isActive) {
                // The listening history was just replaced: recompute every existing
                // rewind-* playlist from it (one-shot job, silent, not gated — spec 2)
                RewindPostImportRegenerationWorker.schedule(context)
            }
        }

        /**
         * @param onImportComplete immediate mode: the import runs straight into the
         *   active profile and this callback closes the flow (null = restart prompt)
         * @param onFilePicked deferred mode: the picked URI is only reported back —
         *   the import runs later, into the profile chosen in the target dialog
         */
        @Composable
        operator fun invoke(
            context: Context,
            onImportComplete: (() -> Unit)? = null,
            onFilePicked: ((Uri) -> Unit)? = null
        ): ImportDatabase =
            ImportDatabase(
                rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    Timber.tag("ImportDatabase").d("File picker callback received, uri: $uri")
                    uri ?: return@rememberLauncherForActivityResult

                    // Deferred mode: the caller keeps the URI and runs the import later,
                    // into the profile picked in the target dialog.
                    onFilePicked?.let { deferred ->
                        deferred(uri)
                        return@rememberLauncherForActivityResult
                    }

                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        runGuardedImport(
                            onFailure = { e ->
                                Timber.tag("ImportDatabase").e(e, "Import failed")
                                withContext(NzikDispatchers.UI) {
                                    Toaster.e("Import failed: ${e.message}")
                                }
                            },
                            isDatabaseClosed = { Database.isClosed },
                            showRestartPrompt = { RestartAppDialog.showDialog() }
                        ) {
                            importTo(context, uri, getActiveProfile(context))
                        }
                        withContext(NzikDispatchers.UI) {
                            // Reset cookie status after import — fresh start (the active
                            // profile's own prefs store, never the base-only file)
                            resetLoginStateAfterDatabaseImport(context)
                            if (onImportComplete != null) {
                                onImportComplete()
                            } else {
                                RestartAppDialog.showDialog()
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
