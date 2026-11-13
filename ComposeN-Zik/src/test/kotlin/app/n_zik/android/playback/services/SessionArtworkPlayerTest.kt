package app.n_zik.android.playback.services

import android.app.Application
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        assertEquals(cover, published.artworkUri)
        assertEquals(true, published.extras?.getBoolean("isCoverModified"))
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
