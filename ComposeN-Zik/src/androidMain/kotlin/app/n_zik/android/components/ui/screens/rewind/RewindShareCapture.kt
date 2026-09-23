package app.n_zik.android.components.ui.screens.rewind

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.withFrameNanos
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.n_zik.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Settle time (ms) before each Rewind deck page frame is copied during the export capture.
 *
 * The capture runs with the deck's one-shot animations cut (see [rewindShareCaptureActive]):
 * reveals, counters and reveal gates all settle the instant a page is activated, so no page
 * needs a long reveal wait anymore. The settle only covers the page jump, the recomposition,
 * a couple of clean frames and a little slack for artwork bitmaps still settling from Coil.
 */
internal const val RewindPageSettleMs = 900L

/**
 * Captures one PNG per deck page straight from the display framebuffer.
 *
 * `PixelCopy` copies what the GPU actually composited — the animated HypnoticCanvas
 * RuntimeShader backgrounds included — which `view.draw()` cannot replay on a software
 * canvas (RuntimeShader and hardware bitmaps both throw there). The deck visits its pages
 * in order; each one waits for its reveal to settle before its frame is copied and saved
 * as `NZik_Rewind_<token>_NN.png`, where the token is [RewindPeriod.fileNameToken]
 * ("2026", "2026_03" or "GLOBAL").
 * Each image carries the same top/bottom padding as the live deck, but with the system bars
 * hidden for the whole pass: the deck pins the ignoring-visibility bar insets through
 * [rewindShareCaptureActive], so the bar areas show the slide background (no clock, battery
 * icons or nav buttons) instead of the system UI. The hide runs the platform's own bar
 * transition, and the first copy waits for it to land (see rewindAwaitBarsHidden) so no
 * frame is ever captured mid-fade.
 *
 * @return the page files in slide order, or null when the display could not be copied
 * or the device is below API 34 (the caller falls back to the single
 * software-replayed final slide).
 */
internal suspend fun captureRewindDeckPages(
    view: View,
    pagerState: PagerState,
    period: RewindPeriod,
    pageCount: Int
): List<File>? {
    val activity = view.context as? Activity ?: return null
    if (Build.VERSION.SDK_INT < 34) {
        // The display-framebuffer PixelCopy overloads only exist on API 34+; older
        // devices fall back to the single software-replayed final slide.
        Timber.tag("RewindShare").w("API < 34: single-slide fallback capture")
        return null
    }

    val shareDirectory = File(activity.cacheDir, "shared_rewind").apply { mkdirs() }
    // One fresh set per share run, so a new share never mixes with previous pages
    shareDirectory.listFiles()?.forEach(File::delete)

    val insetsController = WindowCompat.getInsetsController(activity.window, view)
    val files = mutableListOf<File>()
    try {
        // Clean frames for the whole pass: hide the system bars so the bar areas show the
        // slide background instead of the system UI — the platform runs its own bar
        // transition on the hide, and the first copy waits for it to land (see
        // rewindAwaitBarsHidden) so no frame is ever captured mid-fade.
        // rewindShareCaptureActive is held until the finally below, even when a copy fails
        // or the share coroutine is cancelled (spec GH-275, patch "rewindShareCaptureActive
        // set before the try").
        rewindShareCaptureActive.value = true
        insetsController.hide(WindowInsets.Type.systemBars())
        // Settle: bars out of the framebuffer, capture-mode recomposition landed, two clean frames
        rewindAwaitBarsHidden(view)
        withFrameNanos { }
        withFrameNanos { }
        delay(150)

        for (page in 0 until pageCount) {
            if (pagerState.currentPage != page) {
                pagerState.scrollToPage(page)
                withFrameNanos { }
            }
            delay(RewindPageSettleMs)
            withFrameNanos { }

            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val sourceRect = Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height
            )

            val bitmap = pixelCopyDisplayFrame(
                window = activity.window,
                sourceRect = sourceRect,
                width = view.width,
                height = view.height
            ) ?: run {
                // A failed copy aborts the pass: drop the pages already captured so the
                // caller's single-slide fallback cannot mix with them
                files.forEach(File::delete)
                return null
            }

            // Zero-paged so galleries order the 16 slides lexicographically (01..16)
            val pageFile = File(
                shareDirectory,
                "NZik_Rewind_${period.fileNameToken()}_${(page + 1).toString().padStart(2, '0')}.png"
            )
            val compressed = withContext(Dispatchers.IO) {
                pageFile.outputStream().buffered().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
            bitmap.recycle()
            if (!compressed) {
                files.forEach(File::delete)
                Timber.tag("RewindShare").e("PNG compression failed for page %d", page + 1)
                return null
            }
            files += pageFile
            Timber.tag("RewindShare").d(
                "Captured page %d/%d (%d bytes)",
                page + 1,
                pageCount,
                pageFile.length()
            )
        }
        return files
    } finally {
        rewindShareCaptureActive.value = false
        insetsController.show(WindowInsets.Type.systemBars())
    }
}

