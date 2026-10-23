package app.n_zik.android.components.menu.song

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Decision 6 of the PC UI parity audit (2026-10-09): a local song's menu without FFmpeg (the `*32` builds) has no edit-metadata button, so its playback buttons start right after "info". */
class SongItemMenuLocalIndexTest {

    @Test
    fun `playback buttons start after the edit-metadata button only when FFmpeg ships`() {
        // [info, editMetadata, radio, next, enqueue, favorite, addToPlaylist, export]
        assertEquals(2, localSongPlaybackStart(ffmpeg = true))
        // [info, radio, next, enqueue, favorite, addToPlaylist]
        assertEquals(1, localSongPlaybackStart(ffmpeg = false))
    }

    @Test
    fun `without FFmpeg the five playback buttons are all read, the first one is the radio`() {
        val buttons = listOf("info", "radio", "next", "enqueue", "favorite", "addToPlaylist")
        val start = localSongPlaybackStart(ffmpeg = false)
        assertEquals(
            listOf("radio", "next", "enqueue", "favorite", "addToPlaylist"),
            buttons.subList(start, start + LOCAL_PLAYBACK_COUNT),
        )
    }
}
