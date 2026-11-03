package app.n_zik.android.components.settings.appearance

import android.app.Application
import android.content.Context
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.lyricsFontSizeKey
import app.it.fast4x.rimusic.utils.lyricsOutlineKey
import app.it.fast4x.rimusic.utils.lyricsSizeKey
import app.it.fast4x.rimusic.utils.lyricsSizeLKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.putEnum
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.enums.lyrics.LyricsFontSize
import app.n_zik.android.enums.lyrics.LyricsOutline
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
