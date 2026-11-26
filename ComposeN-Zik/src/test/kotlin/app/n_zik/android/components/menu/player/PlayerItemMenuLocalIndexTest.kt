package app.n_zik.android.components.menu.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the local-song slot layout of [PlayerItemMenu] - the pinning tests were
 * deleted when the FFmpeg gate was removed (spec-ffmpeg-extension-module).
 */
class PlayerItemMenuLocalIndexTest {

    @Test
    fun `local playback starts right after the editMetadata slot`() {
        // 0=info, 1=waves, 2=editMetadata, then playback from 3
        assertEquals(2, LOCAL_PLAYER_PLAYBACK_START - 1)
        assertEquals(3, LOCAL_PLAYER_PLAYBACK_START)
    }

    @Test
    fun `local playback ends before the two navigation buttons`() {
        // 3 fixed slots before playback, 4 playback buttons, then album and artist.
        assertEquals(7, LOCAL_PLAYER_PLAYBACK_START + LOCAL_PLAYER_PLAYBACK_COUNT)
    }
}
