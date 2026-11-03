package app.n_zik.android.core.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.closeWithBackButtonKey
import app.it.fast4x.rimusic.utils.effectRotationKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.n_zik.android.components.settings.appearance.applyAppearanceDefaults
import app.n_zik.android.components.settings.general.applyGeneralDefaults
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EnsurePlayerBehaviorDefaultsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        setActiveProfile("behavior-defaults-test", context)
        context.preferences.edit().clear().putString("unrelated", "keep").commit()
    }

    @After fun tearDown() {
        context.preferences.edit().clear().commit()
        setActiveProfile("default", context)
    }

    @Test fun `first run gives conflicting legacy readers the displayed defaults without touching other profiles`() {
        val other = context.getSharedPreferences("preferences_behavior-other", Context.MODE_PRIVATE)
        other.edit().clear().putBoolean(showButtonPlayerVideoKey, false).commit()
        repeat(2) { ensurePlayerBehaviorDefaults(context.preferences) }

        // These false fallbacks are still used by the existing legacy video screens.
        assertTrue(context.preferences.getBoolean(showButtonPlayerVideoKey, false))
        assertTrue(context.preferences.getBoolean(closeWithBackButtonKey, false))
        assertFalse(context.preferences.getBoolean(effectRotationKey, true))
        assertEquals(90f, context.preferences.getFloat(thumbnailSizeDpKey, 85f), 0f)
        assertEquals("keep", context.preferences.getString("unrelated", null))
        assertEquals(mapOf(showButtonPlayerVideoKey to false), other.all)
    }

    @Test fun `explicit opt outs survive startup while a separately missing default is filled`() {
        context.preferences.edit().putBoolean(showButtonPlayerVideoKey, false)
            .putBoolean(effectRotationKey, true).putFloat(thumbnailSizeDpKey, 63f).commit()
        ensurePlayerBehaviorDefaults(context.preferences)
        assertFalse(context.preferences.getBoolean(showButtonPlayerVideoKey, true))
        assertTrue(context.preferences.getBoolean(closeWithBackButtonKey, false))
        assertTrue(context.preferences.getBoolean(effectRotationKey, false))
        assertEquals(63f, context.preferences.getFloat(thumbnailSizeDpKey, 90f), 0f)

        context.preferences.edit().putBoolean(closeWithBackButtonKey, false).commit()
        val before = context.preferences.all.toMap()
        repeat(2) { ensurePlayerBehaviorDefaults(context.preferences) }
        assertEquals(before, context.preferences.all)
    }

    @Test fun `category resets restore true video and back plus false rotation and startup preserves them`() {
        context.preferences.edit().putBoolean(showButtonPlayerVideoKey, false)
            .putBoolean(closeWithBackButtonKey, false).putBoolean(effectRotationKey, true).commit()
        applyGeneralDefaults(context)
        applyAppearanceDefaults(context)
        ensurePlayerBehaviorDefaults(context.preferences)

        assertTrue(context.preferences.getBoolean(showButtonPlayerVideoKey, false))
        assertTrue(context.preferences.getBoolean(closeWithBackButtonKey, false))
        assertFalse(context.preferences.getBoolean(effectRotationKey, true))
        assertEquals("keep", context.preferences.getString("unrelated", null))
    }

    @Test fun `a cold shortcut target initialized after application startup receives its own defaults`() {
        val original = context.preferences
        original.edit().putBoolean(showButtonPlayerVideoKey, false)
            .putBoolean(closeWithBackButtonKey, false).putBoolean(effectRotationKey, true).commit()
        ensurePlayerBehaviorDefaults(original)
        setActiveProfile("behavior-shortcut-target", context)
        context.preferences.edit().clear().commit()
        ensurePlayerBehaviorDefaults(context.preferences)

        assertTrue(context.preferences.getBoolean(showButtonPlayerVideoKey, false))
        assertTrue(context.preferences.getBoolean(closeWithBackButtonKey, false))
        assertFalse(context.preferences.getBoolean(effectRotationKey, true))
        assertFalse(original.getBoolean(showButtonPlayerVideoKey, true))
        assertFalse(original.getBoolean(closeWithBackButtonKey, true))
        assertTrue(original.getBoolean(effectRotationKey, false))
    }
}
