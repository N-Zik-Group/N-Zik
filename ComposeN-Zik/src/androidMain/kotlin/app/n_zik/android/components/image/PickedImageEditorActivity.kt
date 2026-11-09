package app.n_zik.android.components.image

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.launch
import timber.log.Timber
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import com.yalantis.ucrop.view.UCropView
import com.yalantis.ucrop.R as CropR

/** App-owned adapter for Yalantis/uCrop 2.2.11 (Apache-2.0), with isolated session ownership. */
class PickedImageEditorActivity : UCropActivity() {
    private var session: PickedImageEditor.EditSession? = null
    private var completion: DisposableHandle? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var ownsExport = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val id = intent.getStringExtra(PickedImageEditor.SESSION_EXTRA)
        val request = PickedImageEditor.session(id)
        session = request
        val colors = ImageEditorColors.resolve(this)
        if (request != null) {
            intent.putExtras(UCrop.of(Uri.fromFile(request.source), Uri.fromFile(request.output))
                .withOptions(colors.options(this)).withMaxResultSize(2048, 2048).getIntent(this))
        }
        super.onCreate(savedInstanceState)
        if (request == null) {
            // Process death severs the caller/target identity. Never restore a stale request.
            val app = applicationContext
            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch { PickedImageEditor.discard(app, id) }
            finish()
            return
        }
        completion = request.result.invokeOnCompletion { runOnUiThread { if (!isFinishing) finish() } }
        if (request.result.isCompleted) return
        colors.applyControls(window.decorView)
        findViewById<Toolbar>(CropR.id.toolbar).navigationContentDescription = getString(R.string.common_action_cancel)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { request.result.complete(null) }
        })
        if (request.exportFinished != null) {
            // A previous Activity still owns uCrop's export callback after recreation.
            fun disable(view: View) {
                view.isEnabled = false
                if (view is ViewGroup) for (index in 0 until view.childCount) disable(view.getChildAt(index))
            }
            disable(findViewById(CropR.id.ucrop))
            disable(findViewById(CropR.id.controls_wrapper))
        } else {
            restoreTransform(savedInstanceState)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val created = super.onCreateOptionsMenu(menu)
        menu.findItem(CropR.id.menu_crop)?.title = getString(R.string.image_editor_apply)
        return created
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val result = super.onPrepareOptionsMenu(menu)
        if (session?.exportFinished != null) {
            menu.findItem(CropR.id.menu_crop)?.isVisible = false
            menu.findItem(CropR.id.menu_loader)?.isVisible = true
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            session?.result?.complete(null)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun cropAndSaveImage() {
        val request = session ?: return
        if (request.result.isCompleted || request.exportFinished != null) return
        request.exportFinished = CompletableDeferred()
        ownsExport = true
        try {
            super.cropAndSaveImage()
        } catch (error: Exception) {
            setResultError(error)
            finish()
        }
    }

    override fun setResultUri(uri: Uri, resultAspectRatio: Float, offsetX: Int, offsetY: Int, imageWidth: Int, imageHeight: Int) {
        val request = session ?: return
        request.outputReceived = true
        // uCrop calls finish immediately after this callback. Validation belongs to the session.
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            var prepared: PreparedImage? = null
            try {
                if (!request.result.isCompleted) {
                    prepared = PickedImageEditor.prepareResult(request, uri)
                    if (!request.result.complete(prepared)) prepared.close()
                }
            } catch (error: Exception) {
                prepared?.close()
                reportError(error)
                request.result.complete(null)
            } finally {
                request.exportFinished?.complete(Unit)
            }
        }
    }

    override fun setResultError(throwable: Throwable) {
        session?.let { request ->
            // A recreated Activity must not release another instance's outstanding writer.
            if (request.exportFinished != null && !ownsExport) return
            request.exportFinished?.complete(Unit)
            if (!request.result.isCompleted) {
                reportError(throwable)
                request.result.complete(null)
            }
        }
    }

    private fun reportError(error: Throwable) {
        Timber.tag("PickedImageEditor").e(error, "Could not edit the image")
        Toaster.e(R.string.image_editor_failed)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        findViewById<UCropView>(CropR.id.ucrop)?.let { crop ->
            if (crop.cropImageView.viewBitmap != null && session?.exportFinished == null) {
                ImageCropSnapshot.capture(crop.cropImageView.imageMatrix, crop.imageCropRect())?.let {
                    outState.putFloatArray(MATRIX_STATE, it.values())
                    outState.putFloat(ASPECT_STATE, crop.cropImageView.targetAspectRatio)
                }
            }
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreTransform(state: Bundle?) {
        val snapshot = ImageCropSnapshot.fromValues(state?.getFloatArray(MATRIX_STATE)) ?: return
        val crop = findViewById<UCropView>(CropR.id.ucrop)
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (crop.cropImageView.viewBitmap == null || crop.overlayView.cropViewRect.isEmpty) return
                crop.viewTreeObserver.removeOnGlobalLayoutListener(this)
                layoutListener = null
                crop.cropImageView.targetAspectRatio = requireNotNull(state).getFloat(ASPECT_STATE)
                crop.cropImageView.cancelAllAnimations()
                crop.cropImageView.imageMatrix = snapshot.restore(crop.imageCropRect())
                crop.cropImageView.setImageToWrapCropBounds(false)
            }
        }
        layoutListener = listener
        crop.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun UCropView.imageCropRect() = ImageCropSnapshot.imageRect(
        // Match CropImageView.setCropRect: image matrices exclude the view's padding.
        overlayView.cropViewRect, cropImageView.paddingLeft, cropImageView.paddingTop,
        cropImageView.paddingRight, cropImageView.paddingBottom,
    )

    override fun onDestroy() {
        completion?.dispose()
        layoutListener?.let { findViewById<UCropView>(CropR.id.ucrop)?.viewTreeObserver?.removeOnGlobalLayoutListener(it) }
        if (!isChangingConfigurations && session?.outputReceived != true) session?.result?.complete(null)
        super.onDestroy()
    }

    private companion object {
        const val MATRIX_STATE = "image_crop_matrix"
        const val ASPECT_STATE = "image_crop_aspect"
    }
}
