package app.n_zik.android.components.menu.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Decision 6 (extended to the player menu): a local song without FFmpeg has no edit-metadata button. */
class PlayerItemMenuLocalIndexTest {

    @Test
    fun `playback buttons follow the edit-metadata button only when FFmpeg ships`() {
        val withFfmpeg = listOf("info", "waves", "edit", "eq", "sleep", "fav", "playlist", "albumId", "artistId", "export")
        val start = localPlayerPlaybackStart(ffmpeg = true)
        assertEquals(listOf("eq", "sleep", "fav", "playlist", "albumId", "artistId"), withFfmpeg.subList(start, start + LOCAL_PLAYER_PLAYBACK_COUNT))

        val without = listOf("info", "waves", "eq", "sleep", "fav", "playlist", "albumId", "artistId")
        val start32 = localPlayerPlaybackStart(ffmpeg = false)
        assertEquals(listOf("eq", "sleep", "fav", "playlist", "albumId", "artistId"), without.subList(start32, start32 + LOCAL_PLAYER_PLAYBACK_COUNT))
    }
}
