package app.n_zik.android.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.LikeStateManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end counterpart of [StateManagerChunkingTest] (which uses mocked DAOs
 * because the SQLite bundled with Robolectric, >= 3.32, cannot reproduce the
 * 999-parameter limit): the chunked `IN` queries are executed against a real
 * in-memory Room database, proving the generated SQL runs correctly for a
 * 1500-song library (3 chunks of 500) and that the merged map is complete.
 *
 * JUnit 4 test methods cannot be suspend, so the coroutine entry points use
 * runBlocking (same convention as the Room DAO tests).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StateManagerChunkingRealDbTest {

    private lateinit var db: DatabaseInitializer

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        mockkObject(Database)
        every { Database.songTable } returns db.songTable
    }

    @After
    fun closeDb() {
        unmockkObject(Database)
        db.close()
    }

    @Test
    fun `like states with 1500 real songs run chunked queries and return the complete map`() = runBlocking {
        // 1500 songs: liked (likedAt > 0), disliked (likedAt < 0) and neutral (null)
        val ids = (0 until 1500).map { "song_$it" }
        db.songTable.upsert(
            ids.map { id ->
                val i = id.removePrefix("song_").toInt()
                Song.makePlaceholder(id).copy(likedAt = when (i % 3) { 0 -> 1_000L; 1 -> -1L; else -> null })
            }
        )

        val map = LikeStateManager.getLikeStates(ids).first()

        // the generated chunked IN-queries ran against a real database: the
        // map is complete (every requested id) with the exact per-song states
        assertEquals(1500, map.size)
        assertEquals(500, map.count { it.value == true })
        assertEquals(500, map.count { it.value == false })
        assertEquals(500, map.count { it.value == null })
    }
}
