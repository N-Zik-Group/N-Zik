package app.n_zik.android.components.menu.album

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OnlineAlbumBookmarkChangeTest {
    @Test
    fun dislikeRotationToNeutralHasNoYouTubeMutation() {
        assertNull(onlineAlbumBookmarkChange(likeState = false, dislikesEnabled = true))
        assertEquals(true, onlineAlbumBookmarkChange(likeState = false, dislikesEnabled = false))
        assertEquals(true, onlineAlbumBookmarkChange(likeState = null, dislikesEnabled = true))
        assertEquals(false, onlineAlbumBookmarkChange(likeState = true, dislikesEnabled = true))
    }
}
