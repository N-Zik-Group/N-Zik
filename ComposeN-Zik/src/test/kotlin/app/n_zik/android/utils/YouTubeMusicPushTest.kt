package app.n_zik.android.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeMusicPushTest {
    @Test
    fun pushLikesRequireAPositiveLikeOnAYoutubeVideo() {
        assertTrue(isYouTubeSyncLike("aBcDeFg_-12", 1L))
        assertFalse(isYouTubeSyncLike("aBcDeFg_-12", null))
        assertFalse(isYouTubeSyncLike("aBcDeFg_-12", -1L))
        assertFalse(isYouTubeSyncLike("aBcDeFg_-12", 0L))
        assertFalse(isYouTubeSyncLike("local:12345", 1L))
        assertFalse(isYouTubeSyncLike("a/cDeFg_-12", 1L))
    }

    @Test
    fun mixedPlaylistRemainsLocalInsteadOfLinkingAFilteredCopy() {
        assertTrue(canPushYouTubePlaylistSongs(emptyList()))
        assertTrue(canPushYouTubePlaylistSongs(listOf("aBcDeFg_-12")))
        assertFalse(canPushYouTubePlaylistSongs(listOf("aBcDeFg_-12", "local:12345")))
    }
}
