package app.n_zik.android.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.player.SharedArtworkColors
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class WidgetArtworkColorsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun resetProfile() { setActiveProfile("default", context) }

    private fun cover(color: Int) = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun `widget policy uses complete dark roles and restores original roles without reloading`() = runBlocking {
        setActiveProfile("default", context)
        val bitmap = cover(android.graphics.Color.rgb(160, 160, 160))
        val raw = requireNotNull(loadWidgetArtworkColors(context, null, bitmap))
        context.preferences.edit().putString(colorPaletteModeKey, "Light")
            .putBoolean(dynamicThemeRespectBrightnessKey, false).commit()
        val original = widgetArtworkPalette(context, raw)
        context.preferences.edit().putBoolean(dynamicThemeRespectBrightnessKey, true).commit()
        val dark = widgetArtworkPalette(context, raw)
        assertEquals(raw.display(false, true).palette, dark)
        assertTrue(dark.isDark)
        assertNotEquals(dark.background1, dark.text)
        assertEquals(dark.iconButtonPlayer.toArgb(), widgetIconTintArgb(dark))
        context.preferences.edit().putBoolean(dynamicThemeRespectBrightnessKey, false).commit()
        assertEquals(original, widgetArtworkPalette(context, raw))
        assertSame(raw, loadWidgetArtworkColors(context, null, bitmap))
    }

    @Test fun `active profile policy ignores legacy default widget snapshot`() = runBlocking {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit()
            .putInt("widget_palette_accent", Color.Magenta.toArgb())
            .putLong("widget_palette_timestamp", Long.MAX_VALUE).putBoolean("widget_palette_isDark", false).commit()
        setActiveProfile("widget-profile-test", context)
        context.preferences.edit().putString(colorPaletteModeKey, "Light")
            .putBoolean(dynamicThemeRespectBrightnessKey, true).commit()
        val bitmap = cover(android.graphics.Color.WHITE)
        val actual = NZikWidgetManager.extractPalette(context, bitmap)
        val playlist = PlaylistWidgetManager.extractPalette(context, bitmap)
        assertTrue(actual.isDark)
        assertEquals(actual, playlist)
        assertNotEquals(Color.Magenta, actual.accent)
        assertNotEquals(actual.background1, actual.text)
    }

    @Test fun `colored widget roles stay unchanged across toggle and bitmap pixels stay intact`() = runBlocking {
        setActiveProfile("default", context)
        val bitmap = cover(android.graphics.Color.rgb(30, 120, 200))
        val raw = requireNotNull(SharedArtworkColors.fromBitmap(bitmap))
        assertFalse(raw.isMonochrome)
        context.preferences.edit().putString(colorPaletteModeKey, "Light")
            .putBoolean(dynamicThemeRespectBrightnessKey, false).commit()
        val original = widgetArtworkPalette(context, raw)
        context.preferences.edit().putBoolean(dynamicThemeRespectBrightnessKey, true).commit()
        assertEquals(original, widgetArtworkPalette(context, raw))
        assertEquals(android.graphics.Color.rgb(30, 120, 200), bitmap.getPixel(0, 0))
    }

    @Test fun `pure white fallback is shared while dark widget mode yields readable full palette`() = runBlocking {
        setActiveProfile("default", context)
        val bitmap = cover(android.graphics.Color.WHITE)
        val raw = requireNotNull(SharedArtworkColors.fromBitmap(bitmap))
        val fallback = requireNotNull(raw.display(false, false).dominant)
        // Palette quantizes channel values (white is commonly represented by 248/255).
        assertTrue(fallback.red > .9f)
        assertEquals(fallback.red, fallback.green, .001f)
        assertEquals(fallback.red, fallback.blue, .001f)
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(0, 0))
        context.preferences.edit().putString(colorPaletteModeKey, "Light")
            .putBoolean(dynamicThemeRespectBrightnessKey, true).commit()
        val actual = widgetArtworkPalette(context, raw)
        assertTrue(actual.isDark)
        assertEquals(raw.display(false, true).palette, actual)
        assertNotEquals(actual.background1, actual.text)
    }
}
