package app.n_zik.android.components.ui.screens.rewind

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.withFrameNanos
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Settle time (ms) before each Rewind deck page frame is copied during the export capture.
 *
 * The capture runs with the deck's one-shot animations cut (see [rewindShareCaptureActive]):
 * reveals, counters, typewriter, reveal gates and slide tweens all settle the instant a page
 * is activated, so no page needs a long reveal wait anymore. The settle only covers the page
 * jump, the recomposition, a couple of clean frames and a little slack for artwork bitmaps
 * still settling from Coil.
 */
internal const val RewindPageSettleMs = 900L

/**
 * Captures one PNG per deck page straight from the display framebuffer.
 *
 * `PixelCopy` copies what the GPU actually composited — the animated HypnoticCanvas
 * RuntimeShader backgrounds included — which `view.draw()` cannot replay on a software
 * canvas (RuntimeShader and hardware bitmaps both throw there). The deck visits its pages
 * in order; each one waits for its reveal to settle before its frame is copied and saved
 * as `NZik_Rewind_<year>_NN.png` (a monthly deck adds the month: `NZik_Rewind_<year>_<MM>_NN.png`).
 * Each image carries the same top/bottom padding as the
 * live deck, but with the system bars hidden for the whole pass: the deck pins the
 * ignoring-visibility bar insets through [rewindShareCaptureActive], so the bar areas show
 * the slide background (no clock, battery icons or nav buttons) instead of the system UI.
 *
 * @return the page files in slide order, or null when the display could not be copied
 * or the device is below API 34 (the caller falls back to the single
 * software-replayed final slide).
 */
internal suspend fun captureRewindDeckPages(
    view: View,
    pagerState: PagerState,
    year: Int,
    month: Int? = null,
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

    // Clean frames for the whole pass: hold the deck's bar padding through the
    // ignoring-visibility insets, then hide the system bars so the bar areas show the
    // slide background instead of the system UI. Both are restored in the finally below,
    // even when a copy fails or the share coroutine is cancelled.
    rewindShareCaptureActive.value = true
    val insetsController = WindowCompat.getInsetsController(activity.window, view)
    insetsController.hide(WindowInsets.Type.systemBars())
    // Settle: let the hide and the inset change land in the framebuffer before the first copy
    withFrameNanos { }
    withFrameNanos { }
    delay(300)

    val files = mutableListOf<File>()
    try {
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
            ) ?: return null

            // Zero-paged so galleries order the 16 slides lexicographically (01..16)
            val monthSuffix = month?.let { "_${it.toString().padStart(2, '0')}" }.orEmpty()
            val pageFile = File(
                shareDirectory,
                "NZik_Rewind_${year}${monthSuffix}_${(page + 1).toString().padStart(2, '0')}.png"
            )
            val compressed = withContext(Dispatchers.IO) {
                pageFile.outputStream().buffered().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
            bitmap.recycle()
            if (!compressed) {
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
 * Suspends until one frame of the display has been copied into a fresh bitmap, or returns
 * null when the copy failed (display busy, screen off, ...). API 34+ only: the call
 * reaches the display compositor through the app [Window].
 */
private suspend fun pixelCopyDisplayFrame(
    window: Window,
    sourceRect: Rect,
    width: Int,
    height: Int
): Bitmap? {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
 * @return true when every page was written to the folder
 */
internal suspend fun exportRewindPagesToFolder(
    context: Context,
    folderUri: Uri,
    files: List<File>
): Boolean {
    var written = 0
    return withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocId)
            for (file in files) {
                val docUri = DocumentsContract.createDocument(resolver, parentUri, "image/png", file.name)
                    ?: throw IOException("Failed to create document for ${file.name}")
                resolver.openOutputStream(docUri)
                    ?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("Failed to open output stream for ${file.name}")
                written++
                Timber.tag("RewindShare").d("Exported %s (%d bytes)", file.name, file.length())
            }
            // The exported documents are the deliverable now; drop the temp copies
            files.forEach(File::delete)
            true
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Timber.tag("RewindShare").e(
                error,
                "Rewind export failed (%d/%d pages written)",
                written,
                files.size
            )
            false
        }
    }
}