/**
 * Captures the currently displayed slide as a single PNG from the display framebuffer —
 * the per-slide counterpart of [captureRewindDeckPages] (spec GH-275, patch "per-slide
 * system share"). Same PixelCopy path as the deck export: one page, with the system bars
 * hidden for the single frame — the platform fades them out with its own transition, and
 * the copy waits for the hide to land (see rewindAwaitBarsHidden) so the full-bleed frame
 * is never caught mid-fade. API 34+ only; below API 34 [shareRewindSlide] software-replays
 * the slide in capture mode instead.
 *
 * @return the saved PNG in `cacheDir/shared_rewind`, or null when the copy or the
 * compression failed
 */
internal suspend fun captureRewindSlidePng(
    view: View,
    period: RewindPeriod,
    page: Int
): File? {
    val activity = view.context as? Activity ?: return null
    val shareDirectory = File(activity.cacheDir, "shared_rewind").apply { mkdirs() }
    val insetsController = WindowCompat.getInsetsController(activity.window, view)
    var succeeded = false
    try {
        rewindShareCaptureActive.value = true
        insetsController.hide(WindowInsets.Type.systemBars())
        // Settle: the platform fades the bars out with its own transition — wait for the
        // hide to land in the framebuffer (and the share chrome to leave the slide) before
        // the copy, so the full-bleed frame is clean and never caught mid-fade
        rewindAwaitBarsHidden(view)
        withFrameNanos { }
        withFrameNanos { }
        delay(150)

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val sourceRect = Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )
        val bitmap = pixelCopyDisplayFrame(
            window = activity.window,
            sourceRect = sourceRect,
            width = view.width,
            height = view.height
        ) ?: return null

        // Same naming scheme as the deck export, so a shared slide and an exported deck
        // page of the same period are indistinguishable in the file manager
        val slideFile = File(
            shareDirectory,
            "NZik_Rewind_${period.fileNameToken()}_${(page + 1).toString().padStart(2, '0')}.png"
        )
        val compressed = withContext(Dispatchers.IO) {
            slideFile.outputStream().buffered().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        bitmap.recycle()
        if (!compressed) {
            Timber.tag("RewindShare").e("PNG compression failed for slide %d", page + 1)
            return null
        }
        Timber.tag("RewindShare").d("Captured slide %d (%d bytes)", page + 1, slideFile.length())
        succeeded = true
        return slideFile
    } finally {
        rewindShareCaptureActive.value = false
        if (!succeeded) {
            // The copy failed and no share sheet is coming: fade the bars straight back
            insetsController.show(WindowInsets.Type.systemBars())
        }
    }
}

/**
 * Captures the displayed slide and hands its PNG to the system share sheet — the
 * per-slide counterpart of the 16-image deck export (spec GH-275, patch "per-slide
 * system share"). API 34+ copies the display framebuffer (animated shader included);
 * below API 34 the slide is software-replayed in capture mode (solid slide backgrounds).
 * Both paths hold [rewindShareCaptureActive] in a try/finally for the settle window so the
 * per-slide share UI (top-right icon, finale export pills) is hidden from the captured
 * frame — a shared image never carries interactive chrome.
 *
 * On API 34+ the system bars fade out with the platform transition while the frame is
 * captured, and — once the share sheet is up — stay hidden until the activity resumes
 * (see showSystemBarsOnNextResume), so they never blink back before the sheet covers
 * the screen.
 */
