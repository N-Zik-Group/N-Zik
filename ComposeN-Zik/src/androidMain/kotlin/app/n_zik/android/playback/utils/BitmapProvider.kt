package app.n_zik.android.playback.utils

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import app.n_zik.android.appContext
import timber.log.Timber
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.components.player.ArtworkSource
import app.n_zik.android.components.player.SharedArtworkColors
import app.n_zik.android.utils.getActiveProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//context(Context)
class BitmapProvider(
    private val scope: CoroutineScope,
    private val colorProvider: (isSystemInDarkMode: Boolean) -> Int
) {
    // Removed private val imageLoader

    var lastUri: Uri? = null
        private set

    var lastBitmap: Bitmap? = null
        get() = field?.takeIf { lastSource?.profile == getActiveProfile(appContext()) }
    private var lastSource: ArtworkSource? = null
    private var lastCacheGeneration = -1L
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
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(colorProvider(isSystemInDarkMode))
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
            loadJob?.cancel()
            lastUri = null
            lastSource = null
            lastBitmap = null
            onDone(bitmap)
            return
        }
        
        val context = appContext()
        val profile = getActiveProfile(context)
        val source = ImageCacheFactory.artworkColorSource(uri.toString(), profile)
        val cacheGeneration = SharedArtworkColors.generation.value
        if (lastUri == uri && lastSource == source && lastCacheGeneration == cacheGeneration &&
            (lastBitmap != null || loadJob?.isActive == true)
        ) {
            onDone(lastBitmap ?: defaultBitmap)
            listener?.invoke(lastBitmap)
            return
        }

        loadJob?.cancel()
        lastUri = uri
        lastSource = source
        lastCacheGeneration = cacheGeneration
        lastBitmap = null

        loadJob = scope.launch(NzikDispatchers.DATA) {
            try {
                val loadedBitmap = ImageCacheFactory.loadBitmap(
                    source?.uri,
                    requestSize = 400,
                    cacheDecodedBitmap = false,
                    profile = profile,
                )
                
                withContext(NzikDispatchers.UI) {
                    if (getActiveProfile(context) != profile) return@withContext
                    lastBitmap = loadedBitmap
                    onDone(bitmap)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.tag("BitmapProvider").e("Failed to load bitmap ${e.stackTraceToString()}")
                withContext(NzikDispatchers.UI) {
                    if (getActiveProfile(context) != profile) return@withContext
                    lastBitmap = null
                    onDone(bitmap)
                }
            }
        }
    }
}


