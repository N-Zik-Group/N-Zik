package app.n_zik.android.playback.services.automotive.session

import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import android.os.Handler
import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/** Keeps request ownership through Media3's application of an asynchronous callback result. */
@UnstableApi
internal class AutoQueueApplicationOwner {
    internal class Request {
        var valid = true
        var applied = false
        var playbackAllowed = true
        var data: ListenableFuture<*>? = null
    }

    var applicationLooper: Looper = Looper.getMainLooper()
        private set

    private var observedPlayer: Player? = null
    private val timelineListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Phone/library callers use the binder's guest facade rather than the session facade.
            // A new playlist from that route retires any older asynchronous replacement too.
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && completion.get() == null) invalidate()
        }
    }

    fun release() {
        (observedPlayer as? ListenTogetherGuestGuardPlayer)?.onTransportIntent = null
        observedPlayer?.removeListener(timelineListener)
        observedPlayer = null
        invalidate()
    }

    private val completion = ThreadLocal<Request?>()
    private var current: Request? = null

    @Synchronized fun begin(): Request {
        current?.valid = false
        return Request().also { current = it }
    }

    @Synchronized fun invalidate() {
        current?.valid = false
        current = null
    }

    @Synchronized fun pending(request: Request): Boolean {
        val data = request.data
        if (data?.isDone == true) {
            try { Futures.getDone(data) }
            catch (_: ExecutionException) { request.valid = false }
            catch (_: CancellationException) { request.valid = false }
        }
        return current === request && request.valid && !request.applied
    }

    @Synchronized fun requestPlayback(request: Request) {
        if (current === request && request.valid) request.playbackAllowed = true
    }

    @Synchronized private fun suspendPlayback() {
        current?.playbackAllowed = false
    }

    private fun transportIntent(play: Boolean) {
        // An owned Media3 completion is applying an older intent, not a new user command.
        if (completion.get() != null) return
        if (play) current?.let(::requestPlayback) else suspendPlayback()
    }

    private fun playbackAllowed(): Boolean = completion.get()?.let {
        synchronized(this) { it.valid && current === it && it.playbackAllowed }
    } ?: true

    private fun allowed(): Boolean = completion.get()?.let { synchronized(this) { it.valid && current === it } } ?: true

    private fun setAllowed(): Boolean {
        val request = completion.get() ?: run { invalidate(); return true }
        return synchronized(this) {
            if (!request.valid || current !== request || request.applied) false
            else { request.applied = true; true }
        }
    }

    fun <T> future(delegate: ListenableFuture<T>, request: Request, looper: Looper): ListenableFuture<T> {
        synchronized(this) { request.data = delegate }
        // Failed data cannot ever be applied. Retire it synchronously, before another Play
        // can join it; successful data keeps ownership until actual framework application.
        delegate.addListener({
            try { Futures.getDone(delegate) }
            catch (_: ExecutionException) { synchronized(this) { request.valid = false } }
            catch (_: CancellationException) { synchronized(this) { request.valid = false } }
        }, MoreExecutors.directExecutor())
        val handler = Handler(looper)
        return object : ListenableFuture<T> by delegate {
            override fun addListener(listener: Runnable, executor: Executor) {
                delegate.addListener({
                    val apply = Runnable {
                        if (delegate.isCancelled) synchronized(this@AutoQueueApplicationOwner) { request.valid = false }
                        // Media3 1.10.1 uses directExecutor or postOrRun(applicationHandler).
                        // Enter on that handler before its transform posts the actual player work,
                        // so both paths apply inline within this narrowly scoped request context.
                        val previous = completion.get()
                        completion.set(request)
                        try { executor.execute(listener) }
                        finally { if (previous == null) completion.remove() else completion.set(previous) }
                    }
                    if (Looper.myLooper() === looper) apply.run() else handler.post(apply)
                }, MoreExecutors.directExecutor())
            }
        }
    }

    fun wrap(player: Player): Player {
        (observedPlayer as? ListenTogetherGuestGuardPlayer)?.onTransportIntent = null
        observedPlayer?.removeListener(timelineListener)
        observedPlayer = player
        val sharedGuard = player as? ListenTogetherGuestGuardPlayer
        // Attach before returning the session facade. Only this wired guard bypasses
        // pre-guard recording below: blocked guest operations must never count as intent.
        sharedGuard?.onTransportIntent = ::transportIntent
        player.addListener(timelineListener)
        applicationLooper = player.applicationLooper
        return object : ForwardingPlayer(player) {
            override fun setMediaItem(item: MediaItem) { if (setAllowed()) super.setMediaItem(item) }
            override fun setMediaItem(item: MediaItem, positionMs: Long) { if (setAllowed()) super.setMediaItem(item, positionMs) }
            override fun setMediaItem(item: MediaItem, resetPosition: Boolean) { if (setAllowed()) super.setMediaItem(item, resetPosition) }
            override fun setMediaItems(items: MutableList<MediaItem>) { if (setAllowed()) super.setMediaItems(items) }
            override fun setMediaItems(items: MutableList<MediaItem>, resetPosition: Boolean) { if (setAllowed()) super.setMediaItems(items, resetPosition) }
            override fun setMediaItems(items: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
                if (setAllowed()) super.setMediaItems(items, startIndex, startPositionMs)
            }
            override fun prepare() { if (playbackAllowed()) super.prepare() }
            override fun play() {
                if (sharedGuard == null) transportIntent(true)
                if (playbackAllowed()) super.play()
            }
            override fun pause() { if (sharedGuard == null) transportIntent(false); super.pause() }
            override fun stop() { if (sharedGuard == null) transportIntent(false); super.stop() }
            override fun setPlayWhenReady(value: Boolean) {
                if (sharedGuard == null) transportIntent(value)
                if (playbackAllowed()) super.setPlayWhenReady(value)
            }
            override fun seekToDefaultPosition() { if (allowed()) super.seekToDefaultPosition() }
            override fun seekToDefaultPosition(index: Int) { if (allowed()) super.seekToDefaultPosition(index) }
        }
    }
}