internal suspend fun shareRewindSlide(
    context: Context,
    view: View,
    period: RewindPeriod,
    page: Int
) {
    val file = if (Build.VERSION.SDK_INT >= 34) {
        captureRewindSlidePng(view, period, page)
    } else {
        try {
            // Hide the per-slide share UI (top-right icon, finale export pills) from the
            // replayed frame — the PixelCopy path does the same before its copy.
            rewindShareCaptureActive.value = true
            rewindCaptureMode.value = true
            withFrameNanos { }
            withFrameNanos { }
            captureRewindScreenshot(context, view, period, page)
        } finally {
            rewindCaptureMode.value = false
            rewindShareCaptureActive.value = false
        }
    }
    if (file == null) {
        Timber.tag("RewindShare").e("Failed to capture slide %d for sharing", page + 1)
        return
    }
    val shared = runCatching { shareImageFile(context, file) }
        .onFailure { error ->
            Timber.tag("RewindShare").e(error, "Failed to share slide %d", page + 1)
        }
        .isSuccess
    if (Build.VERSION.SDK_INT >= 34) {
        val activity = view.context as? ComponentActivity
        if (shared) {
            // The bars stay hidden under the share sheet and fade back in when the user
            // returns to the deck — showing them right after the capture would blink
            // them back before the sheet covers the screen (on-device feedback).
            activity?.showSystemBarsOnNextResume()
        } else {
            // The sheet never opened: fade the bars straight back
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView)
                    .show(WindowInsets.Type.systemBars())
            }
        }
    }
}

/**
 * Software-replays the current deck page onto a fresh bitmap and saves it as a PNG in the
 * shared cache directory. Used on devices below API 34 (no display-framebuffer PixelCopy)
 * and as the last-resort single-slide export fallback; the caller exports or shares the
 * returned file like any captured deck page.
 *
 * The caller has switched the deck into capture mode (solid slide backgrounds): a Bitmap
 * canvas replays the view in software mode, where the HypnoticCanvas RuntimeShaders would
 * throw (Software rendering doesn't support RuntimeShader). Coil images are safe too: the
 * shared loader decodes software bitmaps (ImageCacheFactory, allowHardware(false)).
 *
 * @param page the deck page being captured; null when there is no page context (the file
 * name then falls back to a timestamp so two captures never overwrite each other)
 * @return the saved PNG, or null when the replay or the compression failed
 */
internal suspend fun captureRewindScreenshot(
    context: Context,
    view: View,
    period: RewindPeriod,
    page: Int? = null
): File? {
    return runCatching {
        val bitmap = withContext(Dispatchers.Main.immediate) {
            val width = view.width.coerceAtLeast(1)
            val height = view.height.coerceAtLeast(1)
            Timber.tag("RewindShare").d("Capturing %dx%d from %s", width, height, view.javaClass.simpleName)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
                view.draw(AndroidCanvas(target))
            }
        }
        val imageFile = withContext(Dispatchers.IO) {
            val shareDirectory = File(context.cacheDir, "shared_rewind").apply { mkdirs() }
            // Captures from previous share runs (> 24 h old) are dropped (spec cleanup)
            val staleBefore = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
            shareDirectory.listFiles()
                ?.filter { it.lastModified() < staleBefore }
                ?.forEach(File::delete)
            val pageSuffix = page?.let { "_${it.toString().padStart(2, '0')}" }
                ?: "_${System.currentTimeMillis()}"
            File(
                shareDirectory,
                "NZik_Rewind_${period.fileNameToken()}${pageSuffix}.png"
            ).also { outputFile ->
                outputFile.outputStream().buffered().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "PNG compression failed"
                    }
                }
                Timber.tag("RewindShare").d("Saved %d bytes to %s", outputFile.length(), outputFile.name)
            }
        }
        bitmap.recycle()
        imageFile
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        Timber.tag("RewindShare").e(error, "Failed to capture Rewind screenshot")
        null
    }
}

/**
 * Hands [file] to the system share sheet through the app's FileProvider
 * (`<package>.fileprovider` authority, `shared_rewind` cache-path in file_paths.xml):
 * a single `ACTION_SEND` / `image/png` with a read-granting stream URI.
 */
internal fun shareImageFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(share, context.getString(R.string.rw_share_slide))
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

/**
 * Suspends until one frame of the display has been copied into a fresh bitmap, or returns
 * null when the copy failed (display busy, screen off, ...). API 34+ only: the call
 * reaches the display compositor through the app [Window].
 *
 * The bitmap is sized from [width]/[height] coerced to at least 1 px: an un-laid-out view
 * reports 0, and `Bitmap.createBitmap(0, 0, ...)` throws IllegalArgumentException
 * (spec GH-275, patch "Bitmap.createBitmap unguarded at zero size").
 */
