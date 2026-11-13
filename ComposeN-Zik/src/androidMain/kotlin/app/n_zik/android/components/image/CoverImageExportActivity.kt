package app.n_zik.android.components.image

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import timber.log.Timber
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Owns the cover document picker independently of the composition that initiated the export. */
class CoverImageExportActivity : ComponentActivity() {
    private val exportId: String? get() = intent.getStringExtra(EXPORT_ID)
    private val completion by lazy { ViewModelProvider(this)[ExportCompletion::class.java].finished }

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val request = exportId?.let(requests::remove)
        val destination = result.data?.data?.takeIf {
            result.resultCode == Activity.RESULT_OK && it.scheme == "content"
        }
        val app = applicationContext
        val finished = completion
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            try {
                request?.export.use { export ->
                    if (destination == null) return@use
                    if (export == null || request?.profile != getActiveProfile(app)) {
                        CoverImageExport.discardDestination(app, destination)
                        Toaster.e(R.string.cover_download_failed)
                        return@use
                    }
                    export.writeTo(app, destination)
                    Toaster.done()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportFailure(error)
            } finally {
                finished.complete(Unit)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the destination grant until writing finishes, including when the Activity is recreated.
        lifecycleScope.launch(NzikDispatchers.UI) {
            completion.await()
            finish()
        }
        // A recreated Activity has the original picker registration and must await its result.
        if (savedInstanceState != null) return
        val request = exportId?.let(requests::get)
        if (request == null || request.profile != getActiveProfile(this)) {
            finish()
            return
        }
        try {
            picker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = request.export.mimeType
                putExtra(Intent.EXTRA_TITLE, request.export.suggestedName)
            })
        } catch (error: RuntimeException) {
            reportFailure(error)
            finish()
        }
    }

    override fun onDestroy() {
        if (isFinishing) exportId?.let(requests::remove)?.export?.close()
        super.onDestroy()
    }

    internal class ExportCompletion : ViewModel() {
        val finished = CompletableDeferred<Unit>()
    }

    internal companion object {
        private const val EXPORT_ID = "cover_export_id"
        private val requests = ConcurrentHashMap<String, Request>()

        private data class Request(val export: CoverImageExport, val profile: String)

        /** Transfers ownership of [export] to a dedicated result handler. */
        fun launch(context: Context, export: CoverImageExport, profile: String, launch: (Intent) -> Unit) {
            val id = UUID.randomUUID().toString()
            requests[id] = Request(export, profile)
            try {
                launch(Intent(context, CoverImageExportActivity::class.java).putExtra(EXPORT_ID, id))
            } catch (error: RuntimeException) {
                requests.remove(id)?.export?.close()
                throw error
            }
        }

        private fun reportFailure(error: Exception) {
            Timber.tag("CoverImageExport").e(error, "Could not export cover")
            Toaster.e(R.string.cover_download_failed)
        }
    }
}
