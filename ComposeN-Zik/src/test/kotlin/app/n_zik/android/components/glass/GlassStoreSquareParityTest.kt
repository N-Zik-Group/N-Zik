package app.n_zik.android.components.glass

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33], application = Application::class)
class GlassStoreSquareParityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clearPreferences() {
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun deviceContext(lowRam: Boolean): Context {
        val manager = mockk<ActivityManager>()
        every { manager.isLowRamDevice } returns lowRam
        return object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? =
                if (name == Context.ACTIVITY_SERVICE) manager else super.getSystemService(name)
        }
    }

    @Test fun `cold store uses Square capability default including low ram phones`() {
        val normal = GlassStore(deviceContext(false)).read()
        val expected = if (android.os.Build.VERSION.SDK_INT < 31) GlassQuality.Performance else GlassQuality.Balanced
        assertEquals(expected, normal.quality)
        assertSquareSnapshot(normal, expected)
        val lowRam = GlassStore(deviceContext(true)).read()
        assertEquals(GlassQuality.Performance, lowRam.quality)
        assertSquareSnapshot(lowRam, GlassQuality.Performance)
    }

    @Test fun `reset restores selected Square profile after all overrides and survives reopening`() {
        val store = GlassStore(deviceContext(false))
        GlassQuality.entries.forEach { profile ->
            store.setProfile(profile)
            listOf(glassTintStrengthKey, glassVibrancyKey, glassBlurKey, glassFilmKey,
                glassIndicatorOpacityKey, glassRimKey, glassDepthKey, glassAmountKey,
                glassDialogDimmingKey, glassMenuDimmingKey).forEach { store.setFloat(it, .1f) }
            store.setColor(0xFFFF0000.toInt())
            store.setBoolean(glassDispersionKey, true)
            store.setBoolean(glassDepthEffectKey, true)
            GlassArea.entries.forEach { store.setAreaMode(it, GlassAreaMode.Disabled) }
            store.reset()
            val reopened = GlassStore(deviceContext(false)).read()
            assertEquals(profile, reopened.quality)
            assertSquareSnapshot(reopened, profile)
            assertTrue(reopened.areaModes.isEmpty())
            GlassArea.entries.forEach { area ->
                assertEquals(if (area == GlassArea.Images) GlassAreaMode.ContourOnly
                    else GlassAreaMode.GlassAndContour, reopened.mode(area))
            }
            // NZik's independent menu/dialog controls return to their own documented defaults.
            assertEquals(.75f, reopened.dialogDimming)
            assertEquals(.75f, reopened.menuDimming)
        }
    }

    private fun assertSquareSnapshot(actual: GlassSettings, profile: GlassQuality) {
        // Snapshot from docs/Square GlassEffectConfig + GlassProfile.config + GlassStore.read.
        // The store overrides raw config's tintStrength (.5) and puckOpacity (.8).
        val expected = when (profile) {
            GlassQuality.Performance -> listOf(.6f, 1.2f, 2f, .55f, .26f, .55f, .4f, .6f)
            GlassQuality.Balanced -> listOf(.6f, 1.2f, 2f, .5f, .26f, .55f, .4f, .6f)
            GlassQuality.Quality -> listOf(.6f, 1.4f, 4f, .5f, .26f, .55f, .5f, .8f)
        }
        assertEquals(expected, listOf(actual.tintStrength, actual.vibrancy, actual.blur,
            actual.film, actual.indicatorOpacity, actual.rim, actual.depth, actual.amount))
        assertEquals(GlassTintSource.None, actual.source)
        assertEquals(profile == GlassQuality.Quality, actual.dispersion)
        assertFalse(actual.depthEffect)
        assertTrue(actual.enabled)
        assertTrue(actual.nowPlaying)
        assertTrue(actual.miniPlayer)
        assertTrue(actual.nav)
    }
}
