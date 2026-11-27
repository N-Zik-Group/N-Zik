package app.n_zik.android.components.settings.appearance

import android.app.Application
import android.content.Context
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.player.coverDimmingAlpha
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.lyricsFontSizeKey
import app.n_zik.android.utils.lyricsOutlineKey
import app.n_zik.android.utils.lyricsSizeKey
import app.n_zik.android.utils.lyricsSizeLKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsOutline
import app.n_zik.android.utils.blackBackgroundForVisThumbnailKey
import app.n_zik.android.utils.defaultLyricsCoverDimmingOpacity
import app.n_zik.android.utils.defaultVisualizerCoverDimmingOpacity
import app.n_zik.android.utils.lyricsCoverDimmingEnabledKey
import app.n_zik.android.utils.lyricsCoverDimmingOpacityKey
import app.n_zik.android.utils.visualizerCoverDimmingOpacityKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AppearanceLyricsDefaultsTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        setActiveProfile("lyrics-reset-audit", context)
        context.preferences.edit().clear().commit()
    }

    @After fun tearDown() {
        context.preferences.edit().clear().commit()
        setActiveProfile("default", context)
    }

    @Test fun `appearance reset restores the mounted font reader to the fresh Extra Large default`() {
        val preferences = context.preferences
        val fresh = preferences.getEnum(lyricsFontSizeKey, LyricsFontSize.DEFAULT)
        assertEquals(LyricsFontSize.Large, fresh)
        preferences.edit().apply { putEnum(lyricsFontSizeKey, LyricsFontSize.Medium) }.commit()
        var rendered: LyricsFontSize? = null
        compose.setContent {
            val font = rememberPreference(lyricsFontSizeKey, LyricsFontSize.DEFAULT).value
            SideEffect { rendered = font }
        }
        compose.runOnIdle {
            assertEquals(LyricsFontSize.Medium, rendered)
            applyAppearanceDefaults(context)
        }
        compose.runOnIdle { assertEquals(fresh, rendered) }
    }

    @Test fun `cover settings keep saved intensity across toggles and apply changes only when eligible`() {
        val preferences = context.preferences
        preferences.edit().putBoolean(blackBackgroundForVisThumbnailKey, false).commit()

        var visualizerAlpha = -1f
        var lyricsAlpha = -1f
        lateinit var setIntensity: (Float) -> Unit
        val eligible = mutableStateOf(true)
        compose.setContent {
            val visualizerEnabled by rememberPreference(blackBackgroundForVisThumbnailKey, true)
            var visualizerOpacity by rememberPreference(visualizerCoverDimmingOpacityKey, defaultVisualizerCoverDimmingOpacity)
            val lyricsEnabled by rememberPreference(lyricsCoverDimmingEnabledKey, true)
            val lyricsOpacity by rememberPreference(lyricsCoverDimmingOpacityKey, defaultLyricsCoverDimmingOpacity)
            val currentVisualizerAlpha = coverDimmingAlpha(eligible.value, visualizerEnabled, visualizerOpacity, defaultVisualizerCoverDimmingOpacity)
            val currentLyricsAlpha = coverDimmingAlpha(true, lyricsEnabled, lyricsOpacity, defaultLyricsCoverDimmingOpacity)
            SideEffect {
                visualizerAlpha = currentVisualizerAlpha
                lyricsAlpha = currentLyricsAlpha
                setIntensity = { visualizerOpacity = it }
            }
        }
        compose.runOnIdle {
            assertEquals(0f, visualizerAlpha, 0f)
            assertEquals(0.8f, lyricsAlpha, 0f)
            assertFalse(preferences.contains(visualizerCoverDimmingOpacityKey))
            preferences.edit().putBoolean(blackBackgroundForVisThumbnailKey, true).apply()
        }
        compose.runOnIdle {
            assertEquals(0.6f, visualizerAlpha, 0f)
            setIntensity(0.42f)
        }
        compose.runOnIdle {
            assertEquals(0.42f, visualizerAlpha, 0f)
            assertEquals(0.42f, preferences.getFloat(visualizerCoverDimmingOpacityKey, -1f), 0f)
            assertEquals(0.8f, lyricsAlpha, 0f)
            preferences.edit().putBoolean(blackBackgroundForVisThumbnailKey, false).apply()
        }
        compose.runOnIdle {
            assertEquals(0f, visualizerAlpha, 0f)
            assertEquals(0.42f, preferences.getFloat(visualizerCoverDimmingOpacityKey, -1f), 0f)
            preferences.edit().putBoolean(blackBackgroundForVisThumbnailKey, true).apply()
        }
        compose.runOnIdle {
            assertEquals(0.42f, visualizerAlpha, 0f)
            eligible.value = false
            setIntensity(1f)
        }
        compose.runOnIdle {
            assertEquals(0f, visualizerAlpha, 0f)
            assertEquals(1f, preferences.getFloat(visualizerCoverDimmingOpacityKey, -1f), 0f)
            eligible.value = true
        }
        compose.runOnIdle {
            assertEquals(1f, visualizerAlpha, 0f)
            setIntensity(0f)
        }
        compose.runOnIdle {
            assertEquals(0f, visualizerAlpha, 0f)
            assertEquals(0f, preferences.getFloat(visualizerCoverDimmingOpacityKey, -1f), 0f)
            preferences.edit().putBoolean(lyricsCoverDimmingEnabledKey, false)
                .putFloat(lyricsCoverDimmingOpacityKey, 0.3f).apply()
        }
        compose.runOnIdle {
            assertEquals(0f, lyricsAlpha, 0f)
            applyAppearanceDefaults(context)
        }
        compose.runOnIdle {
            assertEquals(0.6f, visualizerAlpha, 0f)
            assertEquals(0.8f, lyricsAlpha, 0f)
            assertTrue(preferences.getBoolean(blackBackgroundForVisThumbnailKey, false))
            assertFalse(preferences.contains(visualizerCoverDimmingOpacityKey))
            assertFalse(preferences.contains(lyricsCoverDimmingEnabledKey))
            assertFalse(preferences.contains(lyricsCoverDimmingOpacityKey))
        }
    }

    @Test fun `appearance reset restores custom lyrics controls without changing another profile or content`() {
        val preferences = context.preferences
        val freshOutline = preferences.getEnum(lyricsOutlineKey, LyricsOutline.None)
        val freshSize = preferences.getFloat(lyricsSizeKey, 20f)
        val other = context.getSharedPreferences("preferences_lyrics-reset-other", Context.MODE_PRIVATE)
        other.edit().clear().apply { putEnum(lyricsFontSizeKey, LyricsFontSize.Medium) }.commit()
        preferences.edit().putString(lyricsOutlineKey, "changed").putFloat(lyricsSizeKey, 38f)
            .putFloat(lyricsSizeLKey, 42f).putString("saved-lyrics-marker", "keep").commit()

        repeat(2) { applyAppearanceDefaults(context) }

        assertEquals(freshOutline, preferences.getEnum(lyricsOutlineKey, LyricsOutline.None))
        assertEquals(freshSize, preferences.getFloat(lyricsSizeKey, 20f), 0f)
        assertEquals(20f, preferences.getFloat(lyricsSizeLKey, 20f), 0f)
        assertFalse(preferences.contains(lyricsOutlineKey))
        assertEquals(LyricsFontSize.Medium, other.getEnum(lyricsFontSizeKey, LyricsFontSize.DEFAULT))
        assertEquals("keep", preferences.getString("saved-lyrics-marker", null))
    }
}
