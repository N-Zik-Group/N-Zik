package app.n_zik.android.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistPushPolicyTest {
    @Test
    fun syncDirectionAndPlaylistPreferenceBothGateRemoteAdditions() {
        assertTrue(shouldPushPlaylistChanges(syncEnabled = true, canPush = true, pushPlaylists = true))
        assertFalse(shouldPushPlaylistChanges(syncEnabled = true, canPush = false, pushPlaylists = true))
        assertFalse(shouldPushPlaylistChanges(syncEnabled = true, canPush = true, pushPlaylists = false))
        assertFalse(shouldPushPlaylistChanges(syncEnabled = false, canPush = true, pushPlaylists = true))
    }
}
