package app.n_zik.android.components.dialog.song

import java.util.concurrent.Executor

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.song
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import app.n_zik.android.models.Song
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.SongTable

/** Uses runBlocking to await the suspend update entry points from synchronous JUnit tests. */
class SongUpdateFavoriteResetTest {
    private val songTable = mockk<SongTable>(relaxed = true)
    private val internal = mockk<DatabaseInitializer>(relaxed = true)
    private val storedRows = mutableMapOf<String, Song>()
    private val writtenRows = mutableListOf<Song>()
    private var inTransaction = false

    @Before
    fun setup() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns mockk<Context>(relaxed = true)
        mockkStatic("app.n_zik.android.utils.ProfilePreferencesKt")
        every { getActiveProfile(any()) } returns "active"
        mockkStatic("app.n_zik.innertube.requests.QueueKt")
        coEvery { Innertube.song(any()) } returns null
        mockkObject(Database)
        every { Database.songTable } returns songTable
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns internal
        every { internal.transactionExecutor } returns Executor { it.run() }
        every { Database.syncTransaction(any()) } answers {
            inTransaction = true
            try {
                firstArg<Database.() -> Unit>().invoke(Database)
            } finally {
                inTransaction = false
            }
        }
        every { songTable.findByIdDirect(any()) } answers { storedRows[firstArg<String>()] }
        every { songTable.updateReplace(any()) } answers {
            assertTrue("Song changes must share the database transaction", inTransaction)
            writtenRows.add(firstArg())
        }
        mockkObject(Toaster)
        every { Toaster.done() } just runs
        every { Toaster.s(any(), any<Long>()) } just runs
        every { Toaster.e(any<Int>(), any<Int>()) } just runs
        every { Toaster.e(any<String>(), any<Int>()) } just runs
        every { Toaster.w(any<String>(), any<Int>()) } just runs
    }

    @After
    fun teardown() = unmockkAll()

    @Test
    fun `favorite reset is optional and requires no provider fetch`() {
        assertFalse(SongUpdateSelection().favorite)
        assertFalse(SongUpdateSelection().isAnySelected)
        assertTrue(SongUpdateSelection(favorite = true).isAnySelected)
        assertFalse(SongUpdateSelection(favorite = true).needsFetch)
    }

    @Test
    fun `the original choices preserve liked and disliked status`() {
        for (likedAt in listOf(123L, -1L)) {
            val stored = storedSong("abcdefghijk", likedAt)
            val updated = applySongUpdate(
                stored,
                stored.copy(title = "Provider title"),
                SongUpdateSelection(true, true, true, true, true),
            )
            assertEquals(likedAt, updated.likedAt)
            assertEquals(stored.playCount, updated.playCount)
        }
    }

    @Test
    fun `single reset uses stored liked disliked and neutral rows without fetching`() = runBlocking {
        for (likedAt in listOf(123L, -1L, null)) {
            val stored = storedSong("abcdefghijk", likedAt)
            storedRows[stored.id] = stored
            runSongUpdate(null, snapshot(stored), SongUpdateSelection(favorite = true), "active")
            assertEquals(stored.copy(likedAt = null), writtenRows.last())
            assertEquals(stored.playCount, writtenRows.last().playCount)
        }
        coVerify(exactly = 0) { Innertube.song(any()) }
        verify(exactly = 3) { Toaster.done() }
    }

    @Test
    fun `favorite and playtime reset commit together while preserving the play count`() = runBlocking {
        val stored = storedSong("abcdefghijk", -1L)
        storedRows[stored.id] = stored
        assertTrue(runSongUpdateCore(
            null, snapshot(stored), SongUpdateSelection(playtime = true, favorite = true), "active",
        ))
        assertEquals(listOf(stored.copy(likedAt = null, totalPlayTimeMs = 0L)), writtenRows)
        assertEquals(stored.playCount, writtenRows.single().playCount)
        verify(exactly = 1) { Database.syncTransaction(any()) }
        coVerify(exactly = 0) { Innertube.song(any()) }
    }

    @Test
    fun `batch resets both statuses with no provider request or fetch delay`() = runBlocking {
        val liked = storedSong("abcdefghijk", 123L)
        val disliked = storedSong("bcdefghijkl", -1L)
        listOf(liked, disliked).forEach { storedRows[it.id] = it }
        val progress = mutableListOf<Pair<Int, Int>>()
        val processed = runSongUpdateBatch(
            null,
            listOf(snapshot(liked), snapshot(disliked)),
            SongUpdateSelection(favorite = true),
            requestProfile = "active",
            fetchGapMs = { error("Favorite-only updates must not wait for provider requests") },
        ) { done, total -> progress.add(done to total) }
        assertEquals(2, processed)
        assertEquals(listOf(liked.copy(likedAt = null), disliked.copy(likedAt = null)), writtenRows)
        assertEquals(listOf(liked.playCount, disliked.playCount), writtenRows.map { it.playCount })
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        coVerify(exactly = 0) { Innertube.song(any()) }
    }

    private fun storedSong(id: String, likedAt: Long?) = Song(
        id, "modified:Stored title", "modified:Stored artist", "3:00", "modified:cover",
        likedAt = likedAt, totalPlayTimeMs = 4567L, position = 4, isYoutubeSong = true,
    ).apply { playCount = 7 }

    private fun snapshot(song: Song) = Song(song.id, "Old title", "Old artist", null, null)
}
