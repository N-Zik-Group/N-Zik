package app.n_zik.android.components.dialog.export

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.kreate.android.me.knighthat.utils.Toaster
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Profiles export — the profile state (the list + the base64 face of every profile that has
 * one) as a single .txt file. The suggested name carries the auto-backup-style timestamp, so
 * the cross-profile tag stays parseable on import.
 */
class ExportProfileStateDialog private constructor(
    private val context: Context,
    private val stateLauncher: ManagedActivityResultLauncher<String, Uri?>
) {
    companion object {
        @Composable
        operator fun invoke(context: Context): ExportProfileStateDialog {
            return ExportProfileStateDialog(
                context,
                rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    uri ?: return@rememberLauncherForActivityResult
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        val result = RescueFiles.exportProfileState(context, uri)
                        withContext(NzikDispatchers.UI) {
                            if (result.isSuccess) {
                                Timber.tag("ExportProfileStateDialog").d("Profile state exported")
                            } else {
                                Timber.tag("ExportProfileStateDialog").e(
                                    result.exceptionOrNull(),
                                    "Profile state export failed"
                                )
                                Toaster.e(context.getString(R.string.export_profile_state_failed))
                            }
                        }
                    }
                }
            )
        }

        /** Suggested name for the profile state export (profile-tagged, timestamped). */
        private fun suggestedName(context: Context): String =
            "${BuildConfig.APP_NAME}_${getActiveProfile(context)}_${fullDate()}_Profiles_Export.txt"

        private fun fullDate(): String =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
    }

    /** Launches the single-step archive export. */
    fun export() {
        stateLauncher.launch(suggestedName(context))
    }
}
