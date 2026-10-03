package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import app.it.fast4x.rimusic.utils.POSITION_POLL_INTERVAL_MS
import app.it.fast4x.rimusic.utils.positionAndDurationState
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #881 (gh-881), Phase 3.1: [positionAndDurationState] polls the live player position on
 * a plain timer (RiPlay model) with no seek latch — the former frame poller stopped writing
 * while an `isSeeking` flag waited for a `STATE_READY` callback, and the field logs show the
 * bar frozen on the previous track after a screen-off track change.
 *
 * No player listener is involved any more, so a seek that never delivers READY (the latch's
 * failure mode) is modelled by simply moving the mocked position: it must reach the state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PositionAndDurationStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val player = mockk<Player>(relaxed = true)
    private var livePosition = 53_795L
    private var liveDuration = 241_661L
    private lateinit var state: State<Pair<Long, Long>>

    @Before
    fun setUp() {
        every { player.currentPosition } answers { livePosition }
        every { player.duration } answers { liveDuration }
    }

    @After
    fun tearDown() = unmockkAll()

    private fun content(active: () -> Boolean = { true }) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            state = player.positionAndDurationState(active = active())
        }
    }

    private fun tick() {
        composeRule.mainClock.advanceTimeBy(POSITION_POLL_INTERVAL_MS * 2)
        composeRule.waitForIdle()
    }

    @Test
    fun `follows the live player position across a track change with no listener callback`() {
        content()
        assertEquals(53_795L to 241_661L, state.value)

        // Track change + seek on the new track: no READY / transition callback is delivered
        // (the old poller's latch would have kept the previous track's 53 795 ms).
        livePosition = 2_976L
        liveDuration = 247_441L
        tick()
        assertEquals(2_976L to 247_441L, state.value)

        livePosition = 56_030L
        tick()
        assertEquals(56_030L to 247_441L, state.value)
    }

    @Test
    fun `does not write while inactive`() {
        content(active = { false })

        livePosition = 99_000L
        tick()

        assertEquals(53_795L to 241_661L, state.value)
    }
}
