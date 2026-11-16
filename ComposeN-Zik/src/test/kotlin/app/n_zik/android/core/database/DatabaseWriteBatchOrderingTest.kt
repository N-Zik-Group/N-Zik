package app.n_zik.android.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.models.Song
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseWriteBatchOrderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: DatabaseInitializer

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns database
    }

    @After
    fun closeDatabase() {
        unmockkObject(DatabaseInitializer.Companion)
        database.close()
    }

    @Test
    fun `next batch waits for SQL and following file cleanup`() = runBlocking {
        val cacheFile = File.createTempFile("song-cache-", ".tmp", context.cacheDir)
        val firstWriteCommitted = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        Database.asyncTransaction {
            songTable.upsert(Song.makePlaceholder("first"))
            firstWriteCommitted.complete(Unit)
            allowCleanup.await()
            check(cacheFile.delete())
        }

        try {
            withTimeout(5_000) { firstWriteCommitted.await() }
            val next = async(start = CoroutineStart.UNDISPATCHED) {
                Database.awaitWriteBatch {
                    assertFalse(cacheFile.exists())
                    assertEquals("first", songTable.findByIdDirect("first")?.id)
                    songTable.upsert(Song.makePlaceholder("second"))
                }
            }
            try {
                assertTrue(cacheFile.exists())
                assertNull(withTimeoutOrNull(250) { next.await() })
            } finally {
                allowCleanup.complete(Unit)
            }
            withTimeout(5_000) { next.await() }
            assertEquals("second", database.songTable.findByIdDirect("second")?.id)
        } finally {
            allowCleanup.complete(Unit)
        }
    }

    @Test
    fun `write batch retries a SQLite lock before letting the next batch proceed`() = runBlocking {
        var attempts = 0
        Database.asyncTransaction {
            if (++attempts < 3) throw SQLiteDatabaseLockedException("test lock")
            songTable.upsert(Song.makePlaceholder("retried"))
        }

        withTimeout(5_000) {
            Database.awaitWriteBatch {
                assertEquals(3, attempts)
                assertEquals("retried", songTable.findByIdDirect("retried")?.id)
            }
        }
    }
}
