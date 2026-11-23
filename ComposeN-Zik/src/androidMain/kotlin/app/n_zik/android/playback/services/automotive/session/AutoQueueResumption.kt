package app.n_zik.android.playback.services.automotive.session

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.PersistentQueueArtwork
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.persistentQueueKey
import app.n_zik.android.utils.preferences
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.guava.future
import timber.log.Timber

@UnstableApi
internal fun loadAutoQueueResumption(
    context: Context,
    database: Database,
    scope: CoroutineScope,
    playbackReady: Deferred<Unit>?,
    accessEpoch: AtomicLong,
    accessPolicy: () -> AutoAccessPolicy,
): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
    val defaultResult = MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
    if (!context.preferences.getBoolean(persistentQueueKey, false)) return Futures.immediateFuture(defaultResult)
    return scope.future(NzikDispatchers.DATA) {
        playbackReady?.await()
        val requestProfile = getActiveProfile(context)
        val epoch = accessEpoch.get()
        try {
            val queued = database.readSavedQueue()
            if (getActiveProfile(context) != requestProfile || epoch != accessEpoch.get()) {
                throw CancellationException("Automotive queue read invalidated")
            }
            val idx = queued.indexOfFirst { it.position != null }.coerceAtLeast(0)
            val startPos = queued.getOrNull(idx)?.position ?: 0L
            val mediaItems = queued.map { item ->
                val restored = PersistentQueueArtwork.restore(context, requestProfile, item.mediaItem) {
                    database.songTable.findByIdDirect(it)?.thumbnailUrl
                }
                val original = PersistentQueueArtwork.originalSource(restored)
                val song = restored.asSong.let { song ->
                    val backedCustomCover = restored.mediaMetadata.artworkUri?.authority == ArtworkContentProvider.AUTHORITY
                    val modified = restored.mediaMetadata.extras?.getBoolean("isCoverModified") == true
                    val coverFile = if (backedCustomCover && modified) {
                        PersistentQueueArtwork.backingCoverFile(context, requestProfile, song.thumbnailUrl, song.id)
                    } else null
                    if (coverFile != null) {
                        song.copy(thumbnailUrl = MODIFIED_PREFIX + Uri.fromFile(coverFile))
                    } else if (backedCustomCover || modified) song else {
                        original?.let { song.copy(thumbnailUrl = it) } ?: song
                    }
                }
                val mapped = CancellableAutoMediaItemMapper.mapSongToMediaItem(song, true, song.isLocal)
                if (original == null) mapped else {
                    val extras = Bundle(mapped.mediaMetadata.extras).apply {
                        putString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA, original)
                    }
                    mapped.buildUpon().setMediaMetadata(mapped.mediaMetadata.buildUpon().setExtras(extras).build()).build()
                }
            }
            val available = accessPolicy().eligible(mediaItems)
            if (getActiveProfile(context) != requestProfile || epoch != accessEpoch.get()) {
                Timber.tag("AutoSessionCallback").w("Access changed before automotive resumption publication")
                throw CancellationException("Automotive resumption publication invalidated")
            } else {
                val retainedIndex = available.indexOfFirst { it.index == idx }
                if (retainedIndex < 0) defaultResult
                else MediaSession.MediaItemsWithStartPosition(available.map { it.value }, retainedIndex, startPos)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.tag("AutoSessionCallback").w(exception, "Unable to resume automotive playback")
            defaultResult
        }
    }
}
