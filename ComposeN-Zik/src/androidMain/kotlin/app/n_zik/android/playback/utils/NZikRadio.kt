package app.n_zik.android.playback.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.services.PlayerServiceModern
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.utils.discoverKey
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.preferences
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.nextPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.utils.asMediaItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastMap
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.mediaItems

@OptIn(UnstableApi::class)
class NZikRadio(
    private val context: Context,
    private val binder: PlayerServiceModern.Binder,
    private val coroutineScope: CoroutineScope
) {
    var isRadioActive by mutableStateOf(false)
        private set

    private var radioJob: Job? = null
    
    private var reminderShown = false
    
    // Tracks if radio is currently fetching data (to prevent double-fetching)
    var isLoading by mutableStateOf(false)
        private set

    val isDiscoverEnabled: Boolean
        get() = context.preferences.getBoolean(discoverKey, false)

    val isAutoFillEnabled: Boolean
        get() = context.preferences.getBoolean(autoLoadSongsInQueueKey, true)

    val radioActionTextRes: Int
        get() = if (isRadioActive) R.string.stop_radio else R.string.start_radio


    /**
     * Toggles the discover filter ON/OFF and shows a toast.
     */
    fun toggleDiscover() {
        if (!isRadioActive && !isAutoFillEnabled) {
            Toaster.e(R.string.state_discover_error)
            return
        }

        val newState = !isDiscoverEnabled
        context.preferences.edit().putBoolean(discoverKey, newState).apply()
        
        if (newState) {
            Toaster.i(R.string.state_discover_on)
        } else {
            Toaster.i(R.string.state_discover_off)
        }
    }

    /**
     * Explicit Radio activation (Toggle)
     */
    fun startRadio(
        mediaItem: MediaItem,
        append: Boolean = false,
        endpoint: NavigationEndpoint.Endpoint.Watch? = null,
        isExplicit: Boolean = false
    ) {
        if (isExplicit) {
            // Toggle OFF if already active on the exact same song
            if (isRadioActive && binder.player.currentMediaItem?.mediaId == mediaItem.mediaId) {
                stopRadio(showToast = true)
                return
            }

            stopRadio(showToast = false) // Clean state before starting
            isRadioActive = true

            // Toast: Radio ON
            if (isDiscoverEnabled) {
                Toaster.i(R.string.state_radio_on_discover)
            } else {
                Toaster.i(R.string.state_radio_on_classic)
            }
        } else {
            // Implicit start (fetching songs silently for queue/autofill)
            stopRadio(showToast = false)
        }

        // Play song immediately
        if (binder.player.currentMediaItem?.mediaId != mediaItem.mediaId) {
            binder.player.forcePlay(mediaItem)
        }

        fetchAndInject(mediaItem.mediaId, endpoint?.playlistId, append)
    }

    fun stopRadio(showToast: Boolean = false) {
        isLoading = false
        isRadioActive = false
        radioJob?.cancel()
        radioJob = null
        
        if (showToast) {
            Toaster.i(R.string.state_radio_off)
        }
    }

    /**
     * Silent Auto-Fill (When queue is about to end)
     */
    fun autoFillQueue() {
        // If neither Radio nor AutoFill is enabled, do nothing.
        if ((!isRadioActive && !isAutoFillEnabled) || isLoading) return
        
        val currentMediaItem = binder.player.currentMediaItem ?: return
        
        // AutoFill: Silent fetch (No toast)
        fetchAndInject(currentMediaItem.mediaId, null, append = true)
    }

    fun showReminderIfNeeded() {
        if (!reminderShown) {
            reminderShown = true
            
            if (isRadioActive) return // No reminder needed if they explicitly started radio
            
            if (isDiscoverEnabled) {
                if (isAutoFillEnabled) {
                    Toaster.i(R.string.state_reminder_discover)
                } else {
                    // Auto-correction: if Discover is ON but AutoFill is OFF and Radio is OFF
                    context.preferences.edit().putBoolean(discoverKey, false).apply()
                }
            }
        }
    }

    private fun fetchAndInject(videoId: String, initialPlaylistId: String?, append: Boolean) {
        radioJob = coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            
            var playlistId = initialPlaylistId
            var mediaItems = emptyList<MediaItem>()

            // Dynamic Seed: Always fetch based on videoId rather than continuation
            if (playlistId == null) {
                // Try to get playlistId from next endpoint
                playlistId = Innertube.nextPage(videoId = videoId)
                    ?.getOrNull()?.itemsPage?.items?.firstOrNull()
                    ?.info?.endpoint?.playlistId
            }
                
            if (!playlistId.isNullOrBlank()) {
                Innertube.nextPage(videoId = videoId, playlistId = playlistId)?.getOrNull()?.let { page ->
                    mediaItems = page.itemsPage?.items?.map { it.asMediaItem } ?: emptyList()
                    // Fallback: If there's a continuation, we can grab it, but usually the first request just sets up the queue
                }
            } else {
                // FALLBACK: If YouTube doesn't return a playlistId, use related songs directly!
                Innertube.nextPage(videoId = videoId)?.getOrNull()?.let { page ->
                    mediaItems = page.itemsPage?.items?.map { it.asMediaItem } ?: emptyList()
                }
            }

            if (mediaItems.isEmpty()) {
                isLoading = false
                return@launch
            }

            // 2. Insert into DB to save history/cache
            Database.asyncTransaction {
                mediaItems.forEach { Database.insertIgnore(it) }
            }

            // 3. Filter Discover and Deduplicate
            val filteredItems = discoverFilter(mediaItems)

            // 4. Inject into Player
            withContext(Dispatchers.Main) {
                injectIntoPlayer(filteredItems, append)
            }

            isLoading = false
        }
    }

    private suspend fun discoverFilter(items: List<MediaItem>): List<MediaItem> {
        // Any call to player must happen on Main thread
        val currentQueueIds = withContext(Dispatchers.Main) {
            binder.player.mediaItems.fastMap { it.mediaId }
        }

        // Deduplicate
        var filtered = items.filter { it.mediaId !in currentQueueIds }

        // Apply Discover Filter (remove known songs)
        if (isDiscoverEnabled) {
            filtered = filtered.filter { item ->
                val isMapped = Database.songPlaylistMapTable.isMappedSync(item.mediaId)
                val isLiked = Database.songTable.isLikedDirect(item.mediaId)
                !(isMapped && isLiked) // Keep if NOT (mapped AND liked)
            }
        }
        return Shuffler.shuffle(filtered)
    }

    @MainThread
    private fun injectIntoPlayer(items: List<MediaItem>, append: Boolean) {
        val player = binder.player
        val curIndex = player.currentMediaItemIndex
        val endIndex = player.mediaItemCount
        
        if (!append && player.mediaItemCount > 1) {
            // Replace remaining queue
            player.moveMediaItem(curIndex, 0)
            player.removeMediaItems(curIndex + 1, endIndex)
        }

        player.addMediaItems(items)
    }
}
