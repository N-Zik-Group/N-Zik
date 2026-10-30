package app.n_zik.android.components.glass

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GlassAreaModesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences get() = context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)

    @Before fun clear() { preferences.edit().clear().commit() }

    @Test fun `each legacy area retains enabled and disabled appearance without rewriting preferences`() {
        GlassArea.entries.filterNot { it == GlassArea.SettingsCards || it == GlassArea.Images || it == GlassArea.MoodCards }.forEach { area ->
            preferences.edit().clear().putBoolean(area.legacyKey, false).commit()
            assertEquals(area.name, GlassAreaMode.Disabled, GlassStore(context).read().mode(area))
            preferences.edit().putBoolean(area.legacyKey, true).commit()
            assertEquals(area.name, GlassAreaMode.GlassAndContour, GlassStore(context).read().mode(area))
            assertFalse(preferences.contains(area.preferenceKey))
        }
    }

    @Test fun `all four legacy card combinations migrate to one independent mode`() {
        listOf(
            Triple(false, false, GlassAreaMode.Disabled),
            Triple(false, true, GlassAreaMode.ContourOnly),
            Triple(true, false, GlassAreaMode.GlassOnly),
            Triple(true, true, GlassAreaMode.GlassAndContour),
        ).forEach { (material, contour, expected) ->
            preferences.edit().clear().putBoolean(glassSettingsCardsKey, material)
                .putBoolean(glassSettingsCardRimsKey, contour).commit()
            assertEquals(expected, GlassStore(context).read().mode(GlassArea.SettingsCards))
        }
    }

    @Test fun `typed modes persist independently and explicit button mode overrides legacy dialog coupling`() {
        val store = GlassStore(context)
        store.setBoolean(glassDialogsKey, false)
        assertEquals(GlassAreaMode.Disabled, store.read().mode(GlassArea.DialogButtons))
        store.setAreaMode(GlassArea.DialogButtons, GlassAreaMode.GlassOnly)
        assertEquals(GlassAreaMode.GlassOnly, GlassStore(context).read().mode(GlassArea.DialogButtons))
        GlassArea.entries.filterNot { it == GlassArea.Images }.forEach { area ->
            GlassAreaMode.entries.forEach { mode ->
                store.setAreaMode(area, mode)
                assertEquals(mode.name, preferences.getString(area.preferenceKey, null))
                assertEquals(mode, GlassStore(context).read().mode(area))
            }
        }
        store.setAreaMode(GlassArea.Nav, GlassAreaMode.ContourOnly)
        store.setAreaMode(GlassArea.SearchBars, GlassAreaMode.GlassOnly)
        assertEquals(GlassAreaMode.ContourOnly, store.read().mode(GlassArea.Nav))
        assertEquals(GlassAreaMode.GlassOnly, store.read().mode(GlassArea.SearchBars))
    }

    @Test fun `glass without contour keeps full optical settings while master and rim remain authoritative`() {
        val original = GlassSettings(enabled = true, rim = .8f, blur = 12f, depth = .7f,
            amount = .9f, dispersion = true, depthEffect = true,
            areaModes = mapOf(GlassArea.Nav to GlassAreaMode.GlassOnly))
        val scoped = original.forArea(GlassArea.Nav)
        assertTrue(original.usesGlassMaterial(GlassArea.Nav))
        assertFalse(original.usesGlassContour(GlassArea.Nav))
        assertEquals(original.copy(rim = 0f), scoped)
        assertEquals(GlassRenderer.Refraction, selectGlassRenderer(scoped, 33, false))
        val contour = original.copy(areaModes = mapOf(GlassArea.Nav to GlassAreaMode.ContourOnly))
        assertFalse(contour.usesGlassMaterial(GlassArea.Nav))
        assertTrue(contour.usesGlassContour(GlassArea.Nav))
        assertFalse(contour.copy(rim = 0f).usesGlassContour(GlassArea.Nav))
        assertFalse(original.copy(enabled = false).usesGlassMaterial(GlassArea.Nav))
        assertFalse(contour.copy(enabled = false).usesGlassContour(GlassArea.Nav))
    }

    @Test fun `images normalize unsupported material modes and invalid saved values retain legacy fallback`() {
        val store = GlassStore(context)
        assertEquals(GlassAreaMode.ContourOnly, store.read().mode(GlassArea.Images))
        store.setAreaMode(GlassArea.Images, GlassAreaMode.GlassAndContour)
        assertEquals(GlassAreaMode.ContourOnly, GlassStore(context).read().mode(GlassArea.Images))
        store.setAreaMode(GlassArea.Images, GlassAreaMode.Disabled)
        assertEquals(GlassAreaMode.Disabled, GlassStore(context).read().mode(GlassArea.Images))
        preferences.edit().putString(GlassArea.Nav.preferenceKey, "future-mode").putBoolean(glassNavKey, false).commit()
        assertEquals(GlassAreaMode.Disabled, GlassStore(context).read().mode(GlassArea.Nav))
        store.reset()
        assertEquals(emptyMap<GlassArea, GlassAreaMode>(), store.read().areaModes)
        assertEquals(GlassAreaMode.GlassAndContour, store.read().mode(GlassArea.Nav))
    }
}
