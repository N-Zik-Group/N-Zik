package app.n_zik.android.components.settings.appearance.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.navigationBarPositionKey

class ThemePreviewFormatTest {
    @Test
    fun `v1 appearance maps to isolated preview values`() {
        val theme = parseThemeV1("""
            {"schemaVersion":1,"categories":{
              "colors":{"palette":"dynamic","mode":"system"},
              "navigation":{"position":"bottom-floating","labels":"icon-only"},
              "glass":{"enabled":true,"tintSource":"theme-accent","navigation":"glass-only",
                "miniPlayer":"disabled","nowPlaying":"glass-and-contour"}
            }}
        """.trimIndent())
        assertEquals("Dynamic", theme.getValue(ThemeCategory.COLORS).plain[colorPaletteNameKey])
        assertEquals("System", theme.getValue(ThemeCategory.COLORS).plain[colorPaletteModeKey])
        assertEquals("BottomFloating", theme.getValue(ThemeCategory.NAVIGATION).plain[navigationBarPositionKey])
        val glass = theme.getValue(ThemeCategory.GLASS)
        assertEquals("GlassOnly", glass.glass[GlassArea.Nav.preferenceKey])
        assertFalse(glass.plain.containsKey(GlassArea.Nav.preferenceKey))
    }

    @Test
    fun `v1 appearance rejects non appearance fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseThemeV1("""{"schemaVersion":1,"categories":{"colors":{
                "palette":"dynamic","mode":"system","activeProfile":"other"
            }}}""")
        }
    }

    @Test
    fun `preview variants preserve every decoded setting except the Glass master switch`() {
        val document = encodeThemeV1(
            mapOf(hideStatusBarKey to true, glassEnabledKey to false),
            mapOf(GlassArea.Nav.preferenceKey to "GlassOnly", GlassArea.MiniPlayer.preferenceKey to "Disabled"),
        )
        val theme = parseThemeV1(document)
        val plain = theme.values.flatMap { it.plain.entries }.associate { it.toPair() }
        val optics = theme.getValue(ThemeCategory.GLASS).glass

        val normal = themePreviewPreferences(document, glass = false)
        val glass = themePreviewPreferences(document, glass = true)

        assertEquals(true, normal.plain[hideStatusBarKey])
        assertEquals(plain + (glassEnabledKey to false), normal.plain)
        assertEquals(plain + (glassEnabledKey to true), glass.plain)
        assertEquals(optics, normal.glass)
        assertEquals(optics, glass.glass)
    }
}
