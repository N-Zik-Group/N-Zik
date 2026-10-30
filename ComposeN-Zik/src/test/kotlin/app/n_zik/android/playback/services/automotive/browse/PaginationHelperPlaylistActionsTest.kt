package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.automotive.session.TestApplication
import app.n_zik.android.playback.services.automotive.models.AutoSelectionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PaginationHelperPlaylistActionsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext<TestApplication>()
    private val parent = "playlist/42"

    private fun songs(count: Int): List<MediaItem> = (0 until count).map { index ->
        MediaItem.Builder().setMediaId("$parent/song-$index").setUri("https://example.test/$index")
            .setMediaMetadata(MediaMetadata.Builder().setIsPlayable(true).setIsBrowsable(false).build()).build()
    }

    private fun shuffle(): MediaItem = AutoSessionConstants.shuffleItem(context, AutoMediaIdContract.playlistShuffle("42"))

    private fun assertActions(items: List<MediaItem>) {
        assertEquals("PLAYLIST_PLAY_ALL/42", items[0].mediaId)
        assertEquals("PLAYLIST_SHUFFLE/42", items[1].mediaId)
        assertTrue(items.take(2).all { it.mediaMetadata.isPlayable == true && it.mediaMetadata.isBrowsable != true })
    }

    @Test
    fun `playlist action rows do not push 199 or 200 songs into page folders`() {
        listOf(199, 200).forEach { count ->
            val rows = songs(count)
            val result = PaginationHelper.paginate(context, listOf(shuffle()) + rows, parent, -1)
            assertActions(result)
            assertEquals(count + 2, result.size)
            assertEquals(rows, result.drop(2))
            assertTrue(result.none { AutoMediaIdContract.parsePage(it.mediaId) != null })
        }
    }

    @Test
    fun `201 400 and 401 songs generate exact folder boundaries with scoped actions outside folders`() {
        listOf(201, 400, 401).forEach { count ->
            val rows = songs(count)
            val source = listOf(shuffle()) + rows
            val root = PaginationHelper.paginate(context, source, parent, -1)
            assertActions(root)
            val folders = root.drop(2)
            assertEquals((count + 199) / 200, folders.size)
            val reconstructed = folders.flatMapIndexed { index, folder ->
                val page = requireNotNull(AutoMediaIdContract.parsePage(folder.mediaId))
                assertEquals(parent, page.parentId)
                assertEquals(index, page.index)
                assertEquals("Page ${index + 1} (${index * 200 + 1} - ${minOf(count, (index + 1) * 200)})", folder.mediaMetadata.title.toString())
                assertEquals(true, folder.mediaMetadata.isBrowsable)
                assertEquals(false, folder.mediaMetadata.isPlayable)
                val children = PaginationHelper.paginate(context, source, page.parentId, page.index)
                assertActions(children)
                val content = children.drop(2)
                assertEquals(rows.drop(index * 200).take(200), content)
                content
            }
            assertEquals(rows, reconstructed)
        }
    }

    @Test
    fun `page two starts at song 200 and retains actual content rows and uri`() {
        val rows = songs(401)
        val result = PaginationHelper.paginate(context, listOf(shuffle()) + rows, parent, 1)
        assertActions(result)
        assertEquals(rows.subList(200, 400), result.drop(2))
        assertEquals("playlist/42/song-200", result[2].mediaId)
        assertEquals(rows[200].localConfiguration?.uri, result[2].localConfiguration?.uri)
        assertEquals("playlist/42/song-399", result.last().mediaId)
    }

    @Test
    fun `existing play all is never counted or duplicated and invalid page is empty`() {
        val rows = songs(200)
        val source = listOf(AutoSessionConstants.playAllItem(context, AutoMediaIdContract.playlistPlayAll("42")), shuffle()) + rows
        val result = PaginationHelper.paginate(context, source, parent, -1)
        assertActions(result)
        assertEquals(rows, result.drop(2))
        assertTrue(PaginationHelper.paginate(context, source, parent, Int.MAX_VALUE).isEmpty())
    }

    @Test
    fun `pagination preserves capture bound shuffle and derives play all for the same capture`() {
        val store = AutoSelectionStore(profile = { "test" })
        val captured = store.capture(parent, listOf(shuffle()) + songs(401))
        val root = PaginationHelper.paginate(context, captured, parent, -1)
        val secondPage = PaginationHelper.paginate(context, captured, parent, 1)
        val originalShuffle = captured.first().mediaId
        val actionIds = root.take(2).map { it.mediaId }
        assertEquals(listOf(AutoMediaIdContract.playAllFor(originalShuffle), originalShuffle), actionIds)
        assertEquals(actionIds, secondPage.take(2).map { it.mediaId })
        assertEquals(401, requireNotNull(store.resolveAction(actionIds[0])).items.size)
        assertEquals(
            requireNotNull(store.resolveAction(actionIds[0])).items,
            requireNotNull(store.resolveAction(actionIds[1])).items
        )
        assertEquals(200, secondPage.drop(2).size)
    }
}
