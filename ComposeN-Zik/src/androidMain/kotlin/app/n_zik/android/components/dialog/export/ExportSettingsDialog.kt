package app.n_zik.android.components.dialog.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.encryptedPreferencesForProfile
import app.n_zik.android.utils.getActiveProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ExportSettingsDialog private constructor(
    private val launcher: ManagedActivityResultLauncher<String, Uri?>,
    private val context: Context,
    private val includeYtbState: MutableState<Boolean>,
    private val includeDiscordState: MutableState<Boolean>,
    private val includeLastfmState: MutableState<Boolean>,
    private val includeProxyState: MutableState<Boolean>,
    private val includeGitHubState: MutableState<Boolean>,
    private val pendingProfile: MutableState<String?>,
) {
    companion object {
        @Composable
        operator fun invoke(
            context: Context,
            onPickerFinished: () -> Unit = {},
            onWriteFailed: () -> Unit = {},
        ): ExportSettingsDialog {
            val includeYtbState = rememberSaveable { mutableStateOf(false) }
            val includeDiscordState = rememberSaveable { mutableStateOf(false) }
            val includeLastfmState = rememberSaveable { mutableStateOf(false) }
            val includeGitHubState = rememberSaveable { mutableStateOf(false) }
            val includeProxyState = rememberSaveable { mutableStateOf(false) }
            val pendingProfile = rememberSaveable { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            val finish by rememberUpdatedState(onPickerFinished)
            val fail by rememberUpdatedState(onWriteFailed)
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv"),
            ) { uri ->
                val profile = pendingProfile.value
                pendingProfile.value = null
                if (uri == null) {
                    finish()
                    return@rememberLauncherForActivityResult
                }
                if (profile == null) {
                    Toaster.e(R.string.export_cached_songs_generic_error)
                    fail()
                    return@rememberLauncherForActivityResult
                }
                val ytb = includeYtbState.value
                val discord = includeDiscordState.value
                val lastfm = includeLastfmState.value
                val proxy = includeProxyState.value
                val github = includeGitHubState.value
                val app = context.applicationContext
                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                    val encrypted = if (ytb || discord || lastfm || proxy || github) {
                        runCatching { app.encryptedPreferencesForProfile(profile) }
                    } else null
                    val result = RescueFiles.exportSettings(
                        app, uri, encrypted, ytb, discord, lastfm, proxy, github, profile = profile,
                    )
                    result.onFailure { error ->
                        Timber.tag("ExportSettingsDialog").e("Settings export failed: %s", error.javaClass.simpleName)
                        runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
                    }
                    withContext(NzikDispatchers.UI) {
                        result.onSuccess { outcome ->
                            if (outcome.encryptedSkipped) Toaster.w(R.string.recovery_encrypted_unavailable_warning)
                            if (scope.isActive) finish()
                        }.onFailure {
                            Toaster.e(R.string.export_cached_songs_generic_error)
                            if (scope.isActive) fail()
                        }
                    }
                }
            }
            return remember(launcher, context) {
                ExportSettingsDialog(
                    launcher, context, includeYtbState, includeDiscordState, includeLastfmState,
                    includeProxyState, includeGitHubState, pendingProfile,
                )
            }
        }
    }

    fun export(includeYtb: Boolean = false, includeDiscord: Boolean = false, includeLastfm: Boolean = false, includeProxy: Boolean = false, includeGitHub: Boolean = false) {
        pendingProfile.value = getActiveProfile(context)
        includeYtbState.value = includeYtb
        includeDiscordState.value = includeDiscord
        includeLastfmState.value = includeLastfm
        includeProxyState.value = includeProxy
        includeGitHubState.value = includeGitHub
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val fileName = "${BuildConfig.APP_NAME} $date Settings"
        Timber.tag("ExportSettingsDialog").d("Launching file picker with name: $fileName.csv")
        launcher.launch("$fileName.csv")
    }
}
