package app.n_zik.android.playback.utils

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.applyCanvas
import app.n_zik.android.appContext
import timber.log.Timber
import app.n_zik.android.core.coil.ImageCacheFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//context(Context)
class BitmapProvider(
    private val scope: CoroutineScope,
    private val bitmapSize: Int,
    private val colorProvider: (isSystemInDarkMode: Boolean) -> Int
) {
    // Removed private val imageLoader

    var lastUri: Uri? = null
        private set

    var lastBitmap: Bitmap? = null
    private var lastIsSystemInDarkMode = false

    private var loadJob: Job? = null

    private lateinit var defaultBitmap: Bitmap

    val bitmap: Bitmap
        get() = lastBitmap ?: defaultBitmap

    var listener: ((Bitmap?) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(lastBitmap)
        }

    init {
        setDefaultBitmap()
    }

    fun setDefaultBitmap(): Boolean {
        val isSystemInDarkMode = appContext().resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        if (::defaultBitmap.isInitialized && isSystemInDarkMode == lastIsSystemInDarkMode) return false

        lastIsSystemInDarkMode = isSystemInDarkMode

        runCatching {
            defaultBitmap =
                Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888).applyCanvas {
                    drawColor(colorProvider(isSystemInDarkMode))
                }
        }.onFailure {
            Timber.tag("BitmapProvider").e("Failed set default bitmap ${it.stackTraceToString()}")
        }

        return lastBitmap == null
    }

    fun load(uri: Uri?, onDone: (Bitmap) -> Unit) {
        Timber.tag("BitmapProvider").d("load method being called")
        
        // If URI is null, use the default bitmap
        if (uri == null) {
            lastUri = null
            lastBitmap = null
            onDone(bitmap)
            return
        }
        
        if (lastUri == uri) {
            onDone(lastBitmap ?: defaultBitmap)
            listener?.invoke(lastBitmap)
            return
        }

        loadJob?.cancel()
        lastUri = uri

        loadJob = scope.launch(Dispatchers.IO) {
            try {
                val loadedBitmap: Bitmap? = ImageCacheFactory.loadBitmap(uri.toString(), allowHardware = false)
                
                withContext(Dispatchers.Main) {
                    if (loadedBitmap != null) {
                        lastBitmap = loadedBitmap
                    } else {
                        lastBitmap = null
                    }
                    onDone(bitmap)
                }
            } catch (e: Exception) {
                Timber.tag("BitmapProvider").e("Failed to load bitmap ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    lastBitmap = null
                    onDone(bitmap)
                }
            }
        }
    }
}


