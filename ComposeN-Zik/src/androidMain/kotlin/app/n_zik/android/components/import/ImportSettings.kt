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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream

import app.n_zik.android.components.ImportFromFile
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.isDiscordBrowsingEnabledKey
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.useYtLoginOnlyForBrowseKey
import app.it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import app.it.fast4x.rimusic.utils.ytAccountEmailKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey

class ImportSettings private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher) {

    companion object {
        fun onImport( context: Context, inStream: InputStream ) {
            Timber.tag("ImportSettings").d("Starting settings import...")
            val rows = csvReader().readAllWithHeader( inStream )
            Timber.tag("ImportSettings").d("Read ${rows.size} rows from CSV")
            val encryptedKeys = listOf(
                ytCookieKey, ytVisitorDataKey, ytDataSyncIdKey, ytAccountNameKey, ytAccountEmailKey,
                ytAccountChannelHandleKey, ytAccountThumbnailKey, enableYouTubeLoginKey,
                enableYouTubeSyncKey, useYtLoginOnlyForBrowseKey, discordPersonalAccessTokenKey,
                discordAvatarKey, discordUsernameKey, isDiscordPresenceEnabledKey, isDiscordBrowsingEnabledKey
            )
            
            val editor = context.preferences.edit()
            val encryptedEditor = context.encryptedPreferences.edit()

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

        @Composable
        operator fun invoke( context: Context, onImportComplete: (() -> Unit)? = null ): ImportSettings {
            val coroutineScope = rememberCoroutineScope()
            return ImportSettings(
                rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    Timber.tag("ImportSettings").d("File picker callback received, uri: $uri")
                    uri ?: return@rememberLauncherForActivityResult

                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver
                                   .openInputStream( uri )
                                   ?.use { inStream ->
                                       onImport( context, inStream )
                                   } ?: Timber.tag("ImportSettings").w("Failed to open input stream")

                            withContext(Dispatchers.Main) {
                                if (onImportComplete != null) {
                                    onImportComplete()
                                } else {
                                    RestartAppDialog.showDialog()
                                }
                            }
                        }.onFailure { e ->
                            Timber.tag("ImportSettings").e(e, "Import failed")
                            withContext(Dispatchers.Main) {
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

