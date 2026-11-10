package app.n_zik.android.components.import

import app.n_zik.android.R

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.core.backup.DatabaseBackup
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import app.n_zik.android.components.ImportFromFile
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.image.ownedCoverMutationMutex
import app.n_zik.android.core.rewind.RewindPostImportRegenerationWorker
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.isCurrentProcessStorageProfile
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber

class ImportDatabase private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher) {

    companion object {

        /**
         * Imports the database at [uri] into [profile]'s data file.
         *
         * For the active profile the live singleton is checkpointed and closed before
         * the replacement, and the post-import side effects (rewind regeneration)
         * run. Importing into another profile leaves the live database open. A ZIP can
         * still replace shared artwork, including globally stored playlist thumbnails.
         */
        suspend fun importTo(context: Context, uri: Uri, profile: String) {
            val isActive = profile == getActiveProfile(context)
            val activeOwner = if (isActive) checkNotNull(profileStorageIdentity(context, profile)) else null
            Timber.tag("ImportDatabase").d("Importing database into profile '$profile' (active: $isActive)")
            ownedCoverMutationMutex.withLock {
                DatabaseBackup.restore(context, uri, profile) {
                    if (activeOwner != null) {
                        check(isCurrentProcessStorageProfile(context, activeOwner)) { "The import target changed" }
                        Database.checkpoint()
                        Database.close()
                    }
                }
            }

            if (activeOwner != null) {
                // The listening history was just replaced: recompute every existing
                // rewind-* playlist from it (one-shot job, silent, not gated — spec 2)
                RewindPostImportRegenerationWorker.schedule(context, activeOwner)
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
                        var failed = false
                        runGuardedImport(
                            onFailure = { e ->
                                failed = true
                                Timber.tag("ImportDatabase").e(e, "Import failed")
                                withContext(NzikDispatchers.UI) {
                                    Toaster.e(R.string.backup_import_failed)
                                }
                            },
                            isDatabaseClosed = { Database.isClosed },
                            showRestartPrompt = { RestartAppDialog.showDialog() }
                        ) {
                            importTo(context, uri, getActiveProfile(context))
                        }
                        if (failed) return@launch
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

    override val supportedMimes: Array<String> = DatabaseBackup.importMimes
}
