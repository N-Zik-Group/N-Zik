package app.n_zik.android.core.database

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.appContext
import app.n_zik.android.core.utils.PropUtils
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.PersistentQueueArtwork
import app.n_zik.android.utils.getActiveProfile

@OptIn(UnstableApi::class)
internal fun validateLibraryArtwork(source: String?, songId: String): String? {
    val direct = PersistentQueueArtwork.readableUri(source, ::appContext)
    if (direct != null) return direct.toString()
    val uri = PersistentQueueArtwork.durableUri(source, songId) ?: return null
    if (uri.authority != ArtworkContentProvider.AUTHORITY) return null
    val context = appContext()
    return PersistentQueueArtwork.backingCoverFile(context, getActiveProfile(context), source, songId)
        ?.let { Uri.fromFile(it).toString() }
}

/** Selects stored song and album artwork from the sources of one media item. */
@OptIn(UnstableApi::class)
internal class LibraryInsertArtwork(
    mediaItem: MediaItem,
    private val songId: String,
    private val incomingCover: String?,
    private val preparedArtwork: Map<String?, String?>?,
) {
    private val providerCover = PersistentQueueArtwork.durableUri(incomingCover, songId)
        ?.authority == ArtworkContentProvider.AUTHORITY
    private val directArtwork = usableCover(incomingCover).takeUnless { providerCover }
    private val recoveredArtwork: String?
    private val incomingModified: Boolean
    val songThumbnail: String?

    init {
        val originalArtwork = PersistentQueueArtwork.originalSource(mediaItem)
        recoveredArtwork = if (directArtwork == null) usableCover(originalArtwork) else null
        val providerFile = if (providerCover) usableCover(incomingCover) else null
        val preferProviderFile = providerFile != null && incomingCover?.startsWith(MODIFIED_PREFIX, true) == true
        val source = if (preferProviderFile) providerFile else directArtwork ?: recoveredArtwork ?: providerFile
        val winningSource = if (!preferProviderFile && directArtwork == null && recoveredArtwork != null) {
            originalArtwork
        } else {
            incomingCover
        }
        incomingModified = winningSource?.startsWith(MODIFIED_PREFIX, true) == true
        songThumbnail = source?.let { if (incomingModified) MODIFIED_PREFIX + it else it }
    }

    fun songThumbnailWithExisting(existingCover: String?): String? = when {
        songThumbnail == null -> existingCover
        songThumbnail == existingCover -> existingCover
        usableCover(existingCover) == null -> songThumbnail
        directArtwork == null && !incomingModified -> existingCover
        providerCover && existingCover == incomingCover -> songThumbnail
        else -> PropUtils.retainIfModified(existingCover, songThumbnail)
    }

    fun albumThumbnail(): String? = (directArtwork ?: recoveredArtwork)?.takeUnless {
        incomingModified || PersistentQueueArtwork.durableUri(it) == null ||
            (it.startsWith("file:") && it.substringAfterLast('/') == "cover_$songId.jpg")
    }

    fun albumThumbnailWithExisting(albumArtwork: String?, existingCover: String?): String? = when {
        albumArtwork == null -> existingCover
        usableCover(existingCover) == null -> albumArtwork
        directArtwork == null -> existingCover
        else -> PropUtils.retainIfModified(existingCover, albumArtwork)
    }

    private fun usableCover(source: String?): String? = if (preparedArtwork == null) {
        validateLibraryArtwork(source, songId)
    } else if (preparedArtwork.containsKey(source)) {
        preparedArtwork[source]
    } else {
        PersistentQueueArtwork.durableUri(source, songId)?.toString()
    }
}
