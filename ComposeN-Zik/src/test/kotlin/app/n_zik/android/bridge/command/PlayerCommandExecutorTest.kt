package app.n_zik.android.bridge.command

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.n_zik.android.bridge.AddPosition
import app.n_zik.android.bridge.AudioOutput
import app.n_zik.android.bridge.state.BridgeServerMessage
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.bridge.state.ErrorMessage
import app.n_zik.android.bridge.state.PlayerSample
import app.n_zik.android.bridge.state.RepeatModeDto
import app.n_zik.android.bridge.state.playingSample
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerCommandExecutorTest {

    private val ids = listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc")

    /** Relaxed player over [ids] whose shuffle order is [shuffleOrder] (window indexes). */
    private fun player(shuffle: Boolean = false, shuffleOrder: List<Int> = ids.indices.toList()): Player {
        val timeline = mockk<Timeline>()
        every { timeline.windowCount } returns ids.size
        every { timeline.getFirstWindowIndex(any()) } answers { if (firstArg()) shuffleOrder.first() else 0 }
        every { timeline.getNextWindowIndex(any(), any(), any()) } answers {
            val order = if (thirdArg()) shuffleOrder else ids.indices.toList()
            order.getOrNull(order.indexOf(firstArg<Int>()) + 1) ?: C.INDEX_UNSET
        }
        return mockk<Player>(relaxed = true) {
            every { currentTimeline } returns timeline
            every { shuffleModeEnabled } returns shuffle
            every { mediaItemCount } returns ids.size
            every { getMediaItemAt(any()) } answers { MediaItem.Builder().setMediaId(ids[firstArg()]).build() }
            every { playbackParameters } returns PlaybackParameters(1f, 1.2f)
        }
    }

    /** Runs blocks directly and publishes [nextSample] (if any) after each command, like the real source. */
    private class FakeAccess(var player: Player?, private val hub: BridgeStateHub) : PlayerAccess {
        var nextSample: PlayerSample? = null
        var applied = 0

        override suspend fun <T> read(block: (Player?) -> T): T = block(player)

        override suspend fun <T> applyAndSample(block: (Player?) -> T): Sampled<T> {
            applied++
            val result = block(player)
            val deltas = nextSample?.let { hub.submit(it) } ?: emptyList()
            return Sampled(result, deltas, hub.currentRevision)
        }
    }

    private class FakeSettings : PlayerSettings {
        var savedSpeed: Float? = null
        override fun jumpPreviousSeconds(): Int = 3
        override fun saveSpeed(speed: Float) {
            savedSpeed = speed
        }
    }

    private class Fixture(
        player: Player?,
        locked: Boolean = false,
        library: Set<String> = emptySet(),
        outputs: AudioOutputSelector? = null,
    ) {
        val hub = BridgeStateHub()
        val access = FakeAccess(player, hub)
        val settings = FakeSettings()
        val lateFailures = LateFailureTracker(hub)
        val executor = PlayerCommandExecutor(
            player = access,
            hub = hub,
            lateFailures = lateFailures,
            settings = settings,
            tracks = { trackIds -> if (library.containsAll(trackIds)) trackIds.map { MediaItem.Builder().setMediaId(it).build() } else null },
            guestLocked = { locked },
            outputs = outputs,
        )
    }

    @Test
    fun `jump with shuffle starts the track at that index of the published order`() = runTest {
        // Shuffle order [2, 0, 1]: published queue is c, a, b
        val p = player(shuffle = true, shuffleOrder = listOf(2, 0, 1))
        val fixture = Fixture(p)

        val result = fixture.executor.execute(BridgeCommand(PlayerAction.QueueJump(1, "aaaaaaaaaaa")))

        assertTrue(result is CommandResult.Applied)
        verify { p.seekTo(0, 0L) }
        verify { p.playWhenReady = true }
    }

    @Test
    fun `stale index or trackId is a QUEUE_MISMATCH with the current revision and nothing applied`() = runTest {
        val p = player(shuffle = true, shuffleOrder = listOf(2, 0, 1))
        val fixture = Fixture(p)
        fixture.hub.submit(playingSample)

        val wrongTrack = fixture.executor.execute(BridgeCommand(PlayerAction.QueueJump(0, "aaaaaaaaaaa")))
        val outOfBounds = fixture.executor.execute(BridgeCommand(PlayerAction.QueueRemove(3, "aaaaaaaaaaa")))

        assertEquals(CommandResult.QueueMismatch(fixture.hub.currentRevision), wrongTrack)
        assertEquals(CommandResult.QueueMismatch(fixture.hub.currentRevision), outOfBounds)
        verify(exactly = 0) { p.seekTo(any(), any()) }
        verify(exactly = 0) { p.removeMediaItem(any()) }
    }

    @Test
    fun `remove converts the effective index to the window index`() = runTest {
        val p = player(shuffle = true, shuffleOrder = listOf(2, 0, 1))
        val fixture = Fixture(p)

        fixture.executor.execute(BridgeCommand(PlayerAction.QueueRemove(2, "bbbbbbbbbbb")))

        verify { p.removeMediaItem(1) }
    }

    @Test
    fun `guest lock rejects guarded commands without touching the player but lets play and pause through`() = runTest {
        val p = player()
        val fixture = Fixture(p, locked = true)

        assertEquals(CommandResult.Rejected, fixture.executor.execute(BridgeCommand(PlayerAction.Seek(1_000L))))
        assertEquals(CommandResult.Rejected, fixture.executor.execute(BridgeCommand(PlayerAction.Next)))
        verify(exactly = 0) { p.seekTo(any()) }
        verify(exactly = 0) { p.seekToNextMediaItem() }

        assertTrue(fixture.executor.execute(BridgeCommand(PlayerAction.Pause)) is CommandResult.Applied)
        verify { p.pause() }
    }

    @Test
    fun `move with shuffle enabled is rejected`() = runTest {
        val p = player(shuffle = true, shuffleOrder = listOf(2, 0, 1))
        val fixture = Fixture(p)

        assertEquals(CommandResult.Rejected, fixture.executor.execute(BridgeCommand(PlayerAction.QueueMove(0, 1, "ccccccccccc"))))
        verify(exactly = 0) { p.moveMediaItem(any(), any()) }
    }

    @Test
    fun `no player service is PLAYER_UNAVAILABLE`() = runTest {
        val fixture = Fixture(player = null)

        assertEquals(CommandResult.Unavailable, fixture.executor.execute(BridgeCommand(PlayerAction.Play)))
    }

    @Test
    fun `a track missing from the library is NOT_FOUND and nothing is applied`() = runTest {
        val p = player()
        val fixture = Fixture(p, library = setOf("aaaaaaaaaaa"))

        val result = fixture.executor.execute(BridgeCommand(PlayerAction.QueuePlay(listOf("aaaaaaaaaaa", "zzzzzzzzzzz"), 0, 0L)))

        assertEquals(CommandResult.NotFound, result)
        assertEquals(0, fixture.access.applied)
        verify(exactly = 0) { p.setMediaItems(any(), any(), any()) }
    }

    @Test
    fun `queue play replaces the queue and starts at startIndex and positionMs`() = runTest {
        val p = player()
        val fixture = Fixture(p, library = setOf("aaaaaaaaaaa", "bbbbbbbbbbb"))

        val result = fixture.executor.execute(BridgeCommand(PlayerAction.QueuePlay(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), 1, 30_000L)))

        assertTrue(result is CommandResult.Applied)
        verify { p.setMediaItems(match<List<MediaItem>> { items -> items.map { it.mediaId } == listOf("aaaaaaaaaaa", "bbbbbbbbbbb") }, 1, 30_000L) }
        verify { p.prepare() }
        verify { p.playWhenReady = true }
    }

    @Test
    fun `changed and revision come from the deltas of the forced sample`() = runTest {
        val fixture = Fixture(player())
        fixture.hub.submit(playingSample)
        val before = fixture.hub.currentRevision

        fixture.access.nextSample = playingSample.copy(isPlaying = false)
        val pause = fixture.executor.execute(BridgeCommand(PlayerAction.Pause))
        assertEquals(CommandResult.Applied(changed = true, revision = before + 1), pause)

        val again = fixture.executor.execute(BridgeCommand(PlayerAction.Pause))
        assertEquals(CommandResult.Applied(changed = false, revision = before + 1), again)
    }

    @Test
    fun `play still buffering announces the next revision`() = runTest {
        val p = player()
        every { p.playWhenReady } returns true
        every { p.isPlaying } returns false
        every { p.playbackState } returns Player.STATE_BUFFERING
        val fixture = Fixture(p)
        val current = fixture.hub.currentRevision

        val result = fixture.executor.execute(BridgeCommand(PlayerAction.Play))

        assertEquals(CommandResult.Applied(changed = true, revision = current + 1), result)
    }

    @Test
    fun `speed keeps the pitch and is persisted like the phone menu`() = runTest {
        val p = player()
        val fixture = Fixture(p)

        fixture.executor.execute(BridgeCommand(PlayerAction.Speed(1.5f)))

        verify { p.playbackParameters = PlaybackParameters(1.5f, 1.2f) }
        assertEquals(1.5f, fixture.settings.savedSpeed)
    }

    @Test
    fun `seek past the duration is bounded to the end and an empty queue is a mismatch`() = runTest {
        val p = player()
        every { p.duration } returns 200_000L
        val fixture = Fixture(p)

        fixture.executor.execute(BridgeCommand(PlayerAction.Seek(999_000L)))
        verify { p.seekTo(200_000L) }

        every { p.mediaItemCount } returns 0
        assertTrue(fixture.executor.execute(BridgeCommand(PlayerAction.Seek(0L))) is CommandResult.QueueMismatch)
    }

    @Test
    fun `player error after a track load is broadcast once with the commandId and no revision`() {
        val hub = BridgeStateHub()
        var now = 1_000L
        val tracker = LateFailureTracker(hub, clock = { now })
        val received = mutableListOf<BridgeServerMessage>()
        hub.subscribe { received += it }
        received.clear()

        tracker.trackLoad("cmd-7")
        now += 5_000L
        tracker.onPlayerError("ERROR_CODE_IO_BAD_HTTP_STATUS")
        tracker.onPlayerError("ERROR_CODE_IO_BAD_HTTP_STATUS")
        tracker.trackLoad("cmd-8")
        now += 11_000L
        tracker.onPlayerError("ERROR_CODE_IO_BAD_HTTP_STATUS")

        assertEquals(1, received.size)
        val error = received.single() as ErrorMessage
        assertEquals("PLAYER_REJECTED", error.code)
        assertEquals("cmd-7", error.commandId)
        assertEquals(0L, hub.currentRevision)
    }

    @Test
    fun `queue add next on a running player inserts after the current track`() = runTest {
        val p = player()
        val fixture = Fixture(p, library = setOf("ddddddddddd"))

        val result = fixture.executor.execute(BridgeCommand(PlayerAction.QueueAdd(listOf("ddddddddddd"), AddPosition.NEXT)))

        assertTrue(result is CommandResult.Applied)
        verify { p.addMediaItems(1, match<List<MediaItem>> { items -> items.map { it.mediaId } == listOf("ddddddddddd") }) }
    }

    @Test
    fun `queue add end moves an already queued current track instead of removing it`() = runTest {
        val p = player()
        val fixture = Fixture(p, library = setOf("aaaaaaaaaaa", "ddddddddddd"))

        fixture.executor.execute(BridgeCommand(PlayerAction.QueueAdd(listOf("aaaaaaaaaaa", "ddddddddddd"), AddPosition.END)))

        verify { p.moveMediaItem(0, 2) }
        verify { p.addMediaItem(3, match { it.mediaId == "ddddddddddd" }) }
        verify(exactly = 0) { p.removeMediaItem(any()) }
    }

    @Test
    fun `queue add on a stopped player announces the next revision`() = runTest {
        val p = player()
        every { p.playbackState } returns Player.STATE_IDLE
        val fixture = Fixture(p, library = setOf("ddddddddddd"))
        val current = fixture.hub.currentRevision

        val result = fixture.executor.execute(BridgeCommand(PlayerAction.QueueAdd(listOf("ddddddddddd"), AddPosition.END)))

        assertEquals(CommandResult.Applied(changed = true, revision = current + 1), result)
    }

    @Test
    fun `previous restarts the track above the threshold and goes back below it`() = runTest {
        val p = player()
        every { p.hasPreviousMediaItem() } returns true
        val fixture = Fixture(p)

        every { p.currentPosition } returns 5_000L
        fixture.executor.execute(BridgeCommand(PlayerAction.Previous))
        verify(exactly = 1) { p.seekTo(0L) }
        verify(exactly = 0) { p.seekToPreviousMediaItem() }

        every { p.currentPosition } returns 1_000L
        fixture.executor.execute(BridgeCommand(PlayerAction.Previous))
        verify(exactly = 1) { p.seekToPreviousMediaItem() }
        verify(exactly = 1) { p.seekTo(0L) }
    }

    @Test
    fun `previous without a previous item restarts the track`() = runTest {
        val p = player()
        every { p.hasPreviousMediaItem() } returns false
        every { p.currentPosition } returns 1_000L
        val fixture = Fixture(p)

        fixture.executor.execute(BridgeCommand(PlayerAction.Previous))

        verify { p.seekTo(0L) }
        verify(exactly = 0) { p.seekToPreviousMediaItem() }
    }

    @Test
    fun `move applies in the natural order and a target out of bounds is a mismatch`() = runTest {
        val p = player()
        val fixture = Fixture(p)

        assertTrue(fixture.executor.execute(BridgeCommand(PlayerAction.QueueMove(0, 2, "aaaaaaaaaaa"))) is CommandResult.Applied)
        verify { p.moveMediaItem(0, 2) }

        val outOfBounds = fixture.executor.execute(BridgeCommand(PlayerAction.QueueMove(0, 3, "aaaaaaaaaaa")))
        assertEquals(CommandResult.QueueMismatch(fixture.hub.currentRevision), outOfBounds)
        verify(exactly = 1) { p.moveMediaItem(any(), any()) }
    }

    @Test
    fun `repeat modes map to the player constants`() = runTest {
        val p = player()
        val fixture = Fixture(p)

        fixture.executor.execute(BridgeCommand(PlayerAction.Repeat(RepeatModeDto.ONE)))
        fixture.executor.execute(BridgeCommand(PlayerAction.Repeat(RepeatModeDto.ALL)))

        verify { p.repeatMode = Player.REPEAT_MODE_ONE }
        verify { p.repeatMode = Player.REPEAT_MODE_ALL }
    }

    @Test
    fun `clear empties the queue`() = runTest {
        val p = player()
        val fixture = Fixture(p)

        fixture.executor.execute(BridgeCommand(PlayerAction.QueueClear))

        verify { p.stop() }
        verify { p.clearMediaItems() }
    }

    @Test
    fun `play from idle prepares the player`() = runTest {
        val p = player()
        every { p.playbackState } returns Player.STATE_IDLE
        val fixture = Fixture(p)

        fixture.executor.execute(BridgeCommand(PlayerAction.Play))

        verify { p.prepare() }
        verify { p.play() }
    }

    private fun Fixture.errors(): MutableList<ErrorMessage> {
        val received = mutableListOf<ErrorMessage>()
        hub.subscribe { message -> if (message is ErrorMessage) received += message }
        return received
    }

    @Test
    fun `player error after queue play reports its commandId`() = runTest {
        val fixture = Fixture(player(), library = setOf("aaaaaaaaaaa"))
        val errors = fixture.errors()

        fixture.executor.execute(BridgeCommand(PlayerAction.QueuePlay(listOf("aaaaaaaaaaa"), 0, 0L), commandId = "cmd-x"))
        fixture.lateFailures.onPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED")

        assertEquals(listOf("cmd-x"), errors.map { it.commandId })
    }

    @Test
    fun `player error after a buffering play reports its commandId`() = runTest {
        val p = player()
        every { p.playWhenReady } returns true
        every { p.isPlaying } returns false
        every { p.playbackState } returns Player.STATE_BUFFERING
        val fixture = Fixture(p)
        val errors = fixture.errors()

        fixture.executor.execute(BridgeCommand(PlayerAction.Play, commandId = "cmd-play"))
        fixture.lateFailures.onPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED")

        assertEquals(listOf("cmd-play"), errors.map { it.commandId })
    }

    @Test
    fun `output goes to the output control, never to the player, even under the guest lock`() = runTest {
        val p = player()
        val asked = mutableListOf<AudioOutput>()
        val fixture = Fixture(p, locked = true, outputs = { output ->
            asked += output
            CommandResult.Applied(changed = true, revision = 7L)
        })

        val result = fixture.executor.execute(BridgeCommand(PlayerAction.Output(AudioOutput.PC)))

        assertEquals(CommandResult.Applied(changed = true, revision = 7L), result)
        assertEquals(listOf(AudioOutput.PC), asked)
        assertEquals(0, fixture.access.applied)
        verify(exactly = 0) { p.pause() }
        verify(exactly = 0) { p.volume = any() }
    }

    @Test
    fun `without an output control pc is rejected and phone changes nothing`() = runTest {
        val fixture = Fixture(player())
        fixture.hub.submit(playingSample)

        assertEquals(CommandResult.Rejected, fixture.executor.execute(BridgeCommand(PlayerAction.Output(AudioOutput.PC))))
        assertEquals(
            CommandResult.Applied(changed = false, revision = fixture.hub.currentRevision),
            fixture.executor.execute(BridgeCommand(PlayerAction.Output(AudioOutput.PHONE))),
        )
    }
}
