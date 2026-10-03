package app.n_zik.android.playback.services.diagnostics

import androidx.media3.exoplayer.ExoPlayer
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

/**
 * Issue #881 (gh-881): the [DiagExoPlayer] wrapper must be a PURE delegation (spec S1 — zero
 * behavior change): every one of its ten seek overrides forwards its arguments to the delegate
 * unchanged, and the `SEEK_CALL` journal excludes the wrapper's own frames so it starts at the
 * real caller (not at `DiagExoPlayer.logSeek`).
 */
class DiagExoPlayerDelegationTest {

    private val delegate = mockk<ExoPlayer>(relaxed = true)
    private val wrapper = DiagExoPlayer(delegate)

    private val captured = mutableListOf<String>()

    private val captureTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            captured += message
        }
    }

    @BeforeEach
    fun setUp() {
        captured.clear()
        Timber.plant(captureTree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(captureTree)
    }

    @Test
    fun `seekTo(positionMs) delegates with the same argument`() {
        wrapper.seekTo(1234L)
        verify { delegate.seekTo(1234L) }
    }

    @Test
    fun `seekTo(index, positionMs) delegates with the same arguments`() {
        wrapper.seekTo(3, 42L)
        verify { delegate.seekTo(3, 42L) }
    }

    @Test
    fun `seekToDefaultPosition delegates`() {
        wrapper.seekToDefaultPosition()
        verify { delegate.seekToDefaultPosition() }
    }

    @Test
    fun `seekToDefaultPosition(index) delegates with the same argument`() {
        wrapper.seekToDefaultPosition(2)
        verify { delegate.seekToDefaultPosition(2) }
    }

    @Test
    fun `seekBack delegates`() {
        wrapper.seekBack()
        verify { delegate.seekBack() }
    }

    @Test
    fun `seekForward delegates`() {
        wrapper.seekForward()
        verify { delegate.seekForward() }
    }

    @Test
    fun `seekToPrevious delegates`() {
        wrapper.seekToPrevious()
        verify { delegate.seekToPrevious() }
    }

    @Test
    fun `seekToNext delegates`() {
        wrapper.seekToNext()
        verify { delegate.seekToNext() }
    }

    @Test
    fun `seekToPreviousMediaItem delegates`() {
        wrapper.seekToPreviousMediaItem()
        verify { delegate.seekToPreviousMediaItem() }
    }

    @Test
    fun `seekToNextMediaItem delegates`() {
        wrapper.seekToNextMediaItem()
        verify { delegate.seekToNextMediaItem() }
    }

    @Test
    fun `the seek journal excludes the wrapper frames and starts at the calling frame`() {
        wrapper.seekTo(42L)

        val line = captured.single { it.startsWith("SEEK_CALL") }
        val stack = line.substringAfter("stack=")

        assertFalse(
            stack.contains("DiagExoPlayer."),
            "wrapper frames must be excluded from the journal: $stack",
        )
        assertTrue(
            stack.startsWith("app.n_zik.android.playback.services.diagnostics.DiagExoPlayerDelegationTest."),
            "the nearest app frame must be the calling frame: $stack",
        )
    }
}
