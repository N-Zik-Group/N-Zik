package app.n_zik.android.components.menu.song

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the local-song slot layout of [SongItemMenu] - the pinning tests were
 * deleted when the FFmpeg gate was removed (spec-ffmpeg-extension-module).
 */
class SongItemMenuLocalIndexTest {

    @Test
    fun `local playback starts right after the editMetadata slot`() {
        // 0=info, 1=editMetadata, then playback from 2
        assertEquals(1, LOCAL_SONG_PLAYBACK_START - 1)
        assertEquals(2, LOCAL_SONG_PLAYBACK_START)
    }

    @Test
    fun `local playback buttons fit the layout`() {
        // 5 playback buttons (2..6), delete/export from 7
        assertEquals(7, LOCAL_SONG_PLAYBACK_START + LOCAL_PLAYBACK_COUNT)
    }
}
