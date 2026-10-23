package app.n_zik.android.components.player

/**
 * Issue #881 (gh-881), Phase 3 Fix E: tolerance (ms) around the seek target within which the
 * player's committed position counts as "caught up". ExoPlayer commits the seek at the sample
 * nearest to the target, so an exact match is never guaranteed.
 */
internal const val PENDING_SEEK_SETTLE_TOLERANCE_MS = 500L

/**
 * Issue #881 (gh-881), Phase 3 Fix E: safety timeout (ms) after which the held seek target is
 * released even if the player never commits (failed seek, error state) so the bar returns to
 * the real position instead of staying stuck on the tapped value.
 */
internal const val PENDING_SEEK_RELEASE_TIMEOUT_MS = 10_000L

/**
 * Issue #881 (gh-881), Phase 3 Fix E: polling interval (ms) for the settle check.
 */
internal const val PENDING_SEEK_POLL_INTERVAL_MS = 50L

/**
 * Issue #881 (gh-881), Phase 3 Fix E: whether the bar/label may stop holding the tapped seek
 * target and fall back to the player's position.
 *
 * The player keeps reporting the PRE-seek position until the seek commits (out-of-window
 * seeks: 80–760 ms measured on device), so the bar used to flash back on every tap. The
 * target is released when the position converges within [PENDING_SEEK_SETTLE_TOLERANCE_MS],
 * or after [PENDING_SEEK_RELEASE_TIMEOUT_MS] as a safety net. Lifted out of the composable
 * so the release decision is unit-testable (same pattern as [resolutionOutcomeLevel] in
 * StreamResolver).
 *
 * @param pendingTargetMs The position the user tapped / the skip button computed.
 * @param playerPositionMs The player's currently committed position.
 * @param heldForMs How long the UI has been holding [pendingTargetMs].
 */
internal fun shouldReleasePendingSeekPosition(
    pendingTargetMs: Long,
    playerPositionMs: Long,
    heldForMs: Long,
): Boolean =
    heldForMs >= PENDING_SEEK_RELEASE_TIMEOUT_MS ||
        kotlin.math.abs(playerPositionMs - pendingTargetMs) <= PENDING_SEEK_SETTLE_TOLERANCE_MS

/**
 * Issue #881 (gh-881), Phase 3.1: the position a skip button (±5/10/30 s) adjusts from,
 * evaluated at TAP time.
 *
 * Field logs 2026-10-04 (FURY): after a track change while the screen was off, every skip
 * tap computed from the same frozen value (the previous track's pre-screen-off position)
 * while the player had long moved on — the composed `position` was stale because the
 * player UI stopped recomposing. The base is now read live: the in-flight drag, else a
 * still-pending seek target (consecutive taps chain from it before the player commits),
 * else the player's live position.
 *
 * @param scrubbingMs The in-progress drag position, if any.
 * @param pendingTargetMs The last issued seek target held on the bar, if any.
 * @param livePlayerPositionMs `player.currentPosition` read at tap time.
 */
internal fun skipBasePosition(
    scrubbingMs: Long?,
    pendingTargetMs: Long?,
    pendingHeldForMs: Long,
    livePlayerPositionMs: Long,
): Long {
    scrubbingMs?.let { return it }
    if (pendingTargetMs != null &&
        !shouldReleasePendingSeekPosition(pendingTargetMs, livePlayerPositionMs, pendingHeldForMs)
    ) {
        return pendingTargetMs
    }
    return livePlayerPositionMs
}
