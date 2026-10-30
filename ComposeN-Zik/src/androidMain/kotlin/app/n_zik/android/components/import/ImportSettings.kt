package app.n_zik.android.components.import

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.components.ui.screens.profiles.plainProfilePrefs
import app.n_zik.android.components.ui.screens.profiles.profileSecurePrefs
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream

import app.n_zik.android.components.ImportFromFile
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.core.backup.SettingsPreferencesBridge
import app.kreate.android.me.knighthat.utils.Toaster

class ImportSettings private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher) {

    companion object {

        /**
         * Imports a settings CSV into [profile]'s prefs: every row is routed to the
         * plain or the encrypted editor of that profile (the base profile keeps the
         * un-suffixed files). [profile] defaults to the active one for the callers
         * that import straight into the running app (migration, immediate imports).
         */
        fun onImport( context: Context, inStream: InputStream, profile: String = getActiveProfile(context) ) {
            Timber.tag("ImportSettings").d("Starting settings import into profile '$profile'...")
            val rows = RescueFiles.readSettingsCsv(inStream)
            val editor = plainProfilePrefs(context, profile).edit()
            val encryptedEditor = profileSecurePrefs(context, profile).edit()
            val glassEditor = SettingsPreferencesBridge.stageGlass(context, rows)
            RescueFiles.applySettingRows(rows, editor, encryptedEditor)
            check(editor.commit()) { "Could not save the settings" }
            check(encryptedEditor.commit()) { "Could not save the credentials" }
            check(glassEditor?.commit() ?: true) { "Could not save the Glass settings" }
            Timber.tag("ImportSettings").d("Settings import complete")
        }

        /**
         * Opens the settings file at [uri] and imports it into [profile]'s prefs.
         * Throws when the source stream cannot be opened.
         */
        suspend fun importFile( context: Context, uri: Uri, profile: String ) {
            val inStream = context.contentResolver
                .openInputStream( uri )
                ?: error("Failed to open input stream for $uri")
            inStream.use { onImport( context, it, profile ) }
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
        ): ImportSettings {
            val coroutineScope = rememberCoroutineScope()
            return ImportSettings(
                rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    Timber.tag("ImportSettings").d("File picker callback received, uri: $uri")
                    uri ?: return@rememberLauncherForActivityResult

                    // Deferred mode: the caller keeps the URI and runs the import later,
                    // into the profile picked in the target dialog.
                    onFilePicked?.let { deferred ->
                        deferred(uri)
                        return@rememberLauncherForActivityResult
                    }

                    coroutineScope.launch(NzikDispatchers.DATA) {
                        runCatching {
                            importFile( context, uri, getActiveProfile(context) )

                            withContext(NzikDispatchers.UI) {
                                if (onImportComplete != null) {
                                    onImportComplete()
                                } else {
                                    RestartAppDialog.showDialog()
                                }
                            }
                        }.onFailure { e ->
                            Timber.tag("ImportSettings").e(e, "Import failed")
                            withContext(NzikDispatchers.UI) {
                                Toaster.e("Import failed: ${e.message}")
                            }
                        }
                    }
                }
            )
        }
    }

    override val supportedMimes: Array<String> = arrayOf(
        "text/csv",
        "text/comma-separated-values",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
}
