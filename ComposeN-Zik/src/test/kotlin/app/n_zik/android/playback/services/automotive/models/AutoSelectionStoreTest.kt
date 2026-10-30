package app.n_zik.android.playback.services.automotive.models

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AutoSelectionStoreTest {
    private fun song(id: String, title: String = id): MediaItem = MediaItem.Builder()
        .setMediaId("playlist/online/$id")
        .setUri("https://audio.example/$id")
        .setCustomCacheKey(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsPlayable(true).build())
        .build()

    private fun action(shuffle: Boolean): MediaItem = MediaItem.Builder()
        .setMediaId(if (shuffle) AutoMediaIdContract.playlistShuffle("online") else AutoMediaIdContract.playlistPlayAll("online"))
        .setMediaMetadata(MediaMetadata.Builder().setIsPlayable(true).build()).build()

    @Test
    fun `repeated online songs keep occurrence metadata and validate index with song id`() {
        val store = AutoSelectionStore(profile = { "one" })
        val shown = store.capture("playlist/online", listOf(song("repeat", "first"), song("other"), song("repeat", "last")))
        assertNotEquals(shown[0].mediaId, shown[2].mediaId)
        val selection = requireNotNull(store.resolve(shown[2].mediaId))
        assertEquals(2, selection.startIndex)
        assertEquals("playlist/online", selection.parentId)
        assertEquals("last", selection.items[selection.startIndex].mediaMetadata.title)
        val id = requireNotNull(AutoMediaIdContract.parseSelection(shown[2].mediaId))
        assertNull(store.resolve(AutoMediaIdContract.selection(id.token, "other", id.index)))
        assertNull(store.resolve(AutoMediaIdContract.selection(id.token, id.songId, 3)))
    }

    @Test
    fun `both displayed actions keep old snapshot after same parent refresh`() {
        var profile = "one"
        val store = AutoSelectionStore(profile = { profile })
        val first = store.capture("playlist/online", listOf(action(true), action(false), song("A")))
        val second = store.capture("playlist/online", listOf(action(true), song("B")))
        first.take(2).forEach { row ->
            val selection = requireNotNull(store.resolveAction(row.mediaId))
            assertEquals(listOf("A"), selection.items.map { it.mediaId })
            assertEquals(0, selection.startIndex)
            assertEquals("playlist/online", selection.parentId)
        }
        val synthesized = AutoMediaIdContract.playAllFor(first[0].mediaId)
        assertEquals(first[1].mediaId, synthesized)
        assertTrue(AutoMediaIdContract.isShuffle(first[0].mediaId))
        assertFalse(AutoMediaIdContract.isShuffle(first[1].mediaId))
        assertTrue(AutoMediaIdContract.isQueueAction(synthesized))
        assertEquals(listOf("B"), store.resolveAction(second[0].mediaId)?.items?.map { it.mediaId })
        profile = "two"
        assertNull(store.resolveAction(first[0].mediaId))
        assertNull(store.resolve(second.last().mediaId))
    }

    @Test
    fun `retained queue drops encoded art but preserves all other metadata uri and displayed art`() {
        val store = AutoSelectionStore(profile = { "one" })
        val extras = Bundle().apply { putString("provider", "online") }
        val original = song("one").buildUpon().setMediaMetadata(
            MediaMetadata.Builder().setTitle("title").setArtist("artist").setAlbumTitle("album")
                .setArtworkUri(Uri.parse("content://app.artwork/one"))
                .setArtworkData(ByteArray(1024 * 1024), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .setExtras(extras).setIsPlayable(true).build()
        ).build()
        val shown = store.capture("playlist/online", listOf(original)).single()
        val retained = requireNotNull(store.resolve(shown.mediaId)).items.single()
        assertNotNull(shown.mediaMetadata.artworkData)
        assertNull(retained.mediaMetadata.artworkData)
        assertEquals(original.mediaMetadata.buildUpon().setArtworkData(null, null).build(), retained.mediaMetadata)
        assertEquals(original.localConfiguration, retained.localConfiguration)
        assertEquals("online", retained.mediaMetadata.extras?.getString("provider"))
    }

    @Test
    fun `scoped parser rejects ambiguous occurrences and malformed action shapes`() {
        assertNull(AutoMediaIdContract.parseSelection("AA_SELECT/token/song"))
        listOf("AA_SELECT/token/-1/song", "AA_SELECT/token/no/song", "AA_SELECT/token/0/", "AA_SELECT//0/song").forEach {
            assertNull(AutoMediaIdContract.parseSelection(it))
        }
        val encoded = AutoMediaIdContract.selection("token", "song/with space", 2)
        assertEquals(AutoMediaIdContract.SelectionId("token", "song/with space", 2), AutoMediaIdContract.parseSelection(encoded))
        listOf("AA_ACTION/token/unknown", "AA_ACTION//SHUFFLE", "AA_ACTION/token/SHUFFLE/extra").forEach {
            assertTrue(AutoMediaIdContract.isScopedAction(it))
            assertNull(AutoMediaIdContract.parseScopedAction(it))
        }
    }

    @Test
    fun `total item budget evicts whole old captures and permits only one oversized newest capture`() {
        val store = AutoSelectionStore(profile = { "one" }, maxRetainedItems = 5)
        val oldest = store.capture("old", listOf(song("old"))).single()
        val retained = store.capture("retained", (0 until 4).map { song("r$it") })
        assertNotNull(store.resolve(oldest.mediaId))
        val newest = store.capture("new", listOf(action(true), song("new")))
        assertNull(store.resolve(oldest.mediaId))
        assertEquals(4, store.resolve(retained[3].mediaId)?.items?.size)
        assertEquals(1, store.resolveAction(newest[0].mediaId)?.items?.size)
        val oversized = store.capture("large", (0 until 8).map { song("large$it") })
        assertNull(store.resolve(retained[0].mediaId))
        assertNull(store.resolveAction(newest[0].mediaId))
        val complete = requireNotNull(store.resolve(oversized.last().mediaId))
        assertEquals((0 until 8).map { "large$it" }, complete.items.map { it.mediaId })
        assertEquals(7, complete.startIndex)
        store.capture("small", listOf(song("small")))
        assertNull(store.resolve(oversized[0].mediaId))
        assertNull(store.itemsForParent("large"))
    }

    @Test
    fun `default item budget preserves normal paged queues and a large playlist completely`() {
        val store = AutoSelectionStore(profile = { "one" })
        listOf(205, 260, 10_000).forEach { size ->
            val shown = store.capture("playlist/$size", (0 until size).map { song("$size-$it") })
            val selection = requireNotNull(store.resolve(shown.last().mediaId))
            assertEquals(size, selection.items.size)
            assertEquals(size - 1, selection.startIndex)
        }
    }

    @Test
    fun `expected profile capture mismatch publishes no stale rows and reads profile once`() {
        var profile = "one"
        var reads = 0
        val store = AutoSelectionStore(profile = { reads++; profile })
        val old = store.capture("private", listOf(song("private"))).single()
        profile = "two"
        reads = 0
        assertTrue(store.capture("late", listOf(song("stale")), expectedProfile = "one").isEmpty())
        assertEquals(1, reads)
        assertNull(store.resolve(old.mediaId))
        assertNull(store.itemsForParent("late"))
        val current = store.capture("current", listOf(song("current")), expectedProfile = "two").single()
        assertNotNull(store.resolve(current.mediaId))
        store.clear()
        assertNull(store.resolve(current.mediaId))
    }


    @Test
    fun `folder only category and empty lucky shuffle retain legacy action ids`() {
        val store = AutoSelectionStore(profile = { "one" })
        val folder = MediaItem.Builder().setMediaId("album/one").setMediaMetadata(
            MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build()
        ).build()
        listOf("ALBUMS_LIBRARY_SHUFFLE", "LUCKY_SHUFFLE").forEach { actionId ->
            val legacyAction = action(true).buildUpon().setMediaId(actionId).build()
            val items = if (actionId == "ALBUMS_LIBRARY_SHUFFLE") listOf(legacyAction, folder) else listOf(legacyAction)
            val shown = store.capture(actionId, items)
            assertEquals(items.map { it.mediaId }, shown.map { it.mediaId })
            assertTrue(requireNotNull(store.itemsForParent(actionId)).isEmpty())
            assertNull(store.resolveAction(shown.first().mediaId))
        }
    }

}
