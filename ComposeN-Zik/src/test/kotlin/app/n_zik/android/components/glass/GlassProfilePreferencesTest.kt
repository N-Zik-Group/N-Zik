package app.n_zik.android.components.glass

import java.io.File
import android.app.Application
import android.content.Context
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.ui.screens.profiles.purgeProfileData
import app.n_zik.android.core.profiles.profileDataPrefs
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
class GlassProfilePreferencesTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() {
        listOf(GlassProfilePreferences.LEGACY_NAME, GlassProfilePreferences.MIGRATION_NAME,
            GlassProfilePreferences.fileName("work"), GlassProfilePreferences.fileName("travel"),
            "profile_preferences", "preferences", "preferences_work", "preferences_travel")
            .forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        setActiveProfile(DEFAULT_PROFILE_ID, context)
    }

    @Test fun `existing global optics and destinations migrate once without later cross profile writes`() {
        val legacy = context.getSharedPreferences(GlassProfilePreferences.LEGACY_NAME, Context.MODE_PRIVATE)
        val favorite = GlassContextArea(GlassScope.PlayerFavorite, GlassArea.ActionButtons)
        legacy.edit().putFloat(glassBlurKey, 11f).putInt(glassCustomColorKey, 0xff123456.toInt())
            .putString(glassTintSourceKey, GlassTintSource.Custom.name)
            .putString(favorite.preferenceKey, GlassAreaMode.ContourOnly.name)
            .putBoolean(glassRippleEnabledKey, false).commit()
        val base = GlassStore(context)
        base.setFloat(glassBlurKey, 3f)
        val work = GlassStore(context, "work")
        assertEquals(11f, work.read().blur, 0f)
        assertEquals(0xff123456.toInt(), work.read().customColor)
        assertEquals(GlassAreaMode.ContourOnly, work.read().contextModes[favorite])
        assertFalse(work.read().rippleEnabled)
        work.setFloat(glassBlurKey, 19f)
        work.setContextMode(GlassScope.PlayerFavorite, GlassArea.ActionButtons, GlassAreaMode.Disabled)
        val travel = GlassStore(context, "travel")
        assertEquals(11f, travel.read().blur, 0f)
        assertEquals(GlassAreaMode.ContourOnly, travel.read().contextModes[favorite])
        assertEquals(3f, base.read().blur, 0f)
        assertEquals(19f, GlassStore(context, "work").read().blur, 0f)
    }

    @Test fun `captured stores and reset remain owned by their original profile`() {
        val work = GlassStore(context, "work")
        val travel = GlassStore(context, "travel")
        travel.setFloat(glassBlurKey, 18f)
        setActiveProfile("travel", context)
        work.setFloat(glassBlurKey, 8f)
        assertEquals(18f, travel.read().blur, 0f)
        work.reset()
        assertEquals(glassProfileDefaults(defaultGlassQuality(33, false)), work.read())
        assertEquals(18f, travel.read().blur, 0f)
        assertEquals(work.read(), GlassStore(context, "work").read())
    }

    @Test fun `deleting a profile removes its Glass values without reseeding a reused ID`() {
        context.getSharedPreferences(GlassProfilePreferences.LEGACY_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(glassBlurKey, 11f).commit()
        GlassStore(context, "work").setFloat(glassBlurKey, 19f)
        purgeProfileData(context, "work")
        assertEquals(2f, GlassStore(context, "work").read().blur, 0f)
        assertEquals(11f, GlassStore(context).read().blur, 0f)
    }

    @Test fun `purging a never opened profile cannot recreate its Glass file from pending migration writes`() {
        context.getSharedPreferences(GlassProfilePreferences.LEGACY_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(glassBlurKey, 11f).commit()

        purgeProfileData(context, "work")

        // Wait for previously queued migration writes before checking the removed XML.
        assertTrue(context.getSharedPreferences(GlassProfilePreferences.MIGRATION_NAME, Context.MODE_PRIVATE)
            .edit().commit())
        assertFalse(File(context.applicationInfo.dataDir,
            "shared_prefs/${GlassProfilePreferences.fileName("work")}.xml").exists())
        assertEquals(2f, GlassStore(context, "work").read().blur, 0f)
        assertEquals(11f, GlassStore(context).read().blur, 0f)
    }

    @Test fun `mounted Glass readers and setting callbacks follow active profile switches`() {
        GlassStore(context).setFloat(glassBlurKey, 4f)
        GlassStore(context, "work").setFloat(glassBlurKey, 17f)
        profileDataPrefs(context, DEFAULT_PROFILE_ID).edit().putBoolean(glassEnabledKey, true).commit()
        profileDataPrefs(context, "work").edit().putBoolean(glassEnabledKey, false).commit()
        var mountedStore: GlassStore? = null
        var mountedEnabled: MutableState<Boolean>? = null
        compose.setContent {
            val store = rememberGlassStore()
            val settings = rememberGlassSettings()
            val enabled = rememberGlassEnabled(store)
            SideEffect {
                mountedStore = store
                mountedEnabled = enabled
            }
            BasicText("${store.profileId}:${settings.blur}:${settings.enabled}")
        }
        compose.onNodeWithText("default:4.0:true").assertIsDisplayed()
        compose.runOnIdle { setActiveProfile("work", context) }
        compose.onNodeWithText("work:17.0:false").assertIsDisplayed()
        compose.runOnIdle { requireNotNull(mountedStore).setFloat(glassBlurKey, 21f) }
        compose.onNodeWithText("work:21.0:false").assertIsDisplayed()
        compose.runOnIdle { requireNotNull(mountedEnabled).value = true }
        compose.onNodeWithText("work:21.0:true").assertIsDisplayed()
        compose.runOnIdle { setActiveProfile(DEFAULT_PROFILE_ID, context) }
        compose.onNodeWithText("default:4.0:true").assertIsDisplayed()
        assertEquals(21f, GlassStore(context, "work").read().blur, 0f)
        assertTrue(GlassStore(context).read().rippleEnabled)
    }
}
