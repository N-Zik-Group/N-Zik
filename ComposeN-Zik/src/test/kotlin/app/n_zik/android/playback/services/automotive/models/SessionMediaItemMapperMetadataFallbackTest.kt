package app.n_zik.android.playback.services.automotive.models

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.R
import app.n_zik.android.appContext
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unknown-metadata regression: songs without a title/artist tag used to publish
 * the raw empty string in the session metadata, so the Android Auto surfaces
 * (queue, browse lists) and the media notification rendered a blank label
 * while the phone lists showed the localized placeholders. The AA item mappers
 * must replace the missing fields with the same placeholders.
 *
 * Robolectric + JUnit 4: `toUri()`/`MediaItem` touch framework classes that
 * plain JVM stubs reject; `appContext()` is stubbed with the real Robolectric
 * application so the `R.string.unknown_*` placeholders resolve.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionMediaItemMapperMetadataFallbackTest {

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

    @Test
    fun `mapSongToMediaItem queue path replaces blank title and artist with the placeholders`() {
        stubAppContext()
        val song = Song(
            id = "stream-abc",
            title = "",
            durationText = "3:00",
            thumbnailUrl = "https://img.example.com/cover.jpg"
        )

        val item = SessionMediaItemMapper.mapSongToMediaItem(song, path = "album", loadArtwork = false)

        assertEquals(app.getString(R.string.unknown_title), item.mediaMetadata.title.toString())
        assertEquals(app.getString(R.string.unknown_artist), item.mediaMetadata.artist.toString())
    }

    @Test
    fun `mapSongToMediaItem persistent queue replaces blank title and artist with the placeholders`() {
        stubAppContext()
        val song = Song(
            id = "stream-def",
            title = "",
            artistsText = null,
            durationText = "3:00",
            thumbnailUrl = "https://img.example.com/cover.jpg"
        )

        val item = SessionMediaItemMapper.mapSongToMediaItem(song, isFromPersistentQueue = true, loadArtwork = false)

        assertEquals(app.getString(R.string.unknown_title), item.mediaMetadata.title.toString())
        assertEquals(app.getString(R.string.unknown_artist), item.mediaMetadata.artist.toString())
    }

    @Test
    fun `mapSongToMediaItem local song without an artist tag keeps the filename title and uses the artist placeholder`() {
        stubAppContext()
        val song = Song(id = "local:77", title = "Filename", durationText = "3:00", thumbnailUrl = null)

        val item = SessionMediaItemMapper.mapSongToMediaItem(song, isFromPersistentQueue = true, loadArtwork = false)

        assertEquals("Filename", item.mediaMetadata.title.toString())
        assertEquals(app.getString(R.string.unknown_artist), item.mediaMetadata.artist.toString())
    }

    @Test
    fun `mapSongToMediaItem keeps present title and artist untouched`() {
        stubAppContext()
        val song = Song(
            id = "stream-ghi",
            title = "Real Title",
            artistsText = "Real Artist",
            durationText = "3:00",
            thumbnailUrl = "https://img.example.com/cover.jpg"
        )

        val item = SessionMediaItemMapper.mapSongToMediaItem(song, path = "album", loadArtwork = false)

        assertEquals("Real Title", item.mediaMetadata.title.toString())
        assertEquals("Real Artist", item.mediaMetadata.artist.toString())
    }
}
