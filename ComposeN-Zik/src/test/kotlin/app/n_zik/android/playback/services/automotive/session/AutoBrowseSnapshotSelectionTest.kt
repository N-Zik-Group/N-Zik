package app.n_zik.android.playback.services.automotive.session

import app.n_zik.android.core.network.utils.NetworkQualityHelper
import io.mockk.mockkObject
import io.mockk.unmockkAll
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.SongArtistMap
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.browse.handlers.BrowseHandler
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import app.n_zik.android.playback.services.automotive.models.AutoSelectionStore
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Exercises emitted browse IDs through the public queue callback without a playback history. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoBrowseSnapshotSelectionTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var tree: AutoBrowseTree

    @Before
    fun setUp() {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns app
        Dependencies.init(application)
        context = app
        context.preferences.edit().remove(maxSongsInQueueAndroidAutoKey).commit()
        clearDatabase()
        AutoSearchState.clear()
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        tree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply {
            isAccessible = true
        }.get(callback) as AutoBrowseTree
    }

    @After
    fun tearDown() {
        unmockkAll()
        context.preferences.edit().remove(maxSongsInQueueAndroidAutoKey).commit()
        tree.clearCache()
        clearDatabase()
    }

    private fun clearDatabase() {
        // JUnit setup is synchronous; Room requires its DAO work off the main thread.
        runBlocking { withContext(NzikDispatchers.DATA) { DatabaseInitializer.Instance.clearAllTables() } }
    }

    private fun mapped(parent: String, ids: List<String>): List<MediaItem> = ids.map { id ->
        SessionMediaItemMapper.mapSongToMediaItem(Song.makePlaceholder(id), parent)
    }

    private fun select(id: String): MediaSession.MediaItemsWithStartPosition = callback.onSetMediaItems(
        mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(),
        mutableListOf(MediaItem.Builder().setMediaId(id).build()), 0, 1234L
    ).get(15, TimeUnit.SECONDS)

    private fun assertQueue(parent: String, ids: List<String>) {
        val shown = tree.selectionStore.capture(parent, mapped(parent, ids))
        val result = select(shown[1].mediaId)
        assertEquals(parent, ids, result.mediaItems.map { it.mediaId })
        assertEquals(parent, 1, result.startIndex)
        assertEquals(parent, ids[1], result.mediaItems[result.startIndex].mediaId)
    }

    @Test
    fun `captured browse contexts preserve the selected row and displayed order`() {
        listOf(
            AutoSessionConstants.ID_SEARCH_SONGS,
            AutoSessionConstants.ID_SEARCH_VIDEOS,
            "artist/UC-one/videos",
            AutoSessionConstants.ID_QUICK_PICKS,
            AutoSessionConstants.ID_SONGS_CACHED
        ).forEachIndexed { index, parent ->
            assertQueue(parent, listOf("last-$index", "selected-$index", "first-$index"))
        }
    }

    @Test
    fun `opening another artist or album does not replace the queue behind an earlier selection`() {
        listOf("artist/A/videos" to "artist/B/videos", "album/A" to "album/B").forEach { (a, b) ->
            val shownA = tree.selectionStore.capture(a, mapped(a, listOf("a-first", "a-selected")))
            tree.selectionStore.capture(b, mapped(b, listOf("b-first", "b-selected")))
            val result = select(shownA[1].mediaId)
            assertEquals(listOf("a-first", "a-selected"), result.mediaItems.map { it.mediaId })
            assertEquals(1, result.startIndex)
        }
    }

    @Test
    fun `browse tree captures the complete ordered list before page slicing`() {
        val parent = AutoSessionConstants.ID_SEARCH_VIDEOS
        val ids = (0 until 205).map { "video-$it" }
        val handler = mockk<BrowseHandler>()
        every { handler.handles(parent) } returns true
        coEvery { handler.getChildren(parent, context, Database, any(), null) } returns mapped(parent, ids)
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply {
            isAccessible = true
        }.set(tree, listOf(handler))
        // The browse API is suspend while JUnit test methods are synchronous.
        val secondPage = runBlocking { tree.getChildren(parent, 1, null) }
        val selected = secondPage.first { it.mediaMetadata.isPlayable == true }
        val result = select(selected.mediaId)
        assertEquals(ids, result.mediaItems.map { it.mediaId })
        assertEquals(200, result.startIndex)
    }

    @Test
    fun `queue cap retains selected item beyond the first page and keeps its neighbors ordered`() {
        context.preferences.edit().putString(maxSongsInQueueAndroidAutoKey, MaxSongs.`50`.name).commit()
        val parent = AutoSessionConstants.ID_SEARCH_SONGS
        val ids = (0 until 260).map { "song-$it" }
        val shown = tree.selectionStore.capture(parent, mapped(parent, ids))
        val result = select(shown[240].mediaId)
        assertEquals(50, result.mediaItems.size)
        assertTrue(result.startIndex in result.mediaItems.indices)
        assertEquals("song-240", result.mediaItems[result.startIndex].mediaId)
        val queueStart = ids.indexOf(result.mediaItems.first().mediaId)
        assertEquals(ids.subList(queueStart, queueStart + 50), result.mediaItems.map { it.mediaId })
    }

    @Test
    fun `expired selection does not queue the opaque token as a song`() {
        val shown = tree.selectionStore.capture("album/A", mapped("album/A", listOf("one", "two")))
        tree.selectionStore.clear()
        val result = select(shown[1].mediaId)
        assertTrue(result.mediaItems.isEmpty())
    }

    @Test
    fun `snapshot replacement keeps old rows valid and preserves playback metadata and uri`() {
        val store = AutoSelectionStore(profile = { "one" })
        val original = mapped("album/A", listOf("one", "two"))
        val first = store.capture("album/A", original)
        val second = store.capture("album/A", mapped("album/A", listOf("replacement")))
        assertNotEquals(first.first().mediaId, second.first().mediaId)
        val resolved = requireNotNull(store.resolve(first[1].mediaId))
        assertEquals(listOf("one", "two"), resolved.items.map { it.mediaId })
        assertEquals(1, resolved.startIndex)
        assertEquals(original[1].mediaMetadata, resolved.items[1].mediaMetadata)
        assertEquals(original[1].localConfiguration?.uri, resolved.items[1].localConfiguration?.uri)
        assertEquals(listOf("replacement"), store.itemsForParent("album/A")?.map { it.mediaId })
    }

    @Test
    fun `profile switch hides previous tokens and parent snapshot`() {
        var profile = "first"
        val store = AutoSelectionStore(profile = { profile })
        val shown = store.capture("album/A", mapped("album/A", listOf("private")))
        profile = "second"
        assertNull(store.resolve(shown.single().mediaId))
        assertNull(store.itemsForParent("album/A"))
        store.capture("album/A", mapped("album/A", listOf("other")))
        assertEquals(listOf("other"), store.itemsForParent("album/A")?.map { it.mediaId })
    }

    @Test
    fun `bounded snapshots evict oldest tokens and reject unknown song suffix`() {
        val store = AutoSelectionStore(profile = { "one" }, maxEntries = 2)
        val oldest = store.capture("album/A", mapped("album/A", listOf("one"))).single()
        val retained = store.capture("album/B", mapped("album/B", listOf("two"))).single()
        store.capture("album/C", mapped("album/C", listOf("three")))
        assertNull(store.resolve(oldest.mediaId))
        assertNull(store.itemsForParent("album/A"))
        assertNotNull(store.resolve(retained.mediaId))
        assertNull(store.resolve(retained.mediaId.substringBeforeLast('/') + "/missing"))
        assertNull(store.resolve("AA_SELECT/missing/two"))
    }

    @Test
    fun `snapshot binds playlist actions and leaves navigation outside the queue`() {
        val store = AutoSelectionStore(profile = { "one" })
        val navigation = MediaItem.Builder().setMediaId("album/A").setMediaMetadata(
            MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build()
        ).build()
        val action = AutoSessionConstants.shuffleItem(context, AutoMediaIdContract.playlistShuffle("42"))
        val song = mapped("playlist/42", listOf("one")).single()
        val shown = store.capture("playlist/42", listOf(action, navigation, song))
        assertTrue(shown[0].mediaId.startsWith("AA_ACTION/"))
        assertEquals(listOf("one"), requireNotNull(store.resolveAction(shown[0].mediaId)).items.map { it.mediaId })
        assertEquals(navigation.mediaId, shown[1].mediaId)
        assertEquals(listOf("one"), requireNotNull(store.resolve(shown[2].mediaId)).items.map { it.mediaId })
    }

    @Test
    fun `new search preserves earlier displayed album selections`() {
        val shown = tree.selectionStore.capture("album/A", mapped("album/A", listOf("first", "selected")))
        callback.onSearch(mockk<MediaLibrarySession>(relaxed = true), mockk<MediaSession.ControllerInfo>(), "new query", null)
            .get(15, TimeUnit.SECONDS)
        val result = select(shown[1].mediaId)
        assertEquals(listOf("first", "selected"), result.mediaItems.map { it.mediaId })
        assertEquals(1, result.startIndex)
    }

    @Test
    fun `non Auto connection preserves displayed scoped rows and playback actions`() {
        val parent = "playlist/42"
        val ids = listOf("displayed-first", "repeated", "displayed-middle", "repeated")
        val handler = mockk<BrowseHandler>()
        every { handler.handles(parent) } returns true
        coEvery { handler.getChildren(parent, context, Database, any(), null) } returns
            listOf(AutoSessionConstants.shuffleItem(context, AutoMediaIdContract.playlistShuffle("42"))) + mapped(parent, ids)
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply {
            isAccessible = true
        }.set(tree, listOf(handler))
        // The browse API is suspend while JUnit test methods are synchronous.
        val displayed = runBlocking { tree.getChildren(parent, -1, null) }
        val rows = displayed.filter { it.mediaMetadata.isPlayable == true && !AutoMediaIdContract.isQueueAction(it.mediaId) }
        val playAll = displayed.first { AutoMediaIdContract.isQueueAction(it.mediaId) && !AutoMediaIdContract.isShuffle(it.mediaId) }
        val shuffle = displayed.first { AutoMediaIdContract.isShuffle(it.mediaId) }
        val session = mockk<MediaLibrarySession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        every { session.isAutoCompanionController(controller) } returns false

        assertTrue(callback.onConnect(session, controller).isAccepted)

        listOf(2, 3).forEach { index ->
            val result = select(rows[index].mediaId)
            assertEquals(ids, result.mediaItems.map { it.mediaId })
            assertEquals(index, result.startIndex)
            assertEquals(ids[index], result.mediaItems[result.startIndex].mediaId)
        }
        val completeQueue = select(playAll.mediaId)
        assertEquals(ids, completeQueue.mediaItems.map { it.mediaId })
        assertEquals(0, completeQueue.startIndex)
        val shuffled = select(shuffle.mediaId).mediaItems.map { it.mediaId }
        assertEquals(ids.groupingBy { it }.eachCount(), shuffled.groupingBy { it }.eachCount())
    }

    @Test
    fun `multiitem request starts at the requested later scoped row`() {
        val first = tree.selectionStore.capture("album/A", mapped("album/A", listOf("wrong-first", "wrong-second")))
        val later = tree.selectionStore.capture("album/B", mapped("album/B", listOf("before", "requested", "after")))
        val result = callback.onSetMediaItems(
            mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(), mutableListOf(first[0], later[1]), 1, 4321L
        ).get(15, TimeUnit.SECONDS)
        assertEquals(listOf("before", "requested", "after"), result.mediaItems.map { it.mediaId })
        assertEquals(1, result.startIndex)
        assertEquals(4321L, result.startPositionMs)
    }

    @Test
    fun `emitted playlist actions keep the full capture after same parent refresh`() {
        val parent = "playlist/42"
        val ids = (0 until 205).map { "displayed-${204 - it}" }
        // Synchronous JUnit fixture setup must dispatch Room writes off its main thread.
        runBlocking { withContext(NzikDispatchers.DATA) {
            Database.playlistTable.insertIgnore(Playlist(id = 42, name = "Different persisted ordering"))
            ids.reversed().forEach { id ->
                Database.songTable.upsert(Song.makePlaceholder(id))
                Database.songPlaylistMapTable.map(id, 42)
            }
            assertEquals(ids.reversed(), Database.songPlaylistMapTable.allSongsOf(42).first().map { it.id })
        } }
        val handler = mockk<BrowseHandler>()
        every { handler.handles(parent) } returns true
        coEvery { handler.getChildren(parent, context, Database, any(), null) } returnsMany listOf(
            listOf(AutoSessionConstants.shuffleItem(context, AutoMediaIdContract.playlistShuffle("42"))) + mapped(parent, ids),
            listOf(AutoSessionConstants.shuffleItem(context, AutoMediaIdContract.playlistShuffle("42"))) + mapped(parent, listOf("replacement"))
        )
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply { isAccessible = true }.set(tree, listOf(handler))
        // The browse API is suspend while JUnit test methods are synchronous.
        val original = runBlocking { tree.getChildren(parent, -1, null) }
        val playAll = original.first { AutoMediaIdContract.isQueueAction(it.mediaId) && !AutoMediaIdContract.isShuffle(it.mediaId) }
        val shuffle = original.first { AutoMediaIdContract.isShuffle(it.mediaId) }
        // Reopening the same parent replaces its newest list, but the earlier actions still own their capture.
        runBlocking { tree.getChildren(parent, -1, null) }
        assertEquals(ids, select(playAll.mediaId).mediaItems.map { it.mediaId })
        val shuffled = select(shuffle.mediaId).mediaItems.map { it.mediaId }
        assertEquals(ids.size, shuffled.size)
        assertEquals(ids.toSet(), shuffled.toSet())
        assertTrue(shuffled.contains("displayed-204"))
    }

    @Test
    fun `captured song category actions reapply exclusions changed after browse`() {
        val ids = listOf("kept-first", "newly-disliked", "repeated", "kept-middle", "repeated")
        val parents = listOf(
            AutoSessionConstants.ID_SONGS_ALL,
            AutoSessionConstants.ID_SONGS_FAVORITES,
            AutoSessionConstants.ID_SONGS_DOWNLOADED,
            AutoSessionConstants.ID_SONGS_CACHED,
            AutoSessionConstants.ID_SONGS_TOP,
            AutoSessionConstants.ID_SONGS_ONDEVICE,
            "${AutoSessionConstants.ID_SEARCH_SONGS}/AC/DC"
        )
        val captures = parents.map { parent ->
            tree.selectionStore.capture(parent, listOf(
                AutoSessionConstants.playAllItem(context, "${parent.substringBefore('/')}_PLAY_ALL"),
                AutoSessionConstants.shuffleItem(context, "${parent.substringBefore('/')}_SHUFFLE")
            ) + mapped(parent, ids))
        }
        // Synchronous JUnit fixture setup must dispatch Room writes off its main thread.
        runBlocking { withContext(NzikDispatchers.DATA) {
            Database.songTable.upsert(Song.makePlaceholder("newly-disliked").copy(likedAt = -1L))
        } }
        val expected = listOf("kept-first", "repeated", "kept-middle", "repeated")
        captures.forEachIndexed { index, shown ->
            val parent = parents[index]
            val playAll = select(shown[0].mediaId)
            assertEquals(parent, expected, playAll.mediaItems.map { it.mediaId })
            assertEquals(parent, 0, playAll.startIndex)
            assertEquals(parent, 0L, playAll.startPositionMs)
            val shuffle = select(shown[1].mediaId)
            assertEquals(parent, expected.groupingBy { it }.eachCount(), shuffle.mediaItems.map { it.mediaId }.groupingBy { it }.eachCount())
            assertEquals(parent, 0, shuffle.startIndex)
            val selected = select(shown.last().mediaId)
            assertEquals(parent, expected, selected.mediaItems.map { it.mediaId })
            assertEquals(parent, 3, selected.startIndex)
            assertEquals(parent, 1234L, selected.startPositionMs)
            assertTrue(parent, select(shown[3].mediaId).mediaItems.isEmpty())
        }
    }

    @Test
    fun `detail disliked quick picks and video captures retain their unfiltered policy`() {
        val ids = listOf("first", "newly-disliked", "last")
        val parents = listOf(
            "playlist/42", "album/A", "artist/A/videos",
            AutoSessionConstants.ID_QUICK_PICKS,
            AutoSessionConstants.ID_SONGS_DISLIKED,
            AutoSessionConstants.ID_SEARCH_VIDEOS
        )
        val captures = parents.map { parent ->
            tree.selectionStore.capture(parent, listOf(
                AutoSessionConstants.playAllItem(context, "POLICY_PLAY_ALL"),
                AutoSessionConstants.shuffleItem(context, "POLICY_SHUFFLE")
            ) + mapped(parent, ids))
        }
        // Synchronous JUnit fixture setup must dispatch Room writes off its main thread.
        runBlocking { withContext(NzikDispatchers.DATA) {
            Database.songTable.upsert(Song.makePlaceholder("newly-disliked").copy(likedAt = -1L))
        } }
        captures.forEachIndexed { index, shown ->
            val parent = parents[index]
            assertEquals(parent, ids, select(shown[0].mediaId).mediaItems.map { it.mediaId })
            assertEquals(parent, ids.toSet(), select(shown[1].mediaId).mediaItems.map { it.mediaId }.toSet())
            val selected = select(shown[3].mediaId)
            assertEquals(parent, ids, selected.mediaItems.map { it.mediaId })
            assertEquals(parent, 1, selected.startIndex)
        }
    }

    @Test
    fun `append reapplies captured category exclusions and preserves negative policies and occurrences`() {
        val filtered = listOf(AutoSessionConstants.ID_SONGS_ALL, AutoSessionConstants.ID_SONGS_FAVORITES,
            AutoSessionConstants.ID_SONGS_DOWNLOADED, AutoSessionConstants.ID_SONGS_CACHED,
            AutoSessionConstants.ID_SONGS_TOP, AutoSessionConstants.ID_SONGS_ONDEVICE,
            "${AutoSessionConstants.ID_SEARCH_SONGS}/AC/DC")
        val unfiltered = listOf("album/A", "playlist/42", "artist/A/videos",
            AutoSessionConstants.ID_QUICK_PICKS, AutoSessionConstants.ID_SONGS_DISLIKED,
            AutoSessionConstants.ID_SEARCH_VIDEOS)
        val captures = (filtered + unfiltered).associateWith { parent ->
            val items = mapped(parent, listOf("first", "blocked", "repeat", "repeat")).mapIndexed { index, item ->
                item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setTrackNumber(index + 1).build()).build()
            }
            tree.selectionStore.capture(parent, items)
        }
        // Synchronous JUnit setup dispatches the click-time dislike change off Room's main thread.
        runBlocking { withContext(NzikDispatchers.DATA) {
            Database.songTable.upsert(Song.makePlaceholder("blocked").copy(likedAt = -1L))
        } }
        captures.forEach { (parent, shown) ->
            val appended = callback.onAddMediaItems(mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(),
                mutableListOf(shown[3], shown[1], shown[0], shown[2])).get(15, TimeUnit.SECONDS)
            val excluded = parent in filtered
            assertEquals(parent, if (excluded) listOf("repeat", "first", "repeat") else listOf("repeat", "blocked", "first", "repeat"),
                appended.map { it.mediaId })
            assertEquals(parent, if (excluded) listOf(4, 1, 3) else listOf(4, 2, 1, 3),
                appended.map { it.mediaMetadata.trackNumber })
        }
    }

    @Test
    fun `scoped song search excludes disliked songs artists and albums without moving selected song`() {
        val ids = listOf("allowed-before", "disliked-song", "disliked-artist", "disliked-album", "selected", "allowed-after")
        // Synchronous JUnit fixture setup must dispatch Room writes off its main thread.
        runBlocking { withContext(NzikDispatchers.DATA) {
            ids.forEach { id -> Database.songTable.upsert(Song.makePlaceholder(id).copy(likedAt = if (id == "disliked-song") -1L else null)) }
            Database.artistTable.insertIgnore(Artist(id = "excluded-artist", dislikedAt = 123L))
            Database.songArtistMapTable.insertIgnore(SongArtistMap(songId = "disliked-artist", artistId = "excluded-artist"))
            Database.albumTable.insertIgnore(Album(id = "excluded-album", dislikedAt = 123L))
            Database.songAlbumMapTable.map("disliked-album", "excluded-album", 0)
        } }
        val parent = "${AutoSessionConstants.ID_SEARCH_SONGS}/query"
        val shown = tree.selectionStore.capture(parent, mapped(AutoSessionConstants.ID_SEARCH_SONGS, ids))
        val result = select(shown[4].mediaId)
        assertEquals(listOf("allowed-before", "selected", "allowed-after"), result.mediaItems.map { it.mediaId })
        assertEquals(1, result.startIndex)
        assertEquals("selected", result.mediaItems[result.startIndex].mediaId)
    }
    @Test
    fun `same song in two successive search result lists keeps each displayed queue`() {
        val parent = AutoSessionConstants.ID_SEARCH_SONGS
        val firstIds = listOf("first-only", "shared-song", "first-tail")
        val secondIds = listOf("shared-song", "second-only")
        val handler = mockk<BrowseHandler>()
        every { handler.handles(parent) } returns true
        coEvery { handler.getChildren(parent, context, Database, any(), null) } returnsMany listOf(
            mapped(parent, firstIds), mapped(parent, secondIds)
        )
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply {
            isAccessible = true
        }.set(tree, listOf(handler))
        // The browse API is suspend while JUnit test methods are synchronous.
        val (first, second) = runBlocking {
            tree.getChildren(parent, -1, null) to tree.getChildren(parent, -1, null)
        }
        assertNotEquals(first[1].mediaId, second[0].mediaId)
        val earlierSelection = select(first[1].mediaId)
        val laterSelection = select(second[0].mediaId)
        assertEquals(firstIds, earlierSelection.mediaItems.map { it.mediaId })
        assertEquals(1, earlierSelection.startIndex)
        assertEquals(secondIds, laterSelection.mediaItems.map { it.mediaId })
        assertEquals(0, laterSelection.startIndex)
    }

    @Test
    fun `get item resolves snapshot metadata without persisted song and rejects expired token`() {
        val original = mapped(AutoSessionConstants.ID_SEARCH_VIDEOS, listOf("not-in-db")).single()
        val shown = tree.selectionStore.capture(AutoSessionConstants.ID_SEARCH_VIDEOS, listOf(original)).single()
        val session = mockk<androidx.media3.session.MediaLibraryService.MediaLibrarySession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        val result = callback.onGetItem(session, controller, shown.mediaId).get(15, TimeUnit.SECONDS)
        assertEquals(0, result.resultCode)
        assertEquals(shown.mediaId, result.value?.mediaId)
        assertEquals(original.mediaMetadata, result.value?.mediaMetadata)
        assertEquals(original.localConfiguration?.uri, result.value?.localConfiguration?.uri)
        tree.selectionStore.clear()
        val expired = callback.onGetItem(session, controller, shown.mediaId).get(15, TimeUnit.SECONDS)
        assertTrue(expired.resultCode != 0)
        assertNull(expired.value)
    }

    @Test
    fun `add media items resolves selected snapshot row and drops expired rows`() {
        val originals = mapped("artist/A/videos", listOf("not-first", "selected-video"))
        val shown = tree.selectionStore.capture("artist/A/videos", originals)
        val session = mockk<MediaSession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        val result = callback.onAddMediaItems(session, controller, mutableListOf(shown[1])).get(15, TimeUnit.SECONDS)
        assertEquals(listOf("selected-video"), result.map { it.mediaId })
        assertEquals(originals[1].mediaMetadata, result.single().mediaMetadata)
        assertEquals(originals[1].localConfiguration?.uri, result.single().localConfiguration?.uri)
        tree.selectionStore.clear()
        val expired = callback.onAddMediaItems(session, controller, mutableListOf(shown[1])).get(15, TimeUnit.SECONDS)
        assertTrue(expired.isEmpty())
    }
}
