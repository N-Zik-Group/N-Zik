package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.content.Context
import android.os.Looper
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.QueuedMediaItem
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.fadeInEffect
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.appContext
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.QueuedMediaItemTable
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.handlers.BrowseHandler
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.browse.AutoSuggestedQueueLoader
import app.n_zik.android.playback.services.automotive.models.AutoSelectionStore
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.pauseWithFade
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.io.IOException
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Real modern MediaController/MediaSession queue-application regressions. No audible or legacy transport claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AutoRealSessionQueueOwnershipTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var session: MediaLibrarySession
    private lateinit var player: RecordingPlayer
    private lateinit var first: MediaBrowser
    private lateinit var second: MediaController
    private lateinit var transport: MediaController
    private lateinit var sharedGuard: ListenTogetherGuestGuardPlayer
    private var guardClock = 1000L
    private lateinit var observed: ObservedCallback
    private val gate = CompletableDeferred<Unit>()
    private val entered = CountDownLatch(1)
    private val sourceFinished = CountDownLatch(1)
    private var gatedId = "explicit"
    private var failGatedSelection = false
    private val searchCounts = mutableListOf<Int>()
    private val childCounts = mutableListOf<Pair<String, Int>>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        listenTogetherGuestLock.value = false
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        context.preferences.edit().clear().putBoolean(persistentQueueKey, true).commit()
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        mockkObject(Database)
        val queue = mockk<QueuedMediaItemTable>()
        every { Database.queueTable } returns queue
        every { queue.all(any()) } returns flowOf(listOf(QueuedMediaItem(mediaItem = item("saved"), position = 456L)))
        mockkConstructor(AutoAccessPolicy::class)
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            val items = firstArg<List<MediaItem>>()
            if (items.firstOrNull()?.mediaId == gatedId) {
                entered.countDown()
                try { withContext(NonCancellable) { gate.await() } } finally { sourceFinished.countDown() }
                if (failGatedSelection) throw IllegalStateException("selection eligibility failed")
            }
            items.withIndex().toList()
        }
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        observed = ObservedCallback(callback)
        player = RecordingPlayer()
        session = MediaLibrarySession.Builder(context, guardedSessionPlayer(), observed).build()
        val firstFuture = MediaBrowser.Builder(context, session.token).setListener(object : MediaBrowser.Listener {
            override fun onSearchResultChanged(browser: MediaBrowser, query: String, itemCount: Int, params: LibraryParams?) {
                searchCounts += itemCount
            }
            override fun onChildrenChanged(browser: MediaBrowser, parentId: String, itemCount: Int, params: LibraryParams?) {
                childCounts += parentId to itemCount
            }
        }).buildAsync()
        val secondFuture = MediaController.Builder(context, session.token).buildAsync()
        val transportFuture = MediaController.Builder(context, session.token).buildAsync()
        pumpUntil { firstFuture.isDone && secondFuture.isDone && transportFuture.isDone }
        transport = transportFuture.get(1, TimeUnit.SECONDS)
        first = firstFuture.get(1, TimeUnit.SECONDS)
        second = secondFuture.get(1, TimeUnit.SECONDS)
        assertTrue(first.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertTrue(second.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
    }

    @After
    fun tearDown() {
        gate.complete(Unit)
        listenTogetherGuestLock.value = false
        if (::first.isInitialized) first.release()
        if (::second.isInitialized) second.release()
        if (::transport.isInitialized) transport.release()
        if (::session.isInitialized) session.release()
        if (::callback.isInitialized) callback.release()
        if (::player.isInitialized) player.release()
        shadowOf(Looper.getMainLooper()).idle()
        if (::context.isInitialized) context.preferences.edit().clear().commit()
        unmockkAll()
    }

    @Test
    fun `real browser retains library and transport commands while access changes`() {
        val browseCommands = listOf(
            SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
            SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN,
            SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM,
            SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE,
            SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE,
        )
        browseCommands.forEach { assertTrue(first.availableSessionCommands.contains(it)) }
        assertTrue(first.availableSessionCommands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
        assertTrue(first.availableSessionCommands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT))
        assertTrue(first.availableSessionCommands.contains(AutoSessionConstants.CommandToggleLike))
        assertTrue(first.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertTrue(first.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        val root = first.getLibraryRoot(null)
        pumpUntil { root.isDone }
        assertEquals(PlayerServiceModern.ROOT, root.get(1, TimeUnit.SECONDS).value?.mediaId)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        callback.invalidateAccess(session)
        pumpUntil { !first.availableSessionCommands.contains(AutoSessionConstants.CommandSearch) }
        browseCommands.forEach { assertTrue(first.availableSessionCommands.contains(it)) }
        assertFalse(first.availableSessionCommands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
        assertFalse(first.availableSessionCommands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT))
        assertFalse(first.availableSessionCommands.contains(AutoSessionConstants.CommandStartRadio))
        assertFalse(first.availableSessionCommands.contains(AutoSessionConstants.CommandToggleDownload))
        assertTrue(first.availableSessionCommands.contains(AutoSessionConstants.CommandToggleLike))
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        callback.invalidateAccess(session)
        pumpUntil { first.availableSessionCommands.contains(AutoSessionConstants.CommandSearch) }
        assertTrue(first.availableSessionCommands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
        listenTogetherGuestLock.value = true
        sharedGuard.announceAvailableCommandsChanged()
        pumpUntil { !first.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) }
        assertFalse(first.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(first.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
    }

    @Test fun `real browser subscribes root and emitted folders receives refresh and rejects unknown or playable parents`() {
        val songs = mockk<SongTable>()
        every { Database.songTable } returns songs
        every { songs.findByIdDirect(any()) } returns null
        val root = await(first.getLibraryRoot(null)).value
        assertEquals(true, root?.mediaMetadata?.isBrowsable)
        assertEquals(0, await(first.subscribe(PlayerServiceModern.ROOT, null)).resultCode)
        assertEquals(0, await(first.subscribe(PlayerServiceModern.ARTIST, null)).resultCode)
        callback.refreshArtwork(session)
        pumpUntil { childCounts.any { it.first == PlayerServiceModern.ARTIST } }
        val tree = browseTree()
        val handler = mockk<BrowseHandler>()
        every { handler.handles(any()) } returns true
        coEvery { handler.getChildren(any(), any(), any(), any(), any()) } returns
            (0 until 205).map { item("track-$it").buildUpon().setMediaMetadata(
                item("track-$it").mediaMetadata.buildUpon().setIsBrowsable(false).build()).build() }
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply { isAccessible = true }.set(tree, listOf(handler))
        val rows = await(first.getChildren("album/generated", 0, 1, null)).value.orEmpty()
        val folder = rows.first()
        assertTrue(folder.mediaId.startsWith("AA_PAGE"))
        val unseenPageId = AutoMediaIdContract.page("album/generated", 1)
        assertTrue(await(first.getItem(unseenPageId)).resultCode != 0)
        assertEquals(unseenPageId, await(first.getChildren("album/generated", 1, 1, null)).value?.single()?.mediaId)
        assertEquals(0, await(first.subscribe(unseenPageId, null)).resultCode)
        assertEquals(0, await(first.subscribe(folder.mediaId, null)).resultCode)
        assertEquals(folder.mediaMetadata.title, await(first.getItem(folder.mediaId)).value?.mediaMetadata?.title)
        await(first.getChildren(PlayerServiceModern.ROOT, 0, 20, null))
        assertEquals(0, await(first.subscribe(folder.mediaId, null)).resultCode)
        assertTrue(await(first.subscribe("album/unseen", null)).resultCode != 0)
        val playable = capturedExplicit()
        gate.complete(Unit)
        assertTrue(await(first.subscribe(playable.mediaId, null)).resultCode != 0)
        callback.refreshArtwork(session)
        pumpUntil { childCounts.any { it.first == PlayerServiceModern.ROOT } }
        callback.invalidateAccess(session)
        assertTrue(await(first.getItem(folder.mediaId)).resultCode != 0)
    }

    @Test fun `suggested root publishes named queues with independent playback and preserves normal browsing`() {
        mockkObject(AutoSuggestedQueueLoader)
        coEvery { AutoSuggestedQueueLoader.load(any(), any(), any(), any()) } returns listOf(
            AutoSuggestedQueueLoader.SuggestedQueue(AutoSessionConstants.ID_SONGS_FAVORITES, "Favorites", null,
                MediaMetadata.MEDIA_TYPE_PLAYLIST, listOf(item("favorite-one"), item("favorite-two"))),
            AutoSuggestedQueueLoader.SuggestedQueue("${AutoSessionConstants.ID_ALBUMS_LIBRARY}/album", "Named album",
                Uri.parse("https://images.example.test/album.jpg"), MediaMetadata.MEDIA_TYPE_ALBUM,
                listOf(item("album-one"), item("album-two")))
        )
        val params = LibraryParams.Builder().setSuggested(true).build()
        val root = requireNotNull(await(first.getLibraryRoot(params)).value)
        assertEquals(AutoSuggestedQueueLoader.ROOT_ID, root.mediaId)
        assertEquals(true, root.mediaMetadata.isBrowsable)
        assertEquals(PlayerServiceModern.ROOT, await(first.getLibraryRoot(null)).value?.mediaId)
        val normalRoots = await(first.getChildren(PlayerServiceModern.ROOT, 0, 20, null)).value.orEmpty()
        assertEquals(listOf(AutoSessionConstants.ID_QUICK_PICKS, PlayerServiceModern.SONG,
            PlayerServiceModern.ARTIST, PlayerServiceModern.ALBUM, PlayerServiceModern.PLAYLIST), normalRoots.map { it.mediaId })
        assertEquals(0, await(first.subscribe(AutoSessionConstants.ID_QUICK_PICKS, null)).resultCode)
        assertEquals(AutoSessionConstants.ID_QUICK_PICKS,
            await(first.getItem(AutoSessionConstants.ID_QUICK_PICKS)).value?.mediaId)
        assertEquals(root.mediaId, await(first.getItem(root.mediaId)).value?.mediaId)
        assertEquals(0, await(first.subscribe(root.mediaId, null)).resultCode)
        val cards = await(first.getChildren(root.mediaId, 0, 10, null)).value.orEmpty()
        assertEquals(listOf("Favorites", "Named album"), cards.map { it.mediaMetadata.title.toString() })
        assertTrue(cards.all { it.mediaMetadata.isPlayable == true && it.mediaMetadata.isBrowsable == false })
        assertTrue(cards.all { AutoMediaIdContract.isScopedAction(it.mediaId) })
        assertEquals("content", cards.last().mediaMetadata.artworkUri?.scheme)
        assertEquals("Named album", await(first.getItem(cards.last().mediaId)).value?.mediaMetadata?.title)
        first.setMediaItem(cards.first())
        pumpUntil { player.setCalls.size == 1 }
        assertEquals(listOf("favorite-one", "favorite-two"), (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
        first.setMediaItem(cards.last())
        pumpUntil { player.setCalls.size == 2 }
        assertEquals(listOf("album-one", "album-two"), (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
    }

    @Test fun `suggested root omits actions evicted while capturing other suggestions`() {
        val tree = browseTree()
        AutoBrowseTree::class.java.getDeclaredField("selectionStore").apply { isAccessible = true }
            .set(tree, AutoSelectionStore(maxEntries = 1))
        mockkObject(AutoSuggestedQueueLoader)
        coEvery { AutoSuggestedQueueLoader.load(any(), any(), any(), any()) } returns listOf("first", "second").map {
            AutoSuggestedQueueLoader.SuggestedQueue(AutoSessionConstants.ID_SONGS_FAVORITES, it, null,
                MediaMetadata.MEDIA_TYPE_PLAYLIST, listOf(item(it)))
        }
        val result = await(first.getChildren(AutoSuggestedQueueLoader.ROOT_ID, 0, 10, null))
        assertEquals(0, result.resultCode)
        val card = requireNotNull(result.value).single()
        assertEquals("second", card.mediaMetadata.title)
        first.setMediaItem(card)
        pumpUntil { player.setCalls.size == 1 }
        assertEquals("second", player.currentMediaItem?.mediaId)
    }

    @Test fun `real browser search announces seven folders and pages through all categories`() {
        assertEquals(0, await(first.search("query/with slash", null)).resultCode)
        pumpUntil { searchCounts.isNotEmpty() }
        assertEquals(listOf(7), searchCounts)
        val pages = (0..3).flatMap { await(first.getSearchResult("query/with slash", it, 2, null)).value.orEmpty() }
        assertEquals(7, pages.size)
        assertEquals(7, pages.map { it.mediaId }.distinct().size)
        assertEquals(0, await(first.subscribe(pages.last().mediaId, null)).resultCode)
        assertTrue(await(first.getSearchResult("query/with slash", Int.MAX_VALUE, Int.MAX_VALUE, null)).value.orEmpty().isEmpty())
    }

    @Test fun `real browser standard pages reuse scoped rows and report full visible count`() {
        val tree = browseTree()
        val handler = mockk<BrowseHandler>()
        every { handler.handles(any()) } returns true
        var rows = (0 until 10).map { item("track-$it") }
        coEvery { handler.getChildren(any(), any(), any(), any(), any()) } answers { rows }
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply { isAccessible = true }.set(tree, listOf(handler))
        val firstPage = await(first.getChildren("album/paging", 0, 3, null)).value.orEmpty()
        val captured = requireNotNull(tree.selectionStore.resolve(firstPage.first().mediaId)).items
        rows = rows.reversed()
        await(first.getChildren("other-navigation", 0, 3, null))
        val secondPage = await(first.getChildren("album/paging", 1, 3, null)).value.orEmpty()
        assertEquals(captured.take(3).map { it.mediaId }, firstPage.map { AutoMediaIdContract.parseSelection(it.mediaId)?.songId })
        assertEquals(captured.drop(3).take(3).map { it.mediaId }, secondPage.map { AutoMediaIdContract.parseSelection(it.mediaId)?.songId })
        assertEquals(AutoMediaIdContract.parseSelection(firstPage.first().mediaId)?.token,
            AutoMediaIdContract.parseSelection(secondPage.first().mediaId)?.token)
        assertEquals(3, tree.selectionStore.resolve(secondPage.first().mediaId)?.startIndex)
        assertTrue(await(first.getChildren("album/paging", Int.MAX_VALUE, Int.MAX_VALUE, null)).value.orEmpty().isEmpty())
        val counts = AutoSessionCallback::class.java.getDeclaredField("browsedParents").apply { isAccessible = true }
        assertEquals(10, (counts.get(callback) as Map<*, *>)["album/paging"])
    }

    @Test fun `search and another browser cannot replace an existing standard page capture`() {
        val tree = browseTree()
        val handler = mockk<BrowseHandler>()
        every { handler.handles(any()) } returns true
        var rows = (0 until 10).map { item("track-$it") }
        coEvery { handler.getChildren(any(), any(), any(), any(), any()) } answers { rows }
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply { isAccessible = true }.set(tree, listOf(handler))
        val firstPage = await(first.getChildren("album/paging", 0, 3, null)).value.orEmpty()
        val other = await(MediaBrowser.Builder(context, session.token).buildAsync())
        try {
            rows = rows.reversed()
            await(other.getChildren("album/paging", 0, 3, null))
            assertEquals(0, await(first.search("new search", null)).resultCode)
            val laterPage = await(first.getChildren("album/paging", 1, 3, null)).value.orEmpty()
            assertEquals(listOf("track-3", "track-4", "track-5"),
                laterPage.map { AutoMediaIdContract.parseSelection(it.mediaId)?.songId })
            assertEquals(AutoMediaIdContract.parseSelection(firstPage.first().mediaId)?.token,
                AutoMediaIdContract.parseSelection(laterPage.first().mediaId)?.token)
            val otherPage = await(other.getChildren("album/paging", 1, 3, null)).value.orEmpty()
            assertEquals(listOf("track-6", "track-5", "track-4"),
                otherPage.map { AutoMediaIdContract.parseSelection(it.mediaId)?.songId })
        } finally {
            other.release()
        }
    }

    @Test fun `later standard page rejects expired selections until the browser reloads`() {
        val tree = browseTree()
        val handler = mockk<BrowseHandler>()
        every { handler.handles(any()) } returns true
        coEvery { handler.getChildren(any(), any(), any(), any(), any()) } returns
            (0 until 10).map { item("track-$it") }
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply { isAccessible = true }.set(tree, listOf(handler))
        await(first.getChildren("album/paging", 0, 3, null))
        repeat(32) { await(first.getChildren("album/refreshed", 0, 3, null)) }
        assertEquals(SessionError.ERROR_BAD_VALUE, await(first.getChildren("album/paging", 1, 3, null)).resultCode)
        assertEquals(0, await(first.getChildren("album/paging", 0, 3, null)).resultCode)
        val next = await(first.getChildren("album/paging", 1, 3, null))
        assertEquals(0, next.resultCode)
        assertEquals(3, tree.selectionStore.resolve(requireNotNull(next.value).first().mediaId)?.startIndex)
    }

    @Test fun `failed voice provider leaves the existing real session queue intact`() {
        val current = browseTree().selectionStore.capture("album/current", listOf(item("playing"))).single()
        first.setMediaItem(current)
        pumpUntil { player.setCalls.size == 1 }
        val songs = mockk<SongTable>()
        every { Database.songTable } returns songs
        every { songs.findAllTitleArtistContains("failed voice") } returns flowOf(emptyList())
        mockkStatic("it.fast4x.innertube.requests.SearchPageKt")
        coEvery { Innertube.searchPage<Innertube.SongItem>("failed voice", Innertube.SearchFilter.Song.value, any()) } throws IOException("provider unavailable")
        observed.selection = null
        first.setMediaItem(MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder()
            .setSearchQuery("failed voice").build()).build())
        pumpUntil { observed.selection?.isDone == true }
        assertThrows(ExecutionException::class.java) { requireNotNull(observed.selection).get() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("playing", player.currentMediaItem?.mediaId)
        assertEquals(1, player.setCalls.size)
    }

    @Test fun `real browser resolves selected voice and youtube request metadata to canonical queue`() {
        val songs = mockk<SongTable>()
        every { Database.songTable } returns songs
        every { songs.findAllTitleArtistContains("voice query") } returns flowOf(listOf(Song(id = "abcdefghijk", title = "Matched song", durationText = "3:00", thumbnailUrl = null)))
        every { songs.findById("abcdefghijk") } returns flowOf(Song(id = "abcdefghijk", title = "Matched song", durationText = "3:00", thumbnailUrl = null))
        val voice = MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("voice query").build()).build()
        first.setMediaItems(listOf(item("ignored"), voice), 1, 123L)
        pumpUntil { player.setCalls.size == 1 }
        assertEquals("abcdefghijk", player.currentMediaItem?.mediaId)
        assertEquals("Matched song", player.currentMediaItem?.mediaMetadata?.title)
        val link = MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder()
            .setMediaUri(Uri.parse("https://youtu.be/abcdefghijk")).build()).build()
        first.setMediaItem(link)
        pumpUntil { player.setCalls.size == 2 }
        assertEquals("abcdefghijk", player.currentMediaItem?.localConfiguration?.customCacheKey)
        assertEquals("abcdefghijk", player.currentMediaItem?.localConfiguration?.uri.toString())
    }

    @Test fun `superseded metadata source cannot replace newer real browser queue`() {
        val songs = mockk<SongTable>()
        every { Database.songTable } returns songs
        every { songs.findAllTitleArtistContains("slow voice") } returns flow {
            entered.countDown()
            withContext(NonCancellable) { gate.await() }
            sourceFinished.countDown()
            emit(listOf(Song.makePlaceholder("old-result")))
        }
        val voice = MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("slow voice").build()).build()
        first.setMediaItem(voice)
        pumpUntil { entered.count == 0L }
        val oldSelection = requireNotNull(observed.selection)
        val replacement = browseTree().selectionStore.capture("album/new", listOf(item("replacement"))).single()
        second.setMediaItem(replacement)
        pumpUntil { player.setCalls.size == 1 }
        assertTrue(oldSelection.isCancelled)
        gate.complete(Unit)
        assertTrue(sourceFinished.await(15, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("replacement", player.currentMediaItem?.mediaId)
        assertEquals(1, player.setCalls.size)
    }

    @Test fun `access invalidation during stored duration lookup cannot publish its queue`() =
        assertDurationLookupOwnership(invalidateAccess = true)

    @Test fun `newer selection supersedes an in flight stored duration lookup`() =
        assertDurationLookupOwnership(invalidateAccess = false)

    private fun assertDurationLookupOwnership(invalidateAccess: Boolean) {
        val songs = mockk<SongTable>()
        every { Database.songTable } returns songs
        val lookupEntered = CountDownLatch(1)
        val lookupRelease = CountDownLatch(1)
        val lookupFinished = CountDownLatch(1)
        every { songs.findByIdsDirect(listOf("duration-pending")) } answers {
            lookupEntered.countDown()
            try {
                assertTrue(lookupRelease.await(15, TimeUnit.SECONDS))
                listOf(Song("duration-pending", "Pending", durationText = "3:35", thumbnailUrl = null))
            } finally {
                lookupFinished.countDown()
            }
        }
        val stale = browseTree().selectionStore.capture("album/duration", listOf(item("duration-pending"))).single()
        try {
            first.setMediaItem(stale)
            pumpUntil { lookupEntered.count == 0L }
            val oldSelection = requireNotNull(observed.selection)
            if (invalidateAccess) callback.invalidateAccess(session) else {
                val known = item("replacement-duration").buildUpon().setMediaMetadata(
                    item("replacement-duration").mediaMetadata.buildUpon().setDurationMs(180_000L).build()).build()
                second.setMediaItem(browseTree().selectionStore.capture("album/new", listOf(known)).single())
                pumpUntil { player.setCalls.size == 1 }
            }
            assertTrue(oldSelection.isCancelled)
            lookupRelease.countDown()
            assertTrue(lookupFinished.await(15, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            if (invalidateAccess) assertTrue(player.setCalls.isEmpty()) else {
                assertEquals("replacement-duration", player.currentMediaItem?.mediaId)
                assertEquals(1, player.setCalls.size)
            }
        } finally {
            lookupRelease.countDown()
        }
    }

    @Test fun `access invalidation cancels pending request metadata before real queue publication`() {
        val songs = mockk<SongTable>()
        every { Database.songTable } returns songs
        every { songs.findAllTitleArtistContains("slow voice") } returns flow {
            entered.countDown()
            withContext(NonCancellable) { gate.await() }
            sourceFinished.countDown()
            emit(listOf(Song.makePlaceholder("old-result")))
        }
        val voice = MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("slow voice").build()).build()
        first.setMediaItem(voice)
        pumpUntil { entered.count == 0L }
        val oldSelection = requireNotNull(observed.selection)
        callback.invalidatePublication()
        gate.complete(Unit)
        assertTrue(sourceFinished.await(15, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(oldSelection.isCancelled)
        assertTrue(player.setCalls.isEmpty())
    }

    private fun browseTree(): AutoBrowseTree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").run {
        isAccessible = true
        get(callback) as AutoBrowseTree
    }

    private fun <T> await(future: ListenableFuture<T>): T {
        pumpUntil { future.isDone }
        return future.get(1, TimeUnit.SECONDS)
    }

    @Test
    fun `completed selection owns queue until real cross controller application`() {
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        val selection = requireNotNull(observed.selection)
        assertFalse(selection.isDone)
        // Modern controllers queue a real flush message at the end of their transaction.
        // Queue that message before DATA completion posts the queue-application runnable.
        second.play()
        assertTrue(observed.resumptions.isEmpty())
        gate.complete(Unit)
        selection.get(15, TimeUnit.SECONDS)
        assertEquals(0, player.mediaItemCount)
        pumpUntil { observed.resumptions.isNotEmpty() && player.playWhenReady }
        assertEquals(listOf("[explicit] index=0 position=999"), player.setCalls)
        assertEquals("explicit", player.currentMediaItem?.mediaId)
        assertEquals(999L, player.currentPosition)
        assertSame(observed.selection, observed.resumptions.single())
        report("completed-before-application", "selectionDoneBeforePumping=true")
    }

    @Test
    fun `pending selection shared by real consumers applies once and plays`() {
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        second.play()
        pumpUntil { observed.resumptions.isNotEmpty() }
        assertSame(observed.selection, observed.resumptions.single())
        assertFalse(requireNotNull(observed.selection).isDone)
        gate.complete(Unit)
        pumpUntil { player.setCalls.isNotEmpty() && player.playWhenReady }
        assertEquals(listOf("[explicit] index=0 position=999"), player.setCalls)
        report("shared-future", "sameFuture=true")
    }

    @Test
    fun `invalidated resumption cannot prepare or play intervening paused queue`() {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        val resume = observed.resumptions.single()
        assertFalse(resume.isDone)
        player.setMediaItems(listOf(item("intervening")), 0, 321L)
        assertFalse(player.playWhenReady)
        callback.invalidatePublication()
        assertTrue(resume.isCancelled)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(player.playWhenReady)
        assertFalse(player.trace.contains("prepare"))
        gate.complete(Unit)
        assertTrue(sourceFinished.await(15, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("[intervening] index=0 position=321"), player.setCalls)
        second.play()
        pumpUntil { player.playWhenReady }
        assertEquals(321L, player.currentPosition)
        report("invalidation-fallback", "resumptionCancelled=true; interveningQueueInitiallyPaused=true")
    }

    @Test
    fun `completed data invalidated before application cannot replace queue and later choice still applies`() {
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        gate.complete(Unit)
        requireNotNull(observed.selection).get(15, TimeUnit.SECONDS)
        assertTrue(player.setCalls.isEmpty())
        callback.invalidatePublication()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(player.setCalls.isEmpty())
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { player.setCalls.size == 1 }
        second.play()
        pumpUntil { player.playWhenReady }
        assertEquals("explicit", player.currentMediaItem?.mediaId)
    }

    @Test
    fun `identical deliberate selections apply twice across player facade recreation`() {
        gate.complete(Unit)
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { player.setCalls.size == 1 }
        // Service crossfade replaces the player facade while retaining this callback owner.
        session.player = guardedSessionPlayer()
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { player.setCalls.size == 2 }
        assertEquals(listOf("[explicit] index=0 position=999", "[explicit] index=0 position=999"), player.setCalls)
        second.play()
        pumpUntil { player.playWhenReady }
    }

    @Test
    fun `raw phone playlist retires pending saved resume without policy invalidation`() {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        player.setMediaItems(listOf(item("phone")), 0, 222L)
        gate.complete(Unit)
        assertTrue(sourceFinished.await(15, TimeUnit.SECONDS))
        requireNotNull(observed.resumptions.single()).get(15, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("[phone] index=0 position=222"), player.setCalls)
        assertFalse(player.playWhenReady)
        assertFalse(player.trace.contains("prepare"))
        second.play()
        pumpUntil { player.playWhenReady }
        assertEquals("phone", player.currentMediaItem?.mediaId)
    }

    @Test
    fun `old upstream timeline after facade rebind cannot retire new pending choice`() {
        val previousPlayer = player
        player = RecordingPlayer()
        session.player = guardedSessionPlayer()
        try {
            first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
            pumpUntil { entered.count == 0L }
            previousPlayer.setMediaItems(listOf(item("old-upstream")), 0, 11L)
            gate.complete(Unit)
            pumpUntil { player.setCalls.size == 1 }
            assertEquals("explicit", player.currentMediaItem?.mediaId)
            second.play()
            pumpUntil { player.playWhenReady }
        } finally {
            previousPlayer.release()
        }
    }

    @Test fun `failed real explicit selection permits next Play to recover saved history`() {
        failGatedSelection = true
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        gate.complete(Unit)
        assertThrows(ExecutionException::class.java) {
            requireNotNull(observed.selection).get(15, TimeUnit.SECONDS)
        }
        assertFalse(requireNotNull(observed.selection).isCancelled)
        second.play()
        pumpUntil { player.playWhenReady && player.setCalls.isNotEmpty() }
        assertEquals(listOf("[saved] index=0 position=456"), player.setCalls)
        assertEquals("saved", player.currentMediaItem?.mediaId)
    }

    @Test fun `Pause during saved resumption retains saved queue without late playback`() =
        assertLatestTransport(stop = false, explicit = false)

    @Test fun `Stop during saved resumption retains saved queue without late playback`() =
        assertLatestTransport(stop = true, explicit = false)

    @Test fun `Pause during explicit selection and Play retains chosen queue without saved fallback`() =
        assertLatestTransport(stop = false, explicit = true)

    @Test fun `Stop during explicit selection and Play retains chosen queue without saved fallback`() =
        assertLatestTransport(stop = true, explicit = true)

    private fun assertLatestTransport(stop: Boolean, explicit: Boolean) {
        gatedId = if (explicit) "explicit" else "saved"
        if (explicit) {
            first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
            pumpUntil { entered.count == 0L }
        }
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        val resume = observed.resumptions.single()
        if (explicit) assertSame(observed.selection, resume)
        assertTrue(transport.isCommandAvailable(if (stop) Player.COMMAND_STOP else Player.COMMAND_PLAY_PAUSE))
        // A separate controller delivers the newer transport command while Play awaits data.
        if (stop) transport.stop() else transport.pause()
        pumpUntil { if (stop) "stop" in player.trace else player.trace.any { it.startsWith("play(false)") } }
        gate.complete(Unit)
        resume.get(15, TimeUnit.SECONDS)
        pumpUntil { player.setCalls.size == 1 }
        assertFalse(player.playWhenReady)
        assertFalse("Old completion must not prepare after Pause/Stop", "prepare" in player.trace)
        assertEquals(if (explicit) "explicit" else "saved", player.currentMediaItem?.mediaId)
        assertEquals(if (explicit) 999L else 456L, player.currentPosition)
        // Policy invalidation after the data was chosen cannot revive the old Play fallback.
        callback.invalidatePublication()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(player.playWhenReady)
        second.play()
        pumpUntil { player.playWhenReady }
        assertEquals(1, player.setCalls.size)
        assertEquals(if (explicit) "explicit" else "saved", player.currentMediaItem?.mediaId)
        assertEquals(if (explicit) 999L else 456L, player.currentPosition)
    }

    @Test fun `new Play after Pause before explicit completion reuses chosen queue and plays`() {
        first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
        pumpUntil { entered.count == 0L }
        second.play()
        pumpUntil { observed.resumptions.size == 1 }
        transport.pause()
        pumpUntil { player.trace.any { it.startsWith("play(false)") } }
        transport.play()
        pumpUntil { observed.resumptions.size == 2 }
        assertSame(observed.selection, observed.resumptions.last())
        gate.complete(Unit)
        pumpUntil { player.playWhenReady && player.setCalls.size == 1 }
        assertEquals("explicit", player.currentMediaItem?.mediaId)
        assertEquals(999L, player.currentPosition)
    }

    @Test fun `shared phone Pause on empty paused player suppresses late saved resumption`() =
        assertSharedTransport(stop = false, explicit = false)

    @Test fun `phone graceful Pause without fade suppresses pending saved resumption and graceful Play prepares`() =
        assertGracefulPauseDuringResumption(duration = 0L)

    @Test fun `phone graceful Pause with fade configured suppresses pending saved resumption and graceful Play prepares`() =
        assertGracefulPauseDuringResumption(duration = 500L)

    private fun assertGracefulPauseDuringResumption(duration: Long) {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        assertEquals(0, player.mediaItemCount)
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertFalse(player.isPlaying)
        assertFalse(player.playWhenReady)
        // Same modern adapter as Binder.gracefulPause, including the legacy no-audio branch.
        sharedGuard.pauseWithFade(duration)
        gate.complete(Unit)
        observed.resumptions.single().get(15, TimeUnit.SECONDS)
        pumpUntil { player.setCalls.size == 1 }
        assertEquals(listOf("[saved] index=0 position=456"), player.setCalls)
        assertFalse(player.playWhenReady)
        assertFalse("Late resumption must not prepare", "prepare" in player.trace)
        // Binder.gracefulPlay uses this actual helper; merely setting playWhenReady would
        // leave the queued player IDLE and would not prove a usable later phone Play.
        sharedGuard.fadeInEffect(duration)
        assertTrue(player.playWhenReady)
        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(1, player.trace.count { it == "prepare" })
        assertEquals(1, player.setCalls.size)
        assertEquals("saved", player.currentMediaItem?.mediaId)
        assertEquals(456L, player.currentPosition)
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
    }

    @Test fun `shared phone Stop preserves explicit queue without late playback`() =
        assertSharedTransport(stop = true, explicit = true)

    @Test fun `shared readiness false preserves explicit queue without late playback`() =
        assertSharedTransport(stop = false, explicit = true, readiness = true)

    private fun assertSharedTransport(stop: Boolean, explicit: Boolean, readiness: Boolean = false) {
        gatedId = if (explicit) "explicit" else "saved"
        if (explicit) {
            first.setMediaItems(listOf(capturedExplicit()), 0, 999L)
            pumpUntil { entered.count == 0L }
        }
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        assertEquals(0, player.mediaItemCount)
        assertFalse(player.playWhenReady)
        // Exactly the guard exposed by Binder.player and used by the noisy-audio receiver.
        // SimpleBasePlayer may no-op an already-paused empty queue; intent must still count.
        if (stop) sharedGuard.stop() else if (readiness) sharedGuard.playWhenReady = false else sharedGuard.pause()
        gate.complete(Unit)
        observed.resumptions.single().get(15, TimeUnit.SECONDS)
        pumpUntil { player.setCalls.size == 1 }
        assertFalse(player.playWhenReady)
        assertFalse("Old completion must not prepare after shared transport", "prepare" in player.trace)
        assertEquals(if (explicit) "explicit" else "saved", player.currentMediaItem?.mediaId)
        assertEquals(if (explicit) 999L else 456L, player.currentPosition)
        sharedGuard.play()
        assertTrue(player.playWhenReady)
        assertEquals(1, player.setCalls.size)
    }

    @Test fun `guest blocked Stop does not veto pending valid Play after unlock`() {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        listenTogetherGuestLock.value = true
        sharedGuard.stop()
        assertFalse("stop" in player.trace)
        listenTogetherGuestLock.value = false
        gate.complete(Unit)
        pumpUntil { player.setCalls.size == 1 && player.playWhenReady }
        assertEquals("saved", player.currentMediaItem?.mediaId)
    }

    @Test fun `guest blocked helper tail cannot rearm paused pending completion`() {
        gatedId = "saved"
        second.play()
        pumpUntil { entered.count == 0L && observed.resumptions.isNotEmpty() }
        val guestIntents = mutableListOf<Boolean>()
        sharedGuard.onGuestPlayPause = { guestIntents += it }
        listenTogetherGuestLock.value = true
        sharedGuard.pause()
        sharedGuard.setMediaItem(item("blocked"))
        sharedGuard.prepare()
        sharedGuard.playWhenReady = true
        sharedGuard.play()
        assertEquals(listOf(false), guestIntents)
        listenTogetherGuestLock.value = false
        gate.complete(Unit)
        pumpUntil { player.setCalls.size == 1 }
        assertFalse(player.playWhenReady)
        assertFalse("prepare" in player.trace)
        assertEquals("saved", player.currentMediaItem?.mediaId)
        listenTogetherGuestLock.value = true
        guardClock += ListenTogetherGuestGuardPlayer.TAIL_WINDOW_MS + 1
        sharedGuard.play()
        assertTrue(player.playWhenReady)
        assertEquals(listOf(false, true), guestIntents)
    }

    private fun guardedSessionPlayer(): Player {
        sharedGuard = ListenTogetherGuestGuardPlayer(player, context, clock = { guardClock }, toastSink = {})
        return callback.wrapPlayer(sharedGuard)
    }

    private fun capturedExplicit(): MediaItem {
        val field = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply { isAccessible = true }
        val tree = field.get(callback) as AutoBrowseTree
        return tree.selectionStore.capture("album/real-session", listOf(item("explicit"))).single()
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id")
        .setCustomCacheKey(id).setMediaMetadata(MediaMetadata.Builder().setTitle(id).setIsPlayable(true).setIsBrowsable(false).build()).build()

    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Real session did not reach expected diagnostic checkpoint: ${player.trace}", condition())
    }

    private fun report(name: String, setup: String) {
        val path = File("build/reports/automotive-real-session/$name.txt")
        requireNotNull(path.parentFile).mkdirs()
        path.writeText(buildString {
            appendLine("QUEUE APPLICATION REGRESSION - ASSERTIONS PASSED")
            appendLine("Transport: real in-process modern MediaController + MediaSession + SimpleBasePlayer")
            appendLine("Legacy Android platform/Compat transport: unsupported by this fixture")
            appendLine(setup)
            appendLine("setCalls=${player.setCalls}")
            appendLine("finalQueue=${(0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }}")
            appendLine("finalIndex=${player.currentMediaItemIndex}; finalPosition=${player.currentPosition}; playWhenReady=${player.playWhenReady}")
            appendLine("trace:")
            player.trace.forEach { appendLine(it) }
        })
    }

    private class ObservedCallback(private val delegate: AutoSessionCallback) : MediaLibrarySession.Callback by delegate {
        override fun onConnectAsync(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.ConnectionResult> =
            delegate.onConnectAsync(session, controller)

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = delegate.onGetLibraryRoot(session, browser, params)

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = delegate.onGetItem(session, browser, mediaId)

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            delegate.onGetChildren(session, browser, parentId, page, pageSize, params)

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = delegate.onSearch(session, browser, query, params)

        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            delegate.onGetSearchResult(session, browser, query, page, pageSize, params)

        override fun onSubscribe(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = delegate.onSubscribe(session, browser, parentId, params)

        var selection: ListenableFuture<MediaSession.MediaItemsWithStartPosition>? = null
        val resumptions = mutableListOf<ListenableFuture<MediaSession.MediaItemsWithStartPosition>>()
        override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
            items: MutableList<MediaItem>, index: Int, positionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            delegate.onSetMediaItems(session, controller, items, index, positionMs).also { selection = it }

        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            delegate.onPlaybackResumption(session, controller, isForPlayback).also { resumptions += it }
    }

    private class RecordingPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        val trace = mutableListOf<String>()
        val setCalls = mutableListOf<String>()
        private var queueIds = emptyList<String>()
        private var state = State.Builder().setAvailableCommands(Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP, Player.COMMAND_RELEASE
        ).build()).build()

        init {
            addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) { trace += "timeline reason=$reason count=${timeline.windowCount}" }
                override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                    trace += "discontinuity reason=$reason ${old.positionMs}->${new.positionMs}"
                }
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) { trace += "transition reason=$reason id=${item?.mediaId}" }
                override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) { trace += "playWhenReady=$value reason=$reason" }
            })
        }

        override fun getState(): State = state
        override fun handleSetMediaItems(items: MutableList<MediaItem>, index: Int, positionMs: Long): ListenableFuture<*> {
            val call = "${items.map { it.mediaId }} index=$index position=$positionMs"
            setCalls += call
            trace += "set $call"
            queueIds = items.map { it.mediaId }
            // Distinct timeline UIDs deliberately model a player rebuilding its queue on every set.
            state = state.buildUpon().setPlaylist(items.map { MediaItemData.Builder(Any()).setMediaItem(it).build() })
                .setCurrentMediaItemIndex(if (index == C.INDEX_UNSET) 0 else index)
                .setContentPositionMs(if (positionMs == C.TIME_UNSET) 0 else positionMs).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetPlayWhenReady(value: Boolean): ListenableFuture<*> {
            trace += "play($value) queue=$queueIds"
            state = state.buildUpon().setPlayWhenReady(value, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            return Futures.immediateVoidFuture()
        }

        override fun handlePrepare(): ListenableFuture<*> {
            trace += "prepare"
            state = state.buildUpon().setPlaybackState(Player.STATE_READY).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            trace += "stop"
            // Player.stop retains the playlist and position, and returns to IDLE.
            state = state.buildUpon().setPlaybackState(Player.STATE_IDLE).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> {
            trace += "release"
            return Futures.immediateVoidFuture()
        }
    }
}
