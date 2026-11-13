package app.n_zik.android.playback.services

import android.content.Context
import android.content.ContentResolver
import android.content.ContextWrapper
import android.database.sqlite.SQLiteConstraintException
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.models.Album
import app.n_zik.android.models.QueuedMediaItem
import app.n_zik.android.models.Song
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.resumePlaybackOnStartKey
import app.n_zik.android.utils.persistentQueueKey
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.listentogether.ListenTogetherGuestGuardPlayer
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.automotive.session.AutoSessionCallback
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersistentQueueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: DatabaseInitializer

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `failed replacement leaves the old queue with its duplicates and position`() = runBlocking {
        val queue = db.queueTable
        queue.replaceAll(listOf(row("z"), row("a", 7200L), row("z")))
        assertThrows(SQLiteConstraintException::class.java) {
            queue.replaceAll(listOf(row("partial", id = 20), row("duplicate-key", id = 20)))
        }
        val expected = listOf("z" to null, "a" to 7200L, "z" to null)
        assertEquals(expected, queue.allDirect().map { it.mediaItem.mediaId to it.position })
        assertEquals(expected, queue.all().first().map { it.mediaItem.mediaId to it.position })
    }

    @Test
    fun `saved provider token restores the original artwork without inline bytes`() {
        val original = "https://example.com/cover.jpg"
        val saved = mediaItem("a", providerUri("source/obsolete"), original).let { item ->
            item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
                .setArtworkData(byteArrayOf(1, 2, 3), null).build()).build()
        }
        db.queueTable.replaceAll(listOf(QueuedMediaItem(mediaItem = saved, position = 456L)))
        val row = db.queueTable.allDirect().single()
        val restored = PersistentQueueArtwork.restore(context, "default", row.mediaItem) {
            error("A usable original should avoid a library lookup")
        }
        assertEquals(Uri.parse(original), restored.mediaMetadata.artworkUri)
        assertEquals(456L, row.position)
        assertNull(restored.mediaMetadata.artworkData)
        assertTrue(requireNotNull(restored.mediaMetadata.extras).getBoolean("isFromPersistentQueue"))
    }

    @Test
    fun `unsigned remote payload and a missing file fall back to the library`() {
        val artwork = "https://example.com/library.jpg"
        db.songTable.upsert(Song(id = "a", title = "a", thumbnailUrl = artwork, durationText = null))
        val unsigned = providerUri("remote").buildUpon()
            .appendQueryParameter("u", "https://example.com/untrusted.jpg").build()
        val missingFile = File(context.cacheDir, "missing-queue-cover.jpg").toURI().toString()
        val saved = mediaItem("a", unsigned, missingFile)
        val restored = restore(saved)
        assertEquals(Uri.parse(artwork), restored.mediaMetadata.artworkUri)
        assertEquals(artwork, PersistentQueueArtwork.originalSource(restored))
    }

    @Test
    fun `missing artwork leaves the track intact and uses the placeholder`() {
        val saved = mediaItem("a", providerUri("source/obsolete"))
        val restored = restore(saved)
        assertEquals(saved.mediaId, restored.mediaId)
        assertEquals(saved.mediaMetadata.title, restored.mediaMetadata.title)
        assertEquals(drawableUri(context, R.drawable.ic_launcher_box), restored.mediaMetadata.artworkUri)
        assertNull(PersistentQueueArtwork.originalSource(restored))
    }

    @Test
    fun `failed library artwork lookup leaves the track in the queue`() {
        val item = mediaItem("a", providerUri("source/obsolete"))
        val restored = PersistentQueueArtwork.restore(context, "default", item) {
            throw IllegalStateException("Library read failed")
        }
        assertEquals("a", restored.mediaId)
        assertEquals(drawableUri(context, R.drawable.ic_launcher_box), restored.mediaMetadata.artworkUri)
    }

    @Test
    fun `live remote token is serialized with a portable original`() {
        val original = "https://example.com/original.jpg"
        val token = requireNotNull(ArtworkContentProvider.uriForRemote(original, "default", false, 1))
        val saved = PersistentQueueArtwork.preserveSnapshotSource(context, "default", mediaItem("a", token))
        db.queueTable.replaceAll(listOf(QueuedMediaItem(mediaItem = saved, position = 765L)))
        assertEquals(original, PersistentQueueArtwork.originalSource(db.queueTable.allDirect().single().mediaItem))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `modified per-song cover persists as its file reference`() {
        val file = File(coversDir(context, "default"), "cover_portable.jpg")
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            mockkObject(DatabaseInitializer.Companion)
            mockkStatic("app.n_zik.android.GlobalVarsKt")
            every { DatabaseInitializer.Instance } returns db
            every { appContext() } returns context
            val item = mediaItem(
                "portable", ArtworkContentProvider.uriFor("portable"),
                "https://example.com/original.jpg", modified = true
            )
            val preparedArtwork = Database.prepareArtworkForInsert(item)
            Database.syncTransaction { insertIgnore(item, autoFix = false, preparedArtwork = preparedArtwork) }
            assertEquals(MODIFIED_PREFIX + Uri.fromFile(file), db.songTable.findByIdDirect("portable")?.thumbnailUrl)
        } finally {
            unmockkStatic("app.n_zik.android.GlobalVarsKt")
            unmockkObject(DatabaseInitializer.Companion)
            bitmap.recycle()
            file.delete()
        }
    }

    @Test
    fun `corrupt local cover and unbacked song route yield to library artwork`() {
        val cover = "https://example.com/library.jpg"
        val file = File.createTempFile("corrupt-artwork", ".jpg", context.cacheDir)
        try {
            file.writeText("invalid image")
            val saved = mediaItem("a", ArtworkContentProvider.uriFor("a"), Uri.fromFile(file).toString())
            val restored = PersistentQueueArtwork.restore(context, "default", saved) { cover }
            assertEquals(Uri.parse(cover), restored.mediaMetadata.artworkUri)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `durable saved artwork is preserved`() {
        val artwork = Uri.parse("https://example.com/display.jpg")
        val saved = mediaItem("a", artwork, "https://example.com/other.jpg")
        val restored = PersistentQueueArtwork.restore(context, "default", saved) {
            error("A durable saved URI should avoid a library lookup")
        }
        assertEquals(artwork, restored.mediaMetadata.artworkUri)
        assertEquals("https://example.com/other.jpg", PersistentQueueArtwork.originalSource(restored))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `legacy registered file token is recovered while its file remains readable`() {
        val file = File.createTempFile("legacy-artwork", ".jpg", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val token = ArtworkContentProvider.uriForSource(context, "a", file)
            val restored = PersistentQueueArtwork.restore(context, "default", mediaItem("a", token)) {
                error("A live token should avoid a library lookup")
            }
            assertEquals(Uri.fromFile(file), restored.mediaMetadata.artworkUri)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `unreadable local artwork falls back with the library cover marker`() {
        val artwork = "https://example.com/library.jpg"
        val unavailable = Uri.parse("content://missing-provider/cover")
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(unavailable) } throws IllegalArgumentException()
        val localContext = contextWithResolver(resolver)
        val stale = mediaItem("a", unavailable, null, modified = true)
        db.songTable.upsert(Song(id = "a", title = "a", thumbnailUrl = artwork, durationText = null))
        assertFalse(requireNotNull(restore(stale, localContext).mediaMetadata.extras).getBoolean("isCoverModified"))
        db.songTable.upsert(Song(id = "a", title = "a", thumbnailUrl = MODIFIED_PREFIX + artwork, durationText = null))
        val restored = restore(mediaItem("a", unavailable), localContext)
        assertEquals(Uri.parse(artwork), restored.mediaMetadata.artworkUri)
        assertTrue(requireNotNull(restored.mediaMetadata.extras).getBoolean("isCoverModified"))
        assertEquals(MODIFIED_PREFIX + artwork, PersistentQueueArtwork.originalSource(restored))
    }

    @Test
    fun `prefixed original repairs the song without propagating its custom cover to the album`() {
        val token = providerUri("source/obsolete")
        val custom = MODIFIED_PREFIX + "https://example.com/custom.jpg"
        val albumCover = "https://example.com/album.jpg"
        db.albumTable.upsert(Album(id = "album", title = "Album", thumbnailUrl = albumCover))
        mockkObject(DatabaseInitializer.Companion)
        try {
            every { DatabaseInitializer.Instance } returns db
            Database.insertIgnore(mediaItem("a", token, custom, albumId = "album"), autoFix = false)
            Database.insertIgnore(mediaItem("b", token, custom, albumId = "new-album"), autoFix = false)
            assertEquals(custom, db.songTable.findByIdDirect("a")?.thumbnailUrl)
            assertEquals(albumCover, db.albumTable.findByIdDirect("album")?.thumbnailUrl)
            assertNull(requireNotNull(db.albumTable.findByIdDirect("new-album")).thumbnailUrl)
        } finally {
            unmockkObject(DatabaseInitializer.Companion)
        }
    }

    @Test
    fun `service destruction saves live selection before the next service restores idle or resumes`() {
        db.queueTable.replaceAll(listOf(row("old", 1L)))
        val executorGate = CountDownLatch(1)
        val timeline = mockk<Timeline>()
        val cover = "https://example.com/final.jpg"
        val items = listOf(
            mediaItem("explicit").let {
                val extras = Bundle(it.mediaMetadata.extras).apply { putBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT", true) }
                it.buildUpon().setMediaMetadata(it.mediaMetadata.buildUpon().setExtras(extras).build()).build()
            },
            mediaItem("a", providerUri("source/obsolete"), cover),
            mediaItem("a")
        )
        every { timeline.windowCount } returns items.size
        every { timeline.getWindow(any(), any()) } answers {
            secondArg<Timeline.Window>().apply { mediaItem = items[firstArg<Int>()] }
        }
        val oldPlayer = mockk<ExoPlayer>(relaxed = true)
        every { oldPlayer.currentTimeline } returns timeline
        every { oldPlayer.currentMediaItemIndex } returns 2
        every { oldPlayer.currentPosition } returns 6543L
        val oldService = service(oldPlayer)
        mockkObject(DatabaseInitializer.Companion)
        mockkObject(NetworkQualityHelper)
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        try {
            every { DatabaseInitializer.Instance } returns db
            every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
            every { appContext() } returns context
            Database.asyncTransaction { check(executorGate.await(10, TimeUnit.SECONDS)) }
            oldService.setField("playbackInitializationStarted", true)
            oldService.onDestroy()
            verifyOrder { oldPlayer.currentPosition; oldPlayer.release() }

            context.preferences.edit().putBoolean(persistentQueueKey, true).commit()
            val auto = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
            val autoResult = auto.onPlaybackResumption(mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(), false)
            assertFalse(autoResult.isDone)
            for (scenario in listOf("idle", "resume", "live", "profile-change")) {
                val resume = scenario == "resume"
                val liveQueue = scenario == "live"
                val profileChange = scenario == "profile-change"
                val newPlayer = mockk<ExoPlayer>(relaxed = true)
                val guest = mockk<ListenTogetherGuestGuardPlayer>(relaxed = true)
                every { guest.playbackState } returns Player.STATE_IDLE
                val published = CountDownLatch(1)
                every { newPlayer.mediaItemCount } answers {
                    published.countDown()
                    if (liveQueue) 1 else 0
                }
                val service = service(newPlayer)
                service.setField("guestGuardPlayer", guest)
                service.preferences.edit().putBoolean(parentalControlEnabledKey, false)
                    .putBoolean(resumePlaybackOnStartKey, !resume).commit()
                PlayerServiceModern::class.java.getDeclaredMethod("maybeRestorePlayerQueue").apply {
                    isAccessible = true
                    invoke(service)
                }
                service.preferences.edit().putBoolean(parentalControlEnabledKey, true)
                    .putBoolean(resumePlaybackOnStartKey, resume).commit()
                if (profileChange) context.profilePreferences.edit().putString(activeProfileKey, "other").commit()
                executorGate.countDown()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while ((if (profileChange) shadowOf(Looper.getMainLooper()).isIdle else published.count > 0) &&
                    System.nanoTime() < deadline
                ) {
                    shadowOf(Looper.getMainLooper()).idle()
                    Thread.sleep(5)
                }
                if (!profileChange) assertEquals(0L, published.count)
                verify(exactly = if (liveQueue || profileChange) 0 else 1) {
                    newPlayer.setMediaItems(match { queue ->
                        queue.map { it.mediaId } == listOf("a", "a") && queue.first().mediaMetadata.artworkUri == Uri.parse(cover)
                    }, 1, 6543L)
                }
                if (liveQueue || profileChange) {
                    verify(exactly = 0) { newPlayer.setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>()) }
                }
                verify(exactly = if (resume) 1 else 0) { guest.prepare() }
                verify(exactly = if (resume) 1 else 0) { guest.play() }
                service.onDestroy()
            }
            assertEquals(6543L, autoResult.get(10, TimeUnit.SECONDS).startPositionMs)
            auto.release()
        } finally {
            executorGate.countDown()
            context.profilePreferences.edit().remove(activeProfileKey).commit()
            unmockkStatic("app.n_zik.android.GlobalVarsKt")
            unmockkObject(NetworkQualityHelper)
            unmockkObject(DatabaseInitializer.Companion)
            context.preferences.edit().remove(parentalControlEnabledKey).remove(resumePlaybackOnStartKey)
                .remove(persistentQueueKey).commit()
        }
    }

    @Test
    fun `clearing the last track replaces the saved queue with an empty snapshot`() {
        db.queueTable.replaceAll(listOf(row("old", 1000L)))
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.currentTimeline } returns Timeline.EMPTY
        val service = service(player)
        mockkObject(DatabaseInitializer.Companion)
        try {
            every { DatabaseInitializer.Instance } returns db
            service.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
            assertEquals(listOf("old"), savedQueueAfterWrites().map { it.mediaItem.mediaId })

            service.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            assertTrue(savedQueueAfterWrites().isEmpty())
        } finally {
            service.onDestroy()
            unmockkObject(DatabaseInitializer.Companion)
        }
    }

    private fun savedQueueAfterWrites(): List<QueuedMediaItem> {
        val result = java.util.concurrent.CompletableFuture<List<QueuedMediaItem>>()
        db.transactionExecutor.execute {
            result.complete(db.queueTable.allDirect())
        }
        return result.get(10, TimeUnit.SECONDS)
    }

    private fun service(player: ExoPlayer) = Robolectric.buildService(PlayerServiceModern::class.java).get().apply {
        this.player = player
        setField("isPersistentQueueEnabled", true)
    }

    private fun PlayerServiceModern.setField(name: String, value: Any) {
        PlayerServiceModern::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setField, value)
        }
    }

    @Test
    fun `library insertion preserves valid covers and repairs stale covers only from durable artwork`() {
        val token = providerUri("source/obsolete")
        val valid = "https://example.com/existing.jpg"
        val durable = "https://example.com/original.jpg"
        db.songTable.upsert(Song(id = "existing", title = "Existing", thumbnailUrl = valid, durationText = null))
        db.songTable.upsert(Song(id = "stale", title = "Stale", thumbnailUrl = token.toString(), durationText = null))
        val deletedFile = Uri.fromFile(File(context.cacheDir, "deleted.jpg")).toString()
        db.songTable.upsert(Song(id = "deleted", title = "Deleted", thumbnailUrl = deletedFile, durationText = null))
        val custom = MODIFIED_PREFIX + valid
        db.songTable.upsert(Song(id = "custom", title = "Custom", thumbnailUrl = custom, durationText = null))

        mockkObject(DatabaseInitializer.Companion)
        try {
            every { DatabaseInitializer.Instance } returns db
            Database.insertIgnore(mediaItem("existing", token, durable), autoFix = false)
            Database.insertIgnore(mediaItem("stale", token, durable), autoFix = false)
            Database.insertIgnore(mediaItem("deleted", token, durable), autoFix = false)
            Database.insertIgnore(mediaItem("custom", Uri.parse(durable)), autoFix = false)
            Database.insertIgnore(mediaItem("new", token), autoFix = false)
            assertEquals(valid, db.songTable.findByIdDirect("existing")?.thumbnailUrl)
            assertEquals(durable, db.songTable.findByIdDirect("stale")?.thumbnailUrl)
            assertEquals(durable, db.songTable.findByIdDirect("deleted")?.thumbnailUrl)
            assertEquals(custom, db.songTable.findByIdDirect("custom")?.thumbnailUrl)
            assertNull(requireNotNull(db.songTable.findByIdDirect("new")).thumbnailUrl)
        } finally {
            unmockkObject(DatabaseInitializer.Companion)
        }
    }

    private fun contextWithResolver(resolver: ContentResolver): Context =
        object : ContextWrapper(context) {
            override fun getContentResolver(): ContentResolver = resolver
        }

    private fun restore(item: MediaItem, artworkContext: Context = context): MediaItem =
        PersistentQueueArtwork.restore(artworkContext, "default", item) { db.songTable.findByIdDirect(it)?.thumbnailUrl }

    private fun row(mediaId: String, position: Long? = null, id: Long = 0) =
        QueuedMediaItem(id = id, mediaItem = mediaItem(mediaId), position = position)

    private fun providerUri(path: String) = Uri.parse("content://${ArtworkContentProvider.AUTHORITY}/$path")

    private fun mediaItem(
        id: String,
        artwork: Uri? = null,
        original: String? = null,
        modified: Boolean = false,
        albumId: String? = null,
    ): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(MediaMetadata.Builder()
            .setTitle(id)
            .setArtworkUri(artwork)
            .setExtras(Bundle().apply {
                putString(ORIGINAL_ARTWORK_SOURCE_EXTRA, original)
                putBoolean("isCoverModified", modified)
                putString("albumId", albumId)
            })
            .build()).build()
}
