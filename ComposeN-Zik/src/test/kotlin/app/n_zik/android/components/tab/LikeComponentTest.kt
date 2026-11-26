package app.n_zik.android.components.tab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LikeComponentTest {
    @Test
    fun `single-track add shortcut is reserved for a disliked song with dislike enabled`() {
        assertTrue(showAddToFavoritesShortcut(likedAt = -1L, dislikesEnabled = true))
        assertFalse(showAddToFavoritesShortcut(likedAt = -1L, dislikesEnabled = false))
        assertFalse(showAddToFavoritesShortcut(likedAt = null, dislikesEnabled = true))
        assertFalse(showAddToFavoritesShortcut(likedAt = null, dislikesEnabled = false))
        assertFalse(showAddToFavoritesShortcut(likedAt = 123L, dislikesEnabled = true))
    }
}
