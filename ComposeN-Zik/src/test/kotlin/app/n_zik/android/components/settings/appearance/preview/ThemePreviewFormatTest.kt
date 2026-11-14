package app.n_zik.android.components.settings.appearance.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
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
}
