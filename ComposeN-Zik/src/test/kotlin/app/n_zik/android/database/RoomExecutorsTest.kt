package app.n_zik.android.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.models.Song
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Confirms wiring `NzikDispatchers.ROOM_QUERY_EXECUTOR`/`ROOM_TX_EXECUTOR` into the RoomDatabase builder (AD-3 of the #606 threading spine) does not regress basic query/transaction behavior - same off-main dispatch, now named and bounded. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomExecutorsTest {

    private lateinit var db: DatabaseInitializer
    private val queryThread = AtomicReference<String>()

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .setQueryExecutor(Executor { task ->
                NzikDispatchers.ROOM_QUERY_EXECUTOR.execute {
                    queryThread.set(Thread.currentThread().name)
                    task.run()
                }
            })
            .setTransactionExecutor(NzikDispatchers.ROOM_TX_EXECUTOR)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `transaction executor upserts a song on a nzik-room-tx thread`() = runBlocking {
        val threadName = withTimeout(5_000) {
            db.withTransaction {
                db.songTable.upsert(Song.makePlaceholder("song-1"))
                Thread.currentThread().name
            }
        }

        assertTrue("expected nzik-room-tx-* but was $threadName", threadName.startsWith("nzik-room-tx-"))
    }

    @Test
    fun `query executor reads back the song on a nzik-room-query thread`() = runBlocking {
        withTimeout(5_000) {
            db.songTable.upsert(Song.makePlaceholder("song-2"))
            queryThread.set(null)
            assertEquals("song-2", db.songTable.findByIdDirect("song-2")?.id)
        }

        val threadName = queryThread.get()
        assertTrue("expected nzik-room-query-* but was $threadName", threadName?.startsWith("nzik-room-query-") == true)
    }

    @Test
    fun `write batches preserve cleanup ordering across suspension`() = runBlocking {
        val writeFinished = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        val observed = CopyOnWriteArrayList<String>()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db

        try {
            Database.asyncTransaction {
                songTable.upsert(Song.makePlaceholder("ordered-write"))
                observed += "write"
                writeFinished.complete(Unit)
                allowCleanup.await()
                observed += "cleanup"
            }

            withTimeout(5_000) { writeFinished.await() }
            val next = async(start = CoroutineStart.UNDISPATCHED) {
                var songId: String? = null
                Database.awaitWriteBatch {
                    songId = songTable.findByIdDirect("ordered-write")?.id
                    observed += "next"
                }
                songId
            }

            assertEquals(listOf("write"), observed)
            assertFalse(next.isCompleted)
            allowCleanup.complete(Unit)
            assertEquals("ordered-write", withTimeout(5_000) { next.await() })
            assertEquals(listOf("write", "cleanup", "next"), observed)
        } finally {
            allowCleanup.complete(Unit)
            unmockkObject(DatabaseInitializer.Companion)
        }
    }
}
