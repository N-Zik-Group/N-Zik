package app.n_zik.android.playback.services

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioOutputSwitchTest {
    @Test
    fun `the service resumes once after the latest successful manual switch`() = runTest {
        val player = mockk<Player>(relaxed = true)
        val track = MediaItem.Builder().setMediaId("playing-track").build()
        every { player.currentMediaItem } returns track
        var playing = true
        every { player.playWhenReady } answers { playing }
        every { player.pause() } answers { playing = false }
        every { player.play() } answers { playing = true }
        val output = AudioOutputSwitch(this) { player }

        assertTrue(output.apply { true })
        advanceTimeBy(500)
        assertTrue(output.apply { true })
        advanceTimeBy(400)
        assertFalse(output.apply { false })
        advanceTimeBy(500)
        assertFalse(playing)
        advanceTimeBy(100)
        runCurrent()

        assertTrue(playing)
        verify(exactly = 1) { player.pause() }
        verify(exactly = 1) { player.play() }
    }

    @Test
    fun `an already paused track stays paused during a manual switch`() = runTest {
        val player = mockk<Player>(relaxed = true)
        val track = MediaItem.Builder().setMediaId("paused-track").build()
        every { player.currentMediaItem } returns track
        every { player.playWhenReady } returns false
        val output = AudioOutputSwitch(this) { player }

        assertTrue(output.apply { true })
        advanceTimeBy(2_000)
        runCurrent()

        verify(exactly = 0) { player.pause() }
        verify(exactly = 0) { player.play() }
    }
}
