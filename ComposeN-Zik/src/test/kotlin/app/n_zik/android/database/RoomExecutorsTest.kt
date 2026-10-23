package app.n_zik.android.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.utils.coroutines.NzikDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Confirms wiring `NzikDispatchers.ROOM_QUERY_EXECUTOR`/`ROOM_TX_EXECUTOR` into the RoomDatabase builder (AD-3 of the #606 threading spine) does not regress basic query/transaction behavior - same off-main dispatch, now named and bounded. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomExecutorsTest {

    private lateinit var db: DatabaseInitializer

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .setQueryExecutor(NzikDispatchers.ROOM_QUERY_EXECUTOR)
            .setTransactionExecutor(NzikDispatchers.ROOM_TX_EXECUTOR)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `transaction executor upserts a song on a nzik-room-tx thread`() {
        val threadName = AtomicReference<String>()
        val latch = CountDownLatch(1)

        db.transactionExecutor.execute {
            threadName.set(Thread.currentThread().name)
            db.songTable.upsert(Song.makePlaceholder("song-1"))
            latch.countDown()
        }

        assertTrue("transaction did not complete in time", latch.await(5, TimeUnit.SECONDS))
        assertTrue("expected nzik-room-tx-* but was ${threadName.get()}", threadName.get().startsWith("nzik-room-tx-"))
    }

    @Test
    fun `query executor reads back the song on a nzik-room-query thread`() {
        db.songTable.upsert(Song.makePlaceholder("song-2"))

        val threadName = AtomicReference<String>()
        val result = AtomicReference<Song?>()
        val latch = CountDownLatch(1)

        db.queryExecutor.execute {
            threadName.set(Thread.currentThread().name)
            result.set(db.songTable.findByIdDirect("song-2"))
            latch.countDown()
        }

        assertTrue("query did not complete in time", latch.await(5, TimeUnit.SECONDS))
        assertTrue("expected nzik-room-query-* but was ${threadName.get()}", threadName.get().startsWith("nzik-room-query-"))
        assertEquals("song-2", result.get()?.id)
    }
}
