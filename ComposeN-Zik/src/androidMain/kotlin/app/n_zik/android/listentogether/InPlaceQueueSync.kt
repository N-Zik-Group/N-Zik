package app.n_zik.android.listentogether

/**
 * In-place queue sync plan: how to bring the player's loaded queue in line with the host
 * queue without a full timeline replace.
 */
internal data class InPlaceQueueSyncPlan(
    /** Index of the target item in the player queue, after any append. */
    val targetIndex: Int,
    /** Number of host items to append when the target is not loaded yet (0 otherwise). */
    val appendCount: Int,
    /** Loaded items to drop from the head after the seek (the played ones). */
    val headToRemove: Int,
    /** True when the loaded tail already matches the host tail after the target. */
    val tailMatches: Boolean,
)

/**
 * Computes the [InPlaceQueueSyncPlan] for syncing [hostQueueIds] onto a player that has
 * [loadedIds] loaded. The target is the first host id. Returns null when the host queue is
 * empty (the caller falls back to a full replace/clear).
 */
internal fun computeInPlaceQueueSyncPlan(
    loadedIds: List<String>,
    hostQueueIds: List<String>,
): InPlaceQueueSyncPlan? {
    if (hostQueueIds.isEmpty()) return null
    val target = hostQueueIds.first()
    val loadedIndex = loadedIds.indexOf(target)
    return if (loadedIndex >= 0) {
        val loadedTail = loadedIds.subList(loadedIndex + 1, loadedIds.size)
        val hostTail = hostQueueIds.subList(1, hostQueueIds.size)
        val tailMatches = loadedTail.size == hostTail.size &&
            loadedTail.zip(hostTail).all { (a, b) -> a == b }
        InPlaceQueueSyncPlan(
            targetIndex = loadedIndex,
            appendCount = 0,
            headToRemove = loadedIndex,
            tailMatches = tailMatches,
        )
    } else {
        InPlaceQueueSyncPlan(
            targetIndex = loadedIds.size,
            appendCount = hostQueueIds.size,
            headToRemove = loadedIds.size,
            tailMatches = true,
        )
    }
}

/**
 * Indices of the loaded items to drop when replacing the queue tail after [targetIndex] with the
 * host tail. Never includes [targetIndex] or any index before it: the (possibly still pending)
 * window seek to the target must survive the tail replacement — Media3 cancels a window
 * transition when one of the windows it crosses is removed (bug 2026-09-26: synchronous
 * head/tail removal around a pending seek left the player IDLE with a dead timeline).
 */
internal fun tailRemovalIndices(loadedCount: Int, targetIndex: Int): IntRange =
    (targetIndex + 1)..(loadedCount - 1)

/**
 * True when the played head can be trimmed synchronously right after seeking to
 * [targetIndex] — i.e. no window transition is in flight because the target is already the
 * current item. Otherwise the head (which includes the item that was playing) must only be
 * removed once the transition to the target has completed, or the transition is cancelled.
 */
internal fun canTrimHeadImmediately(targetIndex: Int, currentMediaItemIndex: Int): Boolean =
    targetIndex == currentMediaItemIndex
