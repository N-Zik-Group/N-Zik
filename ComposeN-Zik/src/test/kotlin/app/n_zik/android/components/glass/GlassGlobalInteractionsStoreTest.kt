package app.n_zik.android.components.glass

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GlassGlobalInteractionsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences get() = context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)
    @Before fun clearPreferences() { preferences.edit().clear().commit() }

    @Test fun `fresh install and reopening preserve the original interaction defaults`() {
        assertDefaults(GlassStore(context).read())
        assertDefaults(GlassStore(context).read())
    }

    @Test fun `independent switches and coefficients survive reopen and every quality change`() {
        val store = GlassStore(context)
        store.setBoolean(glassRippleEnabledKey, false)
        store.setFloat(glassRippleIntensityKey, .3f)
        store.setFloat(glassRippleSizeKey, 1.7f)
        store.setBoolean(glassJellyEnabledKey, false)
        store.setFloat(glassJellyIntensityKey, .6f)
        GlassQuality.entries.forEach { quality ->
            store.setProfile(quality)
            val saved = GlassStore(context).read()
            assertEquals(quality, saved.quality)
            assertEquals(listOf(false, .3f, 1.7f, false, .6f), snapshot(saved))
        }
        store.setBoolean(glassRippleEnabledKey, true)
        store.setBoolean(glassJellyEnabledKey, true)
        assertEquals(listOf(true, .3f, 1.7f, true, .6f), snapshot(GlassStore(context).read()))
    }

    @Test fun `invalid numeric preferences are bounded on write and on cold read`() {
        val store = GlassStore(context)
        val ranges = mapOf(glassRippleIntensityKey to (0f..1f), glassRippleSizeKey to (.5f..2f),
            glassJellyIntensityKey to (0f..1f))
        ranges.forEach { (key, range) ->
            listOf(-100f, 100f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { input ->
                val expected = if (input.isFinite()) input.coerceIn(range) else range.start
                store.setFloat(key, input)
                assertEquals(expected, preferences.getFloat(key, -1f))
                // Also exercise imported/corrupt stored values which bypass the public writer.
                preferences.edit().putFloat(key, input).commit()
                val saved = GlassStore(context).read()
                val actual = when (key) {
                    glassRippleIntensityKey -> saved.rippleIntensity
                    glassRippleSizeKey -> saved.rippleSize
                    else -> saved.jellyIntensity
                }
                assertTrue(actual.isFinite())
                assertEquals(expected, actual)
            }
        }
    }

    @Test fun `reset clears every interaction override and retains the selected optical quality`() {
        val store = GlassStore(context)
        store.setProfile(GlassQuality.Quality)
        store.setBoolean(glassRippleEnabledKey, false)
        store.setBoolean(glassJellyEnabledKey, false)
        store.setFloat(glassRippleIntensityKey, .2f)
        store.setFloat(glassRippleSizeKey, 2f)
        store.setFloat(glassJellyIntensityKey, .4f)
        store.reset()
        val saved = GlassStore(context).read()
        assertEquals(GlassQuality.Quality, saved.quality)
        assertDefaults(saved)
    }

    private fun snapshot(s: GlassSettings): List<Any> =
        listOf(s.rippleEnabled, s.rippleIntensity, s.rippleSize, s.jellyEnabled, s.jellyIntensity)
    private fun assertDefaults(s: GlassSettings) =
        assertEquals(listOf(true, 1f, 1f, true, 1f), snapshot(s))
}
