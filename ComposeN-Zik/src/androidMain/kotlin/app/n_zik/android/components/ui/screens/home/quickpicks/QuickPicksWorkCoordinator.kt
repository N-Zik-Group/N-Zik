package app.n_zik.android.components.ui.screens.home.quickpicks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reentry shares work; refresh and changed request parameters queue the latest load. */
internal class QuickPicksLoadCoordinator(private val scope: CoroutineScope) {
    private data class Request(
        val identity: Any,
        val onStart: (Boolean) -> Unit,
        val load: suspend (force: Boolean) -> Unit,
    )

    private var job: Job? = null
    private var activeIdentity: Any? = null
    private var pending: Request? = null

    @Synchronized
    fun request(
        identity: Any = Unit,
        refresh: Boolean = false,
        invalidate: Boolean = false,
        onStart: (force: Boolean) -> Unit = {},
        load: suspend (force: Boolean) -> Unit,
    ) {
        val request = Request(identity, onStart, load)
        if (job?.isActive == true) {
            if (refresh || invalidate || identity != activeIdentity || pending != null) pending = request
            return
        }
        val force = refresh || invalidate || (activeIdentity != null && activeIdentity != identity)
        activeIdentity = identity
        onStart(force)
        val nextJob = scope.launch(start = CoroutineStart.LAZY) {
            load(force)
            while (true) {
                val next = synchronized(this@QuickPicksLoadCoordinator) {
                    pending.also {
                        pending = null
                        if (it == null) job = null else activeIdentity = it.identity
                    }
                } ?: break
                next.onStart(true)
                next.load(true)
            }
        }
        job = nextJob
        nextJob.invokeOnCompletion {
            synchronized(this) {
                if (job === nextJob) {
                    job = null
                    pending = null
                }
            }
        }
        nextJob.start()
    }
}

/** Observer replacement fences old publications and preserves the complete cleanup chain. */
internal class QuickPicksObservationOwner(private val scope: CoroutineScope) {
    private var owner: Any? = null
    private var publication: Any? = null
    private var job: Job? = null

    @Synchronized
    fun claim(token: Any = Any()): Any = token.also {
        owner = it
        publication = null
        job?.cancel()
    }

    @Synchronized
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(token: Any, restart: Boolean = false, observe: suspend (publication: Any) -> Unit) {
        if (owner !== token || (!restart && job?.isActive == true)) return
        val predecessor = job
        val lease = Any()
        publication = lease
        job = scope.launch(start = CoroutineStart.ATOMIC) {
            // A cancelled intermediate owner must still wait for all predecessor cleanup.
            withContext(NonCancellable) { predecessor?.cancelAndJoin() }
            ensureActive()
            if (isOwner(token)) observe(lease)
        }
    }

    @Synchronized
    private fun isOwner(token: Any): Boolean = owner === token

    @Synchronized
    fun publish(lease: Any, update: () -> Unit) {
        if (publication === lease) update()
    }
}

/** A successful merged page above the usable-section threshold ends the initial attempts. */
internal suspend fun loadQuickPicksHomeAttempts(attempt: suspend () -> Int) {
    for (index in 0 until 3) {
        if (attempt() > 15) break
    }
}