/**
 * Waits until the system bars have actually left the window, so a captured frame is
 * clean. Hiding the bars runs the platform's own bar transition (SystemUI fades them —
 * the same transition video players get), and a fixed settle delay can land the copy
 * mid-fade; polling the visibility-aware window insets waits for that transition to
 * finish. The timeout covers devices where the hide is instant or the insets never
 * report zero.
 */
private suspend fun rewindAwaitBarsHidden(view: View) {
    withTimeoutOrNull(500) {
        settle@ while (true) {
            val root = view.rootWindowInsets
            if (root != null &&
                root.getInsets(WindowInsets.Type.statusBars()).top == 0 &&
                root.getInsets(WindowInsets.Type.navigationBars()).bottom == 0
            ) {
                break@settle
            }
            delay(16)
        }
    }
}

/**
 * Re-shows the system bars on the next activity RESUME. A successful per-slide share
 * launches the share sheet right after the capture; the bars stay hidden under the
 * sheet and fade back in when the user comes back to the deck, instead of blinking
 * back before the sheet covers the screen (on-device feedback).
 */
private fun ComponentActivity.showSystemBarsOnNextResume() {
    val observer = object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_RESUME) {
                lifecycle.removeObserver(this)
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsets.Type.systemBars())
            }
        }
    }
    lifecycle.addObserver(observer)
}

private suspend fun pixelCopyDisplayFrame(
    window: Window,
    sourceRect: Rect,
    width: Int,
    height: Int
): Bitmap? {
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { bitmap.recycle() }
        PixelCopy.request(
            window,
            sourceRect,
            bitmap,
            PixelCopy.OnPixelCopyFinishedListener { result ->
                when {
                    continuation.isCancelled -> bitmap.recycle()
                    result == PixelCopy.SUCCESS -> continuation.resume(bitmap)
                    else -> {
                        Timber.tag("RewindShare").e("PixelCopy failed, result=%d", result)
                        bitmap.recycle()
                        continuation.resume(null)
                    }
                }
            },
            Handler(Looper.getMainLooper())
        )
    }
}

/**
 * Exports the captured pages into a SAF folder the user can browse in any file manager —
 * the same flow the cached-song export uses: one document per page, in slide order, through
 * [DocumentsContract.createDocument]. The system share sheet cannot carry a full deck of 16
 * images through third-party apps, so the deck lands in a folder the user picks instead.
 *
 * Partial-failure handling (spec GH-275, patch "folder export has no partial-failure
 * handling" + re-review "orphaned SAF document"): each temp copy is deleted right
 * after its document is written, so no temp file outlives the run even on success;
 * when a write fails, the document that could not be fully written is removed from
 * the folder and the not-yet-exported temp files are cleaned up, so a partial export
 * leaves neither an empty nor a truncated PNG behind. The caller learns exactly how
 * many pages landed.
 *
 * @return the number of pages actually written to the folder (== [files].size on success)
 */
internal suspend fun exportRewindPagesToFolder(
    context: Context,
    folderUri: Uri,
    files: List<File>
): Int = withContext(Dispatchers.IO) {
    var written = 0
    runCatching {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocId)
        for (file in files) {
            val docUri = DocumentsContract.createDocument(resolver, parentUri, "image/png", file.name)
                ?: throw IOException("Failed to create document for ${file.name}")
            try {
                resolver.openOutputStream(docUri)
                    ?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("Failed to open output stream for ${file.name}")
            } catch (writeError: Exception) {
                if (writeError is CancellationException) throw writeError
                // The document exists in the user's folder but the write failed: remove it
                // so the partial export leaves no empty or truncated PNG behind
                runCatching { resolver.delete(docUri, null) }
                throw writeError
            }
            written++
            Timber.tag("RewindShare").d("Exported %s (%d bytes)", file.name, file.length())
            // The exported document is the deliverable now; drop the temp copy right away
            file.delete()
        }
    }.onFailure { error ->
        if (error is CancellationException) throw error
        // Partial export: clean up the temp copies that never made it into the folder
        files.drop(written).forEach { runCatching { it.delete() } }
        Timber.tag("RewindShare").e(
            error,
            "Rewind export failed (%d/%d pages written)",
            written,
            files.size
        )
    }
    written
}
