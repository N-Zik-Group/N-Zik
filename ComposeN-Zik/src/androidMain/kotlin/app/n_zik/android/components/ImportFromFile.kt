package app.n_zik.android.components

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import app.n_zik.android.R
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber

abstract class ImportFromFile(
    private val launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
) {
    abstract val supportedMimes: Array<String>

    fun onShortClick() {
        try {
            Timber.tag("ImportFromFile").d("Launching file picker with mimes: ${supportedMimes.joinToString()}")
            launcher.launch( supportedMimes )
        } catch ( e: ActivityNotFoundException ) {
            Timber.tag("ImportFromFile").e(e, "No app found to handle file picker")
            Toaster.e( R.string.common_error_no_document_handler )
        }
    }
}
