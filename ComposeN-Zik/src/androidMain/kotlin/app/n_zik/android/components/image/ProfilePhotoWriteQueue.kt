package app.n_zik.android.components.image

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Orders avatar replacement and removal, including blocking IO finishing after cancellation. */
internal class ProfilePhotoWriteQueue {
    private companion object {
        val mutex = Mutex()
    }

    suspend fun save(isCurrent: () -> Boolean, write: suspend () -> Boolean): Boolean = mutex.withLock {
        if (isCurrent()) write() else false
    }

    suspend fun remove(delete: suspend () -> Unit) = withContext(NonCancellable) {
        mutex.withLock { delete() }
    }
}
