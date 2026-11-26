package app.n_zik.android.playback.services

import android.content.Context
import androidx.annotation.MainThread
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.appTextResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.models.QueuedMediaItem
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.isExplicit
import app.n_zik.android.utils.mediaItems
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.resumePlaybackOnStartKey

/** Captures the current queue and restores a saved one only while startup owns playback. */
@UnstableApi
internal class PlaybackPersistentQueue(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val player: () -> Player,
    private val isDestroying: () -> Boolean,
    private val isPersistentQueueEnabled: () -> Boolean,
    private val restoreStartupQueueIfUnowned: (() -> Unit) -> Unit,
    private val resumePlayback: () -> Unit,
) {
    private var queuePlaylistChanged = false

    fun onPlaylistChanged() {
        queuePlaylistChanged = true
    }

    @MainThread
    fun save(player: Player, saveEmpty: Boolean) {
        val mediaItems = player.currentTimeline.mediaItems
        if (mediaItems.isEmpty() && !saveEmpty) return
        val mediaItemIndex = player.currentMediaItemIndex
        val mediaItemPosition = player.currentPosition
        val profile = getActiveProfile(context)
        val snapshot = mediaItems.mapIndexed { index, mediaItem ->
            QueuedMediaItem(mediaItem = mediaItem, position = mediaItemPosition.takeIf { index == mediaItemIndex })
        }

        // Keep snapshots in submission order, including the one taken during teardown.
        Database.saveQueueSnapshot {
            snapshot.map { row ->
                val item = PersistentQueueArtwork.preserveSnapshotSource(context, profile, row.mediaItem)
                QueuedMediaItem(mediaItem = item, position = row.position)
            }
        }
    }

    fun restore() {
        val profile = getActiveProfile(context)

        // A recreated service must read after the previous service's final snapshot.
        coroutineScope.launch(NzikDispatchers.DATA) {
            if (Database.FILE_NAME != Database.fileNameForProfile(profile)) return@launch
            val songs = Database.songTable
            val preferences = context.preferences
            val queuedSong = Database.readSavedQueue()
            val rows = songs.observePlaybackIdentity(queuedSong.map { it.mediaItem.sessionArtistTrackId() }).first()
            val unknownArtist = appTextResources().getString(R.string.metadata_unknown_artist)
            val restored = queuedSong.map { row ->
                val item = row.mediaItem
                val current = rows[item.sessionArtistTrackId()]?.let {
                    item.withCurrentTrackIdentity(it, unknownArtist) { id -> preferences.getString("old_browse_id_$id", null) }
                } ?: item
                PersistentQueueArtwork.restore(context, profile, current) { rows[it]?.song?.thumbnailUrl } to row.position
            }

            withContext(NzikDispatchers.UI) {
                restoreStartupQueueIfUnowned restore@{
                    if (isDestroying() || !isPersistentQueueEnabled() || getActiveProfile(context) != profile ||
                        queuePlaylistChanged || player().mediaItemCount > 0
                    ) return@restore
                    val filtered = if (context.preferences.getBoolean(parentalControlEnabledKey, false)) {
                        restored.filterNot { (item, _) -> item.isExplicit }
                    } else restored
                    if (filtered.isEmpty()) return@restore
                    val index = filtered.indexOfFirst { (_, position) -> position != null }.coerceAtLeast(0)
                    player().setMediaItems(
                        filtered.map { it.first },
                        index,
                        filtered[index].second ?: C.TIME_UNSET
                    )
                    if (RestoredQueueRules.shouldPrepareRestoredQueue(context.preferences.getBoolean(resumePlaybackOnStartKey, false))) {
                        resumePlayback()
                    }
                }
            }
        }
    }
}
