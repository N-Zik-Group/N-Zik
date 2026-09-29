package app.n_zik.android.listentogether

import androidx.compose.runtime.Composable

/**
 * Alpha of a playback control locked for a Listen Together guest
 * (spec-listen-together-guest-lock-hardening): locked controls are non-interactive
 * AND visibly disabled (grayed out), not a silent no-op.
 */
const val LISTEN_TOGETHER_GUEST_LOCKED_ALPHA = 0.4f

/**
 * Disabled tint for a guest-locked control: fully opaque unless the lock is on.
 * Shared by every player UI file that renders a locked control (seek bar, skip,
 * repeat, queue editing).
 */
fun guestLockedAlpha(locked: Boolean): Float =
    if (locked) LISTEN_TOGETHER_GUEST_LOCKED_ALPHA else 1f

/**
 * Reads [listenTogetherGuestLock] in a composable, registering a recomposition on
 * every change. Shared by the player UI files that gate playback controls
 * (seek, skip, repeat, speed, queue, swipes) so the lock is read in one place.
 */
@Composable
fun rememberListenTogetherGuestLock(): Boolean = listenTogetherGuestLock.value

/**
 * Predicate (spec-listen-together-guest-lock-hardening, matrix row
 * MINIPLAYER_DISMISS_GUEST): whether the player sheet's dismiss swipe must be
 * disabled. The dismiss gesture runs a destructive onDismiss — it clears the
 * queue (synced from the host), stops the radio and stops the player service —
 * so while a Listen Together guest is locked the gesture itself is disabled,
 * independent of the "disable closing player swiping down" setting.
 */
fun shouldDisablePlayerSheetDismiss(settingDisabled: Boolean, guestLocked: Boolean): Boolean =
    settingDisabled || guestLocked
