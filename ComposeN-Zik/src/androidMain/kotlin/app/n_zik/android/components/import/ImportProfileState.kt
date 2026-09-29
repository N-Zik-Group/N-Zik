package app.n_zik.android.components.import

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.components.ImportFromFile
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.kreate.android.me.knighthat.utils.Toaster
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Accounts import — the profile state (the list + every face) from a single .txt file; a
 * legacy list-only .txt is still accepted (the faces are then simply absent). No app restart
 * is required on its own: the list, the names and the faces are re-read on recomposition.
 */
class ImportProfileState private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
) : ImportFromFile(launcher) {

    companion object {

        /**
         * @param onImportComplete immediate mode: the import runs and this callback
         *   closes the flow
         * @param onCancelled immediate mode: the picker was cancelled (chained
         *   callers wrap up the flow)
         * @param onFilePicked deferred mode: the picked URI is only reported back —
         *   the host keeps it and imports it later, into the profile chosen in the
         *   target dialog (the all-import chain)
         */
        @Composable
        operator fun invoke(
            context: Context,
            onImportComplete: (() -> Unit)? = null,
            onCancelled: (() -> Unit)? = null,
            onFilePicked: ((Uri) -> Unit)? = null,
            onCrossProfile: (String, () -> Unit) -> Unit
        ): ImportProfileState {
            val stateLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri ?: run {
                    // Picker cancelled: nothing was imported; chained callers (the "all"
                    // import) still have to wrap up the flow.
                    onCancelled?.invoke()
                    return@rememberLauncherForActivityResult
                }
                // Deferred mode: the host keeps the file and imports it later, into
                // the profile picked in the target dialog.
                onFilePicked?.let { deferred ->
                    deferred(uri)
                    return@rememberLauncherForActivityResult
                }
                withCrossProfileGuard(context, uri, onCrossProfile) {
                    importState(context, uri) { onImportComplete?.invoke() }
                }
            }

            return ImportProfileState(stateLauncher)
        }

        /**
         * Runs [onProceed] straight away, or behind a cross-profile confirmation
         * when the picked file's name carries the tag of a profile other than
         * the active one.
         */
        private fun withCrossProfileGuard(
            context: Context,
            uri: Uri,
            onCrossProfile: (String, () -> Unit) -> Unit,
            onProceed: () -> Unit
        ) {
            val tag = RescueFiles.documentDisplayName(context, uri)
                ?.let { RescueFiles.backupProfileTagOf(it, BuildConfig.APP_NAME) }
            if (tag != null && tag != getActiveProfile(context)) {
                onCrossProfile(tag) { onProceed() }
            } else {
                onProceed()
            }
        }

        /** Imports the profile state archive; on success [onDone] closes the flow. */
        private fun importState(context: Context, uri: Uri, onDone: () -> Unit) {
            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                val result = RescueFiles.importProfileState(context, uri)
                withContext(NzikDispatchers.UI) {
                    if (result.isSuccess) {
                        Timber.tag("ImportProfileState").d("Profile state imported")
                        val applied = result.getOrNull()
                        val profiles = applied?.profiles ?: 0
                        val faces = applied?.faces ?: 0
                        val names = applied?.names ?: 0
                        val message = when {
                            profiles > 0 ->
                                context.getString(R.string.import_profile_state_complete, profiles)
                            // A base-only archive (empty list + base face and/or name) imports
                            // 0 profiles: say the base profile was restored instead of
                            // "0 profiles".
                            faces > 0 || names > 0 ->
                                context.getString(R.string.import_profile_state_complete_base)
                            else ->
                                context.getString(R.string.import_profile_state_complete, 0)
                        }
                        Toaster.i(message)
                        onDone()
                    } else {
                        // A failed import leaves the dialog open so the user can retry.
                        Timber.tag("ImportProfileState").e(result.exceptionOrNull(), "Profile state import failed")
                        Toaster.e(context.getString(R.string.import_profile_state_failed))
                    }
                }
            }
        }
    }

    override val supportedMimes: Array<String> = IMPORT_PROFILE_STATE_MIMES
}

/** MIME types accepted by the profile state picker. */
internal val IMPORT_PROFILE_STATE_MIMES: Array<String> = arrayOf(
    "text/plain",
    "application/octet-stream"
)
