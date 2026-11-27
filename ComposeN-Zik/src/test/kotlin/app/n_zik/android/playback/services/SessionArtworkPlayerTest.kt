package app.n_zik.android.playback.services

import android.app.Application
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.models.Info
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.utils.asSmartShuffleRecommendation
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.writeProfileEntries
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The session facade publishes the metadata every session surface reads (Android Auto home card, media notification — both go through `session.getPlayer().getMediaMetadata()`): blank title/artist fields must become the localized unknown placeholders, while present metadata and the existing artwork-URI swap are left untouched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionArtworkPlayerTest {

    private lateinit var app: Application

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun stubAppContext() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        app = ApplicationProvider.getApplicationContext<Application>()
        every { appContext() } returns app
    }

    private fun emptyUpstream(): Player {
        val upstream = mockk<Player>()
        every { upstream.getMediaMetadata() } returns MediaMetadata.Builder().build()
        every { upstream.currentMediaItem } returns null
        every { upstream.isCurrentMediaItemLive } returns false
        return upstream
    }

    private fun upstreamWith(title: String?, artist: String?, mediaId: String): Player {
        val upstream = mockk<Player>()
        every { upstream.getMediaMetadata() } returns MediaMetadata.Builder()
            .apply {
                title?.let { setTitle(it) }
                artist?.let { setArtist(it) }
            }
            .setArtworkUri("https://img.example.com/cover.jpg".toUri())
            .build()
        every { upstream.currentMediaItem } returns MediaItem.Builder().setMediaId(mediaId).build()
        // A loaded track is treated as "music" only when the player is not IDLE
        // (paused / buffering / ready — the session surfaces only matter then).
        every { upstream.playbackState } returns Player.STATE_READY
        every { upstream.isCurrentMediaItemLive } returns false
        every { upstream.duration } returns C.TIME_UNSET
        return upstream
    }

    private fun idleUpstreamWith(mediaId: String): Player {
        val upstream = mockk<Player>()
        every { upstream.getMediaMetadata() } returns MediaMetadata.Builder().build()
        every { upstream.currentMediaItem } returns MediaItem.Builder().setMediaId(mediaId).build()
        every { upstream.playbackState } returns Player.STATE_IDLE
        every { upstream.isCurrentMediaItemLive } returns false
        every { upstream.duration } returns C.TIME_UNSET
        return upstream
    }

    @Test
    fun `smart shuffle preserves the artists used by the car display`() {
        stubAppContext()
        val upstream = upstreamWith("Heartless", "Kanye West", "recommended")
        val source = MediaItem.Builder().setMediaId("recommended").setMediaMetadata(upstream.mediaMetadata)
            .build().asSmartShuffleRecommendation(true)
        every { upstream.currentMediaItem } returns source
        val session = SessionArtworkPlayer(upstream)

        val standard = session.mediaMetadata
        assertNull(standard.displayTitle)
        assertEquals("Kanye West", standard.artist.toString())
        assertEquals("🔀✨ Heartless", standard.title.toString())

        val customTitle = source.mediaMetadata.buildUpon().setDisplayTitle("Heartless").build()
        every { upstream.mediaMetadata } returns customTitle
        val custom = session.mediaMetadata
        assertEquals("🔀✨ Heartless", custom.displayTitle.toString())
        assertEquals("Kanye West", custom.subtitle.toString())
    }

    @Test
    fun `the car queue decorates only the projected recommendation`() {
        stubAppContext()
        val upstream = upstreamWith("Track", "Artist", "recommended")
        val media = Song("recommended", "e:Track", "Artist", "1:00", null)
            .asMediaItem.asSmartShuffleRecommendation(true)
        val timeline = SinglePeriodTimeline(60_000_000, true, false, false, null, media)
        every { upstream.currentTimeline } returns timeline
        val session = SessionArtworkPlayer(upstream)

        val row = session.currentTimeline.getWindow(0, Timeline.Window()).mediaItem
        assertEquals("🔀✨ 🅴 Track", row.mediaMetadata.title.toString())
        assertEquals("Artist", row.mediaMetadata.artist.toString())
        assertEquals(media.mediaId, row.mediaId)
        assertEquals(media.mediaMetadata.title, timeline.getWindow(0, Timeline.Window()).mediaItem.mediaMetadata.title)
    }

    @Test
    fun `phone retains the original file and the session publishes its seekable source without artwork bytes`() {
        stubAppContext()
        val file = File.createTempFile("session-artwork-source", ".jpg", app.cacheDir)
        try {
            val source = file.toURI().toString()
            val song = Song("stream-abc", "Title", "Artist", "1:00", source)
            val item = song.asMediaItem
            assertEquals(source.toUri(), item.mediaMetadata.artworkUri)
            assertNull(item.mediaMetadata.artworkData)
            val upstream = upstreamWith("Title", "Artist", song.id)
            every { upstream.currentMediaItem } returns item
            every { upstream.mediaMetadata } returns item.mediaMetadata

            val session = SessionArtworkPlayer(upstream)
            val provider = requireNotNull(session.mediaMetadata.artworkUri)
            assertEquals("source", provider.pathSegments.first())
            assertEquals(file.canonicalFile, ArtworkContentProvider.registeredSourceFile(app, getActiveProfile(app), provider))
            assertEquals(provider, session.mediaMetadata.artworkUri)
            assertNull(session.mediaMetadata.artworkData)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `selected custom provider cover wins over recoverable remote original`() {
        stubAppContext()
        val upstream = upstreamWith("Song", "Artist", "stream-abc")
        val cover = ArtworkContentProvider.uriFor("stream-abc")
        val metadata = upstream.mediaMetadata.buildUpon().setArtworkUri(cover).setExtras(Bundle().apply {
            putBoolean("isCoverModified", true)
            putString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA, "https://img.example.com/original.jpg")
        }).build()
        every { upstream.mediaMetadata } returns metadata
        every { upstream.currentMediaItem } returns MediaItem.Builder().setMediaId("stream-abc")
            .setMediaMetadata(metadata).build()
        val published = SessionArtworkPlayer(upstream).mediaMetadata
        assertEquals(ArtworkContentProvider.bindLocalRequestUri(app, getActiveProfile(app), cover), published.artworkUri)
        assertEquals(true, published.extras?.getBoolean("isCoverModified"))
    }

    @Test
    fun `the same legacy route publishes a distinct uri for each new profile item`() {
        stubAppContext()
        check(app.writeProfileEntries(listOf("secondary" to "Secondary")))
        val songId = "local:42"
        val legacy = ArtworkContentProvider.uriFor(songId)
        val upstream = upstreamWith("Song", "Artist", songId)
        val metadata = upstream.mediaMetadata.buildUpon().setArtworkUri(legacy).build()
        val firstItem = MediaItem.Builder().setMediaId(songId).setMediaMetadata(metadata).build()
        every { upstream.mediaMetadata } returns metadata
        every { upstream.currentMediaItem } returns firstItem

        val session = SessionArtworkPlayer(upstream)
        val firstUri = requireNotNull(session.mediaMetadata.artworkUri)
        assertEquals(ArtworkContentProvider.scopedUriFor(app, "default", songId), firstUri)

        every { upstream.currentMediaItem } returns firstItem.buildUpon().build()
        setActiveProfile("secondary", app)
        session.refreshArtwork()
        assertNull(session.mediaMetadata.artworkUri)

        every { upstream.currentMediaItem } returns firstItem.buildUpon().build()
        val nextUri = requireNotNull(session.mediaMetadata.artworkUri)
        assertEquals(ArtworkContentProvider.scopedUriFor(app, "secondary", songId), nextUri)
        assertNotEquals(firstUri, nextUri)
    }

    @Test
    fun `blank title and artist are replaced with the localized placeholders`() {
        stubAppContext()
        val metadata = SessionArtworkPlayer(upstreamWith("", "", "stream-abc")).getMediaMetadata()

        assertEquals(app.getString(R.string.metadata_unknown_title), metadata.title.toString())
        assertEquals(app.getString(R.string.metadata_unknown_artist), metadata.artist.toString())
    }

    @Test
    fun `null title and artist are replaced with the localized placeholders`() {
        stubAppContext()
        val metadata = SessionArtworkPlayer(upstreamWith(null, null, "stream-abc")).getMediaMetadata()

        assertEquals(app.getString(R.string.metadata_unknown_title), metadata.title.toString())
        assertEquals(app.getString(R.string.metadata_unknown_artist), metadata.artist.toString())
    }

    @Test
    fun `present title and artist are left untouched`() {
        stubAppContext()
        val metadata = SessionArtworkPlayer(upstreamWith("Real Title", "Real Artist", "stream-abc")).getMediaMetadata()

        assertEquals("Real Title", metadata.title.toString())
        assertEquals("Real Artist", metadata.artist.toString())
    }

    @Test
    fun `the session republishes exact artist edits without replacing the current media item`() {
        stubAppContext()
        val upstream = upstreamWith("Title", "Old Credit", "stream-abc")
        justRun { upstream.addListener(any()) }
        val original = upstream.currentMediaItem
        val session = SessionArtworkPlayer(upstream)
        val events = mutableListOf<String>()
        session.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                events += mediaMetadata.artist.toString()
            }
        })
        val artist = Info("UC1234567890123456789012", "Correct Credit")
        val credit = SessionArtistCredit("Old Credit", listOf(artist))

        session.updateArtistCredit("different-track", credit)
        session.updateArtistCredit("stream-abc", credit)
        session.updateArtistCredit("stream-abc", credit)
        session.updateArtistCredit("stream-abc", SessionArtistCredit("modified:Manual Credit", listOf(artist)))

        assertEquals(listOf("Correct Credit", "Manual Credit"), events)
        assertEquals("Manual Credit", session.mediaMetadata.artist.toString())
        assertEquals(original, session.currentMediaItem)
        verify(exactly = 0) { upstream.replaceMediaItem(any(), any()) }
    }

    @Test
    fun `a room credit never leaks to a new item or to a new profile`() {
        stubAppContext()
        val originalProfile = getActiveProfile(app)
        val upstream = upstreamWith("Title", "Queued Credit", "stream-abc")
        val session = SessionArtworkPlayer(upstream)
        val credit = SessionArtistCredit("modified:Original Profile Credit", emptyList())
        session.updateArtistCredit("stream-abc", credit)
        assertEquals("Original Profile Credit", session.mediaMetadata.artist.toString())

        every { upstream.currentMediaItem } returns MediaItem.Builder().setMediaId("different-track").build()
        assertEquals("Queued Credit", session.mediaMetadata.artist.toString())

        every { upstream.currentMediaItem } returns MediaItem.Builder().setMediaId("stream-abc").build()
        try {
            setActiveProfile("session-credit-test", app)
            assertEquals("Queued Credit", session.mediaMetadata.artist.toString())
        } finally {
            setActiveProfile(originalProfile, app)
        }
    }

    @Test
    fun `an empty player keeps its metadata untouched without placeholders`() {
        stubAppContext()
        val metadata = SessionArtworkPlayer(emptyUpstream()).getMediaMetadata()

        assertNull(metadata.title)
        assertNull(metadata.artist)
        assertNull(metadata.artworkUri)
    }

    @Test
    fun `an empty player with artwork keeps the artwork untouched`() {
        stubAppContext()
        val upstream = mockk<Player>()
        every { upstream.getMediaMetadata() } returns MediaMetadata.Builder()
            .setArtworkUri("https://img.example.com/cover.jpg".toUri())
            .build()
        every { upstream.currentMediaItem } returns null
        every { upstream.isCurrentMediaItemLive } returns false
        val metadata = SessionArtworkPlayer(upstream).getMediaMetadata()

        assertNull(metadata.title)
        assertNull(metadata.artist)
        assertEquals("https://img.example.com/cover.jpg", metadata.artworkUri?.toString())
    }

    @Test
    fun `an IDLE player with a loaded untagged track publishes metadata without placeholders`() {
        stubAppContext()
        // Restored persistent queue with auto-resume off: items loaded, player
        // IDLE, nothing playing — no "unknown title / unknown artist" may surface.
        val metadata = SessionArtworkPlayer(idleUpstreamWith("stream-abc")).getMediaMetadata()

        assertNull(metadata.title)
        assertNull(metadata.artist)
    }
}
