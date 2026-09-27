package app.n_zik.android.utils.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.excludeMediaItems
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #606 (part 2, lot C) -- `Player.excludeMediaItems`
 * (`app.it.fast4x.rimusic.utils.Player.kt`, legacy) runs up to three Room reads
 * (`getAllDislikedIds` / `getSongsByDislikedArtists` / `getSongsByDislikedAlbums`) inside
 * `runBlocking(NzikDispatchers.DATA)`: the DB work now runs on the named dispatcher instead of
 * the caller's thread, while the public API stays synchronous (the caller waits for the
 * filtered list).
 *
 * JUnit 4 + [RobolectricTestRunner] (executed through the project's junit-vintage-engine on the
 * JUnit 5 platform, the Robolectric runner only works with JUnit 4) because
 * `context.preferences` needs a real SharedPreferences. The caller is a named single-thread
 * executor standing in for the main thread (same technique as ForcePlayAtIndexDispatchTest).
 *
 * [Config] pins the SDK explicitly: with Android resources on the unit-test classpath,
 * tests running on the default (target) SDK would trigger Android's
 * `ApplicationSharedMemory` bootstrap, whose raw FileDescriptor reflection does not work
 * on the JVM (same as every other Robolectric test in this module).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExcludeMediaItemsDislikedQueriesOffMainTest {

    private fun mediaItem(id: String) = MediaItem.Builder().setMediaId(id).build()

    private fun setDislikeModes(context: Context, mode: DislikeMode) {
        context.preferences.edit().run {
            putString(excludeDislikedSongsKey, mode.name)
            putString(excludeDislikedArtistsKey, mode.name)
            putString(excludeDislikedAlbumsKey, mode.name)
            apply()
        }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `excludeMediaItems runs the three disliked queries off the caller thread`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setDislikeModes(context, DislikeMode.Enabled)

        val songTable = mockk<SongTable>()
        var songsThread: String? = null
        var artistsThread: String? = null
        var albumsThread: String? = null
        coEvery { songTable.getAllDislikedIds() } answers {
            songsThread = Thread.currentThread().name
            emptyList<String>()
        }
        coEvery { songTable.getSongsByDislikedArtists() } answers {
            artistsThread = Thread.currentThread().name
            emptyList<String>()
        }
        coEvery { songTable.getSongsByDislikedAlbums() } answers {
            albumsThread = Thread.currentThread().name
            emptyList<String>()
        }
        mockkObject(Database)
        every { Database.songTable } returns songTable

        val player = mockk<Player>(relaxed = true)
        val items = listOf(mediaItem("a"), mediaItem("b"))

        val caller: ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "fake-main") }
        try {
            val result = caller.submit(Callable { player.excludeMediaItems(items, context) }).get()

            assertNotNull("getAllDislikedIds was never executed", songsThread)
            assertNotNull("getSongsByDislikedArtists was never executed", artistsThread)
            assertNotNull("getSongsByDislikedAlbums was never executed", albumsThread)
            assertNotEquals(
                "disliked-songs query must not run on the caller's (UI-simulating) thread",
                "fake-main",
                songsThread
            )
            assertNotEquals(
                "disliked-artists query must not run on the caller's (UI-simulating) thread",
                "fake-main",
                artistsThread
            )
            assertNotEquals(
                "disliked-albums query must not run on the caller's (UI-simulating) thread",
                "fake-main",
                albumsThread
            )

            // Empty query results -> the list is unchanged; each query ran exactly once.
            assertEquals(items, result)
            coVerify(exactly = 1) {
                songTable.getAllDislikedIds()
                songTable.getSongsByDislikedArtists()
                songTable.getSongsByDislikedAlbums()
            }
        } finally {
            caller.shutdownNow()
        }
    }

    @Test
    fun `excludeMediaItems issues no disliked queries when the settings are disabled`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setDislikeModes(context, DislikeMode.Disabled)

        val songTable = mockk<SongTable>()
        mockkObject(Database)
        every { Database.songTable } returns songTable

        val player = mockk<Player>(relaxed = true)
        val items = listOf(mediaItem("a"))

        assertEquals(items, player.excludeMediaItems(items, context))

        coVerify(exactly = 0) {
            songTable.getAllDislikedIds()
            songTable.getSongsByDislikedArtists()
            songTable.getSongsByDislikedAlbums()
        }
    }
}
