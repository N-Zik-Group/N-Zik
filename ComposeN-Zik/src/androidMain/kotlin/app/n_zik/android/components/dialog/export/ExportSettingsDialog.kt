package app.n_zik.android.components.dialog.export

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import app.n_zik.android.BuildConfig
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.core.backup.SettingsPreferencesBridge
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import androidx.compose.runtime.MutableState

class ExportSettingsDialog private constructor(
    private val launcher: ManagedActivityResultLauncher<String, Uri?>,
    private val context: Context,
    private val includeYtbState: MutableState<Boolean>,
    private val includeDiscordState: MutableState<Boolean>,
    private val includeLastfmState: MutableState<Boolean>,
    private val includeProxyState: MutableState<Boolean>
) {
    companion object {
        private fun onExport(
            uri: Uri,
            context: Context,
            includeYtb: Boolean,
            includeDiscord: Boolean,
            includeLastfm: Boolean,
            includeProxy: Boolean
        ) = NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runCatching {
                Timber.tag("ExportSettingsDialog").d("Starting settings export...")
                val entries = SettingsPreferencesBridge.export(context, context.preferences)

                // Single source of truth (RescueFiles): the selected credential groups plus,
                // when checked, the proxy key — exactly as the auto backup and the rescue build them.
                entries.addAll(
                    RescueFiles.buildCredentialExport(
                        context.encryptedPreferences.all,
                        includeYtb,
                        includeDiscord,
                        includeLastfm,
                        includeProxy
                    )
                )

                Timber.tag("ExportSettingsDialog").d("Found ${entries.size} settings entries")

                context.contentResolver
                    .openOutputStream( uri )
                    ?.use { outStream ->
                        csvWriter().open( outStream ) {
                            writeRow( "Type", "Key", "Value" )
                            flush()
                            entries.forEach {
                                writeRow( it.first, it.second, it.third )
                            }
                            close()
                        }
                        Timber.tag("ExportSettingsDialog").d("Settings export complete")
                    } ?: Timber.tag("ExportSettingsDialog").w("Failed to open output stream")
            }.onFailure { e ->
                Timber.tag("ExportSettingsDialog").e(e, "Settings export failed")
            }
        }

        @Composable
        operator fun invoke( context: Context ): ExportSettingsDialog {
            val includeYtbState = remember { mutableStateOf(false) }
            val includeDiscordState = remember { mutableStateOf(false) }
            val includeLastfmState = remember { mutableStateOf(false) }
            val includeProxyState = remember { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument( "text/csv" )
            ) { uri ->
                Timber.tag("ExportSettingsDialog").d("File picker callback received, uri: $uri")
                uri ?: return@rememberLauncherForActivityResult
                val ytb = includeYtbState.value
                val discord = includeDiscordState.value
                val lastfm = includeLastfmState.value
                val proxy = includeProxyState.value
                onExport( uri, context, ytb, discord, lastfm, proxy )
            }
            return remember(launcher, context) {
                ExportSettingsDialog(launcher, context, includeYtbState, includeDiscordState, includeLastfmState, includeProxyState)
            }
        }
    }

    fun export(includeYtb: Boolean = false, includeDiscord: Boolean = false, includeLastfm: Boolean = false, includeProxy: Boolean = false) {
        includeYtbState.value = includeYtb
        includeDiscordState.value = includeDiscord
        includeLastfmState.value = includeLastfm
        includeProxyState.value = includeProxy
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val fileName = "${BuildConfig.APP_NAME} $date Settings"
        Timber.tag("ExportSettingsDialog").d("Launching file picker with name: $fileName.csv")
        launcher.launch("$fileName.csv")
    }
}
