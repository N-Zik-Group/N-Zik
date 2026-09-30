package app.n_zik.android.bridge.state

/**
 * Revisioned player state of one server run (contract §7). Pure: it only knows
 * [PlayerSample]s and an injectable [clock]. Each new sample is compared to the previous
 * one and turns into the deltas of §7.2, in the order `queueChanged`, `trackChanged`,
 * `playbackChanged`, `modesChanged`, each at `revision + 1`. The revision starts at `0`
 * with every new hub, i.e. with every server start (contract §1).
 *
 * Deltas, snapshots and heartbeats reach the subscribers under a single lock, so a
 * subscriber always sees its snapshot at revision `r` followed by the deltas `r + 1`,
 * `r + 2`… and a heartbeat never overtakes the delta it reports. [Sink]s must not block.
 */
internal class BridgeStateHub(private val clock: () -> Long = System::currentTimeMillis) {

    /** Receives messages under the hub lock: must only enqueue, never block. */
    fun interface Sink {
        fun deliver(message: BridgeServerMessage)
    }

    private val lock = Any()
    private var revision = 0L
    private var current = PlayerSample.EMPTY
    private val subscribers = LinkedHashSet<Subscription>()

    val currentRevision: Long get() = synchronized(lock) { revision }

    /**
     * Records [sample] and publishes the deltas it implies. The position alone never makes
     * a delta: only an explicit [seek] does (`playbackChanged`). [transition] reports a track
     * transition that leaves the current track unchanged (repeat-one), still a `trackChanged`.
     *
     * @return the deltas published, in order
     */
    fun submit(sample: PlayerSample, seek: Boolean = false, transition: Boolean = false): List<DeltaMessage> =
        synchronized(lock) {
            val previous = current
            current = sample
            val now = clock()
            val position = positionAt(sample, now)
            val deltas = mutableListOf<DeltaMessage>()

            val queueChanged = sample.queue != previous.queue
            if (queueChanged) {
                deltas += QueueChangedMessage(++revision, now, sample.queue, sample.currentIndex, sample.currentTrackId)
            }
            // An index shift caused by a queue edit around the same track is carried by queueChanged
            val trackChanged = sample.currentTrackId != previous.currentTrackId ||
                (!queueChanged && sample.currentIndex != previous.currentIndex) ||
                transition
            if (trackChanged) {
                deltas += TrackChangedMessage(++revision, now, sample.currentIndex, sample.currentTrackId, position, sample.isPlaying)
            }
            // trackChanged already carries the position and the playing state
            val playbackChanged = sample.speed != previous.speed ||
                (!trackChanged && (sample.isPlaying != previous.isPlaying || seek))
            if (playbackChanged) {
                deltas += PlaybackChangedMessage(++revision, now, sample.isPlaying, sample.speed, position)
            }
            if (sample.repeatMode != previous.repeatMode || sample.shuffle != previous.shuffle) {
                deltas += ModesChangedMessage(++revision, now, sample.repeatMode, sample.shuffle)
            }
            deltas.forEach { delta -> subscribers.forEach { it.sink.deliver(delta) } }
            deltas
        }

    /** Full state at the current revision, position at `serverTimeMs` (contract §7.1). */
    fun snapshot(): SnapshotMessage = synchronized(lock) { buildSnapshot() }

    /** Non-revised heartbeat at the current revision (contract §7.3). */
    fun heartbeat(): HeartbeatMessage = synchronized(lock) { buildHeartbeat() }

    /** Registers [sink] and delivers the current snapshot to it first (contract §6.1). */
    fun subscribe(sink: Sink): Subscription = synchronized(lock) {
        Subscription(sink).also {
            sink.deliver(buildSnapshot())
            subscribers += it
        }
    }

    private fun buildSnapshot(): SnapshotMessage {
        val now = clock()
        val sample = current
        return SnapshotMessage(
            revision = revision,
            serverTimeMs = now,
            queue = sample.queue,
            currentIndex = sample.currentIndex,
            currentTrackId = sample.currentTrackId,
            isPlaying = sample.isPlaying,
            speed = sample.speed,
            positionMs = positionAt(sample, now),
            repeatMode = sample.repeatMode,
            shuffle = sample.shuffle,
        )
    }

    private fun buildHeartbeat(): HeartbeatMessage {
        val now = clock()
        val sample = current
        return HeartbeatMessage(revision, now, positionAt(sample, now), sample.isPlaying, sample.speed)
    }

    /** One session's registration; every call is a no-op once [cancel]led. */
    inner class Subscription internal constructor(internal val sink: Sink) {
        fun sendSnapshot() {
            synchronized(lock) { if (this in subscribers) sink.deliver(buildSnapshot()) }
        }

        fun sendHeartbeat() {
            synchronized(lock) { if (this in subscribers) sink.deliver(buildHeartbeat()) }
        }

        fun cancel() {
            synchronized(lock) { subscribers -= this }
        }
    }

    companion object {
        /**
         * Position of [sample] extrapolated to [nowMs]: `positionMs + elapsed × speed` while
         * playing, never past the current track's duration when it is known.
         */
        fun positionAt(sample: PlayerSample, nowMs: Long): Long {
            if (!sample.isPlaying) return sample.positionMs
            val elapsed = (nowMs - sample.sampledAtMs).coerceAtLeast(0L)
            val extrapolated = sample.positionMs + (elapsed * sample.speed).toLong()
            val durationMs = sample.queue.getOrNull(sample.currentIndex)?.durationMs ?: return extrapolated
            return extrapolated.coerceAtMost(maxOf(durationMs, sample.positionMs))
        }
    }
}
