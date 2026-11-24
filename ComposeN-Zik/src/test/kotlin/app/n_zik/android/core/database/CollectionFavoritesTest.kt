package app.n_zik.android.core.database

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.models.Song

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CollectionFavoritesTest {
    @Test
    fun `a mixed collection adds disliked neutral and unknown tracks once`() {
        val songIds = listOf("liked", "disliked", "neutral", "playlist/unknown", "unknown", "")
            .map(::collectionFavoriteSongId)
            .filter(String::isNotBlank)
            .toSet()
        val likeStates = mapOf("liked" to true, "disliked" to false, "neutral" to null)

        assertEquals(
            CollectionFavoritesChange(true, listOf("disliked", "neutral", "unknown")),
            collectionFavoritesChange(songIds) { likeStates[it] == true },
        )
    }

    @Test
    fun `all liked items can be removed and an empty collection offers no action`() {
        val likeStates = mapOf("one" to true, "two" to true)

        assertEquals(
            CollectionFavoritesChange(false, listOf("one", "two")),
            collectionFavoritesChange(likeStates.keys) { likeStates[it] == true },
        )
        assertNull(collectionFavoritesChange(emptySet()) { likeStates[it] == true })
    }

    @Test
    fun `confirmation updates Room once and keeps the downloads when favorites are removed`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java).build()
        mockkObject(DatabaseInitializer.Companion, MyDownloadHelper)
        every { DatabaseInitializer.Instance } returns database
        justRun { MyDownloadHelper.downloadNewlyLiked(any(), any()) }
        try {
            val items = listOf("liked", "disliked", "neutral").associateWith { MediaItem.Builder().setMediaId(it).build() }
            database.songTable.upsert(Song.makePlaceholder("liked").copy(likedAt = 42L))
            database.songTable.upsert(Song.makePlaceholder("disliked").copy(likedAt = -1L))
            database.songTable.upsert(Song.makePlaceholder("neutral"))

            val add = checkNotNull(CollectionFavorites.resolve(context, items.keys))
            assertEquals(CollectionFavoritesChange(true, listOf("disliked", "neutral")), add.change)
            database.songTable.likeState("neutral", 43L)
            CollectionFavorites.apply(context, items, add)

            assertEquals(42L, database.songTable.getLikedAt("liked"))
            assertTrue(database.songTable.isLikedDirect("disliked"))
            assertEquals(43L, database.songTable.getLikedAt("neutral"))
            verify(exactly = 1) { MyDownloadHelper.downloadNewlyLiked(context, items.getValue("disliked")) }
            verify(exactly = 1) { MyDownloadHelper.downloadNewlyLiked(any(), any()) }

            val remove = checkNotNull(CollectionFavorites.resolve(context, items.keys))
            assertEquals(CollectionFavoritesChange(false, items.keys.toList()), remove.change)
            CollectionFavorites.apply(context, items, remove)

            items.keys.forEach { assertNull(database.songTable.getLikedAt(it)) }
            verify(exactly = 0) { MyDownloadHelper.removeNewlyUnliked(any(), any()) }
        } finally {
            unmockkObject(DatabaseInitializer.Companion, MyDownloadHelper)
            database.close()
        }
    }
}
