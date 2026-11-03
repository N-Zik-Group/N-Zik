package app.n_zik.android.components.player

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.core.coil.ImageCacheFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the actual facade used by player, mini-player, lyrics and visualizer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkConsumerReactivityTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null

    @Before fun isolateProfile() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("artwork_consumers_test", context)
        context.preferences.edit().clear().commit()
        SharedArtworkColors.invalidate()
    }

    @After fun restoreProfile() {
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
        SharedArtworkColors.invalidate()
        unmockkObject(ImageCacheFactory)
    }

    @Test fun `two live cover consumers toggle and change mode without replacing their source`() {
        val uri = "test://same-media/original-artwork"
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(190, 190, 190))
        }
        mockkObject(ImageCacheFactory)
        coEvery {
            ImageCacheFactory.loadBitmap(uri, allowHardware = false, requestSize = 1000,
                qualityOverride = ImageCacheFactory.NetworkQuality.HIGH)
        } returns bitmap
        // The real repository loads the original URI at the common high-quality contract.
        // A synchronous JUnit setup needs the suspend API before composition starts.
        val source = runBlocking { requireNotNull(SharedArtworkColors.load(context, uri)) }
        assertTrue("The real quantizer must classify the gray fixture as monochrome", source.isMonochrome)
        assertTrue("The fixture must support the force-dark policy", source.display(false, true).forceDark)
        val preferences = context.preferences
        assertEquals("artwork_consumers_test", getActiveProfile(context))
        assertSame(preferences, context.getSharedPreferences("preferences_artwork_consumers_test", Context.MODE_PRIVATE))
        val requestedDark = mutableStateOf(false)
        var player: ArtworkDisplayColors? = null
        var lyrics: ArtworkDisplayColors? = null
        compose.setContent {
            player = rememberArtworkDisplayColors(uri, requestedDark.value)
            lyrics = rememberArtworkDisplayColors(uri, requestedDark.value)
        }
        compose.waitUntil(5_000) { player != null && lyrics != null }
        compose.runOnIdle {
            assertEquals(source.display(false, false), player)
            assertEquals(player, lyrics)
            assertFalse(requireNotNull(player).forceDark)
            var delivered = false
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == dynamicThemeRespectBrightnessKey) delivered = true
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            try {
                assertTrue(preferences.edit().putBoolean(dynamicThemeRespectBrightnessKey, true).commit())
                assertTrue(preferences.getBoolean(dynamicThemeRespectBrightnessKey, false))
                assertTrue("The real store must deliver the policy change on the UI thread", delivered)
            } finally {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
        // SharedPreferences posts delivery to Android's main Handler under Robolectric.
        // Compose frame advancement alone does not drain that separate looper queue.
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(source.display(false, true), player)
            assertEquals(player, lyrics)
            val roles = requireNotNull(player)
            assertTrue(requireNotNull(roles.palette).isDark)
            assertTrue(requireNotNull(roles.foreground) != roles.background)
            requestedDark.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(source.display(true, true), player)
            assertEquals(player, lyrics)
            context.preferences.edit().putBoolean(dynamicThemeRespectBrightnessKey, false).commit()
        }
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(source.display(true, false), player)
            assertEquals(player, lyrics)
            requestedDark.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(source.display(false, false), player)
            assertEquals(player, lyrics)
        }
        // The original pixels and repository result survive every policy-only change.
        assertEquals(android.graphics.Color.rgb(190, 190, 190), bitmap.getPixel(0, 0))
        // A synchronous JUnit assertion checks the cached result after composition settles.
        assertSame(source, runBlocking { SharedArtworkColors.load(context, uri) })
        coVerify(exactly = 1) {
            ImageCacheFactory.loadBitmap(uri, allowHardware = false, requestSize = 1000,
                qualityOverride = ImageCacheFactory.NetworkQuality.HIGH)
        }
    }
}
