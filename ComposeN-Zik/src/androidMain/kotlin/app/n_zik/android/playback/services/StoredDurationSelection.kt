package app.n_zik.android.playback.services

import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.services.automotive.session.enrichAutoQueueDurations
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val storedDurationSelections = AtomicReference<StoredDurationSelectionOwner?>()
private val unboundSelectionScope by lazy { NzikDispatchers.fireAndForget(NzikDispatchers.MEDIA) }

/** Preparation and publication boundary shared by legacy phone selection entry points. */
internal interface StoredDurationSelection {
    suspend fun enrich(items: List<MediaItem>): List<MediaItem>
    suspend fun publish(apply: () -> Unit)
    fun ensureCurrent()
}

/** Owns phone queue preparation for the service lifetime and its current binder facade. */
internal fun bindStoredDurationSelections(
    scope: CoroutineScope,
    playerChanges: StateFlow<Int>? = null,
    currentProfile: () -> String = { getActiveProfile(appContext()) },
    enrich: suspend (List<MediaItem>, String) -> List<MediaItem> = ::enrichStoredSelectionDurations,
    currentPlayer: () -> Player,
): Job =
    scope.launch(NzikDispatchers.UI, start = CoroutineStart.UNDISPATCHED) {
        val owner = StoredDurationSelectionOwner(
            scope = scope,
            currentPlayer = currentPlayer,
            currentProfile = currentProfile,
            enrich = enrich,
        )
        storedDurationSelections.getAndSet(owner)?.close()
        try {
            owner.refreshPlayer()
            playerChanges?.collect { owner.refreshPlayer() } ?: awaitCancellation()
        } finally {
            owner.close()
            storedDurationSelections.compareAndSet(owner, null)
        }
    }

/** Follows binder facade replacement immediately, including a failed session rebind. Call on UI. */
internal fun notifyStoredDurationPlayerChanged() {
    storedDurationSelections.get()?.refreshPlayer()
}

private suspend fun enrichStoredSelectionDurations(items: List<MediaItem>, profile: String): List<MediaItem> =
    withContext(NzikDispatchers.DATA) {
        if (Database.FILE_NAME != Database.fileNameForProfile(profile)) {
            throw CancellationException("Stored duration database profile changed")
        }
        enrichAutoQueueDurations(items) { ids -> Database.songTable.findByIdsDirect(ids) }
    }

/** Captures selection ownership before dispatch, including for off-main callers. */
internal fun launchStoredDurationSelection(
    player: Player,
    block: suspend (StoredDurationSelection) -> Unit,
): Job {
    val owner = storedDurationSelections.get()
    if (owner != null) return owner.launch(player, block)
    // Temporary/test players without a service binding retain the original MEDIA -> UI
    // behavior. Do not invent an unowned database lookup or attach a lifecycle listener.
    return unboundSelectionScope.launch {
        block(object : StoredDurationSelection {
            override suspend fun enrich(items: List<MediaItem>) = items
            override suspend fun publish(apply: () -> Unit) = withContext(NzikDispatchers.UI) { apply() }
            override fun ensureCurrent() = Unit
        })
    }
}

