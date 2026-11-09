package app.n_zik.android.playback.services

import android.app.Application
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.common.base.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlaybackEngineBuilderTest {
    @Test
    fun `both audio focus roles retain static scheduling and seek friendly buffering`() {
        val context = RuntimeEnvironment.getApplication()
        for (handleAudioFocus in listOf(true, false)) {
            val builder = createPlaybackEngineBuilder(
                context,
                DefaultMediaSourceFactory(context),
                DefaultRenderersFactory(context),
                handleAudioFocus,
            )
            // Media3 exposes no getters for these engine-builder policies.
            assertEquals(false, builder.field("dynamicSchedulingEnabled"))
            assertEquals(handleAudioFocus, builder.field("handleAudioFocus"))
            assertEquals(false, builder.field("handleAudioBecomingNoisy"))
            assertEquals(C.WAKE_MODE_NETWORK, builder.field("wakeMode"))
            assertEquals(false, builder.field("usePlatformDiagnostics"))
            val loadControl = (builder.field("loadControlSupplier") as Supplier<*>).get() as LoadControl
            assertEquals(35_000_000L, loadControl.getBackBufferDurationUs(PlayerId.UNSET))
            assertTrue(loadControl.retainBackBufferFromKeyframe(PlayerId.UNSET))
            val player = builder.build()
            try {
                assertEquals(C.USAGE_MEDIA, player.audioAttributes.usage)
                assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, player.audioAttributes.contentType)
                assertFalse(player.playWhenReady)
            } finally {
                player.release()
            }
        }
    }

    private fun ExoPlayer.Builder.field(name: String): Any? =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)
}
