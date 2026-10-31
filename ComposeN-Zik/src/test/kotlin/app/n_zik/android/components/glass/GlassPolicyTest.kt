package app.n_zik.android.components.glass

import android.content.Context
import androidx.compose.ui.graphics.Color
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
class GlassPolicyTest {
    private lateinit var store: GlassStore

    @Before fun clearSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        store = GlassStore(context)
    }

    @Test fun `optical profile retains exact Square values independently of the master switch`() {
        val settings = glassProfileDefaults(GlassQuality.Balanced)
        assertTrue(settings.enabled)
        assertEquals(GlassTintSource.None, settings.source)
        assertEquals(1.2f, settings.vibrancy)
        assertEquals(2f, settings.blur)
        assertEquals(.4f, settings.depth)
        assertEquals(.6f, settings.amount)
        assertEquals(.5f, settings.film)
        assertEquals(.55f, settings.rim)
        assertEquals(.26f, settings.indicatorOpacity)
        assertEquals(.75f, settings.dialogDimming)
        assertEquals(.75f, settings.menuDimming)
        assertEquals(.6f, settings.tintStrength)
        assertFalse(settings.dispersion)
        assertFalse(settings.depthEffect)
        assertEquals(GlassRenderer.Refraction, selectGlassRenderer(settings, 35, false))
        assertEquals(GlassRenderer.Tint, selectGlassRenderer(settings.copy(enabled = false), 35, false))
    }

    @Test fun `all lens adapters share the persisted dispersion switch`() {
        assertEquals(0f, GlassSettings(dispersion = false).dispersionAmount(), 0f)
        assertEquals(1f, GlassSettings(dispersion = true).dispersionAmount(), 0f)
    }

    @Test fun `quality and performance profiles match Square`() {
        val quality = glassProfileDefaults(GlassQuality.Quality)
        assertEquals(1.4f, quality.vibrancy)
        assertEquals(4f, quality.blur)
        assertEquals(.5f, quality.depth)
        assertEquals(.8f, quality.amount)
        assertTrue(quality.dispersion)
        assertEquals(.55f, glassProfileDefaults(GlassQuality.Performance).film)
        assertEquals(GlassQuality.Balanced, defaultGlassQuality(31, false))
        assertEquals(GlassQuality.Performance, defaultGlassQuality(30, false))
        assertEquals(GlassQuality.Performance, defaultGlassQuality(35, true))
    }

    @Test fun `reset restores device profile and clears every material and surface override`() {
        val fresh = store.read()
        store.setProfile(GlassQuality.Quality)
        store.setFloat(glassFilmKey, .9f)
        store.setFloat(glassIndicatorOpacityKey, .7f)
        store.setFloat(glassDialogDimmingKey, .2f)
        store.setFloat(glassMenuDimmingKey, .9f)
        store.setColor(0xFFFF0000.toInt())
        store.setBoolean(glassNavKey, false)
        store.setBoolean(glassMiniPlayerKey, false)
        listOf(glassDropdownsKey, glassDialogsKey, glassDialogButtonsKey, glassNowPlayingKey, glassImageRimsKey, glassSettingsCardRimsKey, glassSettingsCardsKey, glassSearchBarsKey)
            .forEach { store.setBoolean(it, false) }
        store.reset()
        assertEquals(fresh, store.read())
    }

    @Test fun `profile switch discards optics while preserving destination tint and dimming choices`() {
        store.setProfile(GlassQuality.Balanced)
        store.setFloat(glassBlurKey, 20f)
        store.setFloat(glassDialogDimmingKey, .1f)
        store.setFloat(glassMenuDimmingKey, .8f)
        store.setTint(GlassTintSource.SystemAccent)
        store.setBoolean(glassMenusKey, false)
        listOf(glassDropdownsKey, glassDialogsKey, glassDialogButtonsKey, glassNowPlayingKey, glassImageRimsKey, glassSettingsCardRimsKey, glassSettingsCardsKey, glassSearchBarsKey)
            .forEach { store.setBoolean(it, false) }
        val before = store.read()
        store.setProfile(GlassQuality.Performance)
        assertEquals(before.copy(quality = GlassQuality.Performance, blur = 2f, film = .55f), store.read())
    }

    @Test fun `slider values obey Square ranges including invalid values`() {
        assertEquals(2f, clampGlassValue(glassVibrancyKey, 3f))
        assertEquals(24f, clampGlassValue(glassBlurKey, 40f))
        assertEquals(1f, clampGlassValue(glassDepthKey, 2f))
        assertEquals(0f, clampGlassValue(glassAmountKey, -1f))
        assertEquals(0f, clampGlassValue(glassFilmKey, Float.NaN))
        store.setFloat(glassBlurKey, 100f)
        assertEquals(24f, store.read().blur)
    }

    @Test fun `dialog and menu dimming persist independently and clamp to opacity range`() {
        store.setFloat(glassDialogDimmingKey, .2f)
        store.setFloat(glassMenuDimmingKey, .8f)
        val restored = GlassStore(ApplicationProvider.getApplicationContext()).read()
        assertEquals(.2f, restored.dialogDimming)
        assertEquals(.8f, restored.menuDimming)
        store.setFloat(glassDialogDimmingKey, 2f)
        store.setFloat(glassMenuDimmingKey, -1f)
        assertEquals(1f, store.read().dialogDimming)
        assertEquals(0f, store.read().menuDimming)
        store.setFloat(glassDialogDimmingKey, Float.NaN)
        assertEquals(0f, store.read().dialogDimming)
    }

    @Test fun `every surface switch persists independently`() {
        val fields = listOf<Pair<String, (GlassSettings) -> Boolean>>(
            glassNavKey to { it.nav }, glassMiniPlayerKey to { it.miniPlayer },
            glassHeaderKey to { it.header }, glassMenusKey to { it.menus },
            glassDropdownsKey to { it.dropdowns }, glassDialogsKey to { it.dialogs },
            glassDialogButtonsKey to { it.dialogButtons },
            glassNowPlayingKey to { it.nowPlaying }, glassImageRimsKey to { it.imageRims },
            glassSettingsCardRimsKey to { it.settingsCardRims },
            glassSettingsCardsKey to { it.settingsCards },
            glassSearchBarsKey to { it.searchBars },
        )
        fields.forEach { (key, selected) ->
            store.reset()
            store.setBoolean(key, false)
            val restored = GlassStore(ApplicationProvider.getApplicationContext()).read()
            assertFalse(selected(restored))
            fields.filterNot { it.first == key }.forEach { (_, other) -> assertTrue(other(restored)) }
        }
    }

    @Test fun `former menu preference migrates once without coupling future switches`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)
            .edit().clear().putBoolean(glassMenusKey, false).commit()
        val migrated = GlassStore(context)
        assertFalse(migrated.read().dropdowns)
        assertFalse(migrated.read().dialogs)
        assertTrue(migrated.read().dialogButtons)
        assertTrue(migrated.read().nowPlaying)
        assertTrue(migrated.read().imageRims)
        assertTrue(migrated.read().settingsCardRims)
        assertTrue(migrated.read().settingsCards)
        assertTrue(migrated.read().searchBars)
        migrated.setBoolean(glassMenusKey, true)
        assertFalse(GlassStore(context).read().dropdowns)
        migrated.reset()
        migrated.setBoolean(glassMenusKey, false)
        assertTrue(GlassStore(context).read().dropdowns)
        assertTrue(GlassStore(context).read().dialogs)
    }

    @Test fun `capability ladder chooses safe fallback`() {
        val settings = GlassSettings(enabled = true)
        assertEquals(GlassRenderer.Tint, selectGlassRenderer(settings, 30, false))
        assertEquals(GlassRenderer.Tint, selectGlassRenderer(settings, 35, true))
        assertEquals(GlassRenderer.Blur, selectGlassRenderer(settings, 31, false))
        assertEquals(GlassRenderer.Refraction, selectGlassRenderer(settings, 33, false))
        assertEquals(GlassRenderer.Tint, selectGlassRenderer(settings.copy(quality = GlassQuality.Performance), 35, false))
    }

    @Test fun `foreground and reduced motion policy stay readable`() {
        assertEquals(Color.White, glassForeground(Color(0xFF202024)))
        assertEquals(Color(0xFF17171B), glassForeground(Color(0xFFF5F5F6)))
        assertFalse(glassMotionAllowed(0f, false))
        assertFalse(glassMotionAllowed(1f, true))
        assertTrue(glassMotionAllowed(1f, false))
    }
}
