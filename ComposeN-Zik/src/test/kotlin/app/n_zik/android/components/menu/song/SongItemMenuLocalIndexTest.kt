package app.n_zik.android.components.menu.song

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the local-song slot layout of [SongItemMenu] - the pinning tests were
 * deleted when the FFmpeg gate was removed (spec-ffmpeg-extension-module).
 */
class SongItemMenuLocalIndexTest {

    @Test
    fun `local playback starts after the three editing actions`() {
        // 0=info, 1=edit metadata, 2=rename, 3=cover
        assertEquals(3, LOCAL_SONG_PLAYBACK_START - 1)
        assertEquals(4, LOCAL_SONG_PLAYBACK_START)
    }

    @Test
    fun `local playback buttons fit the layout`() {
        // Five playback slots (4..8), followed by delete/export.
        assertEquals(9, LOCAL_SONG_PLAYBACK_START + LOCAL_PLAYBACK_COUNT)
    }
}
