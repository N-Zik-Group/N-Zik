package app.n_zik.android.components.dialog.song

import android.database.sqlite.SQLiteDatabaseLockedException
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Awaits the existing transaction executor, including its three lock retries. */
internal suspend fun <T> awaitSongUpdateCommit(block: Database.() -> T): T {
    val completion = CompletableDeferred<T>()
    val requestContext = coroutineContext
    var attempts = 0
    Database.asyncTransaction {
        attempts++
        try {
            requestContext.ensureActive()
            completion.complete(block())
        } catch (error: Exception) {
            if (error is SQLiteDatabaseLockedException && attempts < 3) throw error
            completion.completeExceptionally(error)
        }
    }
    return completion.await()
}
