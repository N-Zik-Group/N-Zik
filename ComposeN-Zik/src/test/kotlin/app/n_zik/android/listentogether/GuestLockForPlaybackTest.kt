package app.n_zik.android.listentogether

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuestLockForPlaybackTest {

    @Test
    fun `guest in an active room is locked`() {
        assertTrue(isGuestLockedForPlayback(isInRoom = true, isHost = false))
    }

    @Test
    fun `host in an active room is never locked`() {
        // Host = source of truth: every control stays available
        assertFalse(isGuestLockedForPlayback(isInRoom = true, isHost = true))
    }

    @Test
    fun `user out of a room is never locked`() {
        assertFalse(isGuestLockedForPlayback(isInRoom = false, isHost = false))
        assertFalse(isGuestLockedForPlayback(isInRoom = false, isHost = true))
    }

    @Test
    fun `player sheet dismiss swipe is disabled for a locked guest`() {
        // The dismiss-swipe of the mini player (MainActivity) clears the queue synced
        // from the host and stops the player service: while a guest is locked the
        // gesture itself must be disabled, with or without the
        // "disable closing player swiping down" setting.
        assertFalse(shouldDisablePlayerSheetDismiss(settingDisabled = false, guestLocked = false))
        assertTrue(shouldDisablePlayerSheetDismiss(settingDisabled = true, guestLocked = false))
        assertTrue(shouldDisablePlayerSheetDismiss(settingDisabled = false, guestLocked = true))
        assertTrue(shouldDisablePlayerSheetDismiss(settingDisabled = true, guestLocked = true))
    }

    @Test
    fun `lock predicate matches the manager combine of role and room state`() {
        // The manager derives listenTogetherGuestLock from
        // `state != null && roomRole != RoomRole.HOST` — the pure function must agree
        // for every (roomState, role) pair
        for (roomStatePresent in listOf(false, true)) {
            for (role in listOf(RoomRole.HOST, RoomRole.GUEST, RoomRole.NONE)) {
                val legacy = roomStatePresent && role != RoomRole.HOST
                val pure = isGuestLockedForPlayback(
                    isInRoom = roomStatePresent,
                    isHost = role == RoomRole.HOST,
                )
                assertEquals(
                    legacy,
                    pure,
                    "mismatch for roomState=$roomStatePresent role=$role",
                )
            }
        }
    }
}
