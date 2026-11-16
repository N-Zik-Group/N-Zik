package app.n_zik.android.components.dialog.song

import app.n_zik.android.core.database.Database
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Waits for the queued update and its cleanup, or propagates the write failure. */
internal suspend fun <T> awaitSongUpdateCommit(block: suspend Database.() -> T): T {
    val requestContext = coroutineContext
    return Database.awaitWriteBatch {
        requestContext.ensureActive()
        block()
    }
}
