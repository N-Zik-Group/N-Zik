package app.n_zik.android.playback.services.automotive.models

import android.app.Application
import android.media.MediaMetadata.METADATA_KEY_DURATION
import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.appContext
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SessionMediaItemMapperDurationTest {
    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `all song mapper paths publish standard duration and preserve application text`() {
        for ((text, duration) in listOf("3:07" to 187_000L, "1:02:03" to 3_723_000L, "90:00" to 5_400_000L)) {
            for (id in listOf("remote", "local:77")) {
                val song = Song(id = id, title = "Track", durationText = text, thumbnailUrl = null)
                val items = listOf(
                    SessionMediaItemMapper.baseMediaItem(song),
                    SessionMediaItemMapper.mapSongToMediaItem(song, path = "album"),
                    SessionMediaItemMapper.mapSongToMediaItem(song, isFromPersistentQueue = true),
                )
                for (item in items) {
                    assertEquals(duration, item.mediaMetadata.durationMs)
                    val extras = requireNotNull(item.mediaMetadata.extras)
                    assertEquals(text, extras.getString("durationText"))
                    assertFalse(extras.containsKey(METADATA_KEY_DURATION))
                }
            }
        }
    }

    @Test
    fun `invalid zero negative and overflowing text remains unknown`() {
        for (text in listOf(null, "", "LIVE", "0:00", "-1:30", "1:-30", "1:60", "1:60:00", "1:2:3:4",
            "999999999999999999999:00", "2562047788016:00:00")) {
            val song = Song(id = "unknown", title = "Track", durationText = text, thumbnailUrl = null)
            val item = SessionMediaItemMapper.mapSongToMediaItem(song, path = "album")
            assertNull("Expected unknown duration for $text", item.mediaMetadata.durationMs)
            assertEquals(text, item.mediaMetadata.extras?.getString("durationText"))
            assertFalse(requireNotNull(item.mediaMetadata.extras).containsKey(METADATA_KEY_DURATION))
        }
    }
}
