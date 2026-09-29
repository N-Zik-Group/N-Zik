package app.n_zik.android.components.import

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.util.fastForEach
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
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
            val rows = csvReader().readAllWithHeader( inStream )
            Timber.tag("ImportSettings").d("Read ${rows.size} rows from CSV")
            // Single source of truth (RescueFiles): every key that lives in the encrypted
            // prefs — YouTube / Discord (incl. advanced) / Last.fm groups plus the proxy
            // password — must be routed to the encrypted editor, never to plain prefs.
            val encryptedKeys = RescueFiles.ALL_ENCRYPTED_KEYS

            val editor = plainProfilePrefs( context, profile ).edit()
            val encryptedEditor = profileSecurePrefs( context, profile ).edit()

            rows.fastForEach { row ->
                val type = row["Type"] ?: ""
                val key = row["Key"] ?: ""
                val value = row["Value"] ?: ""
                Timber.tag("ImportSettings").d("Processing row: type=$type, key=$key")

                val isEncrypted = key in encryptedKeys
                val targetEditor = if (isEncrypted) encryptedEditor else editor
                if (isEncrypted) Timber.tag("ImportSettings").d("  → routing to encryptedPreferences")

                runCatching {
                    when( type.lowercase() ) {
                        "string" -> targetEditor.putString( key, value )
                        "int", "integer" -> targetEditor.putInt( key, value.toInt() )
                        "long" -> targetEditor.putLong( key, value.toLong() )
                        "float" -> targetEditor.putFloat( key, value.toFloat() )
                        "boolean" -> targetEditor.putBoolean( key, value.toBoolean() )
                        else -> Timber.tag("ImportSettings").w("Unknown type '$type' for key '$key', skipping")
                    }
                }.onFailure { e ->
                    Timber.tag("ImportSettings").e(e, "Failed to import key '$key' (type=$type, value=$value)")
                }
            }
            editor.commit()
            encryptedEditor.commit()
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