/** Keeps optional local metadata preparation from publishing a superseded phone selection. */
internal class StoredDurationSelectionOwner(
    private val scope: CoroutineScope,
    private val currentPlayer: () -> Player,
    private val currentProfile: () -> String,
    private val enrich: suspend (List<MediaItem>, String) -> List<MediaItem>,
) {
    private val requests = IdentityHashMap<Player, Selection>()
    @Volatile private var closed = false
    private var observedPlayer: Player? = null
    private var observedTimeline: Timeline? = null
    private var generation = 0L
    private var publishing: Selection? = null
    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val player = observedPlayer ?: return
            // A delayed callback for an older snapshot cannot change current ownership.
            if (timeline !== player.currentTimeline) return
            if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                observedTimeline = timeline
                return
            }
            if (timeline === observedTimeline) return
            observedTimeline = timeline
            val pending = synchronized(requests) {
                generation++
                publishing?.generation = generation
                requests[player]?.takeUnless { it === publishing }
            }
            pending?.cancel()
        }
    }

    /** Attaches before requests are exposed, then follows binder facade changes on UI. */
    fun refreshPlayer() {
        checkUiThread()
        if (closed) return
        val latest = currentPlayer()
        if (observedPlayer === latest) return
        check(latest.applicationLooper === Looper.getMainLooper())
        observedPlayer?.removeListener(listener)
        val pending = synchronized(requests) {
            observedPlayer = latest
            generation++
            requests.values.toList()
        }
        pending.forEach { it.cancel() }
        observedTimeline = latest.currentTimeline
        latest.addListener(listener)
    }

    internal val pendingRequestCount: Int
        get() = synchronized(requests) { requests.size }

    fun launch(player: Player, block: suspend (StoredDurationSelection) -> Unit): Job {
        val profile = currentProfile()
        val (selection, previous) = synchronized(requests) {
            val next = Selection(player, profile, generation)
            next to requests.put(player, next)
        }
        previous?.cancel()
        val start = if (Looper.myLooper() === Looper.getMainLooper()) CoroutineStart.UNDISPATCHED
            else CoroutineStart.DEFAULT
        val job = scope.launch(NzikDispatchers.UI, start = start) {
            check(player.applicationLooper === Looper.getMainLooper())
            selection.ensureCurrent()
            withContext(NzikDispatchers.MEDIA) { block(selection) }
        }
        selection.job = job
        // DEFAULT may be cancelled before entering its body; a body-level finally is insufficient.
        job.invokeOnCompletion {
            synchronized(requests) {
                if (requests[player] === selection) requests.remove(player)
            }
        }
        if (!selection.valid) job.cancel()
        return job
    }

    fun close() {
        checkUiThread()
        closed = true
        observedPlayer?.removeListener(listener)
        observedPlayer = null
        observedTimeline = null
        val pending = synchronized(requests) { requests.values.toList().also { requests.clear() } }
        pending.forEach { it.cancel() }
    }

    internal inner class Selection internal constructor(
        private val player: Player,
        private val profile: String,
        internal var generation: Long,
    ) : StoredDurationSelection {
        @Volatile internal var job: Job? = null
        @Volatile internal var valid = true
            private set
        private var publishedTimeline: Timeline? = null

        override suspend fun enrich(items: List<MediaItem>): List<MediaItem> {
            currentCoroutineContext().ensureActive()
            return this@StoredDurationSelectionOwner.enrich(items, profile)
        }

        override suspend fun publish(apply: () -> Unit) = withContext(NzikDispatchers.UI) {
            currentCoroutineContext().ensureActive()
            ensureCurrent()
            check(publishedTimeline == null) { "A selection can publish only once" }
            publishing = this@Selection
            try {
                apply()
                publishedTimeline = player.currentTimeline
                // Reconcile now as well as in the listener: ExoPlayer may defer its own
                // playlist callback until after this synchronous publication returns.
                observedTimeline = publishedTimeline
            } finally {
                publishing = null
            }
        }

        override fun ensureCurrent() {
            checkUiThread()
            if (closed || !valid || synchronized(requests) {
                    requests[player] !== this || observedPlayer !== player ||
                        generation != this@StoredDurationSelectionOwner.generation
                } || currentPlayer() !== player ||
                currentProfile() != profile ||
                publishedTimeline?.let { it !== player.currentTimeline } == true
            ) throw CancellationException("Phone queue selection invalidated")
        }

        internal fun cancel() {
            valid = false
            job?.cancel()
        }
    }

    private fun checkUiThread() = check(Looper.myLooper() === Looper.getMainLooper()) {
        "Phone queue selection ownership belongs to the UI thread"
    }
}
