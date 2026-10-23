package app.n_zik.android.playback.utils

import android.content.Context
import android.content.SharedPreferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.maxSongsInQueueKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShufflerTest {

    companion object {
        /**
         * Captured by [captureProductionDefaultBeforeAnyOverride] in a `@BeforeAll` -- which runs
         * once, before this class's very first `@BeforeEach` -- so it reflects `Shuffler.kt`'s
         * actual field initializer, not a value any test in this class (or elsewhere; grep
         * confirms `ShufflerTest` is the only place that ever writes `Shuffler.backgroundDispatcher`)
         * wrote back. Asserting against this, rather than setting-then-reading the same value
         * inside a single test, is what actually pins the source's declared default.
         */
        private lateinit var productionDefaultBeforeAnyOverride: kotlinx.coroutines.CoroutineDispatcher

        @JvmStatic
        @BeforeAll
        fun captureProductionDefaultBeforeAnyOverride() {
            productionDefaultBeforeAnyOverride = Shuffler.backgroundDispatcher
        }
    }

    private lateinit var binder: PlayerServiceModern.Binder
    private lateinit var player: ExoPlayer
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Shuffler.backgroundDispatcher = UnconfinedTestDispatcher()

        binder = mockk(relaxed = true)
        player = mockk(relaxed = true)
        every { binder.player } returns player

        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context

        mockkStatic("app.it.fast4x.rimusic.utils.PreferencesKt")
        every { context.preferences } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } just Runs

        mockkObject(Toaster)
        every { Toaster.i(any<Int>()) } just Runs
        every { Toaster.i(any<String>()) } just Runs
        every { Toaster.s(any<Int>()) } just Runs
        every { Toaster.s(any<String>()) } just Runs
        every { Toaster.s(any<Int>(), any()) } just Runs
        every { Toaster.e(any<Int>()) } just Runs
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        Shuffler.backgroundDispatcher = NzikDispatchers.DATA
        unmockkAll()
    }

    /**
     * Pins the production default independently of the `@BeforeEach` override above -- without
     * this, a regression that changes the field's declared default (e.g. back to a raw
     * `Dispatchers.IO`/`Default` literal, or to something that isn't actually off-main) would
     * pass unnoticed, since every other test in this class runs under the
     * `UnconfinedTestDispatcher()` override, never under the real production value. Asserts
     * against the value [captureProductionDefaultBeforeAnyOverride] captured before any
     * `@BeforeEach` ran, not against a value this test just wrote itself.
     */
    @Test
    fun `backgroundDispatcher production default is NzikDispatchers DATA`() {
        assertEquals(NzikDispatchers.DATA, productionDefaultBeforeAnyOverride)
    }

    private fun mediaItem(id: String) = MediaItem.Builder().setMediaId(id).build()
    private fun mediaItems(count: Int) = (1..count).map { mediaItem("song_$it") }

    @Nested
    inner class PlayMediaItems {

        @Test
        fun `empty list shows info toast`() {
            Shuffler.play(binder, emptyList<MediaItem>())

            verify { Toaster.i(R.string.no_song_to_shuffle) }
        }

        @Test
        fun `empty list does not stop radio`() {
            Shuffler.play(binder, emptyList<MediaItem>())

            verify(exactly = 0) { binder.stopRadio() }
        }

        @Test
        fun `non-empty list respects maxSongsInQueue cap`() {
            every {
                sharedPreferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            every {
                sharedPreferences.getString(excludeDislikedArtistsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            every {
                sharedPreferences.getString(excludeDislikedAlbumsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            every {
                sharedPreferences.getString(maxSongsInQueueKey, null)
            } returns MaxSongs.`100`.name

            val captured = slot<List<MediaItem>>()
            mockkStatic("app.it.fast4x.rimusic.utils.PlayerKt")
            every { player.forcePlayFromBeginning(capture(captured)) } just Runs

            Shuffler.play(binder, mediaItems(150))

            assertEquals(100, captured.captured.size)
            verify { binder.stopRadio() }
        }

        @Test
        fun `onComplete is invoked when the list is empty`() {
            var completed = false

            Shuffler.play(binder, emptyList<MediaItem>(), onComplete = { completed = true })

            assertTrue(completed)
        }

        @Test
        fun `onComplete fires only after the empty-list toast, not before`() {
            val events = mutableListOf<String>()
            every { Toaster.i(R.string.no_song_to_shuffle) } answers { events.add("toast") }

            Shuffler.play(binder, emptyList<MediaItem>(), onComplete = { events.add("onComplete") })

            assertEquals(listOf("toast", "onComplete"), events)
        }

        @Test
        fun `onComplete is invoked after a successful play`() {
            every {
                sharedPreferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            every {
                sharedPreferences.getString(excludeDislikedArtistsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            every {
                sharedPreferences.getString(excludeDislikedAlbumsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            mockkStatic("app.it.fast4x.rimusic.utils.PlayerKt")
            every { player.forcePlayFromBeginning(any()) } just Runs
            var completed = false

            Shuffler.play(binder, mediaItems(5), onComplete = { completed = true })

            assertTrue(completed)
        }

        @Test
        fun `onComplete fires only after stopRadio and forcePlayFromBeginning complete, not before`() {
            every {
                sharedPreferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            every {
                sharedPreferences.getString(excludeDislikedArtistsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            every {
                sharedPreferences.getString(excludeDislikedAlbumsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            mockkStatic("app.it.fast4x.rimusic.utils.PlayerKt")
            val events = mutableListOf<String>()
            every { binder.stopRadio() } answers { events.add("stopRadio") }
            every { player.forcePlayFromBeginning(any()) } answers { events.add("forcePlayFromBeginning") }

            Shuffler.play(binder, mediaItems(5), onComplete = { events.add("onComplete") })

            assertEquals(listOf("stopRadio", "forcePlayFromBeginning", "onComplete"), events)
        }

        @Test
        fun `play returns before onComplete fires when dispatched on a real background dispatcher`() {
            // Unlike every other test in this class (which overrides backgroundDispatcher with
            // UnconfinedTestDispatcher() in @BeforeEach for determinism), this test explicitly

            // uses a StandardTestDispatcher: its queued work does NOT run until the scheduler is
            // advanced. This is what actually proves play() is fire-and-forget (returns before

            // its background work runs) rather than just checking relative call order under an
            // eagerly-executing dispatcher.
            val testDispatcher = StandardTestDispatcher()
            Shuffler.backgroundDispatcher = testDispatcher
            every {
                sharedPreferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            every {
                sharedPreferences.getString(excludeDislikedArtistsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            every {
                sharedPreferences.getString(excludeDislikedAlbumsKey, DislikeMode.Enabled.name)
            } returns DislikeMode.Disabled.name
            mockkStatic("app.it.fast4x.rimusic.utils.PlayerKt")
            every { player.forcePlayFromBeginning(any()) } just Runs
            var completed = false

            Shuffler.play(binder, mediaItems(5), onComplete = { completed = true })
            assertFalse(completed, "onComplete must not fire before play()'s background work has actually run")

            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(completed, "onComplete must fire once the dispatched work has actually completed")
            verify { binder.stopRadio() }
        }

        @Test
        fun `onComplete still fires when a dislike-filter DB lookup throws, with filtering left at its default-enabled setting`() {
            // Deliberately does NOT override excludeDislikedSongsKey's preference (unlike every
            // other test above) -- SharedPreferences.getString on this relaxed mock returns null,

            // so `?: DislikeMode.Enabled` resolves to the real production default: filtering ON.
            // That's what actually reaches Database.songTable.getAllDislikedIds() below.
            mockkObject(Database)
            val songTable = mockk<SongTable>()
            every { Database.songTable } returns songTable
            coEvery { songTable.getAllDislikedIds() } throws RuntimeException("DB unavailable")
            var completed = false

            Shuffler.play(binder, mediaItems(5), onComplete = { completed = true })

            assertTrue(completed, "onComplete must still fire when filtering throws, or callers' loading flags stay stuck forever")
            verify { Toaster.e(R.string.no_song_found) }
            verify(exactly = 0) { binder.stopRadio() }
        }
    }

    @Nested
    inner class PlaySongs {

        @Test
        fun `empty song list shows info toast`() {
            Shuffler.play(binder, emptyList<MediaItem>())

            verify { Toaster.i(R.string.no_song_to_shuffle) }
        }

        @Test
        fun `empty song list does not stop radio`() {
            Shuffler.play(binder, emptyList<MediaItem>())

            verify(exactly = 0) { binder.stopRadio() }
        }
    }

    @Nested
    inner class Queue {

        @Test
        fun `empty player does nothing`() {
            every { player.currentMediaItemIndex } returns 0
            every { player.mediaItemCount } returns 0

            Shuffler.queue(player)

            verify(exactly = 0) { player.addMediaItems(any<List<MediaItem>>()) }
            verify(exactly = 0) { player.removeMediaItems(any<Int>(), any<Int>()) }
        }

        @Test
        fun `single item does nothing`() {
            every { player.currentMediaItemIndex } returns 0
            every { player.mediaItemCount } returns 1

            Shuffler.queue(player)

            verify(exactly = 0) { player.addMediaItems(any<List<MediaItem>>()) }
        }

        @Test
        fun `no toast for empty player`() {
            every { player.currentMediaItemIndex } returns 0
            every { player.mediaItemCount } returns 0

            Shuffler.queue(player)

            verify(exactly = 0) { Toaster.s(any<Int>(), any()) }
        }

        @Test
        fun `no toast for single item`() {
            every { player.currentMediaItemIndex } returns 0
            every { player.mediaItemCount } returns 1

            Shuffler.queue(player)

            verify(exactly = 0) { Toaster.s(any<Int>(), any()) }
        }

        @Test
        fun `exception shows error toast`() {
            every { player.currentMediaItemIndex } throws RuntimeException("player error")

            Shuffler.queue(player)

            verify { Toaster.e(R.string.no_song_found) }
        }
    }

    @Nested
    inner class Shuffle {

        @Test
        fun `empty list returns empty`() {
            val result: List<Int> = Shuffler.shuffle(emptyList())

            assertEquals(emptyList<Int>(), result)
        }

        @Test
        fun `single element returns same`() {
            val result = Shuffler.shuffle(listOf(42))

            assertEquals(listOf(42), result)
        }

        @Test
        fun `preserves all elements`() {
            val list = (1..100).toList()

            val result = Shuffler.shuffle(list)

            assertEquals(list.size, result.size)
            assertEquals(list.toSet(), result.toSet())
        }

        @Test
        fun `works with strings`() {
            val list = listOf("a", "b", "c", "d", "e")

            val result = Shuffler.shuffle(list)

            assertEquals(list.toSet(), result.toSet())
        }

        @Test
        fun `works with media items`() {
            val list = mediaItems(10)

            val result = Shuffler.shuffle(list)

            assertEquals(list.size, result.size)
            assertEquals(list.map { it.mediaId }.toSet(), result.map { it.mediaId }.toSet())
        }

        @Test
        fun `does not mutate original`() {
            val list = mutableListOf(1, 2, 3, 4, 5)
            val copy = list.toList()

            Shuffler.shuffle(list)

            assertEquals(copy, list)
        }
    }
}
