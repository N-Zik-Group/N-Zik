package app.n_zik.android.playback.services

import android.app.Application
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.appContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The session facade publishes the metadata every session surface reads
 * (Android Auto home card, media notification — both go through
 * `session.getPlayer().getMediaMetadata()`): blank title/artist fields must
 * become the localized unknown placeholders, while present metadata and the
 * existing artwork-URI swap are left untouched.
 */
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
        return upstream
    }

    @Test
    fun `blank title and artist are replaced with the localized placeholders`() {
        stubAppContext()
        val metadata = SessionArtworkPlayer(upstreamWith("", "", "stream-abc")).getMediaMetadata()

        assertEquals(app.getString(R.string.unknown_title), metadata.title.toString())
        assertEquals(app.getString(R.string.unknown_artist), metadata.artist.toString())
    }

    @Test
    fun `null title and artist are replaced with the localized placeholders`() {
        stubAppContext()
        val metadata = SessionArtworkPlayer(upstreamWith(null, null, "stream-abc")).getMediaMetadata()

        assertEquals(app.getString(R.string.unknown_title), metadata.title.toString())
        assertEquals(app.getString(R.string.unknown_artist), metadata.artist.toString())
    }

    @Test
    fun `present title and artist are left untouched`() {
        stubAppContext()
        val metadata = SessionArtworkPlayer(upstreamWith("Real Title", "Real Artist", "stream-abc")).getMediaMetadata()

        assertEquals("Real Title", metadata.title.toString())
        assertEquals("Real Artist", metadata.artist.toString())
    }
}
