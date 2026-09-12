package app.n_zik.android.playback.utils

import android.content.Context
import android.content.SharedPreferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.maxSongsInQueueKey
import app.it.fast4x.rimusic.utils.preferences
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShufflerTest {

    private lateinit var binder: PlayerServiceModern.Binder
    private lateinit var player: ExoPlayer
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

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
        unmockkAll()
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
